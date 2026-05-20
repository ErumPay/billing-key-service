package com.erumpay.billing_key_service.repository;

import com.erumpay.billing_key_service.entity.PgBillingKey;
import com.erumpay.billing_key_service.entity.PgBillingKey.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PgBillingKeyRepository extends JpaRepository<PgBillingKey, Long> {

    Optional<PgBillingKey> findByBillingKeyAndStatus(String billingKey, Status status);

    Optional<PgBillingKey> findByPayCardIdAndBillingKeyAndStatus(Long payCardId, String billingKey, Status status);
}
