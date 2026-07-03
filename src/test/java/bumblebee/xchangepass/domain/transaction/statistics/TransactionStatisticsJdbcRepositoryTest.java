package bumblebee.xchangepass.domain.transaction.statistics;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransaction;
import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import bumblebee.xchangepass.domain.exchangeTransaction.entitiy.ExchangeTransaction;
import bumblebee.xchangepass.domain.transaction.statistics.dto.TransactionStatisticsDirection;
import bumblebee.xchangepass.domain.transaction.statistics.dto.TransactionStatisticsRow;
import bumblebee.xchangepass.domain.transaction.statistics.dto.TransactionStatisticsSourceType;
import bumblebee.xchangepass.domain.transaction.statistics.repository.TransactionStatisticsJdbcRepository;
import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.global.config.QueryDSLConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TransactionStatisticsJdbcRepository.class, QueryDSLConfig.class})
class TransactionStatisticsJdbcRepositoryTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("xcp_transaction_statistics_test")
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
    private TransactionStatisticsJdbcRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute("DROP MATERIALIZED VIEW IF EXISTS mv_transaction_monthly_statistics");
        executeSqlResource("db/migration/postgresql/V3__add_transaction_monthly_statistics_materialized_view.sql");
        executeSqlResource("db/migration/postgresql/V4__add_transaction_monthly_summary.sql");
        jdbcTemplate.update("delete from transaction_monthly_summary");
    }

    @Test
    void monthlyStatisticsAggregatesDirectlyFromTransactionTables() {
        User user = persistUser("stats-user@example.com", "stats-user", "010-1000-1000");
        User receiver = persistUser("stats-receiver@example.com", "stats-receiver", "010-2000-2000");
        LocalDateTime inRange = LocalDateTime.of(2026, 1, 15, 12, 0);
        LocalDateTime outsideRange = LocalDateTime.of(2025, 12, 31, 23, 59);

        persistWalletTransactions(user, receiver, inRange, outsideRange);
        persistCardTransactions(user, inRange);
        persistExchangeTransactions(user, inRange);
        flushAndClear();
        markExchangeTransactionFailed();

        List<TransactionStatisticsRow> rows = repository.findMonthlyStatistics(
                user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 1)
        );
        Map<StatisticsKey, TransactionStatisticsRow> rowByKey = rows.stream()
                .collect(Collectors.toMap(StatisticsKey::from, Function.identity()));

        assertRow(rowByKey, TransactionStatisticsSourceType.WALLET, "DEPOSIT", "KRW",
                TransactionStatisticsDirection.INCOMING, "1000.0000", 1);
        assertRow(rowByKey, TransactionStatisticsSourceType.WALLET, "WITHDRAWAL", "USD",
                TransactionStatisticsDirection.OUTGOING, "20.0000", 1);
        assertRow(rowByKey, TransactionStatisticsSourceType.WALLET, "TRANSFER", "KRW",
                TransactionStatisticsDirection.OUTGOING, "3000.0000", 1);
        assertRow(rowByKey, TransactionStatisticsSourceType.WALLET, "TRANSFER", "USD",
                TransactionStatisticsDirection.INCOMING, "30.0000", 1);
        assertRow(rowByKey, TransactionStatisticsSourceType.CARD, "PAYMENT", "JPY",
                TransactionStatisticsDirection.OUTGOING, "500.0000", 1);
        assertRow(rowByKey, TransactionStatisticsSourceType.CARD, "REFUND", "JPY",
                TransactionStatisticsDirection.INCOMING, "100.0000", 1);
        assertRow(rowByKey, TransactionStatisticsSourceType.CARD, "DEPOSIT", "JPY",
                TransactionStatisticsDirection.INCOMING, "200.0000", 1);
        assertRow(rowByKey, TransactionStatisticsSourceType.EXCHANGE, "COMPLETED", "KRW",
                TransactionStatisticsDirection.OUTGOING, "7000.0000", 1);
        assertRow(rowByKey, TransactionStatisticsSourceType.EXCHANGE, "COMPLETED", "EUR",
                TransactionStatisticsDirection.INCOMING, "5.0000", 1);

        assertThat(rows).hasSize(9);
        assertThat(rows).noneMatch(row -> row.amountSum().compareTo(BigDecimal.valueOf(9999)) == 0);
        assertThat(rows).noneMatch(row -> row.amountSum().compareTo(BigDecimal.valueOf(8000)) == 0);
        assertThat(rows).noneMatch(row -> row.amountSum().compareTo(BigDecimal.valueOf(9000)) == 0);
    }

    @Test
    void materializedViewStatisticsMatchDirectGroupByAfterRefresh() {
        User user = persistUser("mv-user@example.com", "mv-user", "010-3000-3000");
        User receiver = persistUser("mv-receiver@example.com", "mv-receiver", "010-4000-4000");
        LocalDateTime inRange = LocalDateTime.of(2026, 1, 15, 12, 0);
        LocalDateTime outsideRange = LocalDateTime.of(2025, 12, 31, 23, 59);

        persistWalletTransactions(user, receiver, inRange, outsideRange);
        persistCardTransactions(user, inRange);
        persistExchangeTransactions(user, inRange);
        flushAndClear();
        markExchangeTransactionFailed();

        repository.refreshMaterializedView();

        List<TransactionStatisticsRow> groupByRows = repository.findMonthlyStatistics(
                user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 1)
        );
        List<TransactionStatisticsRow> materializedViewRows = repository.findMonthlyStatisticsFromMaterializedView(
                user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 1)
        );

        assertThat(materializedViewRows).containsExactlyElementsOf(groupByRows);
        assertThat(materializedViewRows).hasSize(9);
        assertThat(materializedViewRows)
                .anySatisfy(row -> assertThat(StatisticsKey.from(row)).isEqualTo(new StatisticsKey(
                        TransactionStatisticsSourceType.CARD,
                        "DEPOSIT",
                        "JPY",
                        TransactionStatisticsDirection.INCOMING
                )));
        assertThat(materializedViewRows).noneMatch(row -> row.amountSum().compareTo(BigDecimal.valueOf(8000)) == 0);
        assertThat(materializedViewRows).noneMatch(row -> row.amountSum().compareTo(BigDecimal.valueOf(9000)) == 0);
    }

    @Test
    void summaryStatisticsMatchDirectGroupByAndMaterializedViewAfterRefresh() {
        User user = persistUser("summary-user@example.com", "summaryuser", "010-7000-7000");
        User receiver = persistUser("summary-receiver@example.com", "summaryrecv", "010-8000-8000");
        LocalDateTime inRange = LocalDateTime.of(2026, 1, 15, 12, 0);
        LocalDateTime outsideRange = LocalDateTime.of(2025, 12, 31, 23, 59);

        persistWalletTransactions(user, receiver, inRange, outsideRange);
        persistCardTransactions(user, inRange);
        persistExchangeTransactions(user, inRange);
        flushAndClear();
        markExchangeTransactionFailed();

        repository.refreshMaterializedView();
        repository.refreshMonthlySummary(YearMonth.of(2026, 1), YearMonth.of(2026, 1));

        List<TransactionStatisticsRow> groupByRows = repository.findMonthlyStatistics(
                user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 1)
        );
        List<TransactionStatisticsRow> materializedViewRows = repository.findMonthlyStatisticsFromMaterializedView(
                user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 1)
        );
        List<TransactionStatisticsRow> summaryRows = repository.findMonthlyStatisticsFromSummary(
                user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 1)
        );

        assertThat(materializedViewRows).containsExactlyElementsOf(groupByRows);
        assertThat(toComparableRows(summaryRows)).containsExactlyElementsOf(toComparableRows(groupByRows));
        assertThat(summaryRows).allSatisfy(row -> assertThat(row.dataAsOf()).isNotNull());
        assertThat(summaryRows).hasSize(9);
        assertThat(summaryRows)
                .anySatisfy(row -> assertThat(StatisticsKey.from(row)).isEqualTo(new StatisticsKey(
                        TransactionStatisticsSourceType.CARD,
                        "DEPOSIT",
                        "JPY",
                        TransactionStatisticsDirection.INCOMING
                )));
        assertThat(summaryRows).noneMatch(row -> row.amountSum().compareTo(BigDecimal.valueOf(8000)) == 0);
        assertThat(summaryRows).noneMatch(row -> row.amountSum().compareTo(BigDecimal.valueOf(9000)) == 0);
    }

    @Test
    void materializedViewDoesNotReflectNewRowsUntilRefresh() {
        User user = persistUser("mv-refresh-user@example.com", "mvrefreshuser", "010-5000-5000");
        User receiver = persistUser("mv-refresh-receiver@example.com", "mvrefreshrecv", "010-6000-6000");
        LocalDateTime inRange = LocalDateTime.of(2026, 1, 15, 12, 0);
        LocalDateTime outsideRange = LocalDateTime.of(2025, 12, 31, 23, 59);

        persistWalletTransactions(user, receiver, inRange, outsideRange);
        persistCardTransactions(user, inRange);
        persistExchangeTransactions(user, inRange);
        flushAndClear();
        markExchangeTransactionFailed();
        repository.refreshMaterializedView();

        entityManager.persist(new CardTransaction(
                user, "BOOKSTORE", BigDecimal.valueOf(300), "JPY",
                BigDecimal.valueOf(3000), BigDecimal.valueOf(9800), "CARD-NEW",
                CardTransactionType.PAYMENT, inRange.plusMinutes(2)
        ));
        flushAndClear();

        List<TransactionStatisticsRow> groupByRows = repository.findMonthlyStatistics(
                user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 1)
        );
        List<TransactionStatisticsRow> staleMaterializedViewRows = repository.findMonthlyStatisticsFromMaterializedView(
                user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 1)
        );

        assertRow(toMap(groupByRows), TransactionStatisticsSourceType.CARD, "PAYMENT", "JPY",
                TransactionStatisticsDirection.OUTGOING, "800.0000", 2);
        assertRow(toMap(staleMaterializedViewRows), TransactionStatisticsSourceType.CARD, "PAYMENT", "JPY",
                TransactionStatisticsDirection.OUTGOING, "500.0000", 1);

        repository.refreshMaterializedView();

        List<TransactionStatisticsRow> refreshedMaterializedViewRows = repository.findMonthlyStatisticsFromMaterializedView(
                user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 1)
        );

        assertThat(refreshedMaterializedViewRows).containsExactlyElementsOf(groupByRows);
    }

    @Test
    void summaryDoesNotReflectNewRowsUntilRefresh() {
        User user = persistUser("summary-refresh-user@example.com", "summaryrefresh", "010-9000-9000");
        User receiver = persistUser("summary-refresh-receiver@example.com", "summaryrecv2", "010-9100-9100");
        LocalDateTime inRange = LocalDateTime.of(2026, 1, 15, 12, 0);
        LocalDateTime outsideRange = LocalDateTime.of(2025, 12, 31, 23, 59);

        persistWalletTransactions(user, receiver, inRange, outsideRange);
        persistCardTransactions(user, inRange);
        persistExchangeTransactions(user, inRange);
        flushAndClear();
        markExchangeTransactionFailed();
        repository.refreshMonthlySummary(YearMonth.of(2026, 1), YearMonth.of(2026, 1));

        entityManager.persist(new CardTransaction(
                user, "BOOKSTORE", BigDecimal.valueOf(300), "JPY",
                BigDecimal.valueOf(3000), BigDecimal.valueOf(9800), "CARD-SUMMARY-NEW",
                CardTransactionType.PAYMENT, inRange.plusMinutes(2)
        ));
        flushAndClear();

        List<TransactionStatisticsRow> groupByRows = repository.findMonthlyStatistics(
                user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 1)
        );
        List<TransactionStatisticsRow> staleSummaryRows = repository.findMonthlyStatisticsFromSummary(
                user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 1)
        );

        assertRow(toMap(groupByRows), TransactionStatisticsSourceType.CARD, "PAYMENT", "JPY",
                TransactionStatisticsDirection.OUTGOING, "800.0000", 2);
        assertRow(toMap(staleSummaryRows), TransactionStatisticsSourceType.CARD, "PAYMENT", "JPY",
                TransactionStatisticsDirection.OUTGOING, "500.0000", 1);

        repository.refreshMonthlySummary(YearMonth.of(2026, 1), YearMonth.of(2026, 1));

        List<TransactionStatisticsRow> refreshedSummaryRows = repository.findMonthlyStatisticsFromSummary(
                user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 1)
        );

        assertThat(toComparableRows(refreshedSummaryRows)).containsExactlyElementsOf(toComparableRows(groupByRows));
        assertThat(refreshedSummaryRows).allSatisfy(row -> assertThat(row.dataAsOf()).isNotNull());
    }

    @Test
    void allModesMatchForStatisticsAcrossMultipleMonthRange() {
        User user = persistUser("multi-month-user@example.com", "multimonth", "010-1100-1100");
        User receiver = persistUser("multi-month-receiver@example.com", "multirecv", "010-1200-1200");

        entityManager.persist(new WalletTransaction(
                user, null, BigDecimal.valueOf(1200), null, "KRW",
                WalletTransactionType.DEPOSIT, LocalDateTime.of(2025, 12, 31, 23, 59)
        ));
        entityManager.persist(new WalletTransaction(
                user, null, BigDecimal.valueOf(100), null, "KRW",
                WalletTransactionType.DEPOSIT, LocalDateTime.of(2026, 1, 10, 10, 0)
        ));
        entityManager.persist(new WalletTransaction(
                user, null, BigDecimal.valueOf(200), null, "KRW",
                WalletTransactionType.DEPOSIT, LocalDateTime.of(2026, 2, 10, 10, 0)
        ));
        entityManager.persist(new WalletTransaction(
                user, receiver, BigDecimal.valueOf(50), BigDecimal.valueOf(50),
                "USD", "USD", WalletTransactionType.TRANSFER, LocalDateTime.of(2026, 3, 1, 0, 0)
        ));
        entityManager.persist(new CardTransaction(
                user, "JAN-SHOP", BigDecimal.valueOf(10), "JPY",
                BigDecimal.valueOf(100), BigDecimal.valueOf(1000), "MULTI-JAN",
                CardTransactionType.PAYMENT, LocalDateTime.of(2026, 1, 20, 12, 0)
        ));
        entityManager.persist(new CardTransaction(
                user, "FEB-SHOP", BigDecimal.valueOf(20), "JPY",
                BigDecimal.valueOf(200), BigDecimal.valueOf(980), "MULTI-FEB",
                CardTransactionType.PAYMENT, LocalDateTime.of(2026, 2, 20, 12, 0)
        ));
        ExchangeTransaction januaryExchange = completedExchange(
                user, "KRW", "EUR", "7000", "5",
                LocalDateTime.of(2026, 1, 1, 9, 0),
                LocalDateTime.of(2026, 1, 30, 9, 0)
        );
        ExchangeTransaction februaryExchange = completedExchange(
                user, "KRW", "EUR", "14000", "10",
                LocalDateTime.of(2026, 2, 1, 9, 0),
                LocalDateTime.of(2026, 2, 28, 9, 0)
        );
        entityManager.persist(januaryExchange);
        entityManager.persist(februaryExchange);
        flushAndClear();

        assertAllModesMatch(user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 2));

        List<TransactionStatisticsRow> rows = repository.findMonthlyStatistics(
                user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 2)
        );

        assertThat(rows).extracting(TransactionStatisticsRow::bucketMonth)
                .contains(YearMonth.of(2026, 1), YearMonth.of(2026, 2))
                .doesNotContain(YearMonth.of(2025, 12), YearMonth.of(2026, 3));
        assertThat(rows).noneMatch(row -> row.amountSum().compareTo(BigDecimal.valueOf(1200)) == 0);
        assertThat(rows).noneMatch(row -> row.amountSum().compareTo(BigDecimal.valueOf(50)) == 0);
    }

    @Test
    void monthlyStatisticsIncludesRowsAtMonthStartAndBeforeNextMonth() {
        User user = persistUser("boundary-user@example.com", "boundaryuser", "010-1300-1300");

        entityManager.persist(new CardTransaction(
                user, "MONTH-START", BigDecimal.valueOf(100), "USD",
                BigDecimal.valueOf(1000), BigDecimal.valueOf(9000), "BOUNDARY-START",
                CardTransactionType.PAYMENT, LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        entityManager.persist(new CardTransaction(
                user, "MONTH-END", BigDecimal.valueOf(200), "USD",
                BigDecimal.valueOf(2000), BigDecimal.valueOf(7000), "BOUNDARY-END",
                CardTransactionType.PAYMENT, LocalDateTime.of(2026, 1, 31, 23, 59, 59)
        ));
        entityManager.persist(new CardTransaction(
                user, "NEXT-MONTH-START", BigDecimal.valueOf(300), "USD",
                BigDecimal.valueOf(3000), BigDecimal.valueOf(4000), "BOUNDARY-NEXT",
                CardTransactionType.PAYMENT, LocalDateTime.of(2026, 2, 1, 0, 0)
        ));
        flushAndClear();

        assertAllModesMatch(user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 1));

        Map<ComparableStatisticsKey, ComparableStatisticsRow> januaryRows = toComparableMap(
                repository.findMonthlyStatistics(user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 1))
        );
        assertComparableRow(januaryRows, YearMonth.of(2026, 1), TransactionStatisticsSourceType.CARD,
                "PAYMENT", "USD", TransactionStatisticsDirection.OUTGOING, "300.0000", 2);
        assertThat(januaryRows.keySet()).noneMatch(key -> key.bucketMonth().equals(YearMonth.of(2026, 2)));

        assertAllModesMatch(user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 2));

        Map<ComparableStatisticsKey, ComparableStatisticsRow> januaryToFebruaryRows = toComparableMap(
                repository.findMonthlyStatistics(user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 2))
        );
        assertComparableRow(januaryToFebruaryRows, YearMonth.of(2026, 2), TransactionStatisticsSourceType.CARD,
                "PAYMENT", "USD", TransactionStatisticsDirection.OUTGOING, "300.0000", 1);
    }

    @Test
    void monthlyStatisticsAggregatesWalletTransfersAcrossSenderAndReceiverRoles() {
        User user = persistUser("wallet-role-user@example.com", "walletrole", "010-1400-1400");
        User counterparty = persistUser("wallet-role-counter@example.com", "walletcounter", "010-1500-1500");
        LocalDateTime january = LocalDateTime.of(2026, 1, 15, 10, 0);

        entityManager.persist(new WalletTransaction(
                user, counterparty, BigDecimal.valueOf(100), BigDecimal.valueOf(1),
                "KRW", "USD", WalletTransactionType.TRANSFER, january
        ));
        entityManager.persist(new WalletTransaction(
                user, counterparty, BigDecimal.valueOf(200), BigDecimal.valueOf(2),
                "KRW", "USD", WalletTransactionType.TRANSFER, january.plusMinutes(1)
        ));
        entityManager.persist(new WalletTransaction(
                counterparty, user, BigDecimal.valueOf(300), BigDecimal.valueOf(3),
                "KRW", "USD", WalletTransactionType.TRANSFER, january.plusMinutes(2)
        ));
        entityManager.persist(new WalletTransaction(
                counterparty, user, BigDecimal.valueOf(400), BigDecimal.valueOf(4),
                "KRW", "USD", WalletTransactionType.TRANSFER, january.plusMinutes(3)
        ));
        entityManager.persist(new WalletTransaction(
                user, counterparty, BigDecimal.valueOf(50), BigDecimal.valueOf(50),
                "EUR", "EUR", WalletTransactionType.TRANSFER, january.plusMinutes(4)
        ));
        entityManager.persist(new WalletTransaction(
                counterparty, user, BigDecimal.valueOf(70), BigDecimal.valueOf(70),
                "EUR", "EUR", WalletTransactionType.TRANSFER, january.plusMinutes(5)
        ));
        flushAndClear();

        assertAllModesMatch(user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 1));

        Map<ComparableStatisticsKey, ComparableStatisticsRow> rows = toComparableMap(
                repository.findMonthlyStatistics(user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 1))
        );
        assertComparableRow(rows, YearMonth.of(2026, 1), TransactionStatisticsSourceType.WALLET,
                "TRANSFER", "KRW", TransactionStatisticsDirection.OUTGOING, "300.0000", 2);
        assertComparableRow(rows, YearMonth.of(2026, 1), TransactionStatisticsSourceType.WALLET,
                "TRANSFER", "USD", TransactionStatisticsDirection.INCOMING, "7.0000", 2);
        assertComparableRow(rows, YearMonth.of(2026, 1), TransactionStatisticsSourceType.WALLET,
                "TRANSFER", "EUR", TransactionStatisticsDirection.OUTGOING, "50.0000", 1);
        assertComparableRow(rows, YearMonth.of(2026, 1), TransactionStatisticsSourceType.WALLET,
                "TRANSFER", "EUR", TransactionStatisticsDirection.INCOMING, "70.0000", 1);
    }

    @Test
    void monthlyStatisticsSumsCardTransactionsWithSameKeyAndSeparatesCurrencyAndMonth() {
        User user = persistUser("card-aggregate-user@example.com", "cardaggregate", "010-1600-1600");

        entityManager.persist(new CardTransaction(
                user, "STORE-A", BigDecimal.valueOf(100), "JPY",
                BigDecimal.valueOf(1000), BigDecimal.valueOf(9000), "CARD-AGG-1",
                CardTransactionType.PAYMENT, LocalDateTime.of(2026, 1, 5, 10, 0)
        ));
        entityManager.persist(new CardTransaction(
                user, "STORE-B", BigDecimal.valueOf(200), "JPY",
                BigDecimal.valueOf(2000), BigDecimal.valueOf(7000), "CARD-AGG-2",
                CardTransactionType.PAYMENT, LocalDateTime.of(2026, 1, 5, 11, 0)
        ));
        entityManager.persist(new CardTransaction(
                user, "STORE-C", BigDecimal.valueOf(300), "USD",
                BigDecimal.valueOf(3000), BigDecimal.valueOf(4000), "CARD-AGG-3",
                CardTransactionType.PAYMENT, LocalDateTime.of(2026, 1, 5, 12, 0)
        ));
        entityManager.persist(new CardTransaction(
                user, "STORE-D", BigDecimal.valueOf(400), "JPY",
                BigDecimal.valueOf(4000), BigDecimal.valueOf(0), "CARD-AGG-4",
                CardTransactionType.PAYMENT, LocalDateTime.of(2026, 2, 5, 10, 0)
        ));
        entityManager.persist(new CardTransaction(
                user, "STORE-E", BigDecimal.valueOf(50), "JPY",
                BigDecimal.valueOf(500), BigDecimal.valueOf(50), "CARD-AGG-5",
                CardTransactionType.REFUND, LocalDateTime.of(2026, 1, 5, 13, 0)
        ));
        entityManager.persist(new CardTransaction(
                user, "STORE-F", BigDecimal.valueOf(60), "JPY",
                BigDecimal.valueOf(600), BigDecimal.valueOf(110), "CARD-AGG-6",
                CardTransactionType.DEPOSIT, LocalDateTime.of(2026, 1, 5, 14, 0)
        ));
        flushAndClear();

        assertAllModesMatch(user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 2));

        Map<ComparableStatisticsKey, ComparableStatisticsRow> rows = toComparableMap(
                repository.findMonthlyStatistics(user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 2))
        );
        assertComparableRow(rows, YearMonth.of(2026, 1), TransactionStatisticsSourceType.CARD,
                "PAYMENT", "JPY", TransactionStatisticsDirection.OUTGOING, "300.0000", 2);
        assertComparableRow(rows, YearMonth.of(2026, 1), TransactionStatisticsSourceType.CARD,
                "PAYMENT", "USD", TransactionStatisticsDirection.OUTGOING, "300.0000", 1);
        assertComparableRow(rows, YearMonth.of(2026, 2), TransactionStatisticsSourceType.CARD,
                "PAYMENT", "JPY", TransactionStatisticsDirection.OUTGOING, "400.0000", 1);
        assertComparableRow(rows, YearMonth.of(2026, 1), TransactionStatisticsSourceType.CARD,
                "REFUND", "JPY", TransactionStatisticsDirection.INCOMING, "50.0000", 1);
        assertComparableRow(rows, YearMonth.of(2026, 1), TransactionStatisticsSourceType.CARD,
                "DEPOSIT", "JPY", TransactionStatisticsDirection.INCOMING, "60.0000", 1);
    }

    @Test
    void monthlyStatisticsUsesCompletedAtForExchangeAndSumsCompletedRows() {
        User user = persistUser("exchange-aggregate-user@example.com", "exchangeagg", "010-1700-1700");

        entityManager.persist(completedExchange(
                user, "KRW", "EUR", "1000", "1",
                LocalDateTime.of(2025, 12, 31, 23, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        entityManager.persist(completedExchange(
                user, "KRW", "EUR", "2000", "2",
                LocalDateTime.of(2026, 1, 15, 10, 0),
                LocalDateTime.of(2026, 1, 31, 23, 59, 59)
        ));
        ExchangeTransaction completedWithoutCompletedAt = new ExchangeTransaction(
                user, "KRW", "EUR", BigDecimal.valueOf(3000),
                BigDecimal.valueOf(3), BigDecimal.valueOf(0.001), LocalDateTime.of(2026, 1, 20, 10, 0)
        );
        completedWithoutCompletedAt.complete(null);
        entityManager.persist(completedWithoutCompletedAt);
        entityManager.persist(new ExchangeTransaction(
                user, "KRW", "EUR", BigDecimal.valueOf(4000),
                BigDecimal.valueOf(4), BigDecimal.valueOf(0.001), LocalDateTime.of(2026, 1, 21, 10, 0)
        ));
        ExchangeTransaction failed = new ExchangeTransaction(
                user, "KRW", "EUR", BigDecimal.valueOf(5000),
                BigDecimal.valueOf(5), BigDecimal.valueOf(0.001), LocalDateTime.of(2026, 1, 22, 10, 0)
        );
        entityManager.persist(failed);
        flushAndClear();
        markExchangeTransactionFailed(BigDecimal.valueOf(5000), LocalDateTime.of(2026, 1, 22, 11, 0));

        assertAllModesMatch(user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 1));

        Map<ComparableStatisticsKey, ComparableStatisticsRow> rows = toComparableMap(
                repository.findMonthlyStatistics(user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 1))
        );
        assertComparableRow(rows, YearMonth.of(2026, 1), TransactionStatisticsSourceType.EXCHANGE,
                "COMPLETED", "KRW", TransactionStatisticsDirection.OUTGOING, "3000.0000", 2);
        assertComparableRow(rows, YearMonth.of(2026, 1), TransactionStatisticsSourceType.EXCHANGE,
                "COMPLETED", "EUR", TransactionStatisticsDirection.INCOMING, "3.0000", 2);
        assertThat(rows.values()).noneMatch(row -> row.amountSum().compareTo(BigDecimal.valueOf(3000)) == 0
                && row.transactionCount() == 1);
        assertThat(rows.values()).noneMatch(row -> row.amountSum().compareTo(BigDecimal.valueOf(4000)) == 0);
        assertThat(rows.values()).noneMatch(row -> row.amountSum().compareTo(BigDecimal.valueOf(5000)) == 0);
    }

    @Test
    void summaryRefreshOnlyRebuildsRequestedMonthRange() {
        User user = persistUser("summary-range-user@example.com", "summaryrange", "010-1800-1800");

        entityManager.persist(new CardTransaction(
                user, "JAN-INITIAL", BigDecimal.valueOf(100), "USD",
                BigDecimal.valueOf(1000), BigDecimal.valueOf(9000), "SUMMARY-RANGE-JAN-1",
                CardTransactionType.PAYMENT, LocalDateTime.of(2026, 1, 10, 10, 0)
        ));
        entityManager.persist(new CardTransaction(
                user, "FEB-INITIAL", BigDecimal.valueOf(200), "USD",
                BigDecimal.valueOf(2000), BigDecimal.valueOf(7000), "SUMMARY-RANGE-FEB-1",
                CardTransactionType.PAYMENT, LocalDateTime.of(2026, 2, 10, 10, 0)
        ));
        flushAndClear();
        repository.refreshMonthlySummary(YearMonth.of(2026, 1), YearMonth.of(2026, 2));

        entityManager.persist(new CardTransaction(
                user, "JAN-NEW", BigDecimal.valueOf(300), "USD",
                BigDecimal.valueOf(3000), BigDecimal.valueOf(4000), "SUMMARY-RANGE-JAN-2",
                CardTransactionType.PAYMENT, LocalDateTime.of(2026, 1, 10, 11, 0)
        ));
        entityManager.persist(new CardTransaction(
                user, "FEB-NEW", BigDecimal.valueOf(400), "USD",
                BigDecimal.valueOf(4000), BigDecimal.valueOf(0), "SUMMARY-RANGE-FEB-2",
                CardTransactionType.PAYMENT, LocalDateTime.of(2026, 2, 10, 11, 0)
        ));
        flushAndClear();

        repository.refreshMonthlySummary(YearMonth.of(2026, 1), YearMonth.of(2026, 1));

        Map<ComparableStatisticsKey, ComparableStatisticsRow> groupByRows = toComparableMap(
                repository.findMonthlyStatistics(user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 2))
        );
        Map<ComparableStatisticsKey, ComparableStatisticsRow> summaryRows = toComparableMap(
                repository.findMonthlyStatisticsFromSummary(user.getUserId(), YearMonth.of(2026, 1), YearMonth.of(2026, 2))
        );

        assertComparableRow(groupByRows, YearMonth.of(2026, 1), TransactionStatisticsSourceType.CARD,
                "PAYMENT", "USD", TransactionStatisticsDirection.OUTGOING, "400.0000", 2);
        assertComparableRow(summaryRows, YearMonth.of(2026, 1), TransactionStatisticsSourceType.CARD,
                "PAYMENT", "USD", TransactionStatisticsDirection.OUTGOING, "400.0000", 2);
        assertComparableRow(groupByRows, YearMonth.of(2026, 2), TransactionStatisticsSourceType.CARD,
                "PAYMENT", "USD", TransactionStatisticsDirection.OUTGOING, "600.0000", 2);
        assertComparableRow(summaryRows, YearMonth.of(2026, 2), TransactionStatisticsSourceType.CARD,
                "PAYMENT", "USD", TransactionStatisticsDirection.OUTGOING, "200.0000", 1);
    }

    private void persistWalletTransactions(User user, User receiver, LocalDateTime inRange, LocalDateTime outsideRange) {
        entityManager.persist(new WalletTransaction(
                user, null, BigDecimal.valueOf(1000), null, "KRW", WalletTransactionType.DEPOSIT, inRange
        ));
        entityManager.persist(new WalletTransaction(
                user, null, BigDecimal.valueOf(20), null, "USD", WalletTransactionType.WITHDRAWAL, inRange
        ));
        entityManager.persist(new WalletTransaction(
                user, receiver, BigDecimal.valueOf(3000), BigDecimal.valueOf(2),
                "KRW", "USD", WalletTransactionType.TRANSFER, inRange
        ));
        entityManager.persist(new WalletTransaction(
                receiver, user, BigDecimal.valueOf(4000), BigDecimal.valueOf(30),
                "KRW", "USD", WalletTransactionType.TRANSFER, inRange.plusMinutes(1)
        ));
        entityManager.persist(new WalletTransaction(
                user, null, BigDecimal.valueOf(9999), null, "KRW", WalletTransactionType.DEPOSIT, outsideRange
        ));
    }

    private void persistCardTransactions(User user, LocalDateTime inRange) {
        entityManager.persist(new CardTransaction(
                user, "CAFE", BigDecimal.valueOf(500), "JPY",
                BigDecimal.valueOf(5000), BigDecimal.valueOf(10000), "CARD-0001",
                CardTransactionType.PAYMENT, inRange
        ));
        entityManager.persist(new CardTransaction(
                user, "CAFE", BigDecimal.valueOf(100), "JPY",
                BigDecimal.valueOf(1000), BigDecimal.valueOf(10100), "CARD-0002",
                CardTransactionType.REFUND, inRange.plusMinutes(1)
        ));
        entityManager.persist(new CardTransaction(
                user, "ATM", BigDecimal.valueOf(200), "JPY",
                BigDecimal.valueOf(2000), BigDecimal.valueOf(10300), "CARD-0003",
                CardTransactionType.DEPOSIT, inRange.plusMinutes(2)
        ));
    }

    private void persistExchangeTransactions(User user, LocalDateTime inRange) {
        ExchangeTransaction completed = new ExchangeTransaction(
                user, "KRW", "EUR", BigDecimal.valueOf(7000),
                BigDecimal.valueOf(5), BigDecimal.valueOf(0.0007), inRange.minusMinutes(5)
        );
        completed.complete(inRange);
        entityManager.persist(completed);

        ExchangeTransaction pending = new ExchangeTransaction(
                user, "KRW", "EUR", BigDecimal.valueOf(8000),
                BigDecimal.valueOf(6), BigDecimal.valueOf(0.0007), inRange.minusMinutes(4)
        );
        entityManager.persist(pending);

        ExchangeTransaction failed = new ExchangeTransaction(
                user, "KRW", "EUR", BigDecimal.valueOf(9000),
                BigDecimal.valueOf(7), BigDecimal.valueOf(0.0007), inRange.minusMinutes(3)
        );
        entityManager.persist(failed);
    }

    private void markExchangeTransactionFailed() {
        markExchangeTransactionFailed(BigDecimal.valueOf(9000), LocalDateTime.of(2026, 1, 15, 12, 10));
    }

    private void markExchangeTransactionFailed(BigDecimal amount, LocalDateTime completedAt) {
        jdbcTemplate.update("""
                update exchange_transaction
                set status = 'FAILED',
                    completed_at = ?
                where amount = ?
                """, completedAt, amount);
    }

    private ExchangeTransaction completedExchange(User user, String fromCurrency, String toCurrency,
                                                  String amount, String receivedAmount,
                                                  LocalDateTime createdAt, LocalDateTime completedAt) {
        ExchangeTransaction exchangeTransaction = new ExchangeTransaction(
                user,
                fromCurrency,
                toCurrency,
                new BigDecimal(amount),
                new BigDecimal(receivedAmount),
                BigDecimal.valueOf(0.001),
                createdAt
        );
        exchangeTransaction.complete(completedAt);
        return exchangeTransaction;
    }

    private void assertRow(Map<StatisticsKey, TransactionStatisticsRow> rows,
                           TransactionStatisticsSourceType sourceType,
                           String transactionType,
                           String currency,
                           TransactionStatisticsDirection direction,
                           String amount,
                           long count) {
        TransactionStatisticsRow row = rows.get(new StatisticsKey(sourceType, transactionType, currency, direction));
        assertThat(row).isNotNull();
        assertThat(row.amountSum()).isEqualByComparingTo(amount);
        assertThat(row.transactionCount()).isEqualTo(count);
        assertThat(row.bucketMonth()).isEqualTo(YearMonth.of(2026, 1));
    }

    private Map<StatisticsKey, TransactionStatisticsRow> toMap(List<TransactionStatisticsRow> rows) {
        return rows.stream().collect(Collectors.toMap(StatisticsKey::from, Function.identity()));
    }

    private List<ComparableStatisticsRow> toComparableRows(List<TransactionStatisticsRow> rows) {
        return rows.stream()
                .map(ComparableStatisticsRow::from)
                .toList();
    }

    private Map<ComparableStatisticsKey, ComparableStatisticsRow> toComparableMap(
            List<TransactionStatisticsRow> rows
    ) {
        return rows.stream()
                .map(ComparableStatisticsRow::from)
                .collect(Collectors.toMap(ComparableStatisticsKey::from, Function.identity()));
    }

    private void assertAllModesMatch(Long userId, YearMonth fromMonth, YearMonth toMonth) {
        repository.refreshMaterializedView();
        repository.refreshMonthlySummary(fromMonth, toMonth);

        List<ComparableStatisticsRow> groupByRows = toComparableRows(
                repository.findMonthlyStatistics(userId, fromMonth, toMonth)
        );
        List<ComparableStatisticsRow> materializedViewRows = toComparableRows(
                repository.findMonthlyStatisticsFromMaterializedView(userId, fromMonth, toMonth)
        );
        List<ComparableStatisticsRow> summaryRows = toComparableRows(
                repository.findMonthlyStatisticsFromSummary(userId, fromMonth, toMonth)
        );

        assertThat(materializedViewRows).containsExactlyElementsOf(groupByRows);
        assertThat(summaryRows).containsExactlyElementsOf(groupByRows);
    }

    private void assertComparableRow(Map<ComparableStatisticsKey, ComparableStatisticsRow> rows,
                                     YearMonth bucketMonth,
                                     TransactionStatisticsSourceType sourceType,
                                     String transactionType,
                                     String currency,
                                     TransactionStatisticsDirection direction,
                                     String amount,
                                     long count) {
        ComparableStatisticsRow row = rows.get(new ComparableStatisticsKey(
                bucketMonth, sourceType, transactionType, currency, direction
        ));
        assertThat(row).isNotNull();
        assertThat(row.amountSum()).isEqualByComparingTo(amount);
        assertThat(row.transactionCount()).isEqualTo(count);
    }

    private void executeSqlResource(String location) throws Exception {
        ClassPathResource resource = new ClassPathResource(location);
        String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        jdbcTemplate.execute(sql);
    }

    private User persistUser(String email, String nickname, String phoneNumber) {
        User user = User.builder()
                .userEmail(email)
                .userPwd("Aa1!aaaa")
                .userName("홍길동")
                .userNickname(nickname)
                .userPhoneNumber(phoneNumber)
                .userSex(Sex.MALE)
                .passwordEncoder(NoOpPasswordEncoder.getInstance())
                .build();
        entityManager.persist(user);
        return user;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private record StatisticsKey(
            TransactionStatisticsSourceType sourceType,
            String transactionType,
            String currency,
            TransactionStatisticsDirection direction
    ) {
        private static StatisticsKey from(TransactionStatisticsRow row) {
            return new StatisticsKey(row.sourceType(), row.transactionType(), row.currency(), row.direction());
        }
    }

    private record ComparableStatisticsRow(
            Long userId,
            YearMonth bucketMonth,
            TransactionStatisticsSourceType sourceType,
            String transactionType,
            String currency,
            TransactionStatisticsDirection direction,
            BigDecimal amountSum,
            long transactionCount
    ) {
        private static ComparableStatisticsRow from(TransactionStatisticsRow row) {
            return new ComparableStatisticsRow(
                    row.userId(),
                    row.bucketMonth(),
                    row.sourceType(),
                    row.transactionType(),
                    row.currency(),
                    row.direction(),
                    row.amountSum(),
                    row.transactionCount()
            );
        }
    }

    private record ComparableStatisticsKey(
            YearMonth bucketMonth,
            TransactionStatisticsSourceType sourceType,
            String transactionType,
            String currency,
            TransactionStatisticsDirection direction
    ) {
        private static ComparableStatisticsKey from(ComparableStatisticsRow row) {
            return new ComparableStatisticsKey(
                    row.bucketMonth(),
                    row.sourceType(),
                    row.transactionType(),
                    row.currency(),
                    row.direction()
            );
        }
    }
}
