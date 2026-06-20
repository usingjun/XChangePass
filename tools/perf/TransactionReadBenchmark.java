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
    private static final int PAGE_SIZE = intEnv("PAGE_SIZE", 50);
    private static final int SOURCE_LIMIT = intEnv("SOURCE_LIMIT", PAGE_SIZE + 1);
    private static final int WARMUP = intEnv("WARMUP", 50);
    private static final int RUNS = intEnv("RUNS", 300);
    private static final boolean RESET = boolEnv("RESET", true);
    private static final int PERIOD_START_OFFSET_SECONDS = intEnv("PERIOD_START_OFFSET_SECONDS", TOTAL_RECORDS / 2);
    private static final int PERIOD_SECONDS = intEnv("PERIOD_SECONDS", 86_400);
    private static final String MERCHANT_KEYWORD = env("MERCHANT_KEYWORD", "STAR");
    private static final BigDecimal FILTER_MIN_AMOUNT = decimalEnv("FILTER_MIN_AMOUNT", "10000");
    private static final BigDecimal FILTER_MAX_AMOUNT = decimalEnv("FILTER_MAX_AMOUNT", "50000");
    private static final String FILTER_CURRENCY = env("FILTER_CURRENCY", "USD");

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

    private static final String INTEGRATED_TOP_N_QUERY = """
            select user_id, transaction_time, transaction_type
            from (
                select user_id, transaction_time, transaction_type from (
                    select user_id, transaction_time, transaction_type
                    from bench_wallet_transaction
                    where user_id = ?
                    order by transaction_time desc, transaction_id desc
                    limit ?
                ) wallet_sent

                union all

                select user_id, transaction_time, transaction_type from (
                    select user_id, transaction_time, transaction_type
                    from bench_wallet_transaction
                    where counterparty_user_id = ?
                    order by transaction_time desc, transaction_id desc
                    limit ?
                ) wallet_received

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

    private static final String CARD_MERCHANT_SEARCH_QUERY = """
            select user_id, transaction_time, transaction_type, merchant_name
            from bench_card_transaction
            where user_id = ?
              and merchant_name ilike ?
            order by transaction_time desc, transaction_id desc
            limit ?
            """;

    private static final String FILTERED_TOP_N_QUERY = """
            select user_id, transaction_time, transaction_type
            from (
                select user_id, transaction_time, transaction_type from (
                    select user_id, transaction_time, transaction_type
                    from bench_wallet_transaction
                    where user_id = ?
                      and amount >= ? and amount <= ?
                      and (from_currency = ? or to_currency = ?)
                    order by transaction_time desc, transaction_id desc
                    limit ?
                ) wallet_sent

                union all

                select user_id, transaction_time, transaction_type from (
                    select user_id, transaction_time, transaction_type
                    from bench_wallet_transaction
                    where counterparty_user_id = ?
                      and amount >= ? and amount <= ?
                      and (from_currency = ? or to_currency = ?)
                    order by transaction_time desc, transaction_id desc
                    limit ?
                ) wallet_received

                union all

                select user_id, transaction_time, transaction_type from (
                    select user_id, transaction_time, transaction_type
                    from bench_card_transaction
                    where user_id = ?
                      and merchant_name ilike ?
                      and amount >= ? and amount <= ?
                      and approved_currency = ?
                    order by transaction_time desc, transaction_id desc
                    limit ?
                ) card

                union all

                select user_id, transaction_time, transaction_type from (
                    select user_id, completed_at as transaction_time, 'EXCHANGE' as transaction_type
                    from bench_exchange_transaction
                    where user_id = ?
                      and status = 'COMPLETED'
                      and amount >= ? and amount <= ?
                      and (from_currency = ? or to_currency = ?)
                    order by completed_at desc, transaction_id desc
                    limit ?
                ) exchange
            ) tx
            order by transaction_time desc
            limit ?
            """;

    private static final String CURRENCY_FILTERED_TOP_N_QUERY = """
            select user_id, transaction_time, transaction_type
            from (
                select user_id, transaction_time, transaction_type from (
                    select user_id, transaction_time, transaction_type
                    from bench_wallet_transaction
                    where user_id = ?
                      and (from_currency = ? or to_currency = ?)
                    order by transaction_time desc, transaction_id desc
                    limit ?
                ) wallet_sent

                union all

                select user_id, transaction_time, transaction_type from (
                    select user_id, transaction_time, transaction_type
                    from bench_wallet_transaction
                    where counterparty_user_id = ?
                      and (from_currency = ? or to_currency = ?)
                    order by transaction_time desc, transaction_id desc
                    limit ?
                ) wallet_received

                union all

                select user_id, transaction_time, transaction_type from (
                    select user_id, transaction_time, transaction_type
                    from bench_card_transaction
                    where user_id = ?
                      and approved_currency = ?
                    order by transaction_time desc, transaction_id desc
                    limit ?
                ) card

                union all

                select user_id, transaction_time, transaction_type from (
                    select user_id, completed_at as transaction_time, 'EXCHANGE' as transaction_type
                    from bench_exchange_transaction
                    where user_id = ?
                      and status = 'COMPLETED'
                      and (from_currency = ? or to_currency = ?)
                    order by completed_at desc, transaction_id desc
                    limit ?
                ) exchange
            ) tx
            order by transaction_time desc
            limit ?
            """;

    private static final String AMOUNT_FILTERED_TOP_N_QUERY = """
            select user_id, transaction_time, transaction_type
            from (
                select user_id, transaction_time, transaction_type from (
                    select user_id, transaction_time, transaction_type
                    from bench_wallet_transaction
                    where user_id = ?
                      and amount >= ? and amount <= ?
                    order by transaction_time desc, transaction_id desc
                    limit ?
                ) wallet_sent

                union all

                select user_id, transaction_time, transaction_type from (
                    select user_id, transaction_time, transaction_type
                    from bench_wallet_transaction
                    where counterparty_user_id = ?
                      and amount >= ? and amount <= ?
                    order by transaction_time desc, transaction_id desc
                    limit ?
                ) wallet_received

                union all

                select user_id, transaction_time, transaction_type from (
                    select user_id, transaction_time, transaction_type
                    from bench_card_transaction
                    where user_id = ?
                      and amount >= ? and amount <= ?
                    order by transaction_time desc, transaction_id desc
                    limit ?
                ) card

                union all

                select user_id, transaction_time, transaction_type from (
                    select user_id, completed_at as transaction_time, 'EXCHANGE' as transaction_type
                    from bench_exchange_transaction
                    where user_id = ?
                      and status = 'COMPLETED'
                      and amount >= ? and amount <= ?
                    order by completed_at desc, transaction_id desc
                    limit ?
                ) exchange
            ) tx
            order by transaction_time desc
            limit ?
            """;

    private static final String STATIC_OPTIONAL_FILTERED_TOP_N_QUERY = """
            select user_id, transaction_time, transaction_type
            from (
                select user_id, transaction_time, transaction_type from (
                    select user_id, transaction_time, transaction_type
                    from bench_wallet_transaction
                    where user_id = ?
                      and (? is null or amount >= ?)
                      and (? is null or amount <= ?)
                      and (? is null or from_currency = ? or to_currency = ?)
                    order by transaction_time desc, transaction_id desc
                    limit ?
                ) wallet_sent

                union all

                select user_id, transaction_time, transaction_type from (
                    select user_id, transaction_time, transaction_type
                    from bench_wallet_transaction
                    where counterparty_user_id = ?
                      and (? is null or amount >= ?)
                      and (? is null or amount <= ?)
                      and (? is null or from_currency = ? or to_currency = ?)
                    order by transaction_time desc, transaction_id desc
                    limit ?
                ) wallet_received

                union all

                select user_id, transaction_time, transaction_type from (
                    select user_id, transaction_time, transaction_type
                    from bench_card_transaction
                    where user_id = ?
                      and (? is null or merchant_name ilike ?)
                      and (? is null or amount >= ?)
                      and (? is null or amount <= ?)
                      and (? is null or approved_currency = ?)
                    order by transaction_time desc, transaction_id desc
                    limit ?
                ) card

                union all

                select user_id, transaction_time, transaction_type from (
                    select user_id, completed_at as transaction_time, 'EXCHANGE' as transaction_type
                    from bench_exchange_transaction
                    where user_id = ?
                      and status = 'COMPLETED'
                      and (? is null or amount >= ?)
                      and (? is null or amount <= ?)
                      and (? is null or from_currency = ? or to_currency = ?)
                    order by completed_at desc, transaction_id desc
                    limit ?
                ) exchange
            ) tx
            order by transaction_time desc
            limit ?
            """;

    private static final String STATIC_OPTIONAL_TOP_N_QUERY = """
            select user_id, transaction_time, transaction_type
            from (
                select user_id, transaction_time, transaction_type from (
                    select user_id, transaction_time, transaction_type
                    from bench_wallet_transaction
                    where user_id = ?
                      and (? is null or amount >= ?)
                      and (? is null or amount <= ?)
                      and (? is null or from_currency = ? or to_currency = ?)
                    order by transaction_time desc, transaction_id desc
                    limit ?
                ) wallet

                union all

                select user_id, transaction_time, transaction_type from (
                    select user_id, transaction_time, transaction_type
                    from bench_card_transaction
                    where user_id = ?
                      and (? is null or merchant_name ilike ?)
                      and (? is null or amount >= ?)
                      and (? is null or amount <= ?)
                      and (? is null or approved_currency = ?)
                    order by transaction_time desc, transaction_id desc
                    limit ?
                ) card

                union all

                select user_id, transaction_time, transaction_type from (
                    select user_id, completed_at as transaction_time, 'EXCHANGE' as transaction_type
                    from bench_exchange_transaction
                    where user_id = ?
                      and status = 'COMPLETED'
                      and (? is null or amount >= ?)
                      and (? is null or amount <= ?)
                      and (? is null or from_currency = ? or to_currency = ?)
                    order by completed_at desc, transaction_id desc
                    limit ?
                ) exchange
            ) tx
            order by transaction_time desc
            limit ?
            """;

    private static final String FILTERED_RECEIVED_WALLET_QUERY = """
            select user_id, transaction_time, transaction_type
            from bench_wallet_transaction
            where counterparty_user_id = ?
              and amount >= ? and amount <= ?
              and (from_currency = ? or to_currency = ?)
            order by transaction_time desc, transaction_id desc
            limit ?
            """;

    private static final String CARD_FILTERED_QUERY = """
            select user_id, transaction_time, transaction_type
            from bench_card_transaction
            where user_id = ?
              and merchant_name ilike ?
              and amount >= ? and amount <= ?
              and approved_currency = ?
            order by transaction_time desc, transaction_id desc
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
            System.out.printf(Locale.US,
                    "Benchmark config -> totalRecords %,d, users %,d, pageSize %d, sourceLimit %d, runs %d, warmup %d%n",
                    TOTAL_RECORDS, USER_COUNT, PAGE_SIZE, SOURCE_LIMIT, RUNS, WARMUP);

            BenchmarkResult baseline = benchmark(pg, BASELINE_QUERY, false);
            BenchmarkResult topN = benchmark(pg, TOP_N_QUERY, true);
            BenchmarkResult integratedTopN = benchmarkIntegratedTopN(pg);
            BenchmarkResult staticOptionalTopN = benchmarkStaticOptionalTopN(pg);
            BenchmarkResult staticOptionalFilteredTopN = benchmarkStaticOptionalFilteredTopN(pg);
            BenchmarkResult filteredTopN = benchmarkFilteredTopN(pg);
            BenchmarkResult filteredReceivedWallet = benchmarkFilteredReceivedWallet(pg);
            BenchmarkResult cardFiltered = benchmarkCardFiltered(pg);
            BenchmarkResult currencyFilteredTopN = benchmarkCurrencyFilteredTopN(pg);
            BenchmarkResult amountFilteredTopN = benchmarkAmountFilteredTopN(pg);
            BenchmarkResult periodBtreeOnly = benchmark(pg, PERIOD_SETTLEMENT_QUERY, false);

            printResult("PostgreSQL baseline union", baseline);
            printResult("PostgreSQL Top-N union", topN);
            printResult("Integrated Top-N union sent/received/card/exchange", integratedTopN);
            printResult("Static optional-OR Top-N without filters", staticOptionalTopN);
            printResult("Static optional-OR filtered Top-N", staticOptionalFilteredTopN);
            printResult("Filtered Top-N with amount/currency/merchant/direction", filteredTopN);
            printResult("Filtered received wallet with amount/currency/direction", filteredReceivedWallet);
            printResult("Card-only merchant/amount/currency filtered Top-N before candidate indexes", cardFiltered);
            printResult("Currency-filtered Top-N before candidate indexes", currencyFilteredTopN);
            printResult("Amount-filtered Top-N before candidate indexes", amountFilteredTopN);
            printResult("Period settlement without time-only index", periodBtreeOnly);
            System.out.printf(Locale.US, "Improvement avg: %.2fx, p95: %.2fx%n",
                    baseline.averageMs() / topN.averageMs(),
                    baseline.p95Ms() / topN.p95Ms());
            System.out.printf(Locale.US, "Dynamic predicate impact without filters avg: %.2fx, p95: %.2fx%n",
                    staticOptionalTopN.averageMs() / topN.averageMs(),
                    staticOptionalTopN.p95Ms() / topN.p95Ms());
            System.out.printf(Locale.US, "Dynamic predicate impact with filters avg: %.2fx, p95: %.2fx%n",
                    staticOptionalFilteredTopN.averageMs() / filteredTopN.averageMs(),
                    staticOptionalFilteredTopN.p95Ms() / filteredTopN.p95Ms());

            explain(pg, "Baseline EXPLAIN ANALYZE", BASELINE_QUERY, false);
            explain(pg, "Top-N EXPLAIN ANALYZE", TOP_N_QUERY, true);
            explainIntegratedTopN(pg, "Integrated Top-N sent/received/card/exchange EXPLAIN ANALYZE");
            explainStaticOptionalTopN(pg, "Static optional-OR Top-N without filters EXPLAIN ANALYZE");
            explainStaticOptionalFilteredTopN(pg, "Static optional-OR filtered Top-N EXPLAIN ANALYZE");
            explainFilteredTopN(pg, "Filtered Top-N EXPLAIN ANALYZE");
            explainFilteredReceivedWallet(pg, "Filtered received wallet EXPLAIN ANALYZE");
            explainCardFiltered(pg, "Card-only merchant/amount/currency filtered Top-N before candidate indexes EXPLAIN ANALYZE");
            explainCurrencyFilteredTopN(pg, "Currency-filtered Top-N before candidate indexes EXPLAIN ANALYZE");
            explainAmountFilteredTopN(pg, "Amount-filtered Top-N before candidate indexes EXPLAIN ANALYZE");
            explain(pg, "Period settlement without time-only index EXPLAIN ANALYZE", PERIOD_SETTLEMENT_QUERY, false);

            createCurrencyFilterIndexes(pg);
            BenchmarkResult currencyFilteredTopNWithIndexes = benchmarkCurrencyFilteredTopN(pg);
            BenchmarkResult cardFilteredWithCurrencyIndexes = benchmarkCardFiltered(pg);
            BenchmarkResult filteredTopNWithCurrencyIndexes = benchmarkFilteredTopN(pg);
            printResult("Currency-filtered Top-N with currency candidate indexes", currencyFilteredTopNWithIndexes);
            printResult("Card-only filtered Top-N with currency candidate indexes", cardFilteredWithCurrencyIndexes);
            printResult("Filtered Top-N with currency candidate indexes", filteredTopNWithCurrencyIndexes);
            System.out.printf(Locale.US, "Currency index impact on currency-filtered Top-N avg: %.2fx, p95: %.2fx%n",
                    currencyFilteredTopN.averageMs() / currencyFilteredTopNWithIndexes.averageMs(),
                    currencyFilteredTopN.p95Ms() / currencyFilteredTopNWithIndexes.p95Ms());
            System.out.printf(Locale.US, "Currency index impact on combined filtered Top-N avg: %.2fx, p95: %.2fx%n",
                    filteredTopN.averageMs() / filteredTopNWithCurrencyIndexes.averageMs(),
                    filteredTopN.p95Ms() / filteredTopNWithCurrencyIndexes.p95Ms());
            System.out.printf(Locale.US, "Currency index impact on card-only filtered Top-N avg: %.2fx, p95: %.2fx%n",
                    cardFiltered.averageMs() / cardFilteredWithCurrencyIndexes.averageMs(),
                    cardFiltered.p95Ms() / cardFilteredWithCurrencyIndexes.p95Ms());
            explainCardFiltered(pg, "Card-only filtered Top-N with currency candidate indexes EXPLAIN ANALYZE");
            explainCurrencyFilteredTopN(pg, "Currency-filtered Top-N with currency candidate indexes EXPLAIN ANALYZE");

            createAmountFilterIndexes(pg);
            BenchmarkResult amountFilteredTopNWithIndexes = benchmarkAmountFilteredTopN(pg);
            BenchmarkResult cardFilteredWithCurrencyAndAmountIndexes = benchmarkCardFiltered(pg);
            BenchmarkResult filteredTopNWithCurrencyAndAmountIndexes = benchmarkFilteredTopN(pg);
            printResult("Amount-filtered Top-N with amount candidate indexes", amountFilteredTopNWithIndexes);
            printResult("Card-only filtered Top-N with currency and amount candidate indexes", cardFilteredWithCurrencyAndAmountIndexes);
            printResult("Filtered Top-N with currency and amount candidate indexes", filteredTopNWithCurrencyAndAmountIndexes);
            System.out.printf(Locale.US, "Amount index impact on amount-filtered Top-N avg: %.2fx, p95: %.2fx%n",
                    amountFilteredTopN.averageMs() / amountFilteredTopNWithIndexes.averageMs(),
                    amountFilteredTopN.p95Ms() / amountFilteredTopNWithIndexes.p95Ms());
            System.out.printf(Locale.US, "Currency+amount index impact on combined filtered Top-N avg: %.2fx, p95: %.2fx%n",
                    filteredTopN.averageMs() / filteredTopNWithCurrencyAndAmountIndexes.averageMs(),
                    filteredTopN.p95Ms() / filteredTopNWithCurrencyAndAmountIndexes.p95Ms());
            System.out.printf(Locale.US, "Currency+amount index impact on card-only filtered Top-N avg: %.2fx, p95: %.2fx%n",
                    cardFiltered.averageMs() / cardFilteredWithCurrencyAndAmountIndexes.averageMs(),
                    cardFiltered.p95Ms() / cardFilteredWithCurrencyAndAmountIndexes.p95Ms());
            explainCardFiltered(pg, "Card-only filtered Top-N with currency and amount candidate indexes EXPLAIN ANALYZE");
            explainAmountFilteredTopN(pg, "Amount-filtered Top-N with amount candidate indexes EXPLAIN ANALYZE");

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

            BenchmarkResult merchantSearchWithoutGin = benchmarkMerchantSearch(pg);
            createMerchantSearchGinIndex(pg);
            warmupMerchantSearch(pg);
            BenchmarkResult merchantSearchWithGin = benchmarkMerchantSearch(pg);
            printResult("Card merchant search without GIN trigram", merchantSearchWithoutGin);
            printResult("Card merchant search with GIN trigram", merchantSearchWithGin);
            System.out.printf(Locale.US, "GIN trigram impact on merchant search avg: %.2fx, p95: %.2fx%n",
                    merchantSearchWithoutGin.averageMs() / merchantSearchWithGin.averageMs(),
                    merchantSearchWithoutGin.p95Ms() / merchantSearchWithGin.p95Ms());
            explainMerchantSearch(pg, "Card merchant search with GIN trigram EXPLAIN ANALYZE");
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
                        from_currency varchar(3),
                        to_currency varchar(3),
                        transaction_time timestamp not null,
                        amount numeric(19,4) not null,
                        transaction_type varchar(20) not null
                    )
                    """);
            st.execute("""
                    create table bench_card_transaction (
                        transaction_id bigserial primary key,
                        user_id bigint not null,
                        merchant_name varchar(100) not null,
                        approved_currency varchar(3) not null,
                        transaction_time timestamp not null,
                        amount numeric(19,4) not null,
                        transaction_type varchar(20) not null
                    )
                    """);
            st.execute("""
                    create table bench_exchange_transaction (
                        transaction_id bigserial primary key,
                        user_id bigint not null,
                        from_currency varchar(3) not null,
                        to_currency varchar(3) not null,
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
                     insert into bench_wallet_transaction(user_id, counterparty_user_id, from_currency, to_currency, transaction_time, amount, transaction_type)
                     values (?, ?, ?, ?, ?, ?, ?)
                """);
             PreparedStatement card = pg.prepareStatement("""
                     insert into bench_card_transaction(user_id, merchant_name, approved_currency, transaction_time, amount, transaction_type)
                     values (?, ?, ?, ?, ?, ?)
                """);
             PreparedStatement exchange = pg.prepareStatement("""
                     insert into bench_exchange_transaction(user_id, from_currency, to_currency, created_at, completed_at, amount, status)
                     values (?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (int i = 1; i <= TOTAL_RECORDS; i++) {
                long userId = ((i / 3) % USER_COUNT) + 1L;
                Instant time = base.plusSeconds(i);
                BigDecimal amount = BigDecimal.valueOf((i % 100_000) + 1L);
                int type = i % 3;

                if (type == 0) {
                    wallet.setLong(1, userId);
                    wallet.setLong(2, ((userId + 1) % USER_COUNT) + 1L);
                    wallet.setString(3, fromCurrency(i));
                    wallet.setString(4, toCurrency(i));
                    wallet.setTimestamp(5, Timestamp.from(time));
                    wallet.setBigDecimal(6, amount);
                    wallet.setString(7, "TRANSFER");
                    wallet.addBatch();
                } else if (type == 1) {
                    card.setLong(1, userId);
                    card.setString(2, merchantName(i));
                    card.setString(3, approvedCurrency(i));
                    card.setTimestamp(4, Timestamp.from(time));
                    card.setBigDecimal(5, amount);
                    card.setString(6, "PAYMENT");
                    card.addBatch();
                } else {
                    exchange.setLong(1, userId);
                    exchange.setString(2, fromCurrency(i));
                    exchange.setString(3, toCurrency(i));
                    exchange.setTimestamp(4, Timestamp.from(time.minusSeconds(10)));
                    exchange.setTimestamp(5, Timestamp.from(time));
                    exchange.setBigDecimal(6, amount);
                    exchange.setString(7, i % 10 == 0 ? "PENDING" : "COMPLETED");
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

    private static void createMerchantSearchGinIndex(Connection pg) throws SQLException {
        try (Statement st = pg.createStatement()) {
            st.execute("create extension if not exists pg_trgm");
            st.execute("create index if not exists idx_bench_card_merchant_trgm on bench_card_transaction using gin (merchant_name gin_trgm_ops)");
            st.execute("analyze bench_card_transaction");
        }
    }

    private static void createCurrencyFilterIndexes(Connection pg) throws SQLException {
        try (Statement st = pg.createStatement()) {
            st.execute("create index if not exists idx_bench_wallet_user_from_currency_time on bench_wallet_transaction(user_id, from_currency, transaction_time desc, transaction_id desc)");
            st.execute("create index if not exists idx_bench_wallet_user_to_currency_time on bench_wallet_transaction(user_id, to_currency, transaction_time desc, transaction_id desc)");
            st.execute("create index if not exists idx_bench_wallet_counterparty_from_currency_time on bench_wallet_transaction(counterparty_user_id, from_currency, transaction_time desc, transaction_id desc)");
            st.execute("create index if not exists idx_bench_wallet_counterparty_to_currency_time on bench_wallet_transaction(counterparty_user_id, to_currency, transaction_time desc, transaction_id desc)");
            st.execute("create index if not exists idx_bench_card_user_currency_time on bench_card_transaction(user_id, approved_currency, transaction_time desc, transaction_id desc)");
            st.execute("create index if not exists idx_bench_exchange_user_from_currency_time on bench_exchange_transaction(user_id, from_currency, completed_at desc, transaction_id desc)");
            st.execute("create index if not exists idx_bench_exchange_user_to_currency_time on bench_exchange_transaction(user_id, to_currency, completed_at desc, transaction_id desc)");
            st.execute("analyze bench_wallet_transaction");
            st.execute("analyze bench_card_transaction");
            st.execute("analyze bench_exchange_transaction");
        }
    }

    private static void createAmountFilterIndexes(Connection pg) throws SQLException {
        try (Statement st = pg.createStatement()) {
            st.execute("create index if not exists idx_bench_wallet_user_amount_time on bench_wallet_transaction(user_id, amount, transaction_time desc, transaction_id desc)");
            st.execute("create index if not exists idx_bench_wallet_counterparty_amount_time on bench_wallet_transaction(counterparty_user_id, amount, transaction_time desc, transaction_id desc)");
            st.execute("create index if not exists idx_bench_card_user_amount_time on bench_card_transaction(user_id, amount, transaction_time desc, transaction_id desc)");
            st.execute("create index if not exists idx_bench_exchange_user_amount_time on bench_exchange_transaction(user_id, amount, completed_at desc, transaction_id desc)");
            st.execute("analyze bench_wallet_transaction");
            st.execute("analyze bench_card_transaction");
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

    private static BenchmarkResult benchmarkMerchantSearch(Connection pg) throws SQLException {
        warmupMerchantSearch(pg);
        List<Double> latencies = new ArrayList<>(RUNS);
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            queryMerchantSearch(pg);
            latencies.add(elapsedMs(start));
        }
        return BenchmarkResult.of(latencies);
    }

    private static BenchmarkResult benchmarkFilteredTopN(Connection pg) throws SQLException {
        warmupFilteredTopN(pg);
        List<Double> latencies = new ArrayList<>(RUNS);
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            queryFilteredTopN(pg);
            latencies.add(elapsedMs(start));
        }
        return BenchmarkResult.of(latencies);
    }

    private static BenchmarkResult benchmarkStaticOptionalTopN(Connection pg) throws SQLException {
        warmupStaticOptionalTopN(pg);
        List<Double> latencies = new ArrayList<>(RUNS);
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            queryStaticOptionalTopN(pg);
            latencies.add(elapsedMs(start));
        }
        return BenchmarkResult.of(latencies);
    }

    private static BenchmarkResult benchmarkIntegratedTopN(Connection pg) throws SQLException {
        warmupIntegratedTopN(pg);
        List<Double> latencies = new ArrayList<>(RUNS);
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            queryIntegratedTopN(pg);
            latencies.add(elapsedMs(start));
        }
        return BenchmarkResult.of(latencies);
    }

    private static BenchmarkResult benchmarkStaticOptionalFilteredTopN(Connection pg) throws SQLException {
        warmupStaticOptionalFilteredTopN(pg);
        List<Double> latencies = new ArrayList<>(RUNS);
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            queryStaticOptionalFilteredTopN(pg);
            latencies.add(elapsedMs(start));
        }
        return BenchmarkResult.of(latencies);
    }

    private static BenchmarkResult benchmarkFilteredReceivedWallet(Connection pg) throws SQLException {
        warmupFilteredReceivedWallet(pg);
        List<Double> latencies = new ArrayList<>(RUNS);
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            queryFilteredReceivedWallet(pg);
            latencies.add(elapsedMs(start));
        }
        return BenchmarkResult.of(latencies);
    }

    private static BenchmarkResult benchmarkCardFiltered(Connection pg) throws SQLException {
        warmupCardFiltered(pg);
        List<Double> latencies = new ArrayList<>(RUNS);
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            queryCardFiltered(pg);
            latencies.add(elapsedMs(start));
        }
        return BenchmarkResult.of(latencies);
    }

    private static BenchmarkResult benchmarkCurrencyFilteredTopN(Connection pg) throws SQLException {
        warmupCurrencyFilteredTopN(pg);
        List<Double> latencies = new ArrayList<>(RUNS);
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            queryCurrencyFilteredTopN(pg);
            latencies.add(elapsedMs(start));
        }
        return BenchmarkResult.of(latencies);
    }

    private static BenchmarkResult benchmarkAmountFilteredTopN(Connection pg) throws SQLException {
        warmupAmountFilteredTopN(pg);
        List<Double> latencies = new ArrayList<>(RUNS);
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            queryAmountFilteredTopN(pg);
            latencies.add(elapsedMs(start));
        }
        return BenchmarkResult.of(latencies);
    }

    private static void warmupExchangeTopN(Connection pg) throws SQLException {
        for (int i = 0; i < WARMUP; i++) {
            queryExchangeTopN(pg);
        }
    }

    private static void warmupMerchantSearch(Connection pg) throws SQLException {
        for (int i = 0; i < WARMUP; i++) {
            queryMerchantSearch(pg);
        }
    }

    private static void warmupFilteredTopN(Connection pg) throws SQLException {
        for (int i = 0; i < WARMUP; i++) {
            queryFilteredTopN(pg);
        }
    }

    private static void warmupStaticOptionalTopN(Connection pg) throws SQLException {
        for (int i = 0; i < WARMUP; i++) {
            queryStaticOptionalTopN(pg);
        }
    }

    private static void warmupIntegratedTopN(Connection pg) throws SQLException {
        for (int i = 0; i < WARMUP; i++) {
            queryIntegratedTopN(pg);
        }
    }

    private static void warmupStaticOptionalFilteredTopN(Connection pg) throws SQLException {
        for (int i = 0; i < WARMUP; i++) {
            queryStaticOptionalFilteredTopN(pg);
        }
    }

    private static void warmupFilteredReceivedWallet(Connection pg) throws SQLException {
        for (int i = 0; i < WARMUP; i++) {
            queryFilteredReceivedWallet(pg);
        }
    }

    private static void warmupCardFiltered(Connection pg) throws SQLException {
        for (int i = 0; i < WARMUP; i++) {
            queryCardFiltered(pg);
        }
    }

    private static void warmupCurrencyFilteredTopN(Connection pg) throws SQLException {
        for (int i = 0; i < WARMUP; i++) {
            queryCurrencyFilteredTopN(pg);
        }
    }

    private static void warmupAmountFilteredTopN(Connection pg) throws SQLException {
        for (int i = 0; i < WARMUP; i++) {
            queryAmountFilteredTopN(pg);
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

    private static int queryMerchantSearch(Connection pg) throws SQLException {
        int rows = 0;
        try (PreparedStatement ps = pg.prepareStatement(CARD_MERCHANT_SEARCH_QUERY)) {
            ps.setLong(1, TARGET_USER_ID);
            ps.setString(2, "%" + MERCHANT_KEYWORD + "%");
            ps.setInt(3, PAGE_SIZE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows++;
                }
            }
        }
        return rows;
    }

    private static int queryFilteredTopN(Connection pg) throws SQLException {
        int rows = 0;
        try (PreparedStatement ps = pg.prepareStatement(FILTERED_TOP_N_QUERY)) {
            bindFilteredTopN(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows++;
                }
            }
        }
        return rows;
    }

    private static int queryStaticOptionalTopN(Connection pg) throws SQLException {
        int rows = 0;
        try (PreparedStatement ps = pg.prepareStatement(STATIC_OPTIONAL_TOP_N_QUERY)) {
            bindStaticOptionalTopNWithoutFilters(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows++;
                }
            }
        }
        return rows;
    }

    private static int queryIntegratedTopN(Connection pg) throws SQLException {
        int rows = 0;
        try (PreparedStatement ps = pg.prepareStatement(INTEGRATED_TOP_N_QUERY)) {
            bindIntegratedTopN(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows++;
                }
            }
        }
        return rows;
    }

    private static int queryStaticOptionalFilteredTopN(Connection pg) throws SQLException {
        int rows = 0;
        try (PreparedStatement ps = pg.prepareStatement(STATIC_OPTIONAL_FILTERED_TOP_N_QUERY)) {
            bindStaticOptionalFilteredTopN(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows++;
                }
            }
        }
        return rows;
    }

    private static int queryFilteredReceivedWallet(Connection pg) throws SQLException {
        int rows = 0;
        try (PreparedStatement ps = pg.prepareStatement(FILTERED_RECEIVED_WALLET_QUERY)) {
            ps.setLong(1, TARGET_USER_ID);
            ps.setBigDecimal(2, FILTER_MIN_AMOUNT);
            ps.setBigDecimal(3, FILTER_MAX_AMOUNT);
            ps.setString(4, FILTER_CURRENCY);
            ps.setString(5, FILTER_CURRENCY);
            ps.setInt(6, PAGE_SIZE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows++;
                }
            }
        }
        return rows;
    }

    private static int queryCardFiltered(Connection pg) throws SQLException {
        int rows = 0;
        try (PreparedStatement ps = pg.prepareStatement(CARD_FILTERED_QUERY)) {
            bindCardFiltered(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows++;
                }
            }
        }
        return rows;
    }

    private static int queryCurrencyFilteredTopN(Connection pg) throws SQLException {
        int rows = 0;
        try (PreparedStatement ps = pg.prepareStatement(CURRENCY_FILTERED_TOP_N_QUERY)) {
            bindCurrencyFilteredTopN(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows++;
                }
            }
        }
        return rows;
    }

    private static int queryAmountFilteredTopN(Connection pg) throws SQLException {
        int rows = 0;
        try (PreparedStatement ps = pg.prepareStatement(AMOUNT_FILTERED_TOP_N_QUERY)) {
            bindAmountFilteredTopN(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows++;
                }
            }
        }
        return rows;
    }

    private static void bindFilteredTopN(PreparedStatement ps) throws SQLException {
        int index = 1;
        ps.setLong(index++, TARGET_USER_ID);
        ps.setBigDecimal(index++, FILTER_MIN_AMOUNT);
        ps.setBigDecimal(index++, FILTER_MAX_AMOUNT);
        ps.setString(index++, FILTER_CURRENCY);
        ps.setString(index++, FILTER_CURRENCY);
        ps.setInt(index++, PAGE_SIZE);

        ps.setLong(index++, TARGET_USER_ID);
        ps.setBigDecimal(index++, FILTER_MIN_AMOUNT);
        ps.setBigDecimal(index++, FILTER_MAX_AMOUNT);
        ps.setString(index++, FILTER_CURRENCY);
        ps.setString(index++, FILTER_CURRENCY);
        ps.setInt(index++, PAGE_SIZE);

        ps.setLong(index++, TARGET_USER_ID);
        ps.setString(index++, "%" + MERCHANT_KEYWORD + "%");
        ps.setBigDecimal(index++, FILTER_MIN_AMOUNT);
        ps.setBigDecimal(index++, FILTER_MAX_AMOUNT);
        ps.setString(index++, FILTER_CURRENCY);
        ps.setInt(index++, PAGE_SIZE);

        ps.setLong(index++, TARGET_USER_ID);
        ps.setBigDecimal(index++, FILTER_MIN_AMOUNT);
        ps.setBigDecimal(index++, FILTER_MAX_AMOUNT);
        ps.setString(index++, FILTER_CURRENCY);
        ps.setString(index++, FILTER_CURRENCY);
        ps.setInt(index++, PAGE_SIZE);

        ps.setInt(index, PAGE_SIZE);
    }

    private static void bindCurrencyFilteredTopN(PreparedStatement ps) throws SQLException {
        int index = 1;
        ps.setLong(index++, TARGET_USER_ID);
        ps.setString(index++, FILTER_CURRENCY);
        ps.setString(index++, FILTER_CURRENCY);
        ps.setInt(index++, PAGE_SIZE);

        ps.setLong(index++, TARGET_USER_ID);
        ps.setString(index++, FILTER_CURRENCY);
        ps.setString(index++, FILTER_CURRENCY);
        ps.setInt(index++, PAGE_SIZE);

        ps.setLong(index++, TARGET_USER_ID);
        ps.setString(index++, FILTER_CURRENCY);
        ps.setInt(index++, PAGE_SIZE);

        ps.setLong(index++, TARGET_USER_ID);
        ps.setString(index++, FILTER_CURRENCY);
        ps.setString(index++, FILTER_CURRENCY);
        ps.setInt(index++, PAGE_SIZE);

        ps.setInt(index, PAGE_SIZE);
    }

    private static void bindAmountFilteredTopN(PreparedStatement ps) throws SQLException {
        int index = 1;
        ps.setLong(index++, TARGET_USER_ID);
        ps.setBigDecimal(index++, FILTER_MIN_AMOUNT);
        ps.setBigDecimal(index++, FILTER_MAX_AMOUNT);
        ps.setInt(index++, PAGE_SIZE);

        ps.setLong(index++, TARGET_USER_ID);
        ps.setBigDecimal(index++, FILTER_MIN_AMOUNT);
        ps.setBigDecimal(index++, FILTER_MAX_AMOUNT);
        ps.setInt(index++, PAGE_SIZE);

        ps.setLong(index++, TARGET_USER_ID);
        ps.setBigDecimal(index++, FILTER_MIN_AMOUNT);
        ps.setBigDecimal(index++, FILTER_MAX_AMOUNT);
        ps.setInt(index++, PAGE_SIZE);

        ps.setLong(index++, TARGET_USER_ID);
        ps.setBigDecimal(index++, FILTER_MIN_AMOUNT);
        ps.setBigDecimal(index++, FILTER_MAX_AMOUNT);
        ps.setInt(index++, PAGE_SIZE);

        ps.setInt(index, PAGE_SIZE);
    }

    private static void bindCardFiltered(PreparedStatement ps) throws SQLException {
        ps.setLong(1, TARGET_USER_ID);
        ps.setString(2, "%" + MERCHANT_KEYWORD + "%");
        ps.setBigDecimal(3, FILTER_MIN_AMOUNT);
        ps.setBigDecimal(4, FILTER_MAX_AMOUNT);
        ps.setString(5, FILTER_CURRENCY);
        ps.setInt(6, PAGE_SIZE);
    }

    private static void bindIntegratedTopN(PreparedStatement ps) throws SQLException {
        int index = 1;
        ps.setLong(index++, TARGET_USER_ID);
        ps.setInt(index++, SOURCE_LIMIT);
        ps.setLong(index++, TARGET_USER_ID);
        ps.setInt(index++, SOURCE_LIMIT);
        ps.setLong(index++, TARGET_USER_ID);
        ps.setInt(index++, SOURCE_LIMIT);
        ps.setLong(index++, TARGET_USER_ID);
        ps.setInt(index++, SOURCE_LIMIT);
        ps.setInt(index, PAGE_SIZE);
    }

    private static void bindStaticOptionalTopNWithoutFilters(PreparedStatement ps) throws SQLException {
        int index = 1;
        ps.setLong(index++, TARGET_USER_ID);
        index = bindOptionalAmountCurrency(ps, index, null, null, null);
        ps.setInt(index++, PAGE_SIZE);

        ps.setLong(index++, TARGET_USER_ID);
        index = bindOptionalMerchantAmountApprovedCurrency(ps, index, null, null, null, null);
        ps.setInt(index++, PAGE_SIZE);

        ps.setLong(index++, TARGET_USER_ID);
        index = bindOptionalAmountCurrency(ps, index, null, null, null);
        ps.setInt(index++, PAGE_SIZE);

        ps.setInt(index, PAGE_SIZE);
    }

    private static void bindStaticOptionalFilteredTopN(PreparedStatement ps) throws SQLException {
        int index = 1;
        ps.setLong(index++, TARGET_USER_ID);
        index = bindOptionalAmountCurrency(ps, index, FILTER_MIN_AMOUNT, FILTER_MAX_AMOUNT, FILTER_CURRENCY);
        ps.setInt(index++, PAGE_SIZE);

        ps.setLong(index++, TARGET_USER_ID);
        index = bindOptionalAmountCurrency(ps, index, FILTER_MIN_AMOUNT, FILTER_MAX_AMOUNT, FILTER_CURRENCY);
        ps.setInt(index++, PAGE_SIZE);

        ps.setLong(index++, TARGET_USER_ID);
        index = bindOptionalMerchantAmountApprovedCurrency(ps, index, "%" + MERCHANT_KEYWORD + "%", FILTER_MIN_AMOUNT, FILTER_MAX_AMOUNT, FILTER_CURRENCY);
        ps.setInt(index++, PAGE_SIZE);

        ps.setLong(index++, TARGET_USER_ID);
        index = bindOptionalAmountCurrency(ps, index, FILTER_MIN_AMOUNT, FILTER_MAX_AMOUNT, FILTER_CURRENCY);
        ps.setInt(index++, PAGE_SIZE);

        ps.setInt(index, PAGE_SIZE);
    }

    private static int bindOptionalAmountCurrency(PreparedStatement ps, int index, BigDecimal minAmount,
                                                  BigDecimal maxAmount, String currency) throws SQLException {
        ps.setBigDecimal(index++, minAmount);
        ps.setBigDecimal(index++, minAmount);
        ps.setBigDecimal(index++, maxAmount);
        ps.setBigDecimal(index++, maxAmount);
        ps.setString(index++, currency);
        ps.setString(index++, currency);
        ps.setString(index++, currency);
        return index;
    }

    private static int bindOptionalMerchantAmountCurrency(PreparedStatement ps, int index, String merchantName,
                                                          BigDecimal minAmount, BigDecimal maxAmount,
                                                          String currency) throws SQLException {
        ps.setString(index++, merchantName);
        ps.setString(index++, merchantName);
        return bindOptionalAmountCurrency(ps, index, minAmount, maxAmount, currency);
    }

    private static int bindOptionalMerchantAmountApprovedCurrency(PreparedStatement ps, int index, String merchantName,
                                                                  BigDecimal minAmount, BigDecimal maxAmount,
                                                                  String currency) throws SQLException {
        ps.setString(index++, merchantName);
        ps.setString(index++, merchantName);
        ps.setBigDecimal(index++, minAmount);
        ps.setBigDecimal(index++, minAmount);
        ps.setBigDecimal(index++, maxAmount);
        ps.setBigDecimal(index++, maxAmount);
        ps.setString(index++, currency);
        ps.setString(index++, currency);
        return index;
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

    private static void explainMerchantSearch(Connection pg, String title) throws SQLException {
        System.out.println();
        System.out.println(title + ":");
        try (PreparedStatement ps = pg.prepareStatement("explain analyze " + CARD_MERCHANT_SEARCH_QUERY)) {
            ps.setLong(1, TARGET_USER_ID);
            ps.setString(2, "%" + MERCHANT_KEYWORD + "%");
            ps.setInt(3, PAGE_SIZE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getString(1));
                }
            }
        }
    }

    private static void explainFilteredTopN(Connection pg, String title) throws SQLException {
        System.out.println();
        System.out.println(title + ":");
        try (PreparedStatement ps = pg.prepareStatement("explain analyze " + FILTERED_TOP_N_QUERY)) {
            bindFilteredTopN(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getString(1));
                }
            }
        }
    }

    private static void explainStaticOptionalTopN(Connection pg, String title) throws SQLException {
        System.out.println();
        System.out.println(title + ":");
        try (PreparedStatement ps = pg.prepareStatement("explain analyze " + STATIC_OPTIONAL_TOP_N_QUERY)) {
            bindStaticOptionalTopNWithoutFilters(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getString(1));
                }
            }
        }
    }

    private static void explainIntegratedTopN(Connection pg, String title) throws SQLException {
        System.out.println();
        System.out.println(title + ":");
        try (PreparedStatement ps = pg.prepareStatement("explain analyze " + INTEGRATED_TOP_N_QUERY)) {
            bindIntegratedTopN(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getString(1));
                }
            }
        }
    }

    private static void explainStaticOptionalFilteredTopN(Connection pg, String title) throws SQLException {
        System.out.println();
        System.out.println(title + ":");
        try (PreparedStatement ps = pg.prepareStatement("explain analyze " + STATIC_OPTIONAL_FILTERED_TOP_N_QUERY)) {
            bindStaticOptionalFilteredTopN(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getString(1));
                }
            }
        }
    }

    private static void explainFilteredReceivedWallet(Connection pg, String title) throws SQLException {
        System.out.println();
        System.out.println(title + ":");
        try (PreparedStatement ps = pg.prepareStatement("explain analyze " + FILTERED_RECEIVED_WALLET_QUERY)) {
            ps.setLong(1, TARGET_USER_ID);
            ps.setBigDecimal(2, FILTER_MIN_AMOUNT);
            ps.setBigDecimal(3, FILTER_MAX_AMOUNT);
            ps.setString(4, FILTER_CURRENCY);
            ps.setString(5, FILTER_CURRENCY);
            ps.setInt(6, PAGE_SIZE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getString(1));
                }
            }
        }
    }

    private static void explainCardFiltered(Connection pg, String title) throws SQLException {
        System.out.println();
        System.out.println(title + ":");
        try (PreparedStatement ps = pg.prepareStatement("explain analyze " + CARD_FILTERED_QUERY)) {
            bindCardFiltered(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getString(1));
                }
            }
        }
    }

    private static void explainCurrencyFilteredTopN(Connection pg, String title) throws SQLException {
        System.out.println();
        System.out.println(title + ":");
        try (PreparedStatement ps = pg.prepareStatement("explain analyze " + CURRENCY_FILTERED_TOP_N_QUERY)) {
            bindCurrencyFilteredTopN(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getString(1));
                }
            }
        }
    }

    private static void explainAmountFilteredTopN(Connection pg, String title) throws SQLException {
        System.out.println();
        System.out.println(title + ":");
        try (PreparedStatement ps = pg.prepareStatement("explain analyze " + AMOUNT_FILTERED_TOP_N_QUERY)) {
            bindAmountFilteredTopN(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getString(1));
                }
            }
        }
    }

    private static String merchantName(int sequence) {
        if (sequence % 20 == 1) {
            return "STARBUCKS";
        }
        if (sequence % 20 == 3) {
            return "STARFIELD";
        }
        if (sequence % 10 == 1) {
            return "PARIS BAGUETTE";
        }
        return "STORE_" + (sequence % 1_000);
    }

    private static String approvedCurrency(int sequence) {
        return sequence % 4 == 1 ? "USD" : "KRW";
    }

    private static String fromCurrency(int sequence) {
        return sequence % 4 == 0 ? "USD" : "KRW";
    }

    private static String toCurrency(int sequence) {
        return sequence % 4 == 2 ? "USD" : "KRW";
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

    private static BigDecimal decimalEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return new BigDecimal(value == null || value.isBlank() ? defaultValue : value);
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
