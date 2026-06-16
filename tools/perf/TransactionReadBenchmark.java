import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class TransactionReadBenchmark {

    private static final String PG_URL = env("PG_URL", "jdbc:postgresql://localhost:5432/xcp_benchmark");
    private static final String PG_USER = env("PG_USER", "postgres");
    private static final String PG_PASSWORD = env("PG_PASSWORD", "postgres");
    private static final int TOTAL_RECORDS = intEnv("TOTAL_RECORDS", 1_000_000);
    private static final int USER_COUNT = intEnv("USER_COUNT", 100);
    private static final int TARGET_USER_ID = intEnv("TARGET_USER_ID", 1);
    private static final int PAGE_SIZE = intEnv("PAGE_SIZE", 30);
    private static final int WARMUP = intEnv("WARMUP", 50);
    private static final int RUNS = intEnv("RUNS", 300);
    private static final boolean RESET = boolEnv("RESET", true);
    private static final int PERIOD_START_OFFSET_SECONDS = intEnv("PERIOD_START_OFFSET_SECONDS", TOTAL_RECORDS / 2);
    private static final int PERIOD_SECONDS = intEnv("PERIOD_SECONDS", 86_400);

    private static final String BASELINE_QUERY = """
            select user_id, transaction_time, transaction_type
            from (
                select user_id, transaction_time, transaction_type from bench_wallet_transaction where user_id = ?
                union all
                select user_id, transaction_time, transaction_type from bench_card_transaction where user_id = ?
                union all
                select user_id, completed_at as transaction_time, 'EXCHANGE' as transaction_type
                from bench_exchange_transaction
                where user_id = ? and status = 'COMPLETED'
            ) tx
            order by transaction_time desc
            limit ?
            """;

    private static final String TOP_N_QUERY = """
            select user_id, transaction_time, transaction_type
            from (
                select user_id, transaction_time, transaction_type from (
                    select user_id, transaction_time, transaction_type
                    from bench_wallet_transaction
                    where user_id = ?
                    order by transaction_time desc, transaction_id desc
                    limit ?
                ) wallet

                union all

                select user_id, transaction_time, transaction_type from (
                    select user_id, transaction_time, transaction_type
                    from bench_card_transaction
                    where user_id = ?
                    order by transaction_time desc, transaction_id desc
                    limit ?
                ) card

                union all

                select user_id, transaction_time, transaction_type from (
                    select user_id, completed_at as transaction_time, 'EXCHANGE' as transaction_type
                    from bench_exchange_transaction
                    where user_id = ? and status = 'COMPLETED'
                    order by completed_at desc, transaction_id desc
                    limit ?
                ) exchange
            ) tx
            order by transaction_time desc
            limit ?
            """;

    private static final String PERIOD_SETTLEMENT_QUERY = """
            select source, count(*) as tx_count, coalesce(sum(amount), 0) as total_amount
            from (
                select 'WALLET' as source, transaction_time, amount
                from bench_wallet_transaction
                where transaction_time >= ? and transaction_time < ?

                union all

                select 'CARD' as source, transaction_time, amount
                from bench_card_transaction
                where transaction_time >= ? and transaction_time < ?

                union all

                select 'EXCHANGE' as source, completed_at as transaction_time, amount
                from bench_exchange_transaction
                where status = 'COMPLETED'
                  and completed_at >= ? and completed_at < ?
            ) tx
            group by source
            order by source
            """;

    private static final String EXCHANGE_COMPLETED_TOP_N_QUERY = """
            select user_id, completed_at as transaction_time, 'EXCHANGE' as transaction_type
            from bench_exchange_transaction
            where user_id = ? and status = 'COMPLETED'
            order by completed_at desc, transaction_id desc
            limit ?
            """;

    public static void main(String[] args) throws Exception {
        try (Connection pg = DriverManager.getConnection(PG_URL, PG_USER, PG_PASSWORD)) {
            if (RESET) {
                resetPostgres(pg);
                seed(pg);
            }

            createTopNIndexes(pg);
            warmup(pg, TOP_N_QUERY, true);
            warmup(pg, PERIOD_SETTLEMENT_QUERY, false);

            BenchmarkResult baseline = benchmark(pg, BASELINE_QUERY, false);
            BenchmarkResult topN = benchmark(pg, TOP_N_QUERY, true);
            BenchmarkResult periodBtreeOnly = benchmark(pg, PERIOD_SETTLEMENT_QUERY, false);

            printResult("PostgreSQL baseline union", baseline);
            printResult("PostgreSQL Top-N union", topN);
            printResult("Period settlement without time-only index", periodBtreeOnly);
            System.out.printf(Locale.US, "Improvement avg: %.2fx, p95: %.2fx%n",
                    baseline.averageMs() / topN.averageMs(),
                    baseline.p95Ms() / topN.p95Ms());

            explain(pg, "Baseline EXPLAIN ANALYZE", BASELINE_QUERY, false);
            explain(pg, "Top-N EXPLAIN ANALYZE", TOP_N_QUERY, true);
            explain(pg, "Period settlement without time-only index EXPLAIN ANALYZE", PERIOD_SETTLEMENT_QUERY, false);

            createBrinIndexes(pg);
            warmup(pg, TOP_N_QUERY, true);
            warmup(pg, PERIOD_SETTLEMENT_QUERY, false);

            BenchmarkResult topNWithBrin = benchmark(pg, TOP_N_QUERY, true);
            BenchmarkResult periodWithBrin = benchmark(pg, PERIOD_SETTLEMENT_QUERY, false);

            printResult("PostgreSQL Top-N union with BRIN candidates", topNWithBrin);
            printResult("Period settlement with BRIN candidates", periodWithBrin);
            System.out.printf(Locale.US, "BRIN impact on Top-N avg: %.2fx, p95: %.2fx%n",
                    topN.averageMs() / topNWithBrin.averageMs(),
                    topN.p95Ms() / topNWithBrin.p95Ms());
            System.out.printf(Locale.US, "BRIN impact on period settlement avg: %.2fx, p95: %.2fx%n",
                    periodBtreeOnly.averageMs() / periodWithBrin.averageMs(),
                    periodBtreeOnly.p95Ms() / periodWithBrin.p95Ms());
            explain(pg, "Period settlement with BRIN candidates EXPLAIN ANALYZE", PERIOD_SETTLEMENT_QUERY, false);

            BenchmarkResult exchangeCompletedTopN = benchmarkExchangeTopN(pg);
            createExchangeCompletedPartialIndex(pg);
            warmupExchangeTopN(pg);
            BenchmarkResult exchangeCompletedTopNPartial = benchmarkExchangeTopN(pg);
            printResult("Exchange completed Top-N with regular B-tree", exchangeCompletedTopN);
            printResult("Exchange completed Top-N with partial B-tree", exchangeCompletedTopNPartial);
            System.out.printf(Locale.US, "Partial index impact on exchange Top-N avg: %.2fx, p95: %.2fx%n",
                    exchangeCompletedTopN.averageMs() / exchangeCompletedTopNPartial.averageMs(),
                    exchangeCompletedTopN.p95Ms() / exchangeCompletedTopNPartial.p95Ms());
            explainExchangeTopN(pg, "Exchange completed Top-N with partial B-tree EXPLAIN ANALYZE");
        }
    }

    private static void resetPostgres(Connection pg) throws SQLException {
        try (Statement st = pg.createStatement()) {
            st.execute("drop table if exists bench_wallet_transaction");
            st.execute("drop table if exists bench_card_transaction");
            st.execute("drop table if exists bench_exchange_transaction");
            st.execute("""
                    create table bench_wallet_transaction (
                        transaction_id bigserial primary key,
                        user_id bigint not null,
                        counterparty_user_id bigint,
                        transaction_time timestamp not null,
                        amount numeric(19,4) not null,
                        transaction_type varchar(20) not null
                    )
                    """);
            st.execute("""
                    create table bench_card_transaction (
                        transaction_id bigserial primary key,
                        user_id bigint not null,
                        transaction_time timestamp not null,
                        amount numeric(19,4) not null,
                        transaction_type varchar(20) not null
                    )
                    """);
            st.execute("""
                    create table bench_exchange_transaction (
                        transaction_id bigserial primary key,
                        user_id bigint not null,
                        created_at timestamp not null,
                        completed_at timestamp not null,
                        amount numeric(19,4) not null,
                        status varchar(20) not null
                    )
                    """);
        }
    }

    private static void seed(Connection pg) throws SQLException {
        System.out.printf("Seeding %,d records for %,d users...%n", TOTAL_RECORDS, USER_COUNT);
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        pg.setAutoCommit(false);

        try (PreparedStatement wallet = pg.prepareStatement("""
                     insert into bench_wallet_transaction(user_id, counterparty_user_id, transaction_time, amount, transaction_type)
                     values (?, ?, ?, ?, ?)
                """);
             PreparedStatement card = pg.prepareStatement("""
                     insert into bench_card_transaction(user_id, transaction_time, amount, transaction_type)
                     values (?, ?, ?, ?)
                """);
             PreparedStatement exchange = pg.prepareStatement("""
                     insert into bench_exchange_transaction(user_id, created_at, completed_at, amount, status)
                     values (?, ?, ?, ?, ?)
                """)) {
            for (int i = 1; i <= TOTAL_RECORDS; i++) {
                long userId = ((i / 3) % USER_COUNT) + 1L;
                Instant time = base.plusSeconds(i);
                BigDecimal amount = BigDecimal.valueOf((i % 100_000) + 1L);
                int type = i % 3;

                if (type == 0) {
                    wallet.setLong(1, userId);
                    wallet.setLong(2, ((userId + 1) % USER_COUNT) + 1L);
                    wallet.setTimestamp(3, Timestamp.from(time));
                    wallet.setBigDecimal(4, amount);
                    wallet.setString(5, "TRANSFER");
                    wallet.addBatch();
                } else if (type == 1) {
                    card.setLong(1, userId);
                    card.setTimestamp(2, Timestamp.from(time));
                    card.setBigDecimal(3, amount);
                    card.setString(4, "PAYMENT");
                    card.addBatch();
                } else {
                    exchange.setLong(1, userId);
                    exchange.setTimestamp(2, Timestamp.from(time.minusSeconds(10)));
                    exchange.setTimestamp(3, Timestamp.from(time));
                    exchange.setBigDecimal(4, amount);
                    exchange.setString(5, i % 10 == 0 ? "PENDING" : "COMPLETED");
                    exchange.addBatch();
                }

                if (i % 1_000 == 0) {
                    wallet.executeBatch();
                    card.executeBatch();
                    exchange.executeBatch();
                }
            }

            wallet.executeBatch();
            card.executeBatch();
            exchange.executeBatch();
            pg.commit();
        } finally {
            pg.setAutoCommit(true);
        }
    }

    private static void createTopNIndexes(Connection pg) throws SQLException {
        try (Statement st = pg.createStatement()) {
            st.execute("create index if not exists idx_bench_wallet_user_time on bench_wallet_transaction(user_id, transaction_time desc, transaction_id desc)");
            st.execute("create index if not exists idx_bench_wallet_counterparty_time on bench_wallet_transaction(counterparty_user_id, transaction_time desc, transaction_id desc)");
            st.execute("create index if not exists idx_bench_card_user_time on bench_card_transaction(user_id, transaction_time desc, transaction_id desc)");
            st.execute("create index if not exists idx_bench_exchange_user_completed on bench_exchange_transaction(user_id, completed_at desc, transaction_id desc)");
            st.execute("analyze bench_wallet_transaction");
            st.execute("analyze bench_card_transaction");
            st.execute("analyze bench_exchange_transaction");
        }
    }

    private static void createBrinIndexes(Connection pg) throws SQLException {
        try (Statement st = pg.createStatement()) {
            st.execute("create index if not exists idx_bench_wallet_time_brin on bench_wallet_transaction using brin(transaction_time)");
            st.execute("create index if not exists idx_bench_card_time_brin on bench_card_transaction using brin(transaction_time)");
            st.execute("create index if not exists idx_bench_exchange_completed_brin on bench_exchange_transaction using brin(completed_at)");
            st.execute("analyze bench_wallet_transaction");
            st.execute("analyze bench_card_transaction");
            st.execute("analyze bench_exchange_transaction");
        }
    }

    private static void createExchangeCompletedPartialIndex(Connection pg) throws SQLException {
        try (Statement st = pg.createStatement()) {
            st.execute("""
                    create index if not exists idx_bench_exchange_completed_partial
                    on bench_exchange_transaction(user_id, completed_at desc, transaction_id desc)
                    where status = 'COMPLETED'
                    """);
            st.execute("analyze bench_exchange_transaction");
        }
    }

    private static void warmup(Connection pg, String sql, boolean topN) throws SQLException {
        for (int i = 0; i < WARMUP; i++) {
            query(pg, sql, topN);
        }
    }

    private static BenchmarkResult benchmark(Connection pg, String sql, boolean topN) throws SQLException {
        List<Double> latencies = new ArrayList<>(RUNS);
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            query(pg, sql, topN);
            latencies.add(elapsedMs(start));
        }
        return BenchmarkResult.of(latencies);
    }

    private static int query(Connection pg, String sql, boolean topN) throws SQLException {
        int rows = 0;
        try (PreparedStatement ps = pg.prepareStatement(sql)) {
            bind(ps, topN);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows++;
                }
            }
        }
        return rows;
    }

    private static BenchmarkResult benchmarkExchangeTopN(Connection pg) throws SQLException {
        warmupExchangeTopN(pg);
        List<Double> latencies = new ArrayList<>(RUNS);
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            queryExchangeTopN(pg);
            latencies.add(elapsedMs(start));
        }
        return BenchmarkResult.of(latencies);
    }

    private static void warmupExchangeTopN(Connection pg) throws SQLException {
        for (int i = 0; i < WARMUP; i++) {
            queryExchangeTopN(pg);
        }
    }

    private static int queryExchangeTopN(Connection pg) throws SQLException {
        int rows = 0;
        try (PreparedStatement ps = pg.prepareStatement(EXCHANGE_COMPLETED_TOP_N_QUERY)) {
            ps.setLong(1, TARGET_USER_ID);
            ps.setInt(2, PAGE_SIZE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows++;
                }
            }
        }
        return rows;
    }

    private static void explain(Connection pg, String title, String sql, boolean topN) throws SQLException {
        System.out.println();
        System.out.println(title + ":");
        try (PreparedStatement ps = pg.prepareStatement("explain analyze " + sql)) {
            bind(ps, topN);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getString(1));
                }
            }
        }
    }

    private static void bind(PreparedStatement ps, boolean topN) throws SQLException {
        if (topN) {
            ps.setLong(1, TARGET_USER_ID);
            ps.setInt(2, PAGE_SIZE);
            ps.setLong(3, TARGET_USER_ID);
            ps.setInt(4, PAGE_SIZE);
            ps.setLong(5, TARGET_USER_ID);
            ps.setInt(6, PAGE_SIZE);
            ps.setInt(7, PAGE_SIZE);
            return;
        }

        if (ps.getParameterMetaData().getParameterCount() == 6) {
            Timestamp start = Timestamp.from(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(PERIOD_START_OFFSET_SECONDS));
            Timestamp end = Timestamp.from(start.toInstant().plusSeconds(PERIOD_SECONDS));
            ps.setTimestamp(1, start);
            ps.setTimestamp(2, end);
            ps.setTimestamp(3, start);
            ps.setTimestamp(4, end);
            ps.setTimestamp(5, start);
            ps.setTimestamp(6, end);
        } else {
            ps.setLong(1, TARGET_USER_ID);
            ps.setLong(2, TARGET_USER_ID);
            ps.setLong(3, TARGET_USER_ID);
            ps.setInt(4, PAGE_SIZE);
        }
    }

    private static void explainExchangeTopN(Connection pg, String title) throws SQLException {
        System.out.println();
        System.out.println(title + ":");
        try (PreparedStatement ps = pg.prepareStatement("explain analyze " + EXCHANGE_COMPLETED_TOP_N_QUERY)) {
            ps.setLong(1, TARGET_USER_ID);
            ps.setInt(2, PAGE_SIZE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getString(1));
                }
            }
        }
    }

    private static void printResult(String name, BenchmarkResult result) {
        System.out.printf(Locale.US,
                "%s -> avg %.3f ms, p95 %.3f ms, min %.3f ms, max %.3f ms, throughput %.1f req/s%n",
                name,
                result.averageMs(),
                result.p95Ms(),
                result.minMs(),
                result.maxMs(),
                1_000.0 / result.averageMs());
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int intEnv(String name, int defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private static boolean boolEnv(String name, boolean defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value);
    }

    private record BenchmarkResult(double averageMs, double p95Ms, double minMs, double maxMs) {
        private static BenchmarkResult of(List<Double> latencies) {
            List<Double> sorted = latencies.stream()
                    .sorted(Comparator.naturalOrder())
                    .toList();
            double average = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            int p95Index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1);
            return new BenchmarkResult(
                    average,
                    sorted.get(p95Index),
                    sorted.get(0),
                    sorted.get(sorted.size() - 1)
            );
        }
    }
}
