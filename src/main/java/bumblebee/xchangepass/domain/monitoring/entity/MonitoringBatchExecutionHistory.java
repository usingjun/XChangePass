package bumblebee.xchangepass.domain.monitoring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "monitoring_batch_execution_history")
public class MonitoringBatchExecutionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_type", nullable = false, updatable = false, length = 60)
    private String batchType;

    @Column(name = "target_from", nullable = false, updatable = false)
    private LocalDateTime targetFrom;

    @Column(name = "target_to", nullable = false, updatable = false)
    private LocalDateTime targetTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MonitoringBatchStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "processed_count", nullable = false)
    private long processedCount;

    @Column(name = "success_count", nullable = false)
    private long successCount;

    @Column(name = "failure_count", nullable = false)
    private long failureCount;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    protected MonitoringBatchExecutionHistory() {
    }

    public MonitoringBatchExecutionHistory(String batchType, LocalDateTime targetFrom,
                                           LocalDateTime targetTo) {
        this.batchType = batchType;
        this.targetFrom = targetFrom;
        this.targetTo = targetTo;
        status = MonitoringBatchStatus.RUNNING;
        startedAt = LocalDateTime.now();
    }

    public void complete(long processedCount, long successCount) {
        this.status = MonitoringBatchStatus.COMPLETED;
        this.finishedAt = LocalDateTime.now();
        this.processedCount = processedCount;
        this.successCount = successCount;
        this.failureCount = 0;
        this.errorMessage = null;
    }

    public void fail(long processedCount, String errorMessage) {
        this.status = MonitoringBatchStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
        this.processedCount = processedCount;
        this.successCount = 0;
        this.failureCount = processedCount;
        this.errorMessage = errorMessage == null ? null : errorMessage.substring(0, Math.min(1000, errorMessage.length()));
    }
}
