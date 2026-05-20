package com.erumpay.billing_key_service.service;

import com.erumpay.billing_key_service.client.CardSimulatorClient;
import com.erumpay.billing_key_service.common.AesCryptoUtil;
import com.erumpay.billing_key_service.common.CardCompany;
import com.erumpay.billing_key_service.common.IdempotencyKeyGenerator;
import com.erumpay.billing_key_service.common.IdempotencyKeyGenerator.Operation;
import com.erumpay.billing_key_service.common.IinMapping;
import com.erumpay.billing_key_service.common.RandomStringGenerator;
import com.erumpay.billing_key_service.dto.BillingKeyDeleteRequest;
import com.erumpay.billing_key_service.dto.BillingKeyDeleteResponse;
import com.erumpay.billing_key_service.dto.BillingKeyIssueRequest;
import com.erumpay.billing_key_service.dto.BillingKeyIssueResponse;
import com.erumpay.billing_key_service.dto.BillingKeyTokenRetrieveRequest;
import com.erumpay.billing_key_service.dto.BillingKeyTokenRetrieveResponse;
import com.erumpay.billing_key_service.dto.CardSimulatorTokenDeleteRequest;
import com.erumpay.billing_key_service.dto.CardSimulatorTokenDeleteResponse;
import com.erumpay.billing_key_service.dto.CardSimulatorTokenInquireRequest;
import com.erumpay.billing_key_service.dto.CardSimulatorTokenIssueRequest;
import com.erumpay.billing_key_service.dto.CardSimulatorTokenResponse;
import com.erumpay.billing_key_service.entity.PgBillingKey;
import com.erumpay.billing_key_service.entity.PgBillingKey.Status;
import com.erumpay.billing_key_service.repository.PgBillingKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingKeyService {

    private static final String TOKEN_STATUS_ACTIVE = "ACTIVE";

    private final PgBillingKeyRepository billingKeyRepository;
    private final CardSimulatorClient cardSimulatorClient;
    private final IdempotencyKeyGenerator idempotencyKeyGenerator;
    private final AesCryptoUtil aesCryptoUtil;

    @Value("${pg.id}")
    private String pgId;

    @Transactional
    public BillingKeyIssueResponse issue(BillingKeyIssueRequest request) {
        // 1. idempotency_key 생성
        String idempotencyKey = idempotencyKeyGenerator.generate(Operation.ISS);

        // 2. PENDING row INSERT
        PgBillingKey pending = billingKeyRepository.save(PgBillingKey.builder()
                .idempotencyKey(idempotencyKey)
                .payCardId(request.payCardId())
                .status(Status.PENDING)
                .build());

        // 3. IIN으로 카드사 식별
        CardCompany cardCompany = IinMapping.findByCardNumber(request.cardNumber());

        // 4. 카드사 토큰 발급 호출 (타임아웃 시 조회 API로 재확인)
        CardSimulatorTokenIssueRequest tokenRequest = new CardSimulatorTokenIssueRequest(
                pgId, cardCompany, request.cardNumber(), request.expiryDate(),
                request.cvc(), request.password2digit(), request.birthDate());
        CardSimulatorTokenResponse tokenResponse = callIssueWithTimeoutFallback(idempotencyKey, tokenRequest);

        // 5. 응답 처리
        if (tokenResponse != null && TOKEN_STATUS_ACTIVE.equals(tokenResponse.tokenStatus())) {
            String billingKey = RandomStringGenerator.generateUuidV4NoHyphen();
            pending.activate(
                    billingKey,
                    aesCryptoUtil.encrypt(tokenResponse.cardToken()),
                    tokenResponse.maskedNumber(),
                    tokenResponse.cardCompany());
            return BillingKeyIssueResponse.builder()
                    .payCardId(request.payCardId())
                    .billingKey(billingKey)
                    .maskedNumber(tokenResponse.maskedNumber())
                    .cardCompany(tokenResponse.cardCompany())
                    .responseCode(tokenResponse.responseCode())
                    .responseMessage(tokenResponse.responseMessage())
                    .build();
        }

        // 실패 처리
        pending.markFailed();
        return BillingKeyIssueResponse.builder()
                .payCardId(request.payCardId())
                .billingKey(null)
                .maskedNumber(tokenResponse == null ? null : tokenResponse.maskedNumber())
                .cardCompany(cardCompany)
                .responseCode(tokenResponse == null ? null : tokenResponse.responseCode())
                .responseMessage(tokenResponse == null ? "카드사 통신 실패" : tokenResponse.responseMessage())
                .build();
    }

    @Transactional
    public BillingKeyDeleteResponse delete(BillingKeyDeleteRequest request) {
        // 1. pay_card_id + billing_key 일치 ACTIVE 행 조회
        PgBillingKey active = billingKeyRepository
                .findByPayCardIdAndBillingKeyAndStatus(request.payCardId(), request.billingKey(), Status.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ACTIVE 빌링키를 찾을 수 없습니다."));

        // 2. card_token 복호화 + 카드사 호출용 idempotency_key 생성 (OP=DEL, DB 미저장)
        String plainCardToken = aesCryptoUtil.decrypt(active.getCardToken());
        String idempotencyKey = idempotencyKeyGenerator.generate(Operation.DEL);

        // 3. 카드사 토큰 삭제 호출
        CardSimulatorTokenDeleteResponse tokenResponse;
        try {
            tokenResponse = cardSimulatorClient.deleteToken(idempotencyKey,
                    new CardSimulatorTokenDeleteRequest(pgId, active.getCardCompany(), plainCardToken));
        } catch (feign.RetryableException e) {
            log.error("카드사 토큰 삭제 타임아웃/IO 실패", e);
            // 실패: status='ACTIVE' 유지, Pay 서버에 통신 실패 응답
            return BillingKeyDeleteResponse.builder()
                    .payCardId(request.payCardId())
                    .billingKey(request.billingKey())
                    .responseCode(null)
                    .responseMessage("카드사 통신 실패")
                    .build();
        }

        // 4. 응답 처리 (응답코드/메시지 DB 미저장, Pay 서버에 그대로 전달)
        boolean success = tokenResponse != null
                && isSuccessResponse(tokenResponse.responseCode());
        if (success) {
            active.markDeleted();
        }
        // 실패 시 status는 ACTIVE 유지 (재시도 가능)

        return BillingKeyDeleteResponse.builder()
                .payCardId(request.payCardId())
                .billingKey(request.billingKey())
                .responseCode(tokenResponse == null ? null : tokenResponse.responseCode())
                .responseMessage(tokenResponse == null ? null : tokenResponse.responseMessage())
                .build();
    }

    // 카드사 응답코드의 SUCCESS 분기는 TOKEN 카테고리 SUCCESS 코드("100")
    private boolean isSuccessResponse(String responseCode) {
        return "100".equals(responseCode);
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

    private CardSimulatorTokenResponse callIssueWithTimeoutFallback(String idempotencyKey,
                                                                     CardSimulatorTokenIssueRequest request) {
        try {
            return cardSimulatorClient.issueToken(idempotencyKey, request);
        } catch (feign.RetryableException e) {
            // 타임아웃/IO 실패만 fallback. 4xx/5xx 같은 비즈니스 응답은 호출자에게 전파
            log.warn("카드사 토큰 발급 타임아웃/IO 실패, 조회 API로 재확인 시도: {}", e.getMessage());
            try {
                return cardSimulatorClient.inquireToken(new CardSimulatorTokenInquireRequest(idempotencyKey));
            } catch (feign.RetryableException inner) {
                log.error("카드사 토큰 조회도 타임아웃/IO 실패", inner);
                return null;
            }
        }
    }
}
