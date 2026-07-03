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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
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
        assertRow(rowByKey, TransactionStatisticsSourceType.EXCHANGE, "COMPLETED", "KRW",
                TransactionStatisticsDirection.OUTGOING, "7000.0000", 1);
        assertRow(rowByKey, TransactionStatisticsSourceType.EXCHANGE, "COMPLETED", "EUR",
                TransactionStatisticsDirection.INCOMING, "5.0000", 1);

        assertThat(rows).hasSize(8);
        assertThat(rows).noneMatch(row -> row.amountSum().compareTo(BigDecimal.valueOf(9999)) == 0);
        assertThat(rows).noneMatch(row -> row.amountSum().compareTo(BigDecimal.valueOf(8000)) == 0);
        assertThat(rows).noneMatch(row -> row.amountSum().compareTo(BigDecimal.valueOf(9000)) == 0);
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
        jdbcTemplate.update("""
                update exchange_transaction
                set status = 'FAILED',
                    completed_at = ?
                where amount = ?
                """, LocalDateTime.of(2026, 1, 15, 12, 10), BigDecimal.valueOf(9000));
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
}
