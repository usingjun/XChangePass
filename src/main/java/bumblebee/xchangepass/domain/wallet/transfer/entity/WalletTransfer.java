package bumblebee.xchangepass.domain.wallet.transfer.entity;

import bumblebee.xchangepass.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "wallet_transfer_request",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wallet_transfer_sender_idempotency",
                columnNames = {"sender_user_id", "idempotency_key"}
        )
)
public class WalletTransfer {

    @Id
    @Column(name = "transfer_id", nullable = false, updatable = false)
    private UUID transferId;

    @Column(name = "sender_user_id", nullable = false, updatable = false)
    private Long senderUserId;

    @Column(name = "receiver_user_id")
    private Long receiverUserId;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private UUID idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletTransferStatus status;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_stage", length = 40)
    private WalletTransferFailureStage failureStage;

    @Column(name = "retryable")
    private Boolean retryable;

    @Column(name = "validating_at")
    private LocalDateTime validatingAt;

    @Column(name = "processing_at")
    private LocalDateTime processingAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    protected WalletTransfer() {
    }

    public WalletTransfer(UUID transferId, Long senderUserId, UUID idempotencyKey, String requestHash) {
        this.transferId = transferId;
        this.senderUserId = senderUserId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = WalletTransferStatus.REQUESTED;
    }

    public void startValidating() {
        requireStatus(WalletTransferStatus.REQUESTED);
        status = WalletTransferStatus.VALIDATING;
        validatingAt = LocalDateTime.now();
        attemptCount++;
    }

    public void startProcessing() {
        requireStatus(WalletTransferStatus.VALIDATING);
        status = WalletTransferStatus.PROCESSING;
        processingAt = LocalDateTime.now();
    }

    public void assignReceiver(Long receiverUserId) {
        if (receiverUserId == null) {
            throw new IllegalArgumentException("receiverUserId is required");
        }
        if (this.receiverUserId != null && !this.receiverUserId.equals(receiverUserId)) {
            throw new IllegalStateException("Wallet transfer receiver cannot be changed");
        }
        this.receiverUserId = receiverUserId;
    }

    public void complete() {
        requireStatus(WalletTransferStatus.PROCESSING);
        status = WalletTransferStatus.COMPLETED;
        failureCode = null;
        failureStage = null;
        retryable = null;
        completedAt = LocalDateTime.now();
    }

    public void fail(ErrorCode errorCode, WalletTransferFailureStage stage, boolean retryable) {
        if (status == WalletTransferStatus.COMPLETED || status == WalletTransferStatus.FAILED) {
            throw new IllegalStateException("A terminal transfer cannot be marked as failed again");
        }
        status = WalletTransferStatus.FAILED;
        failureCode = errorCode.name();
        failureStage = stage;
        this.retryable = retryable;
        failedAt = LocalDateTime.now();
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private void requireStatus(WalletTransferStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Invalid wallet transfer status transition: " + status + " -> " + expected);
        }
    }
}
