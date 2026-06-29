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
@Table(name = "exchange_rate_sync_snapshot")
public class ExchangeRateSyncSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sync_history_id", nullable = false)
    private ExchangeRateSyncHistory syncHistory;

    @Column(name = "base_currency", nullable = false, length = 10)
    private String baseCurrency;

    @Column(name = "payload", columnDefinition = "text")
    private String payload;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExchangeRateSyncSnapshotStatus status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    protected ExchangeRateSyncSnapshot() {
    }

    public static ExchangeRateSyncSnapshot received(ExchangeRateSyncHistory syncHistory,
                                                    String baseCurrency,
                                                    String payload,
                                                    String payloadHash) {
        return new ExchangeRateSyncSnapshot(
                syncHistory,
                baseCurrency,
                payload,
                payloadHash,
                ExchangeRateSyncSnapshotStatus.RECEIVED,
                null
        );
    }

    public static ExchangeRateSyncSnapshot failed(ExchangeRateSyncHistory syncHistory,
                                                  String baseCurrency,
                                                  String errorMessage) {
        return new ExchangeRateSyncSnapshot(
                syncHistory,
                baseCurrency,
                null,
                null,
                ExchangeRateSyncSnapshotStatus.FAILED,
                errorMessage
        );
    }

    private ExchangeRateSyncSnapshot(ExchangeRateSyncHistory syncHistory,
                                     String baseCurrency,
                                     String payload,
                                     String payloadHash,
                                     ExchangeRateSyncSnapshotStatus status,
                                     String errorMessage) {
        this.syncHistory = syncHistory;
        this.baseCurrency = baseCurrency;
        this.payload = payload;
        this.payloadHash = payloadHash;
        this.receivedAt = LocalDateTime.now();
        this.status = status;
        this.errorMessage = truncate(errorMessage);
    }

    private String truncate(String value) {
        return value == null ? null : value.substring(0, Math.min(1000, value.length()));
    }
}
