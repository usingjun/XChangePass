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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TransactionStatisticsJdbcRepository.class, QueryDSLConfig.class})
@EnabledIfEnvironmentVariable(named = "RUN_TRANSACTION_STATISTICS_EXPLAIN", matches = "true")
class TransactionStatisticsExplainAnalyzeTest {

    private static final int DEFAULT_ROWS = 100_000;
    private static final int DEFAULT_USERS = 1_000;
    private static final int DEFAULT_MONTHS = 12;
    private static final long DEFAULT_SEED = 20260704L;
    private static final Path EXPLAIN_DIR = Path.of("build", "perf", "explain");

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("xcp_transaction_statistics_explain")
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
    void collectExplainAnalyzePlans() throws Exception {
        BenchmarkConfig config = new BenchmarkConfig(
                environmentInt("TRANSACTION_STATISTICS_BENCHMARK_ROWS", DEFAULT_ROWS),
                environmentInt("TRANSACTION_STATISTICS_BENCHMARK_USERS", DEFAULT_USERS),
                environmentInt("TRANSACTION_STATISTICS_BENCHMARK_MONTHS", DEFAULT_MONTHS),
                environmentLong("TRANSACTION_STATISTICS_BENCHMARK_SEED", DEFAULT_SEED)
        );

        createStatisticsObjects();
        TransactionStatisticsBenchmarkDataFactory dataFactory =
                new TransactionStatisticsBenchmarkDataFactory(jdbcTemplate);
        BenchmarkDataset dataset = dataFactory.create(config);
        repository.refreshMaterializedView();
        repository.refreshMonthlySummary(dataset.fromMonth(), dataset.toMonth());
        analyzeTables(dataFactory);

        Files.createDirectories(EXPLAIN_DIR);
        writeNotes(dataset);

        YearMonth threeMonthTo = dataset.fromMonth().plusMonths(Math.min(2, dataset.monthCount() - 1L));
        Long regularUserId = dataset.regularUserIds().get(0);

        explainMonthlyQueries("heavy-user-12m", dataset.heavyUserId(), dataset.fromMonth(), dataset.toMonth());
        explainMonthlyQueries("heavy-user-3m", dataset.heavyUserId(), dataset.fromMonth(), threeMonthTo);
        explainMonthlyQueries("regular-user-12m", regularUserId, dataset.fromMonth(), dataset.toMonth());

        explain("materialized-view-definition-select.txt",
                "Materialized view refresh source SELECT. REFRESH MATERIALIZED VIEW itself is not explained here.",
                MATERIALIZED_VIEW_DEFINITION_SELECT_SQL);
        explain("summary-refresh-delete.txt",
                "Summary refresh DELETE for the benchmark month range.",
                DELETE_MONTHLY_SUMMARY_SQL,
                toStartTimestamp(dataset.fromMonth()), toExclusiveEndTimestamp(dataset.toMonth()));
        Timestamp dataAsOf = Timestamp.valueOf(LocalDateTime.now());
        explain("summary-refresh-insert-select.txt",
                "Summary refresh INSERT SELECT for the benchmark month range. This runs after the DELETE explain.",
                INSERT_MONTHLY_SUMMARY_SQL,
                dataAsOf, dataAsOf, dataAsOf,
                toStartTimestamp(dataset.fromMonth()), toExclusiveEndTimestamp(dataset.toMonth()),
                toStartTimestamp(dataset.fromMonth()), toExclusiveEndTimestamp(dataset.toMonth()),
                toStartTimestamp(dataset.fromMonth()), toExclusiveEndTimestamp(dataset.toMonth()),
                toStartTimestamp(dataset.fromMonth()), toExclusiveEndTimestamp(dataset.toMonth()),
                toStartTimestamp(dataset.fromMonth()), toExclusiveEndTimestamp(dataset.toMonth()),
                toStartTimestamp(dataset.fromMonth()), toExclusiveEndTimestamp(dataset.toMonth()));
    }

    private void explainMonthlyQueries(String scenario, Long userId, YearMonth fromMonth, YearMonth toMonth)
            throws Exception {
        Timestamp fromTimestamp = toStartTimestamp(fromMonth);
        Timestamp toTimestamp = toExclusiveEndTimestamp(toMonth);

        explain("group-by-" + scenario + ".txt",
                "GROUP_BY query for " + scenario + ".",
                MONTHLY_STATISTICS_SQL,
                userId, fromTimestamp, toTimestamp,
                userId, fromTimestamp, toTimestamp,
                userId, fromTimestamp, toTimestamp,
                userId, fromTimestamp, toTimestamp,
                userId, fromTimestamp, toTimestamp,
                userId, fromTimestamp, toTimestamp);
        explain("materialized-view-" + scenario + ".txt",
                "MATERIALIZED_VIEW query for " + scenario + ".",
                MONTHLY_STATISTICS_MATERIALIZED_VIEW_SQL,
                userId, fromTimestamp, toTimestamp);
        explain("summary-" + scenario + ".txt",
                "SUMMARY query for " + scenario + ".",
                MONTHLY_STATISTICS_SUMMARY_SQL,
                userId, fromTimestamp, toTimestamp);
    }

