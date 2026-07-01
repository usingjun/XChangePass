package bumblebee.xchangepass.domain.wallet.reconciliation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "wallet_balance_reconciliation_issue")
public class WalletBalanceReconciliationIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "issue_id", nullable = false)
    private Long issueId;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "snapshot_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal snapshotAmount;

    @Column(name = "ledger_calculated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal ledgerCalculatedAmount;

    @Column(name = "difference_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal differenceAmount;

    @Column(name = "basis_time", nullable = false)
    private LocalDateTime basisTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletBalanceReconciliationIssueStatus status;

    @Column(name = "first_detected_at", nullable = false, updatable = false)
    private LocalDateTime firstDetectedAt;

    @Column(name = "last_detected_at", nullable = false)
    private LocalDateTime lastDetectedAt;

    @Column(name = "detection_count", nullable = false)
    private long detectionCount;

    @Version
    private Long version;

    protected WalletBalanceReconciliationIssue() {
    }

    public WalletBalanceReconciliationIssue(Long walletId, Long userId, String currency,
                                            BigDecimal snapshotAmount, BigDecimal ledgerCalculatedAmount,
                                            BigDecimal differenceAmount, LocalDateTime basisTime) {
        this.walletId = walletId;
        this.userId = userId;
        this.currency = currency;
        this.status = WalletBalanceReconciliationIssueStatus.OPEN;
        update(snapshotAmount, ledgerCalculatedAmount, differenceAmount, basisTime);
    }

    public void update(BigDecimal snapshotAmount, BigDecimal ledgerCalculatedAmount,
                       BigDecimal differenceAmount, LocalDateTime basisTime) {
        this.snapshotAmount = snapshotAmount;
        this.ledgerCalculatedAmount = ledgerCalculatedAmount;
        this.differenceAmount = differenceAmount;
        this.basisTime = basisTime;
        this.lastDetectedAt = LocalDateTime.now();
        this.detectionCount++;
    }

    public void resolve() {
        this.status = WalletBalanceReconciliationIssueStatus.RESOLVED;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (firstDetectedAt == null) {
            firstDetectedAt = now;
        }
        if (lastDetectedAt == null) {
            lastDetectedAt = now;
        }
        if (detectionCount == 0) {
            detectionCount = 1;
        }
    }
}
