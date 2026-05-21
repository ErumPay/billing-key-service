package com.erumpay.billing_key_service.repository;

import com.erumpay.billing_key_service.entity.PgBillingKey;
import com.erumpay.billing_key_service.entity.PgBillingKey.Status;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PgBillingKeyRepository extends JpaRepository<PgBillingKey, Long> {

    Optional<PgBillingKey> findByBillingKeyAndStatus(String billingKey, Status status);

    Optional<PgBillingKey> findByPayCardIdAndBillingKeyAndStatus(Long payCardId, String billingKey, Status status);

    List<PgBillingKey> findByStatusAndNextPollAtLessThanEqual(Status status, LocalDateTime threshold, Pageable pageable);

    // 진행/활성 row (PENDING, ACTIVE, UNKNOWN) — live_pay_card_id UNIQUE 덕분에 최대 1건만 존재
    Optional<PgBillingKey> findFirstByPayCardIdAndStatusIn(Long payCardId, List<Status> statuses);
}