    private void explain(String fileName, String note, String sql, Object... params) throws Exception {
        List<String> plan = jdbcTemplate.query(
                "EXPLAIN (ANALYZE, BUFFERS)\n" + sql,
                ps -> bind(ps, params),
                (rs, rowNum) -> rs.getString(1)
        );

        List<String> lines = new ArrayList<>();
        lines.add("# " + fileName);
        lines.add(note);
        lines.add("");
        lines.add("```sql");
        lines.add(sql.strip());
        lines.add("```");
        lines.add("");
        lines.add("```text");
        lines.addAll(plan);
        lines.add("```");
        Files.write(EXPLAIN_DIR.resolve(fileName), lines, StandardCharsets.UTF_8);
    }

    private void bind(PreparedStatement ps, Object[] params) throws java.sql.SQLException {
        for (int index = 0; index < params.length; index++) {
            ps.setObject(index + 1, params[index]);
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

    private void analyzeTables(TransactionStatisticsBenchmarkDataFactory dataFactory) {
        dataFactory.analyzeTables();
        jdbcTemplate.execute("ANALYZE mv_transaction_monthly_statistics");
    }

    private void writeNotes(BenchmarkDataset dataset) throws Exception {
        List<String> lines = List.of(
                "# Transaction statistics EXPLAIN ANALYZE",
                "",
                "- Dataset rows: " + dataset.totalRows(),
                "- Users: " + dataset.userCount(),
                "- Months: " + dataset.monthCount() + " (" + dataset.fromMonth() + " ~ " + dataset.toMonth() + ")",
                "- Source rows: wallet " + dataset.walletRows()
                        + " / card " + dataset.cardRows()
                        + " / exchange " + dataset.exchangeRows(),
                "- EXPLAIN option: EXPLAIN (ANALYZE, BUFFERS)",
                "- REFRESH MATERIALIZED VIEW is represented by the materialized view definition SELECT.",
                "- SUMMARY refresh is represented by DELETE and INSERT SELECT plans."
        );
        Files.write(EXPLAIN_DIR.resolve("README.md"), lines, StandardCharsets.UTF_8);
    }

    private Timestamp toStartTimestamp(YearMonth month) {
        return Timestamp.valueOf(month.atDay(1).atStartOfDay());
    }

    private Timestamp toExclusiveEndTimestamp(YearMonth month) {
        return Timestamp.valueOf(month.plusMonths(1).atDay(1).atStartOfDay());
    }

    private static int environmentInt(String name, int defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private static long environmentLong(String name, long defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : Long.parseLong(value);
    }

    private static final String MONTHLY_STATISTICS_SQL = """
            select
                user_id,
                bucket_month,
                source_type,
                transaction_type,
                currency,
                direction,
                sum(amount) as amount_sum,
                count(*) as transaction_count
            from (
                select
                    user_id,
                    date_trunc('month', transaction_time) as bucket_month,
                    'WALLET' as source_type,
                    transaction_type as transaction_type,
                    coalesce(from_currency, to_currency) as currency,
                    amount as amount,
                    case
                        when transaction_type = 'DEPOSIT' then 'INCOMING'
                        when transaction_type = 'WITHDRAWAL' then 'OUTGOING'
                    end as direction
                from wallet_transaction
                where user_id = ?
                  and transaction_time >= ?
                  and transaction_time < ?
                  and transaction_type in ('DEPOSIT', 'WITHDRAWAL')

                union all

                select
                    user_id,
                    date_trunc('month', transaction_time) as bucket_month,
                    'WALLET' as source_type,
                    transaction_type as transaction_type,
                    from_currency as currency,
                    amount as amount,
                    'OUTGOING' as direction
                from wallet_transaction
                where user_id = ?
                  and transaction_time >= ?
                  and transaction_time < ?
                  and transaction_type = 'TRANSFER'

                union all

                select
                    counterparty_user_id as user_id,
                    date_trunc('month', transaction_time) as bucket_month,
                    'WALLET' as source_type,
                    transaction_type as transaction_type,
                    to_currency as currency,
                    received_amount as amount,
                    'INCOMING' as direction
                from wallet_transaction
                where counterparty_user_id = ?
                  and transaction_time >= ?
                  and transaction_time < ?
                  and transaction_type = 'TRANSFER'

                union all

                select
                    user_id,
                    date_trunc('month', transaction_time) as bucket_month,
                    'CARD' as source_type,
                    transaction_type as transaction_type,
                    approved_currency as currency,
                    approved_amount as amount,
                    case
                        when transaction_type = 'PAYMENT' then 'OUTGOING'
                        when transaction_type in ('REFUND', 'DEPOSIT') then 'INCOMING'
                    end as direction
                from card_transaction
                where user_id = ?
                  and transaction_time >= ?
                  and transaction_time < ?
                  and transaction_type in ('PAYMENT', 'REFUND', 'DEPOSIT')

                union all

                select
                    user_id,
                    date_trunc('month', completed_at) as bucket_month,
                    'EXCHANGE' as source_type,
                    status as transaction_type,
                    from_currency as currency,
                    amount as amount,
                    'OUTGOING' as direction
                from exchange_transaction
                where user_id = ?
                  and completed_at >= ?
                  and completed_at < ?
                  and status = 'COMPLETED'
                  and completed_at is not null

                union all

                select
                    user_id,
                    date_trunc('month', completed_at) as bucket_month,
                    'EXCHANGE' as source_type,
                    status as transaction_type,
                    to_currency as currency,
                    received_amount as amount,
                    'INCOMING' as direction
                from exchange_transaction
                where user_id = ?
                  and completed_at >= ?
                  and completed_at < ?
                  and status = 'COMPLETED'
                  and completed_at is not null
            ) statistics_source
            where currency is not null
              and amount is not null
              and direction is not null
            group by user_id, bucket_month, source_type, transaction_type, currency, direction
            order by bucket_month, source_type, transaction_type, currency, direction
            """;

    private static final String MONTHLY_STATISTICS_MATERIALIZED_VIEW_SQL = """
            select
                user_id,
                bucket_month,
                source_type,
                transaction_type,
                currency,
                direction,
                amount_sum,
                transaction_count
            from mv_transaction_monthly_statistics
            where user_id = ?
              and bucket_month >= ?
              and bucket_month < ?
            order by bucket_month, source_type, transaction_type, currency, direction
            """;

    private static final String MONTHLY_STATISTICS_SUMMARY_SQL = """
            select
                user_id,
                bucket_month,
                source_type,
                transaction_type,
                currency,
                direction,
                amount_sum,
                transaction_count,
                data_as_of
            from transaction_monthly_summary
            where user_id = ?
              and bucket_month >= ?
              and bucket_month < ?
            order by bucket_month, source_type, transaction_type, currency, direction
            """;

    private static final String MATERIALIZED_VIEW_DEFINITION_SELECT_SQL = """
            select
                user_id,
                bucket_month,
                source_type,
                transaction_type,
                currency,
                direction,
                sum(amount) as amount_sum,
                count(*) as transaction_count
            from (
                select
                    user_id,
                    date_trunc('month', transaction_time) as bucket_month,
                    'WALLET' as source_type,
                    transaction_type as transaction_type,
                    coalesce(from_currency, to_currency) as currency,
                    amount as amount,
                    case
                        when transaction_type = 'DEPOSIT' then 'INCOMING'
                        when transaction_type = 'WITHDRAWAL' then 'OUTGOING'
                    end as direction
                from wallet_transaction
                where transaction_type in ('DEPOSIT', 'WITHDRAWAL')

                union all

                select
                    user_id,
                    date_trunc('month', transaction_time) as bucket_month,
                    'WALLET' as source_type,
                    transaction_type as transaction_type,
                    from_currency as currency,
                    amount as amount,
                    'OUTGOING' as direction
                from wallet_transaction
                where transaction_type = 'TRANSFER'

                union all

                select
                    counterparty_user_id as user_id,
                    date_trunc('month', transaction_time) as bucket_month,
                    'WALLET' as source_type,
                    transaction_type as transaction_type,
                    to_currency as currency,
                    received_amount as amount,
                    'INCOMING' as direction
                from wallet_transaction
                where transaction_type = 'TRANSFER'

                union all

                select
                    user_id,
                    date_trunc('month', transaction_time) as bucket_month,
                    'CARD' as source_type,
                    transaction_type as transaction_type,
                    approved_currency as currency,
                    approved_amount as amount,
                    case
                        when transaction_type = 'PAYMENT' then 'OUTGOING'
                        when transaction_type in ('REFUND', 'DEPOSIT') then 'INCOMING'
                    end as direction
                from card_transaction
                where transaction_type in ('PAYMENT', 'REFUND', 'DEPOSIT')

                union all

                select
                    user_id,
                    date_trunc('month', completed_at) as bucket_month,
                    'EXCHANGE' as source_type,
                    status as transaction_type,
                    from_currency as currency,
                    amount as amount,
                    'OUTGOING' as direction
                from exchange_transaction
                where status = 'COMPLETED'
                  and completed_at is not null

                union all

                select
                    user_id,
                    date_trunc('month', completed_at) as bucket_month,
                    'EXCHANGE' as source_type,
                    status as transaction_type,
                    to_currency as currency,
                    received_amount as amount,
                    'INCOMING' as direction
                from exchange_transaction
                where status = 'COMPLETED'
                  and completed_at is not null
            ) statistics_source
            where user_id is not null
              and currency is not null
              and amount is not null
              and direction is not null
            group by user_id, bucket_month, source_type, transaction_type, currency, direction
            """;

    private static final String DELETE_MONTHLY_SUMMARY_SQL = """
            delete from transaction_monthly_summary
            where bucket_month >= ?
              and bucket_month < ?
            """;

    private static final String INSERT_MONTHLY_SUMMARY_SQL = """
            insert into transaction_monthly_summary (
                user_id,
                bucket_month,
                source_type,
                transaction_type,
                currency,
                direction,
                amount_sum,
                transaction_count,
                data_as_of,
                created_at,
                updated_at
            )
            select
                user_id,
                bucket_month::date,
                source_type,
                transaction_type,
                currency,
                direction,
                sum(amount) as amount_sum,
                count(*) as transaction_count,
                ? as data_as_of,
                ? as created_at,
                ? as updated_at
            from (
                select
                    user_id,
                    date_trunc('month', transaction_time) as bucket_month,
                    'WALLET' as source_type,
                    transaction_type as transaction_type,
                    coalesce(from_currency, to_currency) as currency,
                    amount as amount,
                    case
                        when transaction_type = 'DEPOSIT' then 'INCOMING'
                        when transaction_type = 'WITHDRAWAL' then 'OUTGOING'
                    end as direction
                from wallet_transaction
                where transaction_time >= ?
                  and transaction_time < ?
                  and transaction_type in ('DEPOSIT', 'WITHDRAWAL')

                union all

                select
                    user_id,
                    date_trunc('month', transaction_time) as bucket_month,
                    'WALLET' as source_type,
                    transaction_type as transaction_type,
                    from_currency as currency,
                    amount as amount,
                    'OUTGOING' as direction
                from wallet_transaction
                where transaction_time >= ?
                  and transaction_time < ?
                  and transaction_type = 'TRANSFER'

                union all

                select
                    counterparty_user_id as user_id,
                    date_trunc('month', transaction_time) as bucket_month,
                    'WALLET' as source_type,
                    transaction_type as transaction_type,
                    to_currency as currency,
                    received_amount as amount,
                    'INCOMING' as direction
                from wallet_transaction
                where transaction_time >= ?
                  and transaction_time < ?
                  and transaction_type = 'TRANSFER'

                union all

                select
                    user_id,
                    date_trunc('month', transaction_time) as bucket_month,
                    'CARD' as source_type,
                    transaction_type as transaction_type,
                    approved_currency as currency,
                    approved_amount as amount,
                    case
                        when transaction_type = 'PAYMENT' then 'OUTGOING'
                        when transaction_type in ('REFUND', 'DEPOSIT') then 'INCOMING'
                    end as direction
                from card_transaction
                where transaction_time >= ?
                  and transaction_time < ?
                  and transaction_type in ('PAYMENT', 'REFUND', 'DEPOSIT')

                union all

                select
                    user_id,
                    date_trunc('month', completed_at) as bucket_month,
                    'EXCHANGE' as source_type,
                    status as transaction_type,
                    from_currency as currency,
                    amount as amount,
                    'OUTGOING' as direction
                from exchange_transaction
                where completed_at >= ?
                  and completed_at < ?
                  and status = 'COMPLETED'
                  and completed_at is not null

                union all

                select
                    user_id,
                    date_trunc('month', completed_at) as bucket_month,
                    'EXCHANGE' as source_type,
                    status as transaction_type,
                    to_currency as currency,
                    received_amount as amount,
                    'INCOMING' as direction
                from exchange_transaction
                where completed_at >= ?
                  and completed_at < ?
                  and status = 'COMPLETED'
                  and completed_at is not null
            ) statistics_source
            where user_id is not null
              and currency is not null
              and amount is not null
              and direction is not null
            group by user_id, bucket_month, source_type, transaction_type, currency, direction
            """;
}
