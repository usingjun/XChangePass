package bumblebee.xchangepass.domain.monitoring;

import bumblebee.xchangepass.domain.monitoring.entity.MonitoringBatchStatus;
import bumblebee.xchangepass.domain.monitoring.entity.TransactionMonitoringSummaryHourly;
import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEvent;
import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEventType;
import bumblebee.xchangepass.domain.monitoring.repository.MonitoringBatchExecutionHistoryRepository;
import bumblebee.xchangepass.domain.monitoring.repository.TransactionMonitoringSummaryHourlyRepository;
import bumblebee.xchangepass.domain.monitoring.repository.TransactionStatusEventRepository;
import bumblebee.xchangepass.domain.monitoring.service.TransactionStatusHourlySummaryService;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferFailureStage;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.global.config.QueryDSLConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({TransactionStatusHourlySummaryService.class, QueryDSLConfig.class})
class TransactionStatusHourlySummaryServiceTest {

    @Autowired
    private TransactionStatusEventRepository eventRepository;

    @Autowired
    private TransactionMonitoringSummaryHourlyRepository summaryRepository;

    @Autowired
    private MonitoringBatchExecutionHistoryRepository historyRepository;

    @Autowired
    private TransactionStatusHourlySummaryService summaryService;

    @BeforeEach
    void setUp() {
        summaryRepository.deleteAll();
        historyRepository.deleteAll();
        eventRepository.deleteAll();
    }

    @Test
    void summarizesHourlyEventsWithoutDuplicatingOnRerun() {
        LocalDateTime from = LocalDateTime.of(2026, 6, 26, 13, 0);
        LocalDateTime to = from.plusHours(1);
        eventRepository.save(event(TransactionStatusEventType.REQUEST_ACCEPTED, from.plusMinutes(1))
                .status(null, WalletTransferStatus.REQUESTED)
                .build());
        eventRepository.save(event(TransactionStatusEventType.FAILED, from.plusMinutes(2))
                .status(WalletTransferStatus.VALIDATING, WalletTransferStatus.FAILED)
                .failure(WalletTransferFailureStage.FRAUD_VALIDATION,
                        "FRAUD_DETECTION_UNAVAILABLE", true)
                .build());
        eventRepository.save(event(TransactionStatusEventType.FAILED, from.plusMinutes(3))
                .status(WalletTransferStatus.PROCESSING, WalletTransferStatus.FAILED)
                .failure(WalletTransferFailureStage.FUNDS_AND_LEDGER_TRANSACTION,
                        "TRANSACTION_PROCESSING_FAILED", false)
                .build());
        eventRepository.save(event(TransactionStatusEventType.COMPLETED, to.plusMinutes(1))
                .status(WalletTransferStatus.PROCESSING, WalletTransferStatus.COMPLETED)
                .build());

        var first = summaryService.summarize(from, to);
        var second = summaryService.summarize(from, to);

        assertThat(first.processedCount()).isEqualTo(3);
        assertThat(second.processedCount()).isEqualTo(3);
        assertThat(summaryRepository.findAll()).hasSize(3);
        assertThat(summaryRepository.findAll())
                .extracting(TransactionMonitoringSummaryHourly::getCount)
                .containsExactlyInAnyOrder(1L, 1L, 1L);
        assertThat(historyRepository.findAll()).hasSize(2)
                .allSatisfy(history -> {
                    assertThat(history.getStatus()).isEqualTo(MonitoringBatchStatus.COMPLETED);
                    assertThat(history.getProcessedCount()).isEqualTo(3);
                    assertThat(history.getSuccessCount()).isEqualTo(3);
                });
    }

