package com.erumpay.billing_key_service.service;

import com.erumpay.billing_key_service.client.CardSimulatorClient;
import com.erumpay.billing_key_service.common.AesCryptoUtil;
import com.erumpay.billing_key_service.common.CardCompany;
import com.erumpay.billing_key_service.common.IdempotencyKeyGenerator;
import com.erumpay.billing_key_service.common.IdempotencyKeyGenerator.Operation;
import com.erumpay.billing_key_service.common.IinMapping;
import com.erumpay.billing_key_service.common.RandomStringGenerator;
import com.erumpay.billing_key_service.dto.BillingKeyIssueRequest;
import com.erumpay.billing_key_service.dto.BillingKeyIssueResponse;
import com.erumpay.billing_key_service.dto.CardSimulatorTokenInquireRequest;
import com.erumpay.billing_key_service.dto.CardSimulatorTokenIssueRequest;
import com.erumpay.billing_key_service.dto.CardSimulatorTokenResponse;
import com.erumpay.billing_key_service.entity.PgBillingKey;
import com.erumpay.billing_key_service.entity.PgBillingKey.Status;
import com.erumpay.billing_key_service.repository.PgBillingKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
