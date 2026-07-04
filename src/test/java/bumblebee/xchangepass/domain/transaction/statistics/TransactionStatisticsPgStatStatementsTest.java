package bumblebee.xchangepass.domain.transaction.statistics;

import bumblebee.xchangepass.domain.transaction.statistics.TransactionStatisticsBenchmarkDataFactory.BenchmarkConfig;
import bumblebee.xchangepass.domain.transaction.statistics.TransactionStatisticsBenchmarkDataFactory.BenchmarkDataset;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TransactionStatisticsJdbcRepository.class, QueryDSLConfig.class})
@EnabledIfEnvironmentVariable(named = "RUN_TRANSACTION_STATISTICS_PG_STAT_STATEMENTS", matches = "true")
class TransactionStatisticsPgStatStatementsTest {

    private static final int DEFAULT_ROWS = 100_000;
    private static final int DEFAULT_USERS = 1_000;
    private static final int DEFAULT_MONTHS = 12;
    private static final long DEFAULT_SEED = 20260704L;
    private static final int DEFAULT_ITERATIONS = 20;
    private static final int TOP_STATEMENT_LIMIT = 10;
    private static final Path OUTPUT_DIR = Path.of("build", "perf", "pg-stat-statements");
    private static final String CSV_HEADER = String.join(",",
            "scenario",
            "statement_rank",
            "query_type",
            "query",
            "calls",
            "total_exec_time_ms",
            "mean_exec_time_ms",
            "min_exec_time_ms",
            "max_exec_time_ms",
            "rows",
            "shared_blks_hit",
            "shared_blks_read",
            "shared_blks_dirtied",
            "shared_blks_written",
            "temp_blks_read",
            "temp_blks_written",
            "blk_read_time_ms",
            "blk_write_time_ms",
            "notes"
    );

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("xcp_transaction_statistics_pgstat")
            .withUsername("benchmark")
            .withPassword("benchmark")
            .withCommand(
                    "postgres",
                    "-c", "shared_preload_libraries=pg_stat_statements",
                    "-c", "pg_stat_statements.track=all",
                    "-c", "track_io_timing=on"
            );

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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void collectTransactionStatisticsPgStatStatements() throws Exception {
        BenchmarkConfig config = new BenchmarkConfig(
                environmentInt("TRANSACTION_STATISTICS_BENCHMARK_ROWS", DEFAULT_ROWS),
                environmentInt("TRANSACTION_STATISTICS_BENCHMARK_USERS", DEFAULT_USERS),
                environmentInt("TRANSACTION_STATISTICS_BENCHMARK_MONTHS", DEFAULT_MONTHS),
                environmentLong("TRANSACTION_STATISTICS_BENCHMARK_SEED", DEFAULT_SEED)
        );
        int iterations = environmentInt("TRANSACTION_STATISTICS_PGSTAT_ITERATIONS", DEFAULT_ITERATIONS);

        createExtension();
        createStatisticsObjects();
        TransactionStatisticsBenchmarkDataFactory dataFactory =
                new TransactionStatisticsBenchmarkDataFactory(jdbcTemplate);
        BenchmarkDataset dataset = dataFactory.create(config);
        dataFactory.analyzeTables();
        repository.refreshMaterializedView();
        repository.refreshMonthlySummary(dataset.fromMonth(), dataset.toMonth());
        dataFactory.analyzeTables();

        YearMonth threeMonthTo = dataset.fromMonth().plusMonths(Math.min(2, dataset.monthCount() - 1L));
        Long regularUserId = dataset.regularUserIds().get(0);

        List<PgStatStatementRow> rows = new ArrayList<>();
        rows.addAll(measureScenario("GROUP_BY_HEAVY_USER_12M", "GROUP_BY_QUERY", iterations,
                () -> repository.findMonthlyStatistics(dataset.heavyUserId(), dataset.fromMonth(), dataset.toMonth()).size()));
        rows.addAll(measureScenario("MATERIALIZED_VIEW_HEAVY_USER_12M", "MATERIALIZED_VIEW_QUERY", iterations,
                () -> repository.findMonthlyStatisticsFromMaterializedView(dataset.heavyUserId(), dataset.fromMonth(), dataset.toMonth()).size()));
        rows.addAll(measureScenario("SUMMARY_HEAVY_USER_12M", "SUMMARY_QUERY", iterations,
                () -> repository.findMonthlyStatisticsFromSummary(dataset.heavyUserId(), dataset.fromMonth(), dataset.toMonth()).size()));

        rows.addAll(measureScenario("GROUP_BY_HEAVY_USER_3M", "GROUP_BY_QUERY", iterations,
                () -> repository.findMonthlyStatistics(dataset.heavyUserId(), dataset.fromMonth(), threeMonthTo).size()));
        rows.addAll(measureScenario("MATERIALIZED_VIEW_HEAVY_USER_3M", "MATERIALIZED_VIEW_QUERY", iterations,
                () -> repository.findMonthlyStatisticsFromMaterializedView(dataset.heavyUserId(), dataset.fromMonth(), threeMonthTo).size()));
        rows.addAll(measureScenario("SUMMARY_HEAVY_USER_3M", "SUMMARY_QUERY", iterations,
                () -> repository.findMonthlyStatisticsFromSummary(dataset.heavyUserId(), dataset.fromMonth(), threeMonthTo).size()));

        rows.addAll(measureScenario("GROUP_BY_REGULAR_USER_12M", "GROUP_BY_QUERY", iterations,
                () -> repository.findMonthlyStatistics(regularUserId, dataset.fromMonth(), dataset.toMonth()).size()));
        rows.addAll(measureScenario("MATERIALIZED_VIEW_REGULAR_USER_12M", "MATERIALIZED_VIEW_QUERY", iterations,
                () -> repository.findMonthlyStatisticsFromMaterializedView(regularUserId, dataset.fromMonth(), dataset.toMonth()).size()));
        rows.addAll(measureScenario("SUMMARY_REGULAR_USER_12M", "SUMMARY_QUERY", iterations,
                () -> repository.findMonthlyStatisticsFromSummary(regularUserId, dataset.fromMonth(), dataset.toMonth()).size()));

        rows.addAll(measureScenario("MV_REFRESH", "MV_REFRESH", iterations,
                () -> {
                    repository.refreshMaterializedView();
                    return 0;
                }));
        rows.addAll(measureScenarioAllowingFailure("MV_REFRESH_CONCURRENTLY", "MV_REFRESH_CONCURRENTLY", iterations,
                () -> {
                    repository.refreshMaterializedViewConcurrently();
                    return 0;
                }));
        rows.addAll(measureScenario("SUMMARY_REFRESH", "SUMMARY_REFRESH", iterations,
                () -> {
                    repository.refreshMonthlySummary(dataset.fromMonth(), dataset.toMonth());
                    return 0;
                }));

        Path csv = writeCsv(rows);
        Path readme = writeReadme(dataset, iterations, rows);
        printSummary(dataset, iterations, csv, readme, rows);
    }

