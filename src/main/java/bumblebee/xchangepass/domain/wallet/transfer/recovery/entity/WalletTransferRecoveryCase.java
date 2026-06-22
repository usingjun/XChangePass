package bumblebee.xchangepass.domain.wallet.transfer.recovery.entity;

import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "wallet_transfer_recovery_case",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wallet_transfer_recovery_transfer_type",
                columnNames = {"transfer_id", "case_type"}
        )
)
public class WalletTransferRecoveryCase {

    @Id
    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Column(name = "transfer_id", nullable = false, updatable = false)
    private UUID transferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_type", nullable = false, updatable = false, length = 40)
    private WalletTransferRecoveryCaseType caseType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletTransferRecoveryCaseSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "observed_transfer_status", nullable = false, length = 20)
    private WalletTransferStatus observedTransferStatus;

    @Column(name = "observed_transfer_version", nullable = false)
    private Long observedTransferVersion;

    @Column(name = "observed_ledger_count", nullable = false)
    private long observedLedgerCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_status", nullable = false, length = 20)
    private WalletTransferRecoveryCaseStatus caseStatus;

    @Column(name = "first_detected_at", nullable = false, updatable = false)
    private LocalDateTime firstDetectedAt;

    @Column(name = "last_detected_at", nullable = false)
    private LocalDateTime lastDetectedAt;

    @Column(name = "detection_count", nullable = false)
    private long detectionCount;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Version
    private Long version;

    protected WalletTransferRecoveryCase() {
    }

    public WalletTransferRecoveryCase(UUID transferId, WalletTransferRecoveryCaseType caseType,
                                      WalletTransferRecoveryCaseSeverity severity,
                                      WalletTransferStatus observedStatus, Long observedVersion,
                                      long observedLedgerCount) {
        this.caseId = UUID.randomUUID();
        this.transferId = transferId;
        this.caseType = caseType;
        this.severity = severity;
        this.observedTransferStatus = observedStatus;
        this.observedTransferVersion = observedVersion;
        this.observedLedgerCount = observedLedgerCount;
        this.caseStatus = WalletTransferRecoveryCaseStatus.OPEN;
    }

    public void reobserve(WalletTransferRecoveryCaseSeverity severity,
                          WalletTransferStatus observedStatus, Long observedVersion,
                          long observedLedgerCount) {
        this.severity = severity;
        this.observedTransferStatus = observedStatus;
        this.observedTransferVersion = observedVersion;
        this.observedLedgerCount = observedLedgerCount;
        this.lastDetectedAt = LocalDateTime.now();
        this.detectionCount++;
        if (caseStatus == WalletTransferRecoveryCaseStatus.RESOLVED) {
            caseStatus = WalletTransferRecoveryCaseStatus.OPEN;
            resolutionNote = null;
            resolvedAt = null;
        }
    }

    public void acknowledge() {
        if (caseStatus != WalletTransferRecoveryCaseStatus.OPEN) {
            throw new IllegalStateException("Only an open recovery case can be acknowledged");
        }
        caseStatus = WalletTransferRecoveryCaseStatus.ACKNOWLEDGED;
    }

    public void resolve(String note) {
        if (caseStatus == WalletTransferRecoveryCaseStatus.RESOLVED) {
            throw new IllegalStateException("Recovery case is already resolved");
        }
        caseStatus = WalletTransferRecoveryCaseStatus.RESOLVED;
        resolutionNote = note;
        resolvedAt = LocalDateTime.now();
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        firstDetectedAt = now;
        lastDetectedAt = now;
        detectionCount = 1;
    }
}
