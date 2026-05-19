package com.erumpay.billing_key_service.repository;

import com.erumpay.billing_key_service.entity.PgBillingKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PgBillingKeyRepository extends JpaRepository<PgBillingKey, Long> {
}
