
package com.example.payment_api.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.payment_api.enums.PaymentStatus;
import com.example.payment_api.exception.IllegalPaymentStateException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_account", nullable = false, length = 20)
    private String sourceAccount;

    @Column(name = "destination_account", nullable = false, length = 20)
    private String destinationAccount;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Stored as TINYINT(1): 0 = active, 1 = deleted.
    @Column(name = "is_deleted", nullable = false)
    @JdbcTypeCode(SqlTypes.TINYINT)
    private boolean deleted = false;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "idempotency_request_hash", length = 64, updatable = false)
    private String idempotencyRequestHash;

    // Optimistic locking: an edit and the processor can't silently overwrite each other.
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "bank_reference", length = 100)
    private String bankReference;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    protected Payment() {}

    public Payment(
            String sourceAccount,
            String destinationAccount,
            BigDecimal amount,
            String currency,
            String idempotencyKey,
            String idempotencyRequestHash) {
        this.idempotencyKey = idempotencyKey;
        this.idempotencyRequestHash = idempotencyRequestHash;
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
        this.currency = currency;
        this.status = PaymentStatus.PENDING;
        // MySQL TIMESTAMP stores whole seconds, so truncate to match what GET returns.
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    public void updateDetails(
            String sourceAccount,
            String destinationAccount,
            BigDecimal amount,
            String currency) {
        requireStatus(PaymentStatus.PENDING, "update");
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
        this.currency = currency;
    }

    public void markDeleted() {
        requireStatus(PaymentStatus.PENDING, "delete");
        this.deleted = true;
    }

    // Claimed by the processor; from here on the payment can't be edited or deleted.
    public void markProcessing() {
        requireStatus(PaymentStatus.PENDING, "start processing");
        this.status = PaymentStatus.PROCESSING;
    }

    public void markCompleted(String bankReference) {
        requireStatus(PaymentStatus.PROCESSING, "complete");
        this.status = PaymentStatus.COMPLETED;
        this.bankReference = bankReference;
    }

    public void markFailed(String failureReason) {
        requireStatus(PaymentStatus.PROCESSING, "fail");
        this.status = PaymentStatus.FAILED;
        this.failureReason = failureReason;
    }

    private void requireStatus(PaymentStatus required, String action) {
        if (status != required) {
            throw new IllegalPaymentStateException(id, status, action);
        }
    }

    public boolean isDeleted() {
        return deleted;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getIdempotencyRequestHash() {
        return idempotencyRequestHash;
    }

    public Long getId() {
        return id;
    }

    public String getSourceAccount() {
        return sourceAccount;
    }

    public String getDestinationAccount() {
        return destinationAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getBankReference() {
        return bankReference;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
