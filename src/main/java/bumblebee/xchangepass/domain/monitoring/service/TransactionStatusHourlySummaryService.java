package bumblebee.xchangepass.domain.monitoring.service;

import bumblebee.xchangepass.domain.monitoring.dto.TransactionStatusHourlySummaryResponse;
import bumblebee.xchangepass.domain.monitoring.dto.TransactionStatusHourlySummaryResult;
import bumblebee.xchangepass.domain.monitoring.entity.MonitoringBatchExecutionHistory;
import bumblebee.xchangepass.domain.monitoring.entity.TransactionMonitoringSummaryHourly;
import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEvent;
import bumblebee.xchangepass.domain.monitoring.repository.MonitoringBatchExecutionHistoryRepository;
import bumblebee.xchangepass.domain.monitoring.repository.TransactionMonitoringSummaryHourlyRepository;
import bumblebee.xchangepass.domain.monitoring.repository.TransactionStatusEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TransactionStatusHourlySummaryService {

    public static final String BATCH_TYPE = "TRANSACTION_STATUS_HOURLY_SUMMARY";

    private final TransactionStatusEventRepository eventRepository;
    private final TransactionMonitoringSummaryHourlyRepository summaryRepository;
    private final MonitoringBatchExecutionHistoryRepository historyRepository;
    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbcTemplate;

    public TransactionStatusHourlySummaryResult summarize(LocalDateTime targetFrom,
                                                          LocalDateTime targetTo) {
        return runSummary(targetFrom, targetTo, this::writeSummaryWithDatabaseAggregation);
    }

    public TransactionStatusHourlySummaryResult summarizeWithJavaGrouping(LocalDateTime targetFrom,
                                                                          LocalDateTime targetTo) {
        return runSummary(targetFrom, targetTo, this::writeSummaryWithJavaGrouping);
    }

    public TransactionStatusHourlySummaryResult summarizeWithDatabaseAggregation(LocalDateTime targetFrom,
                                                                                 LocalDateTime targetTo) {
        return summarize(targetFrom, targetTo);
    }

    private TransactionStatusHourlySummaryResult runSummary(
            LocalDateTime targetFrom,
            LocalDateTime targetTo,
            SummaryWriter summaryWriter
    ) {
        validateHourlyRange(targetFrom, targetTo);
        LocalDateTime startedAt = LocalDateTime.now();
        MonitoringBatchExecutionHistory history = createHistory(targetFrom, targetTo);
        try {
            SummaryWriteResult writeResult = summaryWriter.write(targetFrom, targetTo);
            completeHistory(history.getId(), writeResult.processedCount(), writeResult.summaryRowCount());
            return new TransactionStatusHourlySummaryResult(
                    history.getId(),
                    writeResult.processedCount(),
                    writeResult.summaryRowCount(),
                    Duration.between(startedAt, LocalDateTime.now())
            );
        } catch (RuntimeException exception) {
            failHistory(history.getId(), exception.getMessage());
            throw exception;
        }
    }

    public List<TransactionStatusHourlySummaryResponse> findSummaries(LocalDateTime targetFrom,
                                                                       LocalDateTime targetTo) {
        validateHourlyRange(targetFrom, targetTo);
        return summaryRepository
                .findBySummaryHourGreaterThanEqualAndSummaryHourLessThanOrderBySummaryHourAsc(
                        targetFrom, targetTo
                )
                .stream()
                .map(TransactionStatusHourlySummaryResponse::from)
                .toList();
    }

    private MonitoringBatchExecutionHistory createHistory(LocalDateTime targetFrom,
                                                          LocalDateTime targetTo) {
        return requiresNew().execute(status -> historyRepository.saveAndFlush(
                new MonitoringBatchExecutionHistory(BATCH_TYPE, targetFrom, targetTo)
        ));
    }

    private SummaryWriteResult writeSummaryWithJavaGrouping(LocalDateTime targetFrom, LocalDateTime targetTo) {
        return required().execute(status -> {
            List<TransactionStatusEvent> events = eventRepository
                    .findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscIdAsc(
                            targetFrom, targetTo
                    );
            Map<SummaryKey, SummaryAccumulator> grouped = new LinkedHashMap<>();
            events.forEach(event -> grouped
                    .computeIfAbsent(SummaryKey.from(event), key -> new SummaryAccumulator())
                    .add(event));

            summaryRepository.deleteBySummaryHourRange(targetFrom, targetTo);
            List<TransactionMonitoringSummaryHourly> summaries = grouped.entrySet().stream()
                    .map(entry -> entry.getKey().toEntity(entry.getValue()))
                    .toList();
            summaryRepository.saveAll(summaries);
            return new SummaryWriteResult(events.size(), summaries.size());
        });
    }

    private SummaryWriteResult writeSummaryWithDatabaseAggregation(LocalDateTime targetFrom,
                                                                   LocalDateTime targetTo) {
        return required().execute(status -> {
            List<TransactionMonitoringSummaryHourly> summaries = jdbcTemplate.query("""
                            select
                                date_trunc('hour', occurred_at) as summary_hour,
                                coalesce(transaction_type, 'NONE') as transaction_type,
                                coalesce(event_type, 'NONE') as event_type,
                                coalesce(current_status, 'NONE') as status,
                                coalesce(failure_stage, 'NONE') as failure_stage,
                                coalesce(error_code, 'NONE') as error_code,
                                count(*) as event_count,
                                sum(case when retryable = true then 1 else 0 end) as retryable_count,
                                sum(case when retryable = false then 1 else 0 end) as non_retryable_count
                            from transaction_status_event
                            where occurred_at >= ?
                              and occurred_at < ?
                            group by
                                date_trunc('hour', occurred_at),
                                coalesce(transaction_type, 'NONE'),
                                coalesce(event_type, 'NONE'),
                                coalesce(current_status, 'NONE'),
                                coalesce(failure_stage, 'NONE'),
                                coalesce(error_code, 'NONE')
                            order by summary_hour
                            """,
                    (rs, rowNum) -> new TransactionMonitoringSummaryHourly(
                            rs.getTimestamp("summary_hour").toLocalDateTime(),
                            rs.getString("transaction_type"),
                            rs.getString("event_type"),
                            rs.getString("status"),
                            rs.getString("failure_stage"),
                            rs.getString("error_code"),
                            rs.getLong("event_count"),
                            rs.getLong("retryable_count"),
                            rs.getLong("non_retryable_count")
                    ),
                    Timestamp.valueOf(targetFrom),
                    Timestamp.valueOf(targetTo)
            );

            summaryRepository.deleteBySummaryHourRange(targetFrom, targetTo);
            summaryRepository.saveAll(summaries);
            long processedCount = summaries.stream()
                    .mapToLong(TransactionMonitoringSummaryHourly::getCount)
                    .sum();
            return new SummaryWriteResult(processedCount, summaries.size());
        });
    }

    private void completeHistory(Long historyId, long processedCount, long summaryRowCount) {
        requiresNew().executeWithoutResult(status -> {
            MonitoringBatchExecutionHistory history = historyRepository.findById(historyId).orElseThrow();
            history.complete(processedCount, summaryRowCount);
        });
    }

    private void failHistory(Long historyId, String errorMessage) {
        requiresNew().executeWithoutResult(status -> {
            MonitoringBatchExecutionHistory history = historyRepository.findById(historyId).orElseThrow();
            history.fail(0, errorMessage);
        });
    }

    private TransactionTemplate required() {
        return new TransactionTemplate(transactionManager);
    }

    private TransactionTemplate requiresNew() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
        return transactionTemplate;
    }

    private void validateHourlyRange(LocalDateTime targetFrom, LocalDateTime targetTo) {
        if (targetFrom == null || targetTo == null) {
            throw new IllegalArgumentException("targetFrom and targetTo are required");
        }
        if (!targetFrom.isBefore(targetTo)) {
            throw new IllegalArgumentException("targetFrom must be before targetTo");
        }
        if (!targetFrom.equals(targetFrom.truncatedTo(ChronoUnit.HOURS))
                || !targetTo.equals(targetTo.truncatedTo(ChronoUnit.HOURS))) {
            throw new IllegalArgumentException("targetFrom and targetTo must be aligned to hour boundaries");
        }
    }

    private record SummaryWriteResult(long processedCount, long summaryRowCount) {
    }

    @FunctionalInterface
    private interface SummaryWriter {
        SummaryWriteResult write(LocalDateTime targetFrom, LocalDateTime targetTo);
    }

    private record SummaryKey(
            LocalDateTime summaryHour,
            String transactionType,
            String eventType,
            String status,
            String failureStage,
            String errorCode
    ) {
        private static SummaryKey from(TransactionStatusEvent event) {
            return new SummaryKey(
                    event.getOccurredAt().truncatedTo(ChronoUnit.HOURS),
                    code(event.getTransactionType()),
                    code(event.getEventType()),
                    code(event.getCurrentStatus()),
                    code(event.getFailureStage()),
                    code(event.getErrorCode())
            );
        }

        private TransactionMonitoringSummaryHourly toEntity(SummaryAccumulator accumulator) {
            return new TransactionMonitoringSummaryHourly(
                    summaryHour,
                    transactionType,
                    eventType,
                    status,
                    failureStage,
                    errorCode,
                    accumulator.count(),
                    accumulator.retryableCount(),
                    accumulator.nonRetryableCount()
            );
        }

        private static String code(Enum<?> value) {
            return value == null ? TransactionMonitoringSummaryHourly.NONE : value.name();
        }

        private static String code(String value) {
            return value == null || value.isBlank() ? TransactionMonitoringSummaryHourly.NONE : value;
        }
    }

    private static class SummaryAccumulator {
        private long count;
        private long retryableCount;
        private long nonRetryableCount;

        private void add(TransactionStatusEvent event) {
            count++;
            if (Boolean.TRUE.equals(event.getRetryable())) {
                retryableCount++;
            } else if (Boolean.FALSE.equals(event.getRetryable())) {
                nonRetryableCount++;
            }
        }

        private long count() {
            return count;
        }

        private long retryableCount() {
            return retryableCount;
        }

        private long nonRetryableCount() {
            return nonRetryableCount;
        }
    }
}
