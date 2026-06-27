package bumblebee.xchangepass.domain.monitoring;

import bumblebee.xchangepass.domain.monitoring.entity.TransactionMonitoringSummaryHourly;
import bumblebee.xchangepass.domain.monitoring.repository.MonitoringBatchExecutionHistoryRepository;
import bumblebee.xchangepass.domain.monitoring.repository.TransactionMonitoringSummaryHourlyRepository;
import bumblebee.xchangepass.domain.monitoring.repository.TransactionStatusEventRepository;
import bumblebee.xchangepass.domain.monitoring.service.TransactionStatusHourlySummaryService;
import bumblebee.xchangepass.global.config.QueryDSLConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({TransactionStatusHourlySummaryService.class, QueryDSLConfig.class})
@EnabledIfEnvironmentVariable(named = "XCP_BENCHMARK", matches = "true")
class TransactionStatusHourlySummaryBaselineBenchmarkTest {

    private static final Path BENCHMARK_DIR = Path.of("docs", "benchmarks");
    private static final String BENCHMARK_DATE = "2026-06-27";
    private static final Path CSV_PATH = BENCHMARK_DIR.resolve(
            "transaction-monitoring-summary-" + BENCHMARK_DATE + ".csv"
    );
    private static final Path MD_PATH = BENCHMARK_DIR.resolve(
            "transaction-monitoring-summary-" + BENCHMARK_DATE + ".md"
    );
    private static final int EVENT_COUNT = benchmarkEventCount();

    @Autowired
    private TransactionStatusEventRepository eventRepository;

    @Autowired
    private TransactionMonitoringSummaryHourlyRepository summaryRepository;

    @Autowired
    private MonitoringBatchExecutionHistoryRepository historyRepository;

    @Autowired
    private TransactionStatusHourlySummaryService summaryService;

    @Test
    void writesBaselineBenchmarkReport() throws IOException {
        insertDataset();

        List<HourlyBenchmarkRow> rows = new ArrayList<>();
        LocalDateTime hour = TransactionStatusEventDatasetFactory.DEFAULT_START_AT;
        while (hour.isBefore(TransactionStatusEventDatasetFactory.DEFAULT_END_AT)) {
            LocalDateTime nextHour = hour.plusHours(1);
            var result = summaryService.summarizeWithJavaGrouping(hour, nextHour);
            long sourceCount = eventRepository
                    .countByOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                            hour, nextHour
                    );
            List<TransactionMonitoringSummaryHourly> summaries = summaryRepository
                    .findBySummaryHourGreaterThanEqualAndSummaryHourLessThanOrderBySummaryHourAsc(
                            hour, nextHour
                    );
            long summaryCount = summaries.stream()
                    .mapToLong(TransactionMonitoringSummaryHourly::getCount)
                    .sum();
            rows.add(new HourlyBenchmarkRow(
                    hour,
                    sourceCount,
                    summaryCount,
                    summaries.size(),
                    result.elapsed().toMillis(),
                    eventsPerSecond(sourceCount, result.elapsed().toMillis()),
                    sourceCount - summaryCount,
                    result.historyId()
            ));
            hour = nextHour;
        }