    private List<PgStatStatementRow> measureScenario(String scenario, String queryType, int iterations,
                                                     MeasuredOperation operation) {
        resetPgStatStatements();
        int rowsReturned = 0;
        for (int index = 0; index < iterations; index++) {
            rowsReturned += operation.run();
        }
        return collectScenarioRows(scenario, queryType, "iterations=" + iterations + "; rowsReturned=" + rowsReturned);
    }

    private List<PgStatStatementRow> measureScenarioAllowingFailure(
            String scenario, String queryType, int iterations, MeasuredOperation operation
    ) {
        resetPgStatStatements();
        int errors = 0;
        String errorNote = "";
        for (int index = 0; index < iterations; index++) {
            try {
                operation.run();
            } catch (RuntimeException exception) {
                errors++;
                errorNote = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            }
        }
        return collectScenarioRows(scenario, queryType,
                "iterations=" + iterations + "; errors=" + errors + (errorNote.isBlank() ? "" : "; " + errorNote));
    }

    private List<PgStatStatementRow> collectScenarioRows(String scenario, String queryType, String notes) {
        List<PgStatStatementRow> rows = jdbcTemplate.query("""
                        select
                            query,
                            calls,
                            total_exec_time,
                            mean_exec_time,
                            min_exec_time,
                            max_exec_time,
                            rows,
                            shared_blks_hit,
                            shared_blks_read,
                            shared_blks_dirtied,
                            shared_blks_written,
                            temp_blks_read,
                            temp_blks_written,
                            blk_read_time,
                            blk_write_time
                        from pg_stat_statements
                        where dbid = (select oid from pg_database where datname = current_database())
                          and query not ilike '%pg_stat_statements%'
                          and query not ilike '%pg_stat_clear_snapshot%'
                        order by total_exec_time desc
                        limit ?
                        """,
                (rs, rowNum) -> new PgStatStatementRow(
                        scenario,
                        rowNum + 1,
                        queryType,
                        normalizeWhitespace(rs.getString("query")),
                        rs.getLong("calls"),
                        rs.getDouble("total_exec_time"),
                        rs.getDouble("mean_exec_time"),
                        rs.getDouble("min_exec_time"),
                        rs.getDouble("max_exec_time"),
                        rs.getLong("rows"),
                        rs.getLong("shared_blks_hit"),
                        rs.getLong("shared_blks_read"),
                        rs.getLong("shared_blks_dirtied"),
                        rs.getLong("shared_blks_written"),
                        rs.getLong("temp_blks_read"),
                        rs.getLong("temp_blks_written"),
                        rs.getDouble("blk_read_time"),
                        rs.getDouble("blk_write_time"),
                        notes
                ),
                TOP_STATEMENT_LIMIT);
        return rows.stream()
                .sorted(Comparator.comparingInt(PgStatStatementRow::statementRank))
                .toList();
    }

