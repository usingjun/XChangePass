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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TransactionStatisticsJdbcRepository.class, QueryDSLConfig.class})
@EnabledIfEnvironmentVariable(named = "RUN_TRANSACTION_STATISTICS_TABLE_STATS", matches = "true")
class TransactionStatisticsTableStatsTest {

    private static final int DEFAULT_ROWS = 100_000;
    private static final int DEFAULT_USERS = 1_000;
    private static final int DEFAULT_MONTHS = 12;
    private static final long DEFAULT_SEED = 20260704L;
    private static final int DEFAULT_REFRESH_REPEAT = 5;
    private static final Path OUTPUT_DIR = Path.of("build", "perf", "table-stats");
    private static final String CSV_HEADER = String.join(",",
            "snapshot",
            "relname",
            "seq_scan",
            "seq_tup_read",
            "idx_scan",
            "idx_tup_fetch",
            "n_tup_ins",
            "n_tup_upd",
            "n_tup_del",
            "n_live_tup",
            "n_dead_tup",
            "last_vacuum",
            "last_autovacuum",
            "last_analyze",
            "last_autoanalyze",
            "vacuum_count",
            "autovacuum_count",
            "analyze_count",
            "autoanalyze_count",
            "notes"
    );

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("xcp_transaction_statistics_table_stats")
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void collectTransactionStatisticsTableStats() throws Exception {
        BenchmarkConfig config = new BenchmarkConfig(
                environmentInt("TRANSACTION_STATISTICS_BENCHMARK_ROWS", DEFAULT_ROWS),
                environmentInt("TRANSACTION_STATISTICS_BENCHMARK_USERS", DEFAULT_USERS),
                environmentInt("TRANSACTION_STATISTICS_BENCHMARK_MONTHS", DEFAULT_MONTHS),
                environmentLong("TRANSACTION_STATISTICS_BENCHMARK_SEED", DEFAULT_SEED)
        );
        int refreshRepeat = environmentInt(
                "TRANSACTION_STATISTICS_TABLE_STATS_REFRESH_REPEAT",
                DEFAULT_REFRESH_REPEAT
        );

        createStatisticsObjects();
        TransactionStatisticsBenchmarkDataFactory dataFactory =
                new TransactionStatisticsBenchmarkDataFactory(jdbcTemplate);
        BenchmarkDataset dataset = dataFactory.create(config);

        List<TableStatsSnapshot> snapshots = new ArrayList<>();
        snapshots.addAll(capture(
                "A_AFTER_DATA_LOAD",
                "benchmark data loaded; summary refresh not executed"
        ));

        dataFactory.analyzeTables();
        snapshots.addAll(capture(
                "B_AFTER_ANALYZE",
                "manual ANALYZE executed for source and summary tables"
        ));

        repository.refreshMonthlySummary(dataset.fromMonth(), dataset.toMonth());
        snapshots.addAll(capture(
                "C_AFTER_FIRST_SUMMARY_REFRESH",
                "first summary refresh completed"
        ));

        for (int index = 0; index < refreshRepeat; index++) {
            repository.refreshMonthlySummary(dataset.fromMonth(), dataset.toMonth());
        }
        snapshots.addAll(capture(
                "D_AFTER_REPEATED_SUMMARY_REFRESH",
                "summary refresh repeated " + refreshRepeat + " additional times"
        ));

        String vacuumNote = "VACUUM ANALYZE transaction_monthly_summary completed";
        try {
            jdbcTemplate.execute("VACUUM ANALYZE transaction_monthly_summary");
        } catch (RuntimeException exception) {
            vacuumNote = "VACUUM ANALYZE skipped: " + exception.getClass().getSimpleName()
                    + " - " + exception.getMessage();
        }
        snapshots.addAll(capture("E_AFTER_VACUUM_ANALYZE", vacuumNote));

        Path csv = writeCsv(snapshots);
        Path readme = writeReadme(dataset, refreshRepeat, snapshots);
        printSummary(dataset, refreshRepeat, csv, readme, snapshots);
    }

