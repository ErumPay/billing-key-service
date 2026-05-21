package com.erumpay.billing_key_service.service;

import com.erumpay.billing_key_service.client.CardSimulatorClient;
import com.erumpay.billing_key_service.common.AesCryptoUtil;
import com.erumpay.billing_key_service.common.CardCompany;
import com.erumpay.billing_key_service.common.IdempotencyKeyGenerator;
import com.erumpay.billing_key_service.common.IdempotencyKeyGenerator.Operation;
import com.erumpay.billing_key_service.common.IinMapping;
import com.erumpay.billing_key_service.common.RandomStringGenerator;
import com.erumpay.billing_key_service.dto.api.request.BillingKeyDeleteRequest;
import com.erumpay.billing_key_service.dto.api.request.BillingKeyIssueRequest;
import com.erumpay.billing_key_service.dto.api.request.BillingKeyTokenRetrieveRequest;
import com.erumpay.billing_key_service.dto.api.response.BillingKeyDeleteResponse;
import com.erumpay.billing_key_service.dto.api.response.BillingKeyIssueResponse;
import com.erumpay.billing_key_service.dto.api.response.BillingKeyTokenRetrieveResponse;
import com.erumpay.billing_key_service.dto.client.request.CardSimulatorTokenDeleteRequest;
import com.erumpay.billing_key_service.dto.client.request.CardSimulatorTokenInquireRequest;
import com.erumpay.billing_key_service.dto.client.request.CardSimulatorTokenIssueRequest;
import com.erumpay.billing_key_service.dto.client.response.CardSimulatorTokenDeleteResponse;
import com.erumpay.billing_key_service.dto.client.response.CardSimulatorTokenResponse;
import com.erumpay.billing_key_service.entity.PgBillingKey;
import com.erumpay.billing_key_service.entity.PgBillingKey.Status;
import com.erumpay.billing_key_service.repository.PgBillingKeyRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingKeyService {

    private static final String TOKEN_STATUS_ACTIVE = "ACTIVE";
    // 카드사 토큰 카테고리 SUCCESS 응답코드
    private static final String CARD_SIMULATOR_TOKEN_SUCCESS_CODE = "100";

    private final PgBillingKeyRepository billingKeyRepository;
    private final CardSimulatorClient cardSimulatorClient;
    private final IdempotencyKeyGenerator idempotencyKeyGenerator;
    private final AesCryptoUtil aesCryptoUtil;

    @Autowired
    @Lazy
    private BillingKeyService self;

    @Value("${pg.id}")
    private String pgId;

    public BillingKeyIssueResponse issue(BillingKeyIssueRequest request) {
        // 0. 중복 요청 사전 검사 — pay_card_id에 진행/활성 row가 있으면 기존 결과 echo
        Optional<BillingKeyIssueResponse> dup = findLiveRow(request.payCardId())
                .map(row -> echoExisting(row, request.payCardId()));
        if (dup.isPresent()) {
            return dup.get();
        }

        // 1. idempotency_key 생성
        String idempotencyKey = idempotencyKeyGenerator.generate(Operation.ISS);

        // 2. PENDING row INSERT (tx1). 동시 race로 UNIQUE 위반 시 echo 처리
        Long billingKeyId;
        try {
            billingKeyId = self.insertPending(idempotencyKey, request.payCardId());
        } catch (DataIntegrityViolationException e) {
            return findLiveRow(request.payCardId())
                    .map(row -> echoExisting(row, request.payCardId()))
                    .orElseThrow(() -> e);
        }

        // 3. IIN으로 카드사 식별
        CardCompany cardCompany = IinMapping.findByCardNumber(request.cardNumber());

        // 4. 카드사 토큰 발급 호출 (트랜잭션 외부)
        CardSimulatorTokenIssueRequest tokenRequest = new CardSimulatorTokenIssueRequest(
                pgId, cardCompany, request.cardNumber(), request.expiryDate(),
                request.cvc(), request.password2digit(), request.birthDate());
        IssueOutcome outcome = callIssueWithTimeoutFallback(idempotencyKey, tokenRequest);

        // 5. 응답 처리 (tx2)
        return self.applyIssueResult(billingKeyId, request.payCardId(), cardCompany, outcome);
    }

    private Optional<PgBillingKey> findLiveRow(Long payCardId) {
        return billingKeyRepository.findFirstByPayCardIdAndStatusIn(
                payCardId, List.of(Status.PENDING, Status.ACTIVE, Status.UNKNOWN));
    }

    private BillingKeyIssueResponse echoExisting(PgBillingKey row, Long payCardId) {
        if (row.getStatus() == Status.ACTIVE) {
            return BillingKeyIssueResponse.builder()
                    .payCardId(payCardId)
                    .billingKey(row.getBillingKey())
                    .maskedNumber(row.getMaskedNumber())
                    .cardCompany(row.getCardCompany())
                    .responseCode(null)
                    .responseMessage("이미 발급된 빌링키")
                    .build();
        }
        // PENDING / UNKNOWN
        return BillingKeyIssueResponse.builder()
                .payCardId(payCardId)
                .billingKey(null)
                .maskedNumber(null)
                .cardCompany(row.getCardCompany())
                .responseCode(null)
                .responseMessage("발급 처리 중")
                .build();
    }

    @Transactional
    public Long insertPending(String idempotencyKey, Long payCardId) {
        return billingKeyRepository.save(PgBillingKey.builder()
                .idempotencyKey(idempotencyKey)
                .payCardId(payCardId)
                .status(Status.PENDING)
                .build()).getBillingKeyId();
    }

    @Transactional
    public BillingKeyIssueResponse applyIssueResult(Long billingKeyId, Long payCardId, CardCompany cardCompany,
                                                    IssueOutcome outcome) {
        PgBillingKey pending = billingKeyRepository.findById(billingKeyId)
                .orElseThrow(() -> new EntityNotFoundException("PgBillingKey not found: " + billingKeyId));
        CardSimulatorTokenResponse tokenResponse = outcome.response();

        if (tokenResponse != null && TOKEN_STATUS_ACTIVE.equals(tokenResponse.tokenStatus())) {
            String billingKey = RandomStringGenerator.generateUuidV4NoHyphen();
            pending.activate(
                    billingKey,
                    aesCryptoUtil.encrypt(tokenResponse.cardToken()),
                    tokenResponse.maskedNumber(),
                    tokenResponse.cardCompany());
            return BillingKeyIssueResponse.builder()
                    .payCardId(payCardId)
                    .billingKey(billingKey)
                    .maskedNumber(tokenResponse.maskedNumber())
                    .cardCompany(tokenResponse.cardCompany())
                    .responseCode(tokenResponse.responseCode())
                    .responseMessage(tokenResponse.responseMessage())
                    .build();
        }

        // 발급+조회 모두 타임아웃: 카드사 측 실제 상태 미확정 → UNKNOWN으로 마킹 후 폴링에 위임.
        // 사용자에게는 즉시 실패 응답을 돌려주되, DB는 reconciliation 대상으로 유지.
        if (outcome.inquireFailed()) {
            pending.markUnknown();
        } else {
            pending.markFailed();
        }
        return BillingKeyIssueResponse.builder()
                .payCardId(payCardId)
                .billingKey(null)
                .maskedNumber(tokenResponse == null ? null : tokenResponse.maskedNumber())
                .cardCompany(cardCompany)
                .responseCode(tokenResponse == null ? null : tokenResponse.responseCode())
                .responseMessage(tokenResponse == null ? "카드사 통신 실패" : tokenResponse.responseMessage())
                .build();
    }

    public BillingKeyDeleteResponse delete(BillingKeyDeleteRequest request) {
        // 1. ACTIVE 빌링키 조회 (tx1, read-only)
        ActiveBillingKeySnapshot snapshot = self.loadActiveForDelete(request);

        // 2. 카드사 호출용 idempotency_key 생성 (트랜잭션 외부)
        String idempotencyKey = idempotencyKeyGenerator.generate(Operation.DEL);

        // 3. 카드사 토큰 삭제 호출 (트랜잭션 외부)
        CardSimulatorTokenDeleteResponse tokenResponse;
        try {
            tokenResponse = cardSimulatorClient.deleteToken(idempotencyKey,
                    new CardSimulatorTokenDeleteRequest(pgId, snapshot.cardCompany(), snapshot.plainCardToken()));
        } catch (feign.RetryableException e) {
            log.error("카드사 토큰 삭제 타임아웃/IO 실패", e);
            // status='ACTIVE' 유지, Pay 서버에 통신 실패 응답
            return BillingKeyDeleteResponse.builder()
                    .payCardId(request.payCardId())
                    .billingKey(request.billingKey())
                    .responseCode(null)
                    .responseMessage("카드사 통신 실패")
                    .build();
        }

        // 4. 응답에 따른 상태 변경 (tx2). 응답코드/메시지는 DB 미저장, Pay 서버에 그대로 전달
        if (tokenResponse != null && CARD_SIMULATOR_TOKEN_SUCCESS_CODE.equals(tokenResponse.responseCode())) {
            self.markBillingKeyDeleted(snapshot.billingKeyId());
        }
        return BillingKeyDeleteResponse.builder()
                .payCardId(request.payCardId())
                .billingKey(request.billingKey())
                .responseCode(tokenResponse == null ? null : tokenResponse.responseCode())
                .responseMessage(tokenResponse == null ? null : tokenResponse.responseMessage())
                .build();
    }

    @Transactional(readOnly = true)
    public ActiveBillingKeySnapshot loadActiveForDelete(BillingKeyDeleteRequest request) {
        PgBillingKey active = billingKeyRepository
                .findByPayCardIdAndBillingKeyAndStatus(request.payCardId(), request.billingKey(), Status.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ACTIVE 빌링키를 찾을 수 없습니다."));
        return new ActiveBillingKeySnapshot(
                active.getBillingKeyId(),
                active.getCardCompany(),
                aesCryptoUtil.decrypt(active.getCardToken()));
    }

    @Transactional
    public void markBillingKeyDeleted(Long billingKeyId) {
        PgBillingKey row = billingKeyRepository.findById(billingKeyId)
                .orElseThrow(() -> new EntityNotFoundException("PgBillingKey not found: " + billingKeyId));
        row.markDeleted();
    }

    @Transactional(readOnly = true)
    public BillingKeyTokenRetrieveResponse tokenRetrieve(BillingKeyTokenRetrieveRequest request) {
        PgBillingKey billingKey = billingKeyRepository
                .findByBillingKeyAndStatus(request.billingKey(), Status.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ACTIVE 빌링키를 찾을 수 없습니다."));

        String cardToken = aesCryptoUtil.decrypt(billingKey.getCardToken());

        return BillingKeyTokenRetrieveResponse.builder()
                .billingKey(billingKey.getBillingKey())
                .cardToken(cardToken)
                .cardCompany(billingKey.getCardCompany())
                .build();
    }

    private IssueOutcome callIssueWithTimeoutFallback(String idempotencyKey,
                                                       CardSimulatorTokenIssueRequest request) {
        try {
            return new IssueOutcome(cardSimulatorClient.issueToken(idempotencyKey, request), false);
        } catch (feign.RetryableException e) {
            // 타임아웃/IO 실패만 fallback. 4xx/5xx 같은 비즈니스 응답은 호출자에게 전파
            log.warn("카드사 토큰 발급 타임아웃/IO 실패, 조회 API로 재확인 시도: {}", e.getMessage());
            try {
                return new IssueOutcome(
                        cardSimulatorClient.inquireToken(new CardSimulatorTokenInquireRequest(idempotencyKey)),
                        false);
            } catch (feign.RetryableException inner) {
                log.error("카드사 토큰 조회도 타임아웃/IO 실패. UNKNOWN 마킹 후 폴링에 위임", inner);
                return new IssueOutcome(null, true);
            }
        }
    }

    public record ActiveBillingKeySnapshot(Long billingKeyId, CardCompany cardCompany, String plainCardToken) {
    }

    public record IssueOutcome(CardSimulatorTokenResponse response, boolean inquireFailed) {
    }
}
