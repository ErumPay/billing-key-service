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
import com.erumpay.billing_key_service.response.BillingResponseCode;
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
    // 카드시뮬 토큰 발급/삭제 공통 성공 응답코드
    private static final String CARD_SIMULATOR_TOKEN_SUCCESS_CODE = "SIM-TOKEN-100";

    private final PgBillingKeyRepository billingKeyRepository;
    private final CardSimulatorClient cardSimulatorClient;
    private final IdempotencyKeyGenerator idempotencyKeyGenerator;
    private final AesCryptoUtil aesCryptoUtil;

    @Autowired
    @Lazy
    private BillingKeyService self;

    @Value("${pg.id}")
    private String pgId;


    // [be] 하지혁 260603 BillingKey API 1 : 빌링키 발급
    public BillingKeyIssueResponse issue(BillingKeyIssueRequest request) {
        // 1. pay_card_id 기반 중복 요청 검사
        Optional<BillingKeyIssueResponse> dup = findLiveRow(request.payCardId())
            .map(row -> echoExisting(row, request.payCardId()));
        // 중복 시 echo
        if (dup.isPresent()) { return dup.get(); }

        // 2. 카드사 토큰 발급용 멱등성 키 생성
        String idempotencyKey = idempotencyKeyGenerator.generate(Operation.ISS);

        // 3. 발급 중(PENDING) 상태로 DB 갱신
        Long billingKeyId;
        try {
            billingKeyId = self.insertPending(idempotencyKey, request.payCardId());
        }
        // UNIQUE 위반 시 중복 요청 echo로 재시도
        catch (DataIntegrityViolationException e) {
            return findLiveRow(request.payCardId())
                .map(row -> echoExisting(row, request.payCardId()))
                .orElseThrow(() -> e);
        }

        // 4. 카드번호로 카드사 식별
        CardCompany cardCompany = IinMapping.findByCardNumber(request.cardNumber());

        // 5. 재시도 포함 카드사 토큰 발급 API 요청
        CardSimulatorTokenIssueRequest tokenRequest = new CardSimulatorTokenIssueRequest(
            pgId,
            cardCompany,
            request.cardNumber(),
            request.expiryDate(),
            request.cvc(),
            request.password2digit(),
            request.birthDate()
        );
        IssueOutcome outcome = callIssueWithTimeoutFallback(idempotencyKey, tokenRequest);

        // 6. 카드사 통신 결과에 따른 상태 전이 및 응답 반환
        return self.applyIssueResult(
            billingKeyId,
            request.payCardId(),
            cardCompany,
            outcome);
    }

    // pay_card_id 기반 PENDING/ACTIVE 빌링키 조회 (API 1)
    private Optional<PgBillingKey> findLiveRow(Long payCardId) {
        return billingKeyRepository.findFirstByPayCardIdAndStatusIn(payCardId, List.of(Status.PENDING, Status.ACTIVE));
    }

    // 중복 요청에 대한 echo 응답 (API 1)
    private BillingKeyIssueResponse echoExisting(PgBillingKey row, Long payCardId) {
        if (row.getStatus() == Status.ACTIVE) {
            return buildIssueResponse(payCardId, row.getBillingKey(), row.getMaskedNumber(), row.getCardCompany(),
                    BillingResponseCode.BILLING_KEY_ALREADY_ACTIVE);
        }
        return buildIssueResponse(payCardId, null, null, null, BillingResponseCode.BILLING_KEY_PENDING);
    }

    // 발급 응답 빌더 헬퍼 (API 1)
    private BillingKeyIssueResponse buildIssueResponse(Long payCardId, String billingKey, String maskedNumber,
                                                       CardCompany cardCompany, BillingResponseCode rc) {
        return BillingKeyIssueResponse.builder()
                .payCardId(payCardId)
                .billingKey(billingKey)
                .maskedNumber(maskedNumber)
                .cardCompany(cardCompany)
                .responseHttp(rc.getHttp())
                .responseCode(rc.getCode())
                .responseReason(rc.getReason())
                .responseMessage(rc.getMessage())
                .build();
    }

    // PENDING 상태 row INSERT (API 1)
    @Transactional
    public Long insertPending(String idempotencyKey, Long payCardId) {
        return billingKeyRepository.save(PgBillingKey.builder()
            .idempotencyKey(idempotencyKey)
            .payCardId(payCardId)
            .status(Status.PENDING)
            .build()).getBillingKeyId();
    }

    // 카드사 통신 결과에 따른 상태 전이 (API 1)
    @Transactional
    public BillingKeyIssueResponse applyIssueResult(Long billingKeyId,
                                                    Long payCardId,
                                                    CardCompany cardCompany,
                                                    IssueOutcome outcome) {
        PgBillingKey pending = billingKeyRepository.findById(billingKeyId)
            .orElseThrow(() -> new EntityNotFoundException("PgBillingKey not found: " + billingKeyId));
        CardSimulatorTokenResponse tokenResponse = outcome.response();

        // 발급 성공: ACTIVE 전이
        if (tokenResponse != null && TOKEN_STATUS_ACTIVE.equals(tokenResponse.tokenStatus())) {
            String billingKey = RandomStringGenerator.generateUuidV4NoHyphen();
            pending.activate(billingKey, aesCryptoUtil.encrypt(tokenResponse.cardToken()),tokenResponse.maskedNumber(), tokenResponse.cardCompany());
            return buildIssueResponse(payCardId, billingKey, tokenResponse.maskedNumber(), tokenResponse.cardCompany(),
                    BillingResponseCode.BILLING_KEY_SUCCESS);
        }

        // 발급 실패: 조회까지 실패 시 UNKNOWN(reconciliation 대상), 그 외 FAILED
        if (outcome.inquireFailed()) {
            pending.markUnknown();
        }
        else {
            pending.markFailed();
        }
        // SIM 응답 → BIL 응답 1:1 매핑. 매핑 실패 시 통일된 null 응답으로 raw 노출 차단
        BillingResponseCode rc = tokenResponse == null
                ? null
                : BillingResponseCode.fromSimulatorCode(tokenResponse.responseCode());
        if (rc == null) {
            return BillingKeyIssueResponse.builder()
                    .payCardId(payCardId)
                    .billingKey(null)
                    .maskedNumber(tokenResponse == null ? null : tokenResponse.maskedNumber())
                    .cardCompany(cardCompany)
                    .responseHttp(null)
                    .responseCode(null)
                    .responseReason(null)
                    .responseMessage(tokenResponse == null ? "카드사 통신 실패" : tokenResponse.responseMessage())
                    .build();
        }
        return buildIssueResponse(payCardId,
                null,
                tokenResponse.maskedNumber(),
                cardCompany,
                rc);
    }

    // [be] 하지혁 260603 BillingKey API 2 : 빌링키 삭제
    public BillingKeyDeleteResponse delete(BillingKeyDeleteRequest request) {
        // 1. ACTIVE 빌링키 조회 (tx1, read-only)
        ActiveBillingKeySnapshot snapshot;
        try {
            snapshot = self.loadActiveForDelete(request);
        } catch (ResponseStatusException e) {
            // 미존재 시 예외처리 (명세 C장: 200+BIL-KEY-103)
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                BillingResponseCode rc = BillingResponseCode.BILLING_KEY_NOT_FOUND;
                return BillingKeyDeleteResponse.builder()
                        .payCardId(request.payCardId())
                        .billingKey(request.billingKey())
                        .responseHttp(rc.getHttp())
                        .responseCode(rc.getCode())
                        .responseReason(rc.getReason())
                        .responseMessage(rc.getMessage())
                        .build();
            }
            throw e;
        }

        // 2. 카드사 호출용 idempotency_key 생성 (트랜잭션 외부)
        String idempotencyKey = idempotencyKeyGenerator.generate(Operation.DEL);

        // 3. 카드사 토큰 삭제 호출 (트랜잭션 외부)
        CardSimulatorTokenDeleteResponse tokenResponse;
        try {
            tokenResponse = cardSimulatorClient.deleteToken(idempotencyKey,
                    new CardSimulatorTokenDeleteRequest(pgId, snapshot.cardCompany(), snapshot.plainCardToken()));
        } catch (feign.RetryableException e) {
            // 카드사 통신 실패 예외처리: status=ACTIVE 유지, Pay 서버에 통신 실패 응답
            log.error("카드사 토큰 삭제 타임아웃/IO 실패", e);
            return BillingKeyDeleteResponse.builder()
                    .payCardId(request.payCardId())
                    .billingKey(request.billingKey())
                    .responseHttp(null)
                    .responseCode(null)
                    .responseReason(null)
                    .responseMessage("카드사 통신 실패")
                    .build();
        }

        // 4. 응답에 따른 상태 변경 (tx2). SIM 응답 → BIL 응답 1:1 매핑
        if (tokenResponse != null && CARD_SIMULATOR_TOKEN_SUCCESS_CODE.equals(tokenResponse.responseCode())) {
            self.markBillingKeyDeleted(snapshot.billingKeyId());
        }
        BillingResponseCode rc = tokenResponse == null
                ? null
                : BillingResponseCode.fromSimulatorCode(tokenResponse.responseCode());
        return buildDeleteResponse(request.payCardId(), request.billingKey(), rc, tokenResponse);
    }

    // 삭제 응답 빌더 헬퍼 (API 2)
    private BillingKeyDeleteResponse buildDeleteResponse(Long payCardId, String billingKey,
                                                          BillingResponseCode rc,
                                                          CardSimulatorTokenDeleteResponse fallback) {
        if (rc != null) {
            return BillingKeyDeleteResponse.builder()
                    .payCardId(payCardId)
                    .billingKey(billingKey)
                    .responseHttp(rc.getHttp())
                    .responseCode(rc.getCode())
                    .responseReason(rc.getReason())
                    .responseMessage(rc.getMessage())
                    .build();
        }
        // 매핑 미정의: SIM raw 응답을 그대로 노출하지 않고 통신 실패 형식으로 전달
        return BillingKeyDeleteResponse.builder()
                .payCardId(payCardId)
                .billingKey(billingKey)
                .responseHttp(null)
                .responseCode(null)
                .responseReason(null)
                .responseMessage(fallback == null ? null : fallback.responseMessage())
                .build();
    }

    // ACTIVE 빌링키 조회 + 토큰 평문 복호화 (API 2)
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

    // 빌링키 DELETED 전이 (API 2)
    @Transactional
    public void markBillingKeyDeleted(Long billingKeyId) {
        PgBillingKey row = billingKeyRepository.findById(billingKeyId)
                .orElseThrow(() -> new EntityNotFoundException("PgBillingKey not found: " + billingKeyId));
        row.markDeleted();
    }

    // [be] 하지혁 260603 BillingKey API 3 : 빌링키 토큰 조회
    @Transactional(readOnly = true)
    public BillingKeyTokenRetrieveResponse tokenRetrieve(BillingKeyTokenRetrieveRequest request) {
        // 미존재 시 예외처리 (명세 C장: 200+BIL-KEY-103)
        PgBillingKey billingKey = billingKeyRepository
                .findByBillingKeyAndStatus(request.billingKey(), Status.ACTIVE)
                .orElse(null);
        if (billingKey == null) {
            BillingResponseCode rc = BillingResponseCode.BILLING_KEY_NOT_FOUND;
            return BillingKeyTokenRetrieveResponse.builder()
                    .billingKey(request.billingKey())
                    .cardToken(null)
                    .cardCompany(null)
                    .responseHttp(rc.getHttp())
                    .responseCode(rc.getCode())
                    .responseReason(rc.getReason())
                    .responseMessage(rc.getMessage())
                    .build();
        }

        String cardToken = aesCryptoUtil.decrypt(billingKey.getCardToken());
        BillingResponseCode rc = BillingResponseCode.BILLING_KEY_SUCCESS;
        return BillingKeyTokenRetrieveResponse.builder()
                .billingKey(billingKey.getBillingKey())
                .cardToken(cardToken)
                .cardCompany(billingKey.getCardCompany())
                .responseHttp(rc.getHttp())
                .responseCode(rc.getCode())
                .responseReason(rc.getReason())
                .responseMessage(rc.getMessage())
                .build();
    }

    // 카드사 토큰 발급 + 타임아웃 시 조회로 폴백 (API 1)
    private IssueOutcome callIssueWithTimeoutFallback(String idempotencyKey, CardSimulatorTokenIssueRequest request) {
        try {
            return new IssueOutcome(cardSimulatorClient.issueToken(idempotencyKey, request), false);
        } catch (feign.RetryableException e) {
            log.warn("카드사 토큰 발급 타임아웃/IO 실패, 조회 API로 재확인 시도: {}", e.getMessage());
            try {
                return new IssueOutcome(cardSimulatorClient.inquireToken(new CardSimulatorTokenInquireRequest(idempotencyKey)), false);
            } catch (feign.RetryableException inner) {
                // 조회까지 실패 시 UNKNOWN 마킹 후 reconciliation에 위임
                log.error("카드사 토큰 조회도 타임아웃/IO 실패. UNKNOWN 마킹 후 Polling에 위임", inner);
                return new IssueOutcome(null, true);
            }
        }
    }

    public record ActiveBillingKeySnapshot(Long billingKeyId, CardCompany cardCompany, String plainCardToken) {
    }

    public record IssueOutcome(CardSimulatorTokenResponse response, boolean inquireFailed) {
    }
}
