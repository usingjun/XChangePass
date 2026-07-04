package bumblebee.xchangepass.domain.transaction.statistics;

import bumblebee.xchangepass.domain.transaction.statistics.TransactionStatisticsBenchmarkDataFactory.BenchmarkConfig;
import bumblebee.xchangepass.domain.transaction.statistics.TransactionStatisticsBenchmarkDataFactory.BenchmarkDataset;
import bumblebee.xchangepass.domain.transaction.statistics.dto.TransactionStatisticsMode;
import bumblebee.xchangepass.domain.transaction.statistics.repository.TransactionStatisticsJdbcRepository;
import bumblebee.xchangepass.global.config.QueryDSLConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TransactionStatisticsJdbcRepository.class, QueryDSLConfig.class})
@EnabledIfEnvironmentVariable(named = "RUN_TRANSACTION_STATISTICS_BENCHMARK", matches = "true")
class TransactionStatisticsBenchmarkRunnerTest {

    private static final int DEFAULT_ROWS = 100_000;
    private static final int DEFAULT_USERS = 1_000;
    private static final int DEFAULT_MONTHS = 12;
    private static final long DEFAULT_SEED = 20260704L;
    private static final int DEFAULT_WARMUP = 5;
    private static final int DEFAULT_ITERATIONS = 50;

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("xcp_transaction_statistics_benchmark")
            .withUsername("benchmark")
            .withPassword("benchmark");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 20);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("logging.level.org.hibernate.SQL", () -> "OFF");
        registry.add("logging.level.org.hibernate.orm.jdbc.bind", () -> "OFF");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionStatisticsJdbcRepository repository;

    @Test
    void compareTransactionStatisticsQueryModesAndRefreshCost() throws Exception {
        BenchmarkConfig config = new BenchmarkConfig(
                environmentInt("TRANSACTION_STATISTICS_BENCHMARK_ROWS", DEFAULT_ROWS),
                environmentInt("TRANSACTION_STATISTICS_BENCHMARK_USERS", DEFAULT_USERS),
                environmentInt("TRANSACTION_STATISTICS_BENCHMARK_MONTHS", DEFAULT_MONTHS),
                environmentLong("TRANSACTION_STATISTICS_BENCHMARK_SEED", DEFAULT_SEED)
        );
        int warmupRuns = environmentInt("TRANSACTION_STATISTICS_BENCHMARK_WARMUP", DEFAULT_WARMUP);
        int measurementRuns = environmentInt("TRANSACTION_STATISTICS_BENCHMARK_ITERATIONS", DEFAULT_ITERATIONS);

        createStatisticsObjects();
        TransactionStatisticsBenchmarkDataFactory dataFactory =
                new TransactionStatisticsBenchmarkDataFactory(jdbcTemplate);
        BenchmarkDataset dataset = dataFactory.create(config);
        dataFactory.analyzeTables();

        List<BenchmarkResult> results = new ArrayList<>();
        results.add(measureRefresh("MV_REFRESH", dataset, warmupRuns, measurementRuns,
                repository::refreshMaterializedView));
        results.add(measureRefreshAllowingFailure("MV_REFRESH_CONCURRENTLY", dataset, warmupRuns, measurementRuns,
                repository::refreshMaterializedViewConcurrently));
        results.add(measureRefresh("SUMMARY_REFRESH", dataset, warmupRuns, measurementRuns,
                () -> repository.refreshMonthlySummary(dataset.fromMonth(), dataset.toMonth())));
        dataFactory.analyzeTables();

        results.add(measureQuery("HEAVY_USER_12M", TransactionStatisticsMode.GROUP_BY, dataset,
                warmupRuns, measurementRuns, () -> repository.findMonthlyStatistics(
                        dataset.heavyUserId(), dataset.fromMonth(), dataset.toMonth()
                ).size()));
        results.add(measureQuery("HEAVY_USER_12M", TransactionStatisticsMode.MATERIALIZED_VIEW, dataset,
                warmupRuns, measurementRuns, () -> repository.findMonthlyStatisticsFromMaterializedView(
                        dataset.heavyUserId(), dataset.fromMonth(), dataset.toMonth()
                ).size()));
        results.add(measureQuery("HEAVY_USER_12M", TransactionStatisticsMode.SUMMARY, dataset,
                warmupRuns, measurementRuns, () -> repository.findMonthlyStatisticsFromSummary(
                        dataset.heavyUserId(), dataset.fromMonth(), dataset.toMonth()
                ).size()));

        YearMonth shortToMonth = dataset.fromMonth().plusMonths(Math.min(2, dataset.monthCount() - 1L));
        results.add(measureQuery("HEAVY_USER_3M", TransactionStatisticsMode.GROUP_BY, dataset,
                warmupRuns, measurementRuns, () -> repository.findMonthlyStatistics(
                        dataset.heavyUserId(), dataset.fromMonth(), shortToMonth
                ).size()));
        results.add(measureQuery("HEAVY_USER_3M", TransactionStatisticsMode.MATERIALIZED_VIEW, dataset,
                warmupRuns, measurementRuns, () -> repository.findMonthlyStatisticsFromMaterializedView(
                        dataset.heavyUserId(), dataset.fromMonth(), shortToMonth
                ).size()));
        results.add(measureQuery("HEAVY_USER_3M", TransactionStatisticsMode.SUMMARY, dataset,
                warmupRuns, measurementRuns, () -> repository.findMonthlyStatisticsFromSummary(
                        dataset.heavyUserId(), dataset.fromMonth(), shortToMonth
                ).size()));

        results.addAll(measureRegularUsers(dataset, warmupRuns, measurementRuns));

        Path csv = writeCsv(results);
        printMarkdown(dataset, results, csv);
    }

    private List<BenchmarkResult> measureRegularUsers(BenchmarkDataset dataset, int warmupRuns, int measurementRuns) {
        List<Long> users = dataset.regularUserIds();
        List<BenchmarkResult> results = new ArrayList<>();
        results.add(measureQuery("REGULAR_USERS_12M", TransactionStatisticsMode.GROUP_BY, dataset,
                warmupRuns, measurementRuns, new RoundRobinQuery(users, userId -> repository.findMonthlyStatistics(
                        userId, dataset.fromMonth(), dataset.toMonth()
                ).size())));
        results.add(measureQuery("REGULAR_USERS_12M", TransactionStatisticsMode.MATERIALIZED_VIEW, dataset,
                warmupRuns, measurementRuns, new RoundRobinQuery(users, userId -> repository.findMonthlyStatisticsFromMaterializedView(
                        userId, dataset.fromMonth(), dataset.toMonth()
                ).size())));
        results.add(measureQuery("REGULAR_USERS_12M", TransactionStatisticsMode.SUMMARY, dataset,
                warmupRuns, measurementRuns, new RoundRobinQuery(users, userId -> repository.findMonthlyStatisticsFromSummary(
                        userId, dataset.fromMonth(), dataset.toMonth()
                ).size())));
        return results;
    }

    private BenchmarkResult measureQuery(String scenario, TransactionStatisticsMode mode, BenchmarkDataset dataset,
                                         int warmupRuns, int measurementRuns, Supplier<Integer> query) {
        return measure(scenario, mode.name() + "_QUERY", dataset, warmupRuns, measurementRuns, () -> {
            int rows = query.get();
            return "rowsReturned=" + rows;
        });
    }

    private BenchmarkResult measureRefresh(String scenario, BenchmarkDataset dataset,
                                           int warmupRuns, int measurementRuns, Runnable refresh) {
        return measure(scenario, scenario, dataset, warmupRuns, measurementRuns, () -> {
            refresh.run();
            return "OK";
        });
    }

    private BenchmarkResult measureRefreshAllowingFailure(String scenario, BenchmarkDataset dataset,
                                                          int warmupRuns, int measurementRuns, Runnable refresh) {
        try {
            return measureRefresh(scenario, dataset, warmupRuns, measurementRuns, refresh);
        } catch (RuntimeException exception) {
            return BenchmarkResult.failed(scenario, scenario, dataset, warmupRuns, measurementRuns,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private BenchmarkResult measure(String scenario, String mode, BenchmarkDataset dataset,
                                    int warmupRuns, int measurementRuns, MeasuredOperation operation) {
        for (int index = 0; index < warmupRuns; index++) {
            operation.run();
        }

        List<Long> latencies = new ArrayList<>(measurementRuns);
        int errorCount = 0;
        String notes = "";
        for (int index = 0; index < measurementRuns; index++) {
            long startedAt = System.nanoTime();
            try {
                notes = operation.run();
            } catch (RuntimeException exception) {
                errorCount++;
                notes = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            }
            latencies.add(System.nanoTime() - startedAt);
        }
        return BenchmarkResult.of(scenario, mode, dataset, warmupRuns, measurementRuns, latencies, errorCount, notes);
    }

    private void createStatisticsObjects() throws Exception {
        jdbcTemplate.execute("DROP MATERIALIZED VIEW IF EXISTS mv_transaction_monthly_statistics");
        executeSqlResource("db/migration/postgresql/V3__add_transaction_monthly_statistics_materialized_view.sql");
        executeSqlResource("db/migration/postgresql/V4__add_transaction_monthly_summary.sql");
    }

    private void executeSqlResource(String location) throws Exception {
        ClassPathResource resource = new ClassPathResource(location);
        String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        jdbcTemplate.execute(sql);
    }

    private Path writeCsv(List<BenchmarkResult> results) throws Exception {
        Path csv = Path.of("build", "perf", "transaction-statistics-benchmark-results.csv");
        Files.createDirectories(csv.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("scenario,mode,dataset_rows,user_count,month_count,warmup_runs,measurement_runs,avg_ms,p95_ms,p99_ms,min_ms,max_ms,throughput_ops,error_count,notes");
        for (BenchmarkResult result : results) {
            lines.add(result.toCsvLine());
        }
        Files.write(csv, lines, StandardCharsets.UTF_8);
        return csv;
    }

    private void printMarkdown(BenchmarkDataset dataset, List<BenchmarkResult> results, Path csv) {
        System.out.println();
        System.out.println("## Transaction Statistics Benchmark");
        System.out.printf("- Dataset rows: %,d%n", dataset.totalRows());
        System.out.printf("- Users: %,d%n", dataset.userCount());
        System.out.printf("- Months: %,d (%s ~ %s)%n", dataset.monthCount(), dataset.fromMonth(), dataset.toMonth());
        System.out.printf("- Source rows: wallet %,d / card %,d / exchange %,d%n",
                dataset.walletRows(), dataset.cardRows(), dataset.exchangeRows());
        System.out.println("- CSV: `" + csv + "`");
        System.out.println();
        System.out.println("| Scenario | Mode | Avg | p95 | p99 | Min | Max | Throughput | Errors | Notes |");
        System.out.println("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |");
        for (BenchmarkResult result : results) {
            System.out.printf(Locale.US,
                    "| %s | %s | %.3fms | %.3fms | %.3fms | %.3fms | %.3fms | %.2f ops/s | %d | %s |%n",
                    result.scenario(),
                    result.mode(),
                    result.averageMs(),
                    result.p95Ms(),
                    result.p99Ms(),
                    result.minMs(),
                    result.maxMs(),
                    result.throughputOps(),
                    result.errorCount(),
                    result.notes()
            );
        }
    }

    private static int environmentInt(String name, int defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private static long environmentLong(String name, long defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : Long.parseLong(value);
    }

    private interface MeasuredOperation {
        String run();
    }

    private interface UserQuery {
        int run(Long userId);
    }

    private static class RoundRobinQuery implements Supplier<Integer> {
        private final List<Long> userIds;
        private final UserQuery query;
        private int cursor;

        private RoundRobinQuery(List<Long> userIds, UserQuery query) {
            this.userIds = userIds;
            this.query = query;
        }

        @Override
        public Integer get() {
            Long userId = userIds.get(cursor % userIds.size());
            cursor++;
            return query.run(userId);
        }
    }

    private record BenchmarkResult(
            String scenario,
            String mode,
            int datasetRows,
            int userCount,
            int monthCount,
            int warmupRuns,
            int measurementRuns,
            double averageMs,
            double p95Ms,
            double p99Ms,
            double minMs,
            double maxMs,
            double throughputOps,
            int errorCount,
            String notes
    ) {
        private static BenchmarkResult of(String scenario, String mode, BenchmarkDataset dataset,
                                          int warmupRuns, int measurementRuns, List<Long> latencies,
                                          int errorCount, String notes) {
            latencies.sort(Comparator.naturalOrder());
            long totalNanos = latencies.stream().mapToLong(Long::longValue).sum();
            return new BenchmarkResult(
                    scenario,
                    mode,
                    dataset.totalRows(),
                    dataset.userCount(),
                    dataset.monthCount(),
                    warmupRuns,
                    measurementRuns,
                    toMillis(totalNanos / (double) latencies.size()),
                    percentileMillis(latencies, 0.95),
                    percentileMillis(latencies, 0.99),
                    toMillis(latencies.get(0)),
                    toMillis(latencies.get(latencies.size() - 1)),
                    measurementRuns * 1_000_000_000.0 / Duration.ofNanos(totalNanos).toNanos(),
                    errorCount,
                    notes
            );
        }

        private static BenchmarkResult failed(String scenario, String mode, BenchmarkDataset dataset,
                                              int warmupRuns, int measurementRuns, String notes) {
            return new BenchmarkResult(scenario, mode, dataset.totalRows(), dataset.userCount(), dataset.monthCount(),
                    warmupRuns, measurementRuns, 0, 0, 0, 0, 0, 0, measurementRuns, notes);
        }

        private String toCsvLine() {
            return String.join(",",
                    scenario,
                    mode,
                    Integer.toString(datasetRows),
                    Integer.toString(userCount),
                    Integer.toString(monthCount),
                    Integer.toString(warmupRuns),
                    Integer.toString(measurementRuns),
                    format(averageMs),
                    format(p95Ms),
                    format(p99Ms),
                    format(minMs),
                    format(maxMs),
                    format(throughputOps),
                    Integer.toString(errorCount),
                    escapeCsv(notes)
            );
        }

        private static double percentileMillis(List<Long> sortedLatencies, double percentile) {
            int index = (int) Math.ceil(percentile * sortedLatencies.size()) - 1;
            return toMillis(sortedLatencies.get(Math.max(0, Math.min(index, sortedLatencies.size() - 1))));
        }

        private static double toMillis(double nanos) {
            return nanos / 1_000_000.0;
        }

        private static String format(double value) {
            return String.format(Locale.US, "%.3f", value);
        }

        private static String escapeCsv(String value) {
            String escaped = value == null ? "" : value.replace("\"", "\"\"");
            return "\"" + escaped + "\"";
        }
    }
}