    private List<TableStatsSnapshot> capture(String snapshot, String notes) {
        forceStatisticsFlush();
        return jdbcTemplate.query("""
                        select
                            relname,
                            seq_scan,
                            seq_tup_read,
                            idx_scan,
                            idx_tup_fetch,
                            n_tup_ins,
                            n_tup_upd,
                            n_tup_del,
                            n_live_tup,
                            n_dead_tup,
                            last_vacuum::text as last_vacuum,
                            last_autovacuum::text as last_autovacuum,
                            last_analyze::text as last_analyze,
                            last_autoanalyze::text as last_autoanalyze,
                            vacuum_count,
                            autovacuum_count,
                            analyze_count,
                            autoanalyze_count
                        from pg_stat_user_tables
                        where relname in (
                            'wallet_transaction',
                            'card_transaction',
                            'exchange_transaction',
                            'transaction_monthly_summary'
                        )
                        order by relname
                        """,
                (rs, rowNum) -> new TableStatsSnapshot(
                        snapshot,
                        rs.getString("relname"),
                        rs.getLong("seq_scan"),
                        rs.getLong("seq_tup_read"),
                        rs.getLong("idx_scan"),
                        rs.getLong("idx_tup_fetch"),
                        rs.getLong("n_tup_ins"),
                        rs.getLong("n_tup_upd"),
                        rs.getLong("n_tup_del"),
                        rs.getLong("n_live_tup"),
                        rs.getLong("n_dead_tup"),
                        nullableString(rs.getString("last_vacuum")),
                        nullableString(rs.getString("last_autovacuum")),
                        nullableString(rs.getString("last_analyze")),
                        nullableString(rs.getString("last_autoanalyze")),
                        rs.getLong("vacuum_count"),
                        rs.getLong("autovacuum_count"),
                        rs.getLong("analyze_count"),
                        rs.getLong("autoanalyze_count"),
                        notes
                ));
    }

    private void forceStatisticsFlush() {
        jdbcTemplate.execute("select pg_stat_clear_snapshot()");
        jdbcTemplate.execute("select pg_stat_force_next_flush()");
    }

    private Path writeCsv(List<TableStatsSnapshot> snapshots) throws Exception {
        Files.createDirectories(OUTPUT_DIR);
        Path csv = OUTPUT_DIR.resolve("transaction-statistics-table-stats.csv");
        List<String> lines = new ArrayList<>();
        lines.add(CSV_HEADER);
        for (TableStatsSnapshot snapshot : snapshots) {
            lines.add(snapshot.toCsvLine());
        }
        Files.write(csv, lines, StandardCharsets.UTF_8);
        return csv;
    }

    private Path writeReadme(BenchmarkDataset dataset, int refreshRepeat, List<TableStatsSnapshot> snapshots)
            throws Exception {
        Files.createDirectories(OUTPUT_DIR);
        Path readme = OUTPUT_DIR.resolve("README.md");
        List<String> lines = new ArrayList<>();
        lines.add("# Transaction statistics table stats");
        lines.add("");
        lines.add("- Dataset rows: " + dataset.totalRows());
        lines.add("- Users: " + dataset.userCount());
        lines.add("- Months: " + dataset.monthCount() + " (" + dataset.fromMonth() + " ~ " + dataset.toMonth() + ")");
        lines.add("- Source rows: wallet " + dataset.walletRows()
                + " / card " + dataset.cardRows()
                + " / exchange " + dataset.exchangeRows());
        lines.add("- Summary refresh repeat: " + refreshRepeat);
        lines.add("- Collector: pg_stat_user_tables");
        lines.add("");
        lines.add("| Snapshot | relname | n_tup_ins | n_tup_del | n_live_tup | n_dead_tup | analyze_count | vacuum_count | notes |");
        lines.add("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |");
        for (TableStatsSnapshot snapshot : snapshots) {
            if ("transaction_monthly_summary".equals(snapshot.relname())) {
                lines.add(String.format(Locale.US,
                        "| %s | %s | %d | %d | %d | %d | %d | %d | %s |",
                        snapshot.snapshot(),
                        snapshot.relname(),
                        snapshot.nTupIns(),
                        snapshot.nTupDel(),
                        snapshot.nLiveTup(),
                        snapshot.nDeadTup(),
                        snapshot.analyzeCount(),
                        snapshot.vacuumCount(),
                        snapshot.notes()
                ));
            }
        }
        Files.write(readme, lines, StandardCharsets.UTF_8);
        return readme;
    }

