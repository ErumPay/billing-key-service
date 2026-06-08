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

    private static final long FIRST_POLL_DELAY_SECONDS = 30;

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

    @Column(name = "card_company", length = 50)
    private CardCompany cardCompany;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "unknown_since")
    private LocalDateTime unknownSince;

    @Column(name = "poll_retry_count", nullable = false)
    private int pollRetryCount;

    @Column(name = "next_poll_at")
    private LocalDateTime nextPollAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING, ACTIVE, UNKNOWN, DELETED, FAILED
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
        if (this.status != Status.PENDING && this.status != Status.UNKNOWN) {
            throw new IllegalStateException("Only PENDING/UNKNOWN billing keys can be marked as failed. current=" + this.status);
        }
        this.status = Status.FAILED;
    }

    public void markUnknown() {
        if (this.status != Status.PENDING) {
            throw new IllegalStateException("Only PENDING billing keys can be marked as unknown. current=" + this.status);
        }
        LocalDateTime now = LocalDateTime.now();
        this.status = Status.UNKNOWN;
        this.unknownSince = now;
        this.pollRetryCount = 0;
        this.nextPollAt = now.plusSeconds(FIRST_POLL_DELAY_SECONDS);
    }

    public void recordPollAttempt(LocalDateTime nextPollAt) {
        if (this.status != Status.UNKNOWN) {
            throw new IllegalStateException("Only UNKNOWN billing keys can record poll attempts. current=" + this.status);
        }
        this.pollRetryCount += 1;
        this.nextPollAt = nextPollAt;
    }

    public void markDeleted() {
        if (this.status != Status.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE billing keys can be deleted. current=" + this.status);
        }
        this.status = Status.DELETED;
    }
}
