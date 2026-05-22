package com.erumpay.billing_key_service.service;

import com.erumpay.billing_key_service.client.CardSimulatorClient;
import com.erumpay.billing_key_service.common.IdempotencyKeyGenerator;
import com.erumpay.billing_key_service.common.IdempotencyKeyGenerator.Operation;
import com.erumpay.billing_key_service.dto.client.request.CardSimulatorTokenDeleteRequest;
import com.erumpay.billing_key_service.dto.client.request.CardSimulatorTokenInquireRequest;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BillingKeyReconciliationWorker {

    private static final String TOKEN_STATUS_ACTIVE = "ACTIVE";
    private static final int BATCH_SIZE = 20;
    // 지수 백오프: 30s, 1m, 5m, 15m, 1h, 6h (cap), 그 이상은 6시간 유지
    private static final long[] BACKOFF_SECONDS = {30, 60, 300, 900, 3600, 21600};
    // 무한 폴링 방지: 시도 횟수 초과 시 알람 후 다음 사이클로 계속 진행하되 로그 강조
    private static final int MAX_RETRY_BEFORE_ALERT = BACKOFF_SECONDS.length * 2;

    private final PgBillingKeyRepository billingKeyRepository;
    private final CardSimulatorClient cardSimulatorClient;
    private final IdempotencyKeyGenerator idempotencyKeyGenerator;

    @Autowired
    @Lazy
    private BillingKeyReconciliationWorker self;

    @Value("${pg.id}")
    private String pgId;

    @Scheduled(fixedDelayString = "${reconciliation.poll-interval-ms:30000}")
    public void reconcile() {
        List<Long> targets = self.fetchPollTargetIds();
        if (targets.isEmpty()) {
            return;
        }
        log.info("UNKNOWN reconciliation 시작: {} rows", targets.size());
        for (Long id : targets) {
            try {
                self.reconcileOne(id);
            } catch (Exception e) {
                log.error("reconcile 실패 billingKeyId={}", id, e);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Long> fetchPollTargetIds() {
        return billingKeyRepository
                .findByStatusAndNextPollAtLessThanEqual(Status.UNKNOWN, LocalDateTime.now(),
                        PageRequest.of(0, BATCH_SIZE))
                .stream()
                .map(PgBillingKey::getBillingKeyId)
                .toList();
    }

    @Transactional
    public void reconcileOne(Long billingKeyId) {
        PgBillingKey row = billingKeyRepository.findById(billingKeyId)
                .orElseThrow(() -> new EntityNotFoundException("PgBillingKey not found: " + billingKeyId));
        if (row.getStatus() != Status.UNKNOWN) {
            // 다른 사이클에서 이미 상태 전이됨
            return;
        }

        CardSimulatorTokenResponse inquireResponse;
        try {
            inquireResponse = cardSimulatorClient.inquireToken(
                    new CardSimulatorTokenInquireRequest(row.getIdempotencyKey()));
        } catch (feign.RetryableException e) {
            scheduleNextPoll(row);
            return;
        }

        if (inquireResponse != null && TOKEN_STATUS_ACTIVE.equals(inquireResponse.tokenStatus())) {
            // 카드사 측에 토큰이 살아있음 → 사용자에겐 이미 실패 응답했으므로 정합을 위해 카드사 토큰 삭제 후 FAILED 확정
            if (deleteOrphanToken(row, inquireResponse.cardToken())) {
                row.markFailed();
                log.info("UNKNOWN reconciliation: 카드사 토큰 삭제 후 FAILED 확정 billingKeyId={}", billingKeyId);
            } else {
                scheduleNextPoll(row);
            }
            return;
        }

        // 카드사에도 토큰 없음 → FAILED 확정
        row.markFailed();
        log.info("UNKNOWN reconciliation: 카드사에 토큰 없음 확인, FAILED 확정 billingKeyId={}", billingKeyId);
    }

    private boolean deleteOrphanToken(PgBillingKey row, String cardToken) {
        String deleteIdempotencyKey = idempotencyKeyGenerator.generate(Operation.DEL);
        try {
            cardSimulatorClient.deleteToken(deleteIdempotencyKey,
                    new CardSimulatorTokenDeleteRequest(pgId, row.getCardCompany(), cardToken));
            return true;
        } catch (feign.RetryableException e) {
            log.warn("orphan token 삭제 타임아웃, 다음 폴링에서 재시도 billingKeyId={}", row.getBillingKeyId());
            return false;
        }
    }

    private void scheduleNextPoll(PgBillingKey row) {
        int retry = row.getPollRetryCount();
        if (retry + 1 >= MAX_RETRY_BEFORE_ALERT) {
            // 카드사 무응답이 임계 초과 → UNKNOWN 영구 잔존 방지를 위해 FAILED 강제 확정.
            // 그 시점까지 deleteOrphanToken 시도가 충분히 이뤄졌고, 미정리 orphan은 알람으로 수동 처리.
            row.markFailed();
            log.error("UNKNOWN reconciliation 시도 횟수 초과, FAILED 강제 확정 billingKeyId={} retry={}",
                    row.getBillingKeyId(), retry + 1);
            return;
        }
        long backoff = BACKOFF_SECONDS[Math.min(retry, BACKOFF_SECONDS.length - 1)];
        row.recordPollAttempt(LocalDateTime.now().plusSeconds(backoff));
    }
}