        writeReports(rows);
        verifyAccuracy(rows);
    }

    private void insertDataset() {
        int eventCount = EVENT_COUNT;
        int chunkSize = TransactionStatusEventDatasetFactory.DEFAULT_CHUNK_SIZE;
        for (int offset = 0; offset < eventCount; offset += chunkSize) {
            eventRepository.saveAll(TransactionStatusEventDatasetFactory.createChunk(
                    offset,
                    Math.min(chunkSize, eventCount - offset),
                    TransactionStatusEventDatasetFactory.DEFAULT_START_AT,
                    TransactionStatusEventDatasetFactory.DEFAULT_END_AT,
                    eventCount
            ));
            eventRepository.flush();
        }
    }

    private void verifyAccuracy(List<HourlyBenchmarkRow> rows) {
        long totalSourceCount = rows.stream().mapToLong(HourlyBenchmarkRow::sourceCount).sum();
        long totalSummaryCount = rows.stream().mapToLong(HourlyBenchmarkRow::summaryCount).sum();

        assertThat(totalSourceCount).isEqualTo(EVENT_COUNT);
        assertThat(totalSummaryCount).isEqualTo(totalSourceCount);
        assertThat(rows).allSatisfy(row -> assertThat(row.countDiff()).isZero());
        assertThat(historyRepository.count()).isEqualTo(rows.size());
    }

    private void writeReports(List<HourlyBenchmarkRow> rows) throws IOException {
        Files.createDirectories(BENCHMARK_DIR);
        Files.writeString(CSV_PATH, csv(rows));
        Files.writeString(MD_PATH, markdown(rows));
    }

    private String csv(List<HourlyBenchmarkRow> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("hour,source_count,summary_count,summary_rows,elapsed_ms,events_per_second,count_diff,history_id\n");
        rows.forEach(row -> builder.append(row.hour())
                .append(',')
                .append(row.sourceCount())
                .append(',')
                .append(row.summaryCount())
                .append(',')
                .append(row.summaryRows())
                .append(',')
                .append(row.elapsedMs())
                .append(',')
                .append(row.eventsPerSecond())
                .append(',')
                .append(row.countDiff())
                .append(',')
                .append(row.historyId())
                .append('\n'));
        return builder.toString();
    }

    private String markdown(List<HourlyBenchmarkRow> rows) {
        long totalSourceCount = rows.stream().mapToLong(HourlyBenchmarkRow::sourceCount).sum();
        long totalSummaryCount = rows.stream().mapToLong(HourlyBenchmarkRow::summaryCount).sum();
        long totalElapsedMs = rows.stream().mapToLong(HourlyBenchmarkRow::elapsedMs).sum();
        long maxElapsedMs = rows.stream().mapToLong(HourlyBenchmarkRow::elapsedMs).max().orElse(0);
        long minElapsedMs = rows.stream().mapToLong(HourlyBenchmarkRow::elapsedMs).min().orElse(0);
        long countDiff = totalSourceCount - totalSummaryCount;

        return """
                # Transaction Monitoring Summary Baseline - %s

                ## Dataset
                - Events: %,d
                - Range: %s ~ %s
                - Bucket: 1 hour
                - Mode: Java grouping baseline

                ## Result
                | Metric | Value |
                |---|---:|
                | Total source count | %,d |
                | Total summary count | %,d |
                | Count diff | %,d |
                | Total elapsed ms | %,d |
                | Avg elapsed ms/hour | %s |
                | Min elapsed ms/hour | %,d |
                | Max elapsed ms/hour | %,d |
                | Avg events/sec | %s |

                ## Files
                - CSV: `%s`
                """.formatted(
                BENCHMARK_DATE,
                EVENT_COUNT,
                TransactionStatusEventDatasetFactory.DEFAULT_START_AT,
                TransactionStatusEventDatasetFactory.DEFAULT_END_AT,
                totalSourceCount,
                totalSummaryCount,
                countDiff,
                totalElapsedMs,
                decimal(totalElapsedMs, rows.size()),
                minElapsedMs,
                maxElapsedMs,
                eventsPerSecond(totalSourceCount, totalElapsedMs),
                CSV_PATH
        );
    }

    private String eventsPerSecond(long eventCount, long elapsedMs) {
        if (elapsedMs == 0) {
            return "N/A";
        }
        return BigDecimal.valueOf(eventCount)
                .multiply(BigDecimal.valueOf(1000))
                .divide(BigDecimal.valueOf(elapsedMs), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private String decimal(long numerator, long denominator) {
        if (denominator == 0) {
            return "0";
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private static int benchmarkEventCount() {
        String configured = System.getenv("XCP_BENCHMARK_EVENT_COUNT");
        return configured == null || configured.isBlank()
                ? TransactionStatusEventDatasetFactory.DEFAULT_EVENT_COUNT
                : Integer.parseInt(configured);
    }

    private record HourlyBenchmarkRow(
            LocalDateTime hour,
            long sourceCount,
            long summaryCount,
            long summaryRows,
            long elapsedMs,
            String eventsPerSecond,
            long countDiff,
            Long historyId
    ) {
    }
}
