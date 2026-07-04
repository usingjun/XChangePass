package bumblebee.xchangepass.domain.transaction.statistics;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

class TransactionStatisticsBenchmarkDataFactory {

    private static final String[] CURRENCIES = {"KRW", "USD", "JPY", "EUR"};
    private static final int BATCH_SIZE = 1_000;

    private final JdbcTemplate jdbcTemplate;

    TransactionStatisticsBenchmarkDataFactory(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    BenchmarkDataset create(BenchmarkConfig config) {
        List<Long> userIds = insertUsers(config.userCount());
        Long heavyUserId = userIds.get(0);
        Random random = new Random(config.seed());
        YearMonth startMonth = YearMonth.of(2026, 1);

        List<WalletRow> walletRows = new ArrayList<>();
        List<CardRow> cardRows = new ArrayList<>();
        List<ExchangeRow> exchangeRows = new ArrayList<>();

        for (int index = 0; index < config.totalRows(); index++) {
            Long userId = selectUserId(userIds, heavyUserId, random);
            LocalDateTime occurredAt = randomTime(startMonth, config.monthCount(), random);
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

        insertWalletRows(walletRows);
        insertCardRows(cardRows);
        insertExchangeRows(exchangeRows);

        return new BenchmarkDataset(
                config.totalRows(),
                userIds.size(),
                config.monthCount(),
                startMonth,
                startMonth.plusMonths(config.monthCount() - 1L),
                heavyUserId,
                userIds.stream().skip(1).limit(Math.min(20, Math.max(1, userIds.size() - 1))).toList(),
                walletRows.size(),
                cardRows.size(),
                exchangeRows.size()
        );
    }

    void analyzeTables() {
        jdbcTemplate.execute("ANALYZE wallet_transaction");
        jdbcTemplate.execute("ANALYZE card_transaction");
        jdbcTemplate.execute("ANALYZE exchange_transaction");
        jdbcTemplate.execute("ANALYZE transaction_monthly_summary");
    }

    private List<Long> insertUsers(int userCount) {
        List<Long> userIds = new ArrayList<>(userCount);
        for (int index = 0; index < userCount; index++) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            int sequence = index + 1;
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
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
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, "stats-benchmark-" + sequence + "@example.com");
                ps.setString(2, "{noop}Benchmark123!");
                ps.setString(3, "u" + sequence % 100);
                ps.setString(4, "bench" + sequence);
                ps.setString(5, phoneNumber(sequence));
                ps.setInt(6, 0);
                ps.setString(7, sequence % 2 == 0 ? "MALE" : "FEMALE");
                ps.setString(8, "ROLE_USER");
                ps.setBoolean(9, false);
                ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0)));
                return ps;
            }, keyHolder);
            userIds.add(((Number) Objects.requireNonNull(keyHolder.getKeyList().get(0).get("user_id"))).longValue());
        }
        return userIds;
    }

    private WalletRow walletRow(int index, List<Long> userIds, Long userId, Random random,
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

    private CardRow cardRow(int index, Long userId, Random random, LocalDateTime occurredAt,
                            String currency, BigDecimal amount) {
        String type = switch (random.nextInt(10)) {
            case 0 -> "REFUND";
            case 1 -> "DEPOSIT";
            default -> "PAYMENT";
        };
        return new CardRow(userId, "merchant-" + random.nextInt(100),
                amount, currency, amount.multiply(BigDecimal.TEN), BigDecimal.ZERO,
                "TSB-" + index, type, occurredAt);
    }

    private ExchangeRow exchangeRow(Long userId, Random random, LocalDateTime occurredAt,
                                    String fromCurrency, String toCurrency,
                                    BigDecimal amount, BigDecimal receivedAmount) {
        int statusRandom = random.nextInt(10);
        String status = statusRandom < 8 ? "COMPLETED" : statusRandom == 8 ? "PENDING" : "FAILED";
        LocalDateTime completedAt = "COMPLETED".equals(status) ? occurredAt : null;
        return new ExchangeRow(userId, fromCurrency, toCurrency, amount, receivedAmount,
                new BigDecimal("0.00100000"), status, occurredAt.minusHours(1), completedAt);
    }

    private void insertWalletRows(List<WalletRow> rows) {
        batch(rows, """
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

    private void insertCardRows(List<CardRow> rows) {
        batch(rows, """
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

    private void insertExchangeRows(List<ExchangeRow> rows) {
        batch(rows, """
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
            if (row.completedAt() == null) {
                ps.setTimestamp(9, null);
            } else {
                ps.setTimestamp(9, Timestamp.valueOf(row.completedAt()));
            }
        });
    }

    private <T> void batch(List<T> rows, String sql, RowBinder<T> binder) {
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            List<T> chunk = rows.subList(from, Math.min(from + BATCH_SIZE, rows.size()));
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int index) throws java.sql.SQLException {
                    binder.bind(ps, chunk.get(index));
                }

                @Override
                public int getBatchSize() {
                    return chunk.size();
                }
            });
        }
    }

    private Long selectUserId(List<Long> userIds, Long heavyUserId, Random random) {
        if (random.nextInt(100) < 20) {
            return heavyUserId;
        }
        return userIds.get(random.nextInt(userIds.size()));
    }

    private Long selectCounterpartyId(List<Long> userIds, Long userId, Random random) {
        Long counterpartyId = userIds.get(random.nextInt(userIds.size()));
        if (counterpartyId.equals(userId) && userIds.size() > 1) {
            return userIds.get((userIds.indexOf(counterpartyId) + 1) % userIds.size());
        }
        return counterpartyId;
    }

    private LocalDateTime randomTime(YearMonth startMonth, int monthCount, Random random) {
        int monthOffset = random.nextInt(100) < 30 ? Math.max(0, monthCount / 2) : random.nextInt(monthCount);
        YearMonth month = startMonth.plusMonths(monthOffset);
        int day = 1 + random.nextInt(month.lengthOfMonth());
        return month.atDay(day).atTime(random.nextInt(24), random.nextInt(60), random.nextInt(60));
    }

    private String randomCurrency(Random random) {
        return CURRENCIES[random.nextInt(CURRENCIES.length)];
    }

    private BigDecimal amount(int index, Random random) {
        return BigDecimal.valueOf(1_000L + random.nextInt(99_000) + index % 100);
    }

    private BigDecimal receivedAmount(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return amount;
        }
        return amount.divide(BigDecimal.valueOf(1_000), 4, java.math.RoundingMode.HALF_UP);
    }

    private void setNullableLong(PreparedStatement ps, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            ps.setObject(index, null);
            return;
        }
        ps.setLong(index, value);
    }

    private String phoneNumber(int sequence) {
        int middle = 1_000 + sequence / 10_000;
        int last = sequence % 10_000;
        return "010-" + middle + "-" + String.format("%04d", last);
    }

    record BenchmarkConfig(
            int totalRows,
            int userCount,
            int monthCount,
            long seed
    ) {
    }

    record BenchmarkDataset(
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
        void bind(PreparedStatement ps, T row) throws java.sql.SQLException;
    }
}
