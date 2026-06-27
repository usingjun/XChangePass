package bumblebee.xchangepass.domain.monitoring.entity;

import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferFailureStage;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "transaction_status_event",
        indexes = {
                @Index(name = "idx_tx_status_event_transaction_time", columnList = "transaction_id, occurred_at"),
                @Index(name = "idx_tx_status_event_time_status", columnList = "occurred_at, current_status"),
                @Index(name = "idx_tx_status_event_time_failure", columnList = "occurred_at, failure_stage")
        }
)
public class TransactionStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, updatable = false, length = 20)
    private TransactionType transactionType;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "wallet_id", updatable = false)
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 50)
    private TransactionStatusEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", updatable = false, length = 20)
    private WalletTransferStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", updatable = false, length = 20)
    private WalletTransferStatus currentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_stage", updatable = false, length = 40)
    private WalletTransferFailureStage failureStage;

    @Column(name = "error_code", updatable = false, length = 64)
    private String errorCode;

    @Column(name = "retryable", updatable = false)
    private Boolean retryable;

    @Column(name = "idempotency_key", updatable = false)
    private UUID idempotencyKey;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected TransactionStatusEvent() {
    }

    private TransactionStatusEvent(TransactionStatusEventBuilder builder) {
        transactionId = builder.transactionId;
        transactionType = builder.transactionType;
        userId = builder.userId;
        walletId = builder.walletId;
        eventType = builder.eventType;
        previousStatus = builder.previousStatus;
        currentStatus = builder.currentStatus;
        failureStage = builder.failureStage;
        errorCode = builder.errorCode;
        retryable = builder.retryable;
        idempotencyKey = builder.idempotencyKey;
        occurredAt = builder.occurredAt == null ? LocalDateTime.now() : builder.occurredAt;
    }

    public static TransactionStatusEventBuilder builder(UUID transactionId,
                                                        TransactionStatusEventType eventType) {
        return new TransactionStatusEventBuilder(transactionId, eventType);
    }

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
        createdAt = LocalDateTime.now();
    }

    public static class TransactionStatusEventBuilder {
        private final UUID transactionId;
        private final TransactionStatusEventType eventType;
        private TransactionType transactionType = TransactionType.WALLET;
        private Long userId;
        private Long walletId;
        private WalletTransferStatus previousStatus;
        private WalletTransferStatus currentStatus;
        private WalletTransferFailureStage failureStage;
        private String errorCode;
        private Boolean retryable;
        private UUID idempotencyKey;
        private LocalDateTime occurredAt;

        private TransactionStatusEventBuilder(UUID transactionId, TransactionStatusEventType eventType) {
            this.transactionId = transactionId;
            this.eventType = eventType;
        }

        public TransactionStatusEventBuilder transactionType(TransactionType transactionType) {
            this.transactionType = transactionType;
            return this;
        }

        public TransactionStatusEventBuilder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public TransactionStatusEventBuilder walletId(Long walletId) {
            this.walletId = walletId;
            return this;
        }

        public TransactionStatusEventBuilder status(WalletTransferStatus previousStatus,
                                                    WalletTransferStatus currentStatus) {
            this.previousStatus = previousStatus;
            this.currentStatus = currentStatus;
            return this;
        }

        public TransactionStatusEventBuilder failure(WalletTransferFailureStage failureStage,
                                                     String errorCode,
                                                     Boolean retryable) {
            this.failureStage = failureStage;
            this.errorCode = errorCode;
            this.retryable = retryable;
            return this;
        }

        public TransactionStatusEventBuilder idempotencyKey(UUID idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public TransactionStatusEventBuilder occurredAt(LocalDateTime occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public TransactionStatusEvent build() {
            return new TransactionStatusEvent(this);
        }
    }
}
