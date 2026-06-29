package bumblebee.xchangepass.domain.exchangeRate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "exchange_rate_sync_failure")
public class ExchangeRateSyncFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sync_history_id", nullable = false)
    private ExchangeRateSyncHistory syncHistory;

    @Column(name = "base_currency", nullable = false, length = 10)
    private String baseCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type", nullable = false, length = 40)
    private ExchangeRateSyncFailureType failureType;

    @Column(name = "raw_payload", columnDefinition = "text")
    private String rawPayload;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "retryable", nullable = false)
    private boolean retryable;

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ExchangeRateSyncFailure() {
    }

    public ExchangeRateSyncFailure(ExchangeRateSyncHistory syncHistory,
                                   String baseCurrency,
                                   ExchangeRateSyncFailureType failureType,
                                   String rawPayload,
                                   String errorMessage,
                                   boolean retryable) {
        this.syncHistory = syncHistory;
        this.baseCurrency = baseCurrency;
        this.failureType = failureType;
        this.rawPayload = rawPayload;
        this.errorMessage = truncate(errorMessage);
        this.retryable = retryable;
        this.resolved = false;
        this.createdAt = LocalDateTime.now();
    }

    public void resolve() {
        this.resolved = true;
    }

    private String truncate(String value) {
        return value == null ? null : value.substring(0, Math.min(1000, value.length()));
    }
}
