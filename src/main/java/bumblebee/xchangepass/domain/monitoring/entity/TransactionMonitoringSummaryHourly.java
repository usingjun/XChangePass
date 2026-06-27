package bumblebee.xchangepass.domain.monitoring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "transaction_monitoring_summary_hourly",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tx_monitoring_summary_hourly",
                columnNames = {
                        "summary_hour",
                        "transaction_type",
                        "event_type",
                        "status",
                        "failure_stage",
                        "error_code"
                }
        ),
        indexes = {
                @Index(name = "idx_tx_monitoring_summary_hour", columnList = "summary_hour")
        }
)
public class TransactionMonitoringSummaryHourly {

    public static final String NONE = "NONE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "summary_hour", nullable = false, updatable = false)
    private LocalDateTime summaryHour;

    @Column(name = "transaction_type", nullable = false, updatable = false, length = 20)
    private String transactionType;

    @Column(name = "event_type", nullable = false, updatable = false, length = 50)
    private String eventType;

    @Column(name = "status", nullable = false, updatable = false, length = 40)
    private String status;

    @Column(name = "failure_stage", nullable = false, updatable = false, length = 40)
    private String failureStage;

    @Column(name = "error_code", nullable = false, updatable = false, length = 80)
    private String errorCode;

    @Column(name = "event_count", nullable = false)
    private long count;

    @Column(name = "retryable_count", nullable = false)
    private long retryableCount;

    @Column(name = "non_retryable_count", nullable = false)
    private long nonRetryableCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected TransactionMonitoringSummaryHourly() {
    }

    public TransactionMonitoringSummaryHourly(LocalDateTime summaryHour, String transactionType,
                                              String eventType, String status, String failureStage,
                                              String errorCode, long count, long retryableCount,
                                              long nonRetryableCount) {
        this.summaryHour = summaryHour;
        this.transactionType = transactionType;
        this.eventType = eventType;
        this.status = status;
        this.failureStage = failureStage;
        this.errorCode = errorCode;
        this.count = count;
        this.retryableCount = retryableCount;
        this.nonRetryableCount = nonRetryableCount;
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
}
