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

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private UUID idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletTransferStatus status;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

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

    public void startProcessing() {
        requireStatus(WalletTransferStatus.REQUESTED);
        status = WalletTransferStatus.PROCESSING;
    }

    public void complete() {
        requireStatus(WalletTransferStatus.PROCESSING);
        status = WalletTransferStatus.COMPLETED;
        failureCode = null;
    }

    public void fail(ErrorCode errorCode) {
        if (status == WalletTransferStatus.COMPLETED) {
            throw new IllegalStateException("A completed transfer cannot be marked as failed");
        }
        status = WalletTransferStatus.FAILED;
        failureCode = errorCode.name();
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
