package bumblebee.xchangepass.domain.wallet.reconciliation;

import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.wallet.reconciliation.dto.WalletLedgerAggregationResult;
import bumblebee.xchangepass.domain.wallet.reconciliation.repository.WalletLedgerAggregationRepository;
import bumblebee.xchangepass.domain.wallet.reconciliation.repository.WalletLedgerAggregationRepositoryImpl;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.global.config.QueryDSLConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({WalletLedgerAggregationRepositoryImpl.class, QueryDSLConfig.class})
class WalletLedgerAggregationRepositoryTest {

    @Autowired
    private WalletLedgerAggregationRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void depositAddsAmountByUserAndCurrency() {
        User user = persistUser("deposit");
        persistLedger(user, null, "1000.00", null, null, "KRW", WalletTransactionType.DEPOSIT);
        persistLedger(user, null, "500.00", null, null, "KRW", WalletTransactionType.DEPOSIT);
        flushAndClear();

        WalletLedgerAggregationResult result = repository.aggregate();

        assertAmount(result, user.getUserId(), "KRW", "1500.00");
        assertThat(result.uncalculableLedgerCount()).isZero();
    }

    @Test
    void withdrawalSubtractsAmountByUserAndCurrency() {
        User user = persistUser("withdrawal");
        persistLedger(user, null, "1000.00", null, null, "KRW", WalletTransactionType.DEPOSIT);
        persistLedger(user, null, "300.00", null, null, "KRW", WalletTransactionType.WITHDRAWAL);
        flushAndClear();

        WalletLedgerAggregationResult result = repository.aggregate();

        assertAmount(result, user.getUserId(), "KRW", "700.00");
    }

    @Test
    void transferSubtractsSenderAndCreditsReceiverReceivedAmount() {
        User sender = persistUser("transfer-sender");
        User receiver = persistUser("transfer-receiver");
        persistLedger(sender, receiver, "1000.00", "7.50", "KRW", "USD", WalletTransactionType.TRANSFER);
        flushAndClear();

        WalletLedgerAggregationResult result = repository.aggregate();

        assertAmount(result, sender.getUserId(), "KRW", "-1000.00");
        assertAmount(result, receiver.getUserId(), "USD", "7.50");
        assertThat(result.uncalculableLedgerCount()).isZero();
    }

    @Test
    void sameCurrencyTransferFallsBackToAmountWhenReceivedAmountIsNull() {
        User sender = persistUser("same-currency-sender");
        User receiver = persistUser("same-currency-receiver");
        persistLedger(sender, receiver, "1000.00", null, "KRW", "KRW", WalletTransactionType.TRANSFER);
        flushAndClear();

        WalletLedgerAggregationResult result = repository.aggregate();

        assertAmount(result, sender.getUserId(), "KRW", "-1000.00");
        assertAmount(result, receiver.getUserId(), "KRW", "1000.00");
        assertThat(result.uncalculableLedgerCount()).isZero();
    }

    @Test
    void foreignCurrencyTransferWithoutReceivedAmountSkipsReceiverAndCountsUncalculableLedger() {
        User sender = persistUser("foreign-sender");
        User receiver = persistUser("foreign-receiver");
        persistLedger(sender, receiver, "1000.00", null, "KRW", "USD", WalletTransactionType.TRANSFER);
        flushAndClear();

        WalletLedgerAggregationResult result = repository.aggregate();
        Map<LedgerKey, BigDecimal> aggregates = aggregateMap(result);

        assertAmount(result, sender.getUserId(), "KRW", "-1000.00");
        assertThat(aggregates).doesNotContainKey(new LedgerKey(receiver.getUserId(), "USD"));
        assertThat(result.uncalculableLedgerCount()).isEqualTo(1);
    }

    private User persistUser(String suffix) {
        int token = Math.abs(suffix.hashCode() % 900000) + 100000;
        User user = User.builder()
                .userEmail(suffix + "@example.com")
                .userPwd("Aa1!aaaa")
                .userName("u" + (token % 10000))
                .userNickname("nick" + token)
                .userPhoneNumber("010-" + (token % 9000 + 1000) + "-1234")
                .userSex(Sex.MALE)
                .passwordEncoder(NoOpPasswordEncoder.getInstance())
                .build();
        entityManager.persist(user);
        return user;
    }

    private void persistLedger(User user, User counterpartyUser, String amount, String receivedAmount,
                               String fromCurrency, String toCurrency, WalletTransactionType type) {
        entityManager.persist(new WalletTransaction(
                user,
                counterpartyUser,
                amount(amount),
                receivedAmount == null ? null : amount(receivedAmount),
                fromCurrency,
                toCurrency,
                type,
                LocalDateTime.now()
        ));
    }

    private Map<LedgerKey, BigDecimal> aggregateMap(WalletLedgerAggregationResult result) {
        return result.balances().stream()
                .collect(Collectors.toMap(
                        aggregate -> new LedgerKey(aggregate.userId(), aggregate.currency()),
                        aggregate -> aggregate.ledgerCalculatedAmount()
                ));
    }

    private void assertAmount(WalletLedgerAggregationResult result, Long userId, String currency, String expected) {
        assertThat(aggregateMap(result).get(new LedgerKey(userId, currency))).isEqualByComparingTo(expected);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private record LedgerKey(Long userId, String currency) {
    }
}
