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
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TransactionStatusHourlySummaryService.class, QueryDSLConfig.class})
@EnabledIfEnvironmentVariable(named = "XCP_PG_BENCHMARK", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TransactionStatusHourlySummaryAggregationBenchmarkTest {

    private static final Path BENCHMARK_DIR = Path.of("docs", "benchmarks");
    private static final String BENCHMARK_DATE = "2026-06-27";
    private static final int EVENT_COUNT = benchmarkEventCount();
    private static final Path CSV_PATH = BENCHMARK_DIR.resolve(
            "transaction-monitoring-aggregation-comparison-" + BENCHMARK_DATE + ".csv"
    );
    private static final Path MD_PATH = BENCHMARK_DIR.resolve(
            "transaction-monitoring-aggregation-comparison-" + BENCHMARK_DATE + ".md"
    );
    private static final String BTREE_INDEX = "idx_tx_status_event_occurred_at_id";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("xcp_monitoring_aggregation_benchmark")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("logging.level.org.hibernate.SQL", () -> "OFF");
        registry.add("logging.level.org.hibernate.orm.jdbc.bind", () -> "OFF");
    }

    @Autowired
    private TransactionStatusEventRepository eventRepository;

    @Autowired
    private TransactionMonitoringSummaryHourlyRepository summaryRepository;

    @Autowired
    private MonitoringBatchExecutionHistoryRepository historyRepository;

    @Autowired
    private TransactionStatusHourlySummaryService summaryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void comparesJavaGroupingAndDatabaseAggregation() throws IOException {
        insertDataset();
        applyBtreeIndex();

        List<AggregationBenchmarkRow> rows = List.of(
                measure(AggregationMode.JAVA_GROUPING),
                measure(AggregationMode.DB_GROUP_BY)
        );

        verifyAccuracy(rows);
        writeReports(rows);
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
        jdbcTemplate.execute("ANALYZE transaction_status_event");
    }

    private void applyBtreeIndex() {
        jdbcTemplate.execute("DROP INDEX IF EXISTS " + BTREE_INDEX);
        jdbcTemplate.execute("""
                CREATE INDEX %s
                ON transaction_status_event (occurred_at, id)
                """.formatted(BTREE_INDEX));
        jdbcTemplate.execute("ANALYZE transaction_status_event");
    }

    private AggregationBenchmarkRow measure(AggregationMode mode) {
        resetSummaryState();

        long totalElapsedMs = 0;
        LocalDateTime hour = TransactionStatusEventDatasetFactory.DEFAULT_START_AT;
        while (hour.isBefore(TransactionStatusEventDatasetFactory.DEFAULT_END_AT)) {
            LocalDateTime nextHour = hour.plusHours(1);
            totalElapsedMs += switch (mode) {
                case JAVA_GROUPING -> summaryService.summarizeWithJavaGrouping(hour, nextHour)
                        .elapsed()
                        .toMillis();
                case DB_GROUP_BY -> summaryService.summarize(hour, nextHour).elapsed().toMillis();
            };
            hour = nextHour;
        }

        long sourceCount = eventRepository
                .countByOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                        TransactionStatusEventDatasetFactory.DEFAULT_START_AT,
                        TransactionStatusEventDatasetFactory.DEFAULT_END_AT
                );
        long summaryCount = summaryRepository.findAll().stream()
                .mapToLong(TransactionMonitoringSummaryHourly::getCount)
                .sum();
        long summaryRows = summaryRepository.count();
        return new AggregationBenchmarkRow(
                mode.name(),
                sourceCount,
                summaryCount,
                summaryRows,
                totalElapsedMs,
                eventsPerSecond(sourceCount, totalElapsedMs),
                sourceCount - summaryCount
        );
    }

    private void resetSummaryState() {
        summaryRepository.deleteAll();
        historyRepository.deleteAll();
    }

    private void verifyAccuracy(List<AggregationBenchmarkRow> rows) {
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.sourceCount()).isEqualTo(EVENT_COUNT);
            assertThat(row.summaryCount()).isEqualTo(row.sourceCount());
            assertThat(row.countDiff()).isZero();
        });
    }

    private void writeReports(List<AggregationBenchmarkRow> rows) throws IOException {
        Files.createDirectories(BENCHMARK_DIR);
        Files.writeString(CSV_PATH, csv(rows));
        Files.writeString(MD_PATH, markdown(rows));
    }

    private String csv(List<AggregationBenchmarkRow> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("aggregation_mode,source_count,summary_count,summary_rows,total_elapsed_ms,events_per_second,count_diff\n");
        rows.forEach(row -> builder.append(row.aggregationMode())
                .append(',')
                .append(row.sourceCount())
                .append(',')
                .append(row.summaryCount())
                .append(',')
                .append(row.summaryRows())
                .append(',')
                .append(row.totalElapsedMs())
                .append(',')
                .append(row.eventsPerSecond())
                .append(',')
                .append(row.countDiff())
                .append('\n'));
        return builder.toString();
    }

    private String markdown(List<AggregationBenchmarkRow> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Transaction Monitoring Aggregation Comparison - ")
                .append(BENCHMARK_DATE)
                .append("\n\n")
                .append("## Dataset\n")
                .append("- Events: ")
                .append(EVENT_COUNT)
                .append("\n")
                .append("- Range: ")
                .append(TransactionStatusEventDatasetFactory.DEFAULT_START_AT)
                .append(" ~ ")
                .append(TransactionStatusEventDatasetFactory.DEFAULT_END_AT)
                .append("\n")
                .append("- Bucket: 1 hour\n")
                .append("- Index: BTREE (occurred_at, id)\n\n")
                .append("## Result\n")
                .append("| Aggregation mode | Source | Summary | Diff | Summary rows | Elapsed ms | Events/sec |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|\n");
        rows.forEach(row -> builder.append("| ")
                .append(row.aggregationMode())
                .append(" | ")
                .append(row.sourceCount())
                .append(" | ")
                .append(row.summaryCount())
                .append(" | ")
                .append(row.countDiff())
                .append(" | ")
                .append(row.summaryRows())
                .append(" | ")
                .append(row.totalElapsedMs())
                .append(" | ")
                .append(row.eventsPerSecond())
                .append(" |\n"));
        builder.append("\n## Files\n- CSV: `")
                .append(CSV_PATH)
                .append("`\n");
        return builder.toString();
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

    private static int benchmarkEventCount() {
        String configured = System.getenv("XCP_BENCHMARK_EVENT_COUNT");
        return configured == null || configured.isBlank()
                ? TransactionStatusEventDatasetFactory.DEFAULT_EVENT_COUNT
                : Integer.parseInt(configured);
    }

    private enum AggregationMode {
        JAVA_GROUPING,
        DB_GROUP_BY
    }

    private record AggregationBenchmarkRow(
            String aggregationMode,
            long sourceCount,
            long summaryCount,
            long summaryRows,
            long totalElapsedMs,
            String eventsPerSecond,
            long countDiff
    ) {
    }
}
