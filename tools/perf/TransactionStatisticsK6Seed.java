import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public class TransactionStatisticsK6Seed {

    private static final String PG_URL = env("PG_URL", "jdbc:postgresql://localhost:5432/xcp");
    private static final String PG_USER = env("PG_USER", "iyongjun");
    private static final String PG_PASSWORD = env("PG_PASSWORD", "");
    private static final String JWT_SECRET = requiredEnv("JWT_SECRET");
    private static final int TOTAL_ROWS = intEnv("TRANSACTION_STATISTICS_K6_SEED_ROWS", 100_000);
    private static final int USER_COUNT = intEnv("TRANSACTION_STATISTICS_K6_SEED_USERS", 1_000);
    private static final int MONTH_COUNT = intEnv("TRANSACTION_STATISTICS_K6_SEED_MONTHS", 12);
    private static final long SEED = longEnv("TRANSACTION_STATISTICS_K6_SEED_RANDOM", 20260704L);
    private static final String PASSWORD = env("TRANSACTION_STATISTICS_K6_PASSWORD", "Benchmark123!");
    private static final Path OUTPUT_DIR = Path.of("build", "perf", "k6");
    private static final String[] CURRENCIES = {"KRW", "USD", "JPY", "EUR"};
    private static final int BATCH_SIZE = 1_000;

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT_DIR);

        try (Connection connection = DriverManager.getConnection(PG_URL, PG_USER, PG_PASSWORD)) {
            connection.setAutoCommit(false);
            createStatisticsObjects(connection);
            BenchmarkDataset dataset = createDataset(connection);
            refreshMaterializedView(connection);
            refreshMonthlySummary(connection, dataset.fromMonth(), dataset.toMonth());
            analyzeTables(connection);
            connection.commit();

            String token = generateAccessToken(dataset.heavyUserId());
            writeEnv(dataset, token);
            writeReadme(dataset);

            System.out.println("Transaction statistics k6 seed completed.");
            System.out.println("- rows: " + dataset.totalRows());
            System.out.println("- users: " + dataset.userCount());
            System.out.println("- months: " + dataset.monthCount());
            System.out.println("- heavyUserId: " + dataset.heavyUserId());
            System.out.println("- regularUserIds: " + dataset.regularUserIds());
            System.out.println("- auth env: " + OUTPUT_DIR.resolve("transaction-statistics-auth.env"));
        }
    }

    private static BenchmarkDataset createDataset(Connection connection) throws SQLException {
        List<Long> userIds = insertUsers(connection, USER_COUNT);
        Long heavyUserId = userIds.get(0);
        Random random = new Random(SEED);
        YearMonth startMonth = YearMonth.of(2026, 1);

        List<WalletRow> walletRows = new ArrayList<>();
        List<CardRow> cardRows = new ArrayList<>();
        List<ExchangeRow> exchangeRows = new ArrayList<>();

        for (int index = 0; index < TOTAL_ROWS; index++) {
            Long userId = selectUserId(userIds, heavyUserId, random);
            LocalDateTime occurredAt = randomTime(startMonth, MONTH_COUNT, random);
            String fromCurrency = randomCurrency(random);
            String toCurrency = randomCurrency(random);
            BigDecimal amount = amount(index, random);
            BigDecimal receivedAmount = receivedAmount(amount, fromCurrency, toCurrency);
            int source = random.nextInt(100);

            if (source < 50) {
                walletRows.add(walletRow(index, userIds, userId, random, occurredAt,
                        fromCurrency, toCurrency, amount, receivedAmount));
            } else if (source < 85) {
                cardRows.add(cardRow(index, userId, random, occurredAt, fromCurrency, amount));
            } else {
                exchangeRows.add(exchangeRow(userId, random, occurredAt, fromCurrency, toCurrency,
                        amount, receivedAmount));
            }
        }

        insertWalletRows(connection, walletRows);
        insertCardRows(connection, cardRows);
        insertExchangeRows(connection, exchangeRows);

        return new BenchmarkDataset(
                TOTAL_ROWS,
                userIds.size(),
                MONTH_COUNT,
                startMonth,
                startMonth.plusMonths(MONTH_COUNT - 1L),
                heavyUserId,
                userIds.stream().skip(1).limit(Math.min(20, Math.max(1, userIds.size() - 1))).toList(),
                walletRows.size(),
                cardRows.size(),
                exchangeRows.size()
        );
    }

    private static List<Long> insertUsers(Connection connection, int userCount) throws SQLException {
        List<Long> userIds = new ArrayList<>(userCount);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String encodedPassword = encoder.encode(PASSWORD);
        String runId = Long.toString(System.currentTimeMillis());

        String sql = """
                insert into users (
                    user_email,
                    password,
                    user_name,
                    user_nickname,
                    user_phonenumber,
                    user_age,
                    user_sex,
                    user_type,
                    is_deleted,
                    user_join_date
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        for (int index = 0; index < userCount; index++) {
            int sequence = index + 1;
            try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "stats-k6-" + runId + "-" + sequence + "@example.com");
                ps.setString(2, encodedPassword);
                ps.setString(3, "u" + sequence % 100);
                ps.setString(4, "k6" + runId.substring(Math.max(0, runId.length() - 7)) + sequence);
                ps.setString(5, phoneNumber(sequence, runId));
                ps.setInt(6, 0);
                ps.setString(7, sequence % 2 == 0 ? "MALE" : "FEMALE");
                ps.setString(8, "ROLE_USER");
                ps.setBoolean(9, false);
                ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0)));
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        userIds.add(((Number) Objects.requireNonNull(rs.getObject("user_id"))).longValue());
                    }
                }
            }
        }
        return userIds;
    }

    private static WalletRow walletRow(int index, List<Long> userIds, Long userId, Random random,
                                       LocalDateTime occurredAt, String fromCurrency, String toCurrency,
                                       BigDecimal amount, BigDecimal receivedAmount) {
        int type = random.nextInt(10);
        if (type < 3) {
            return new WalletRow(userId, null, amount, null, null, fromCurrency, fromCurrency,
                    "DEPOSIT", occurredAt);
        }
        if (type < 6) {
            return new WalletRow(userId, null, amount, null, null, fromCurrency, fromCurrency,
                    "WITHDRAWAL", occurredAt);
        }
        Long counterpartyId = selectCounterpartyId(userIds, userId, random);
        return new WalletRow(userId, counterpartyId, amount, receivedAmount, UUID.randomUUID(),
                fromCurrency, toCurrency, "TRANSFER", occurredAt);
    }

    private static CardRow cardRow(int index, Long userId, Random random, LocalDateTime occurredAt,
                                   String currency, BigDecimal amount) {
        String type = switch (random.nextInt(10)) {
            case 0 -> "REFUND";
            case 1 -> "DEPOSIT";
            default -> "PAYMENT";
        };
        return new CardRow(userId, "merchant-" + random.nextInt(100),
                amount, currency, amount.multiply(BigDecimal.TEN), BigDecimal.ZERO,
                "K6-" + index, type, occurredAt);
    }

    private static ExchangeRow exchangeRow(Long userId, Random random, LocalDateTime occurredAt,
                                           String fromCurrency, String toCurrency,
                                           BigDecimal amount, BigDecimal receivedAmount) {
        int statusRandom = random.nextInt(10);
        String status = statusRandom < 8 ? "COMPLETED" : statusRandom == 8 ? "PENDING" : "FAILED";
        LocalDateTime completedAt = "COMPLETED".equals(status) ? occurredAt : null;
        return new ExchangeRow(userId, fromCurrency, toCurrency, amount, receivedAmount,
                new BigDecimal("0.00100000"), status, occurredAt.minusHours(1), completedAt);
    }

    private static void insertWalletRows(Connection connection, List<WalletRow> rows) throws SQLException {
        batch(connection, rows, """
                insert into wallet_transaction (
                    user_id,
                    counterparty_user_id,
                    amount,
                    received_amount,
                    transfer_id,
                    from_currency,
                    to_currency,
                    transaction_type,
                    transaction_time
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (ps, row) -> {
            ps.setLong(1, row.userId());
            setNullableLong(ps, 2, row.counterpartyUserId());
            ps.setBigDecimal(3, row.amount());
            ps.setBigDecimal(4, row.receivedAmount());
            ps.setObject(5, row.transferId());
            ps.setString(6, row.fromCurrency());
            ps.setString(7, row.toCurrency());
            ps.setString(8, row.transactionType());
            ps.setTimestamp(9, Timestamp.valueOf(row.transactionTime()));
        });
    }

    private static void insertCardRows(Connection connection, List<CardRow> rows) throws SQLException {
        batch(connection, rows, """
                insert into card_transaction (
                    user_id,
                    merchant_name,
                    approved_amount,
                    approved_currency,
                    krw_amount,
                    balance_after,
                    approval_number,
                    transaction_type,
                    transaction_time
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (ps, row) -> {
            ps.setLong(1, row.userId());
            ps.setString(2, row.merchantName());
            ps.setBigDecimal(3, row.approvedAmount());
            ps.setString(4, row.approvedCurrency());
            ps.setBigDecimal(5, row.krwAmount());
            ps.setBigDecimal(6, row.balanceAfter());
            ps.setString(7, row.approvalNumber());
            ps.setString(8, row.transactionType());
            ps.setTimestamp(9, Timestamp.valueOf(row.transactionTime()));
        });
    }

    private static void insertExchangeRows(Connection connection, List<ExchangeRow> rows) throws SQLException {
        batch(connection, rows, """
                insert into exchange_transaction (
                    user_id,
                    from_currency,
                    to_currency,
                    amount,
                    received_amount,
                    exchange_rate,
                    status,
                    created_at,
                    completed_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (ps, row) -> {
            ps.setLong(1, row.userId());
            ps.setString(2, row.fromCurrency());
            ps.setString(3, row.toCurrency());
            ps.setBigDecimal(4, row.amount());
            ps.setBigDecimal(5, row.receivedAmount());
            ps.setBigDecimal(6, row.exchangeRate());
            ps.setString(7, row.status());
            ps.setTimestamp(8, Timestamp.valueOf(row.createdAt()));
            ps.setTimestamp(9, row.completedAt() == null ? null : Timestamp.valueOf(row.completedAt()));
        });
    }

    private static <T> void batch(Connection connection, List<T> rows, String sql, RowBinder<T> binder)
            throws SQLException {
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            List<T> chunk = rows.subList(from, Math.min(from + BATCH_SIZE, rows.size()));
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (T row : chunk) {
                    binder.bind(ps, row);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    private static void createStatisticsObjects(Connection connection) throws SQLException, IOException {
        executeSqlResource(connection, "src/main/resources/db/migration/postgresql/V3__add_transaction_monthly_statistics_materialized_view.sql");
        executeSqlResource(connection, "src/main/resources/db/migration/postgresql/V4__add_transaction_monthly_summary.sql");
    }

    private static void executeSqlResource(Connection connection, String location) throws SQLException, IOException {
        String sql = Files.readString(Path.of(location), StandardCharsets.UTF_8);
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void refreshMaterializedView(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("REFRESH MATERIALIZED VIEW mv_transaction_monthly_statistics");
        }
    }

    private static void refreshMonthlySummary(Connection connection, YearMonth fromMonth, YearMonth toMonth)
            throws SQLException {
        LocalDateTime from = fromMonth.atDay(1).atStartOfDay();
        LocalDateTime toExclusive = toMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        try (PreparedStatement delete = connection.prepareStatement("""
                delete from transaction_monthly_summary
                where bucket_month >= ?
                  and bucket_month < ?
                """)) {
            delete.setTimestamp(1, Timestamp.valueOf(from));
            delete.setTimestamp(2, Timestamp.valueOf(toExclusive));
            delete.executeUpdate();
        }

        try (PreparedStatement insert = connection.prepareStatement("""
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
                    select user_id, date_trunc('month', transaction_time) as bucket_month, 'WALLET' as source_type,
                           transaction_type as transaction_type, coalesce(from_currency, to_currency) as currency,
                           amount as amount,
                           case when transaction_type = 'DEPOSIT' then 'INCOMING'
                                when transaction_type = 'WITHDRAWAL' then 'OUTGOING' end as direction
                    from wallet_transaction
                    where transaction_time >= ? and transaction_time < ?
                      and transaction_type in ('DEPOSIT', 'WITHDRAWAL')
                    union all
                    select user_id, date_trunc('month', transaction_time) as bucket_month, 'WALLET' as source_type,
                           transaction_type as transaction_type, from_currency as currency, amount as amount,
                           'OUTGOING' as direction
                    from wallet_transaction
                    where transaction_time >= ? and transaction_time < ? and transaction_type = 'TRANSFER'
                    union all
                    select counterparty_user_id as user_id, date_trunc('month', transaction_time) as bucket_month,
                           'WALLET' as source_type, transaction_type as transaction_type, to_currency as currency,
                           received_amount as amount, 'INCOMING' as direction
                    from wallet_transaction
                    where transaction_time >= ? and transaction_time < ? and transaction_type = 'TRANSFER'
                    union all
                    select user_id, date_trunc('month', transaction_time) as bucket_month, 'CARD' as source_type,
                           transaction_type as transaction_type, approved_currency as currency, approved_amount as amount,
                           case when transaction_type = 'PAYMENT' then 'OUTGOING'
                                when transaction_type in ('REFUND', 'DEPOSIT') then 'INCOMING' end as direction
                    from card_transaction
                    where transaction_time >= ? and transaction_time < ?
                      and transaction_type in ('PAYMENT', 'REFUND', 'DEPOSIT')
                    union all
                    select user_id, date_trunc('month', completed_at) as bucket_month, 'EXCHANGE' as source_type,
                           status as transaction_type, from_currency as currency, amount as amount,
                           'OUTGOING' as direction
                    from exchange_transaction
                    where completed_at >= ? and completed_at < ? and status = 'COMPLETED' and completed_at is not null
                    union all
                    select user_id, date_trunc('month', completed_at) as bucket_month, 'EXCHANGE' as source_type,
                           status as transaction_type, to_currency as currency, received_amount as amount,
                           'INCOMING' as direction
                    from exchange_transaction
                    where completed_at >= ? and completed_at < ? and status = 'COMPLETED' and completed_at is not null
                ) statistics_source
                where user_id is not null and currency is not null and amount is not null and direction is not null
                group by user_id, bucket_month, source_type, transaction_type, currency, direction
                """)) {
            insert.setTimestamp(1, Timestamp.valueOf(now));
            insert.setTimestamp(2, Timestamp.valueOf(now));
            insert.setTimestamp(3, Timestamp.valueOf(now));
            int index = 4;
            for (int i = 0; i < 6; i++) {
                insert.setTimestamp(index++, Timestamp.valueOf(from));
                insert.setTimestamp(index++, Timestamp.valueOf(toExclusive));
            }
            insert.executeUpdate();
        }
    }

    private static void analyzeTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ANALYZE wallet_transaction");
            statement.execute("ANALYZE card_transaction");
            statement.execute("ANALYZE exchange_transaction");
            statement.execute("ANALYZE mv_transaction_monthly_statistics");
            statement.execute("ANALYZE transaction_monthly_summary");
        }
    }

    private static String generateAccessToken(Long userId) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000L * 60 * 30))
                .signWith(key)
                .compact();
    }

    private static void writeEnv(BenchmarkDataset dataset, String token) throws IOException {
        String userIds = String.join(",", dataset.regularUserIds().stream()
                .limit(4)
                .map(String::valueOf)
                .toList());
        if (!userIds.isBlank()) {
            userIds = dataset.heavyUserId() + "," + userIds;
        } else {
            userIds = String.valueOf(dataset.heavyUserId());
        }

        List<String> lines = List.of(
                "ACCESS_TOKEN_COOKIE=" + token,
                "AUTH_TOKEN=" + token,
                "TEST_USER_ID=" + dataset.heavyUserId(),
                "USER_IDS=" + userIds,
                "FROM_MONTH=" + dataset.fromMonth(),
                "TO_MONTH=" + dataset.toMonth()
        );
        Files.write(OUTPUT_DIR.resolve("transaction-statistics-auth.env"), lines, StandardCharsets.UTF_8);
    }

    private static void writeReadme(BenchmarkDataset dataset) throws IOException {
        List<String> lines = List.of(
                "# 거래 통계 k6 seed 결과",
                "",
                "- 데이터 rows: " + dataset.totalRows(),
                "- 사용자 수: " + dataset.userCount(),
                "- 월 수: " + dataset.monthCount() + " (" + dataset.fromMonth() + " ~ " + dataset.toMonth() + ")",
                "- heavy user id: " + dataset.heavyUserId(),
                "- regular user ids: " + dataset.regularUserIds(),
                "- auth env: build/perf/k6/transaction-statistics-auth.env",
                "",
                "이 파일과 auth env는 로컬 benchmark 실행 결과이며 커밋하지 않는다."
        );
        Files.write(OUTPUT_DIR.resolve("transaction-statistics-seed-readme.md"), lines, StandardCharsets.UTF_8);
    }

    private static Long selectUserId(List<Long> userIds, Long heavyUserId, Random random) {
        if (random.nextInt(100) < 20) {
            return heavyUserId;
        }
        return userIds.get(random.nextInt(userIds.size()));
    }

    private static Long selectCounterpartyId(List<Long> userIds, Long userId, Random random) {
        Long counterpartyId = userIds.get(random.nextInt(userIds.size()));
        if (counterpartyId.equals(userId) && userIds.size() > 1) {
            return userIds.get((userIds.indexOf(counterpartyId) + 1) % userIds.size());
        }
        return counterpartyId;
    }

    private static LocalDateTime randomTime(YearMonth startMonth, int monthCount, Random random) {
        int monthOffset = random.nextInt(100) < 30 ? Math.max(0, monthCount / 2) : random.nextInt(monthCount);
        YearMonth month = startMonth.plusMonths(monthOffset);
        int day = 1 + random.nextInt(month.lengthOfMonth());
        return month.atDay(day).atTime(random.nextInt(24), random.nextInt(60), random.nextInt(60));
    }

    private static String randomCurrency(Random random) {
        return CURRENCIES[random.nextInt(CURRENCIES.length)];
    }

    private static BigDecimal amount(int index, Random random) {
        return BigDecimal.valueOf(1_000L + random.nextInt(99_000) + index % 100);
    }

    private static BigDecimal receivedAmount(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return amount;
        }
        return amount.divide(BigDecimal.valueOf(1_000), 4, java.math.RoundingMode.HALF_UP);
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
            return;
        }
        ps.setLong(index, value);
    }

    private static String phoneNumber(int sequence, String runId) {
        int middle = 1_000 + sequence / 10_000;
        int lastPrefix = Math.abs(runId.hashCode()) % 9;
        int last = sequence % 1_000;
        return "010-" + middle + "-" + lastPrefix + String.format("%03d", last);
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required.");
        }
        return value;
    }

    private static int intEnv(String name, int defaultValue) {
        return Integer.parseInt(env(name, Integer.toString(defaultValue)));
    }

    private static long longEnv(String name, long defaultValue) {
        return Long.parseLong(env(name, Long.toString(defaultValue)));
    }

    private record BenchmarkDataset(
            int totalRows,
            int userCount,
            int monthCount,
            YearMonth fromMonth,
            YearMonth toMonth,
            Long heavyUserId,
            List<Long> regularUserIds,
            int walletRows,
            int cardRows,
            int exchangeRows
    ) {
    }

    private record WalletRow(
            Long userId,
            Long counterpartyUserId,
            BigDecimal amount,
            BigDecimal receivedAmount,
            UUID transferId,
            String fromCurrency,
            String toCurrency,
            String transactionType,
            LocalDateTime transactionTime
    ) {
    }

    private record CardRow(
            Long userId,
            String merchantName,
            BigDecimal approvedAmount,
            String approvedCurrency,
            BigDecimal krwAmount,
            BigDecimal balanceAfter,
            String approvalNumber,
            String transactionType,
            LocalDateTime transactionTime
    ) {
    }

    private record ExchangeRow(
            Long userId,
            String fromCurrency,
            String toCurrency,
            BigDecimal amount,
            BigDecimal receivedAmount,
            BigDecimal exchangeRate,
            String status,
            LocalDateTime createdAt,
            LocalDateTime completedAt
    ) {
    }

    @FunctionalInterface
    private interface RowBinder<T> {
        void bind(PreparedStatement ps, T row) throws SQLException;
    }
}