    private void printSummary(BenchmarkDataset dataset, int refreshRepeat, Path csv, Path readme,
                              List<TableStatsSnapshot> snapshots) {
        System.out.println();
        System.out.println("## Transaction Statistics Table Stats");
        System.out.printf("- Dataset rows: %,d%n", dataset.totalRows());
        System.out.printf("- Users: %,d%n", dataset.userCount());
        System.out.printf("- Months: %,d (%s ~ %s)%n", dataset.monthCount(), dataset.fromMonth(), dataset.toMonth());
        System.out.printf("- Summary refresh repeat: %,d%n", refreshRepeat);
        System.out.println("- CSV: `" + csv + "`");
        System.out.println("- README: `" + readme + "`");
        System.out.println();
        System.out.println("| Snapshot | relname | n_tup_ins | n_tup_del | n_live_tup | n_dead_tup | analyze_count | vacuum_count | notes |");
        System.out.println("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |");
        for (TableStatsSnapshot snapshot : snapshots) {
            if ("transaction_monthly_summary".equals(snapshot.relname())) {
                System.out.printf(Locale.US,
                        "| %s | %s | %d | %d | %d | %d | %d | %d | %s |%n",
                        snapshot.snapshot(),
                        snapshot.relname(),
                        snapshot.nTupIns(),
                        snapshot.nTupDel(),
                        snapshot.nLiveTup(),
                        snapshot.nDeadTup(),
                        snapshot.analyzeCount(),
                        snapshot.vacuumCount(),
                        snapshot.notes()
                );
            }
        }
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

    private static String nullableString(String value) {
        return value == null ? "" : value;
    }

    private static int environmentInt(String name, int defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private static long environmentLong(String name, long defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : Long.parseLong(value);
    }

    private record TableStatsSnapshot(
            String snapshot,
            String relname,
            long seqScan,
            long seqTupRead,
            long idxScan,
            long idxTupFetch,
            long nTupIns,
            long nTupUpd,
            long nTupDel,
            long nLiveTup,
            long nDeadTup,
            String lastVacuum,
            String lastAutovacuum,
            String lastAnalyze,
            String lastAutoanalyze,
            long vacuumCount,
            long autovacuumCount,
            long analyzeCount,
            long autoanalyzeCount,
            String notes
    ) {

        private String toCsvLine() {
            return String.join(",",
                    escape(snapshot),
                    escape(relname),
                    Long.toString(seqScan),
                    Long.toString(seqTupRead),
                    Long.toString(idxScan),
                    Long.toString(idxTupFetch),
                    Long.toString(nTupIns),
                    Long.toString(nTupUpd),
                    Long.toString(nTupDel),
                    Long.toString(nLiveTup),
                    Long.toString(nDeadTup),
                    escape(lastVacuum),
                    escape(lastAutovacuum),
                    escape(lastAnalyze),
                    escape(lastAutoanalyze),
                    Long.toString(vacuumCount),
                    Long.toString(autovacuumCount),
                    Long.toString(analyzeCount),
                    Long.toString(autoanalyzeCount),
                    escape(notes)
            );
        }

        private static String escape(String value) {
            String escaped = value == null ? "" : value.replace("\"", "\"\"");
            return "\"" + escaped + "\"";
        }
    }
}
