package bumblebee.xchangepass.domain.exchangeRate.entity;

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
@Table(name = "exchange_rate_sync_history")
public class ExchangeRateSyncHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExchangeRateSyncStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "requested_base_currency_count", nullable = false)
    private int requestedBaseCurrencyCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "activated", nullable = false)
    private boolean activated;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    protected ExchangeRateSyncHistory() {
    }

    public ExchangeRateSyncHistory(int requestedBaseCurrencyCount) {
        this.status = ExchangeRateSyncStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.requestedBaseCurrencyCount = requestedBaseCurrencyCount;
        this.activated = false;
    }

    public void complete(int successCount, boolean activated) {
        this.status = ExchangeRateSyncStatus.COMPLETED;
        this.finishedAt = LocalDateTime.now();
        this.successCount = successCount;
        this.failureCount = 0;
        this.activated = activated;
        this.errorMessage = null;
    }

    public void partialFail(int successCount, int failureCount, String errorMessage) {
        this.status = ExchangeRateSyncStatus.PARTIAL_FAILED;
        this.finishedAt = LocalDateTime.now();
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.activated = false;
        this.errorMessage = truncate(errorMessage);
    }

    public void fail(String errorMessage) {
        this.status = ExchangeRateSyncStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
        this.successCount = 0;
        this.failureCount = requestedBaseCurrencyCount;
        this.activated = false;
        this.errorMessage = truncate(errorMessage);
    }

    private String truncate(String value) {
        return value == null ? null : value.substring(0, Math.min(1000, value.length()));
    }
}