    @Test
    void countsRetryableAndNonRetryableFailures() {
        LocalDateTime from = LocalDateTime.of(2026, 6, 26, 14, 0);
        LocalDateTime to = from.plusHours(1);
        eventRepository.save(event(TransactionStatusEventType.FAILED, from.plusMinutes(1))
                .status(WalletTransferStatus.VALIDATING, WalletTransferStatus.FAILED)
                .failure(WalletTransferFailureStage.FRAUD_VALIDATION,
                        "FRAUD_DETECTION_UNAVAILABLE", true)
                .build());
        eventRepository.save(event(TransactionStatusEventType.FAILED, from.plusMinutes(2))
                .status(WalletTransferStatus.VALIDATING, WalletTransferStatus.FAILED)
                .failure(WalletTransferFailureStage.FRAUD_VALIDATION,
                        "FRAUD_DETECTION_UNAVAILABLE", true)
                .build());
        eventRepository.save(event(TransactionStatusEventType.FAILED, from.plusMinutes(3))
                .status(WalletTransferStatus.PROCESSING, WalletTransferStatus.FAILED)
                .failure(WalletTransferFailureStage.FUNDS_AND_LEDGER_TRANSACTION,
                        "TRANSACTION_PROCESSING_FAILED", false)
                .build());

        summaryService.summarize(from, to);

        assertThat(summaryRepository.findAll())
                .filteredOn(summary -> summary.getErrorCode().equals("FRAUD_DETECTION_UNAVAILABLE"))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.getCount()).isEqualTo(2);
                    assertThat(summary.getRetryableCount()).isEqualTo(2);
                    assertThat(summary.getNonRetryableCount()).isZero();
                });
        assertThat(summaryRepository.findAll())
                .filteredOn(summary -> summary.getErrorCode().equals("TRANSACTION_PROCESSING_FAILED"))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.getCount()).isEqualTo(1);
                    assertThat(summary.getRetryableCount()).isZero();
                    assertThat(summary.getNonRetryableCount()).isEqualTo(1);
                });
    }

    @Test
    void normalizesNullKeyPartsToNone() {
        LocalDateTime from = LocalDateTime.of(2026, 6, 26, 15, 0);
        LocalDateTime to = from.plusHours(1);
        eventRepository.save(event(TransactionStatusEventType.IDEMPOTENT_DUPLICATE_DETECTED, from.plusMinutes(1))
                .status(WalletTransferStatus.COMPLETED, WalletTransferStatus.COMPLETED)
                .build());

        summaryService.summarize(from, to);

        TransactionMonitoringSummaryHourly summary = summaryRepository.findAll().get(0);
        assertThat(summary.getFailureStage()).isEqualTo(TransactionMonitoringSummaryHourly.NONE);
        assertThat(summary.getErrorCode()).isEqualTo(TransactionMonitoringSummaryHourly.NONE);
    }

    @Test
    void findsHourlySummariesByRange() {
        LocalDateTime firstHour = LocalDateTime.of(2026, 6, 26, 13, 0);
        LocalDateTime secondHour = firstHour.plusHours(1);
        eventRepository.save(event(TransactionStatusEventType.REQUEST_ACCEPTED, firstHour.plusMinutes(1))
                .status(null, WalletTransferStatus.REQUESTED)
                .build());
        eventRepository.save(event(TransactionStatusEventType.FAILED, secondHour.plusMinutes(1))
                .status(WalletTransferStatus.PROCESSING, WalletTransferStatus.FAILED)
                .failure(WalletTransferFailureStage.FUNDS_AND_LEDGER_TRANSACTION,
                        "TRANSACTION_PROCESSING_FAILED", false)
                .build());

        summaryService.summarize(firstHour, secondHour.plusHours(1));

        var responses = summaryService.findSummaries(firstHour, secondHour);

        assertThat(responses).singleElement()
                .satisfies(response -> {
                    assertThat(response.summaryHour()).isEqualTo(firstHour);
                    assertThat(response.eventType()).isEqualTo(TransactionStatusEventType.REQUEST_ACCEPTED.name());
                    assertThat(response.status()).isEqualTo(WalletTransferStatus.REQUESTED.name());
                    assertThat(response.count()).isEqualTo(1);
                    assertThat(response.retryableCount()).isZero();
                    assertThat(response.nonRetryableCount()).isZero();
                });
    }

    @Test
    void defaultDatabaseAggregationMatchesJavaGroupingSummary() {
        LocalDateTime from = LocalDateTime.of(2026, 6, 26, 13, 0);
        LocalDateTime to = from.plusHours(2);
        eventRepository.save(event(TransactionStatusEventType.REQUEST_ACCEPTED, from.plusMinutes(1))
                .status(null, WalletTransferStatus.REQUESTED)
                .build());
        eventRepository.save(event(TransactionStatusEventType.REQUEST_ACCEPTED, from.plusMinutes(2))
                .status(null, WalletTransferStatus.REQUESTED)
                .build());
        eventRepository.save(event(TransactionStatusEventType.FAILED, from.plusMinutes(3))
                .status(WalletTransferStatus.PROCESSING, WalletTransferStatus.FAILED)
                .failure(WalletTransferFailureStage.FUNDS_AND_LEDGER_TRANSACTION,
                        "TRANSACTION_PROCESSING_FAILED", false)
                .build());
        eventRepository.save(event(TransactionStatusEventType.FAILED, from.plusHours(1).plusMinutes(1))
                .status(WalletTransferStatus.VALIDATING, WalletTransferStatus.FAILED)
                .failure(WalletTransferFailureStage.FRAUD_VALIDATION,
                        "FRAUD_DETECTION_UNAVAILABLE", true)
                .build());
        eventRepository.save(event(TransactionStatusEventType.IDEMPOTENT_DUPLICATE_DETECTED,
                        from.plusHours(1).plusMinutes(2))
                .status(WalletTransferStatus.COMPLETED, WalletTransferStatus.COMPLETED)
                .build());

        var javaResult = summaryService.summarizeWithJavaGrouping(from, to);
        var javaSummaries = sortedSummaries();
        var databaseResult = summaryService.summarize(from, to);
        var databaseSummaries = sortedSummaries();

        assertThat(javaResult.processedCount()).isEqualTo(5);
        assertThat(databaseResult.processedCount()).isEqualTo(javaResult.processedCount());
        assertThat(databaseResult.summaryRowCount()).isEqualTo(javaResult.summaryRowCount());
        assertThat(databaseSummaries)
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "createdAt", "updatedAt")
                .containsExactlyElementsOf(javaSummaries);
    }

    @Test
    void rejectsNonHourlyRange() {
        LocalDateTime from = LocalDateTime.of(2026, 6, 26, 13, 30);
        LocalDateTime to = LocalDateTime.of(2026, 6, 26, 14, 0);

        assertThatThrownBy(() -> summaryService.summarize(from, to))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hour boundaries");
    }

    private TransactionStatusEvent.TransactionStatusEventBuilder event(
            TransactionStatusEventType eventType, LocalDateTime occurredAt) {
        return TransactionStatusEvent.builder(UUID.randomUUID(), eventType)
                .userId(1L)
                .idempotencyKey(UUID.randomUUID())
                .occurredAt(occurredAt);
    }

    private java.util.List<TransactionMonitoringSummaryHourly> sortedSummaries() {
        return summaryRepository.findAll().stream()
                .sorted(Comparator.comparing(TransactionMonitoringSummaryHourly::getSummaryHour)
                        .thenComparing(TransactionMonitoringSummaryHourly::getTransactionType)
                        .thenComparing(TransactionMonitoringSummaryHourly::getEventType)
                        .thenComparing(TransactionMonitoringSummaryHourly::getStatus)
                        .thenComparing(TransactionMonitoringSummaryHourly::getFailureStage)
                        .thenComparing(TransactionMonitoringSummaryHourly::getErrorCode))
                .toList();
    }
}
