package com.erumpay.billing_key_service.entity;

import com.erumpay.billing_key_service.common.CardCompany;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pg_billing_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PgBillingKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "billing_key_id")
    private Long billingKeyId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "billing_key", length = 32)
    private String billingKey;

    @Column(name = "pay_card_id", nullable = false)
    private Long payCardId;

    @Column(name = "card_token")
    private String cardToken;

    @Column(name = "masked_number", length = 25)
    private String maskedNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_company", length = 50)
    private CardCompany cardCompany;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING, ACTIVE, DELETED, FAILED
    }

    @Builder
    private PgBillingKey(String idempotencyKey, Long payCardId, Status status) {
        this.idempotencyKey = idempotencyKey;
        this.payCardId = payCardId;
        this.status = status == null ? Status.PENDING : status;
    }

    public void activate(String billingKey, String cardToken, String maskedNumber, CardCompany cardCompany) {
        if (this.status != Status.PENDING) {
            throw new IllegalStateException("Only PENDING billing keys can be activated. current=" + this.status);
        }
        this.billingKey = billingKey;
        this.cardToken = cardToken;
        this.maskedNumber = maskedNumber;
        this.cardCompany = cardCompany;
        this.status = Status.ACTIVE;
    }

    public void markFailed() {
        if (this.status != Status.PENDING) {
            throw new IllegalStateException("Only PENDING billing keys can be marked as failed. current=" + this.status);
        }
        this.status = Status.FAILED;
    }
}