    private Path writeCsv(List<PgStatStatementRow> rows) throws Exception {
        Files.createDirectories(OUTPUT_DIR);
        Path csv = OUTPUT_DIR.resolve("transaction-statistics-pg-stat-statements.csv");
        List<String> lines = new ArrayList<>();
        lines.add(CSV_HEADER);
        for (PgStatStatementRow row : rows) {
            lines.add(row.toCsvLine());
        }
        Files.write(csv, lines, StandardCharsets.UTF_8);
        return csv;
    }

    private Path writeReadme(BenchmarkDataset dataset, int iterations, List<PgStatStatementRow> rows) throws Exception {
        Files.createDirectories(OUTPUT_DIR);
        Path readme = OUTPUT_DIR.resolve("README.md");
        List<String> lines = new ArrayList<>();
        lines.add("# 거래 통계 pg_stat_statements 수집 결과");
        lines.add("");
        lines.add("- 데이터 rows: " + dataset.totalRows());
        lines.add("- 사용자 수: " + dataset.userCount());
        lines.add("- 월 수: " + dataset.monthCount() + " (" + dataset.fromMonth() + " ~ " + dataset.toMonth() + ")");
        lines.add("- 원천 rows: wallet " + dataset.walletRows()
                + " / card " + dataset.cardRows()
                + " / exchange " + dataset.exchangeRows());
        lines.add("- 반복 횟수: " + iterations);
        lines.add("- 수집 도구: pg_stat_statements");
        lines.add("- 적용 범위: benchmark 전용 Testcontainers PostgreSQL");
        lines.add("");
        lines.add("| 시나리오 | 쿼리 유형 | 호출 수 | 평균 실행 시간 | 총 실행 시간 | Rows | Shared Hit | Shared Read | Temp Read | Temp Written |");
        lines.add("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |");
        for (PgStatStatementRow row : rows) {
            if (row.statementRank() == 1) {
                lines.add(String.format(Locale.US,
                        "| %s | %s | %d | %.3fms | %.3fms | %d | %d | %d | %d | %d |",
                        row.scenario(),
                        row.queryType(),
                        row.calls(),
                        row.meanExecTimeMs(),
                        row.totalExecTimeMs(),
                        row.rows(),
                        row.sharedBlksHit(),
                        row.sharedBlksRead(),
                        row.tempBlksRead(),
                        row.tempBlksWritten()
                ));
            }
        }
        Files.write(readme, lines, StandardCharsets.UTF_8);
        return readme;
    }

    private void printSummary(BenchmarkDataset dataset, int iterations, Path csv, Path readme,
                              List<PgStatStatementRow> rows) {
        System.out.println();
        System.out.println("## Transaction Statistics pg_stat_statements");
        System.out.printf("- Dataset rows: %,d%n", dataset.totalRows());
        System.out.printf("- Users: %,d%n", dataset.userCount());
        System.out.printf("- Months: %,d (%s ~ %s)%n", dataset.monthCount(), dataset.fromMonth(), dataset.toMonth());
        System.out.printf("- Iterations: %,d%n", iterations);
        System.out.println("- CSV: `" + csv + "`");
        System.out.println("- README: `" + readme + "`");
        System.out.println();
        System.out.println("| Scenario | Query Type | Calls | Mean Exec | Total Exec | Rows | Shared Hit | Shared Read | Temp Read | Temp Written |");
        System.out.println("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |");
        for (PgStatStatementRow row : rows) {
            if (row.statementRank() == 1) {
                System.out.printf(Locale.US,
                        "| %s | %s | %d | %.3fms | %.3fms | %d | %d | %d | %d | %d |%n",
                        row.scenario(),
                        row.queryType(),
                        row.calls(),
                        row.meanExecTimeMs(),
                        row.totalExecTimeMs(),
                        row.rows(),
                        row.sharedBlksHit(),
                        row.sharedBlksRead(),
                        row.tempBlksRead(),
                        row.tempBlksWritten()
                );
            }
        }
    }

    private void createExtension() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_stat_statements");
    }

    private void resetPgStatStatements() {
        jdbcTemplate.execute("select pg_stat_statements_reset()");
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

    private static int environmentInt(String name, int defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private static long environmentLong(String name, long defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : Long.parseLong(value);
    }

    private static String normalizeWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private interface MeasuredOperation {
        int run();
    }

    private record PgStatStatementRow(
            String scenario,
            int statementRank,
            String queryType,
            String query,
            long calls,
            double totalExecTimeMs,
            double meanExecTimeMs,
            double minExecTimeMs,
            double maxExecTimeMs,
            long rows,
            long sharedBlksHit,
            long sharedBlksRead,
            long sharedBlksDirtied,
            long sharedBlksWritten,
            long tempBlksRead,
            long tempBlksWritten,
            double blkReadTimeMs,
            double blkWriteTimeMs,
            String notes
    ) {

        private String toCsvLine() {
            return String.join(",",
                    escape(scenario),
                    Integer.toString(statementRank),
                    escape(queryType),
                    escape(query),
                    Long.toString(calls),
                    format(totalExecTimeMs),
                    format(meanExecTimeMs),
                    format(minExecTimeMs),
                    format(maxExecTimeMs),
                    Long.toString(rows),
                    Long.toString(sharedBlksHit),
                    Long.toString(sharedBlksRead),
                    Long.toString(sharedBlksDirtied),
                    Long.toString(sharedBlksWritten),
                    Long.toString(tempBlksRead),
                    Long.toString(tempBlksWritten),
                    format(blkReadTimeMs),
                    format(blkWriteTimeMs),
                    escape(notes)
            );
        }

        private static String format(double value) {
            return String.format(Locale.US, "%.3f", value);
        }

        private static String escape(String value) {
            String escaped = value == null ? "" : value.replace("\"", "\"\"");
            return "\"" + escaped + "\"";
        }
    }
}
