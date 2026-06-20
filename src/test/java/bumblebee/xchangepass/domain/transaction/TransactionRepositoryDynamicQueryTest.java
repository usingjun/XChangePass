package bumblebee.xchangepass.domain.transaction;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransaction;
import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import bumblebee.xchangepass.domain.cardTransaction.repository.CardTransactionRepository;
import bumblebee.xchangepass.domain.exchangeTransaction.entitiy.ExchangeTransaction;
import bumblebee.xchangepass.domain.exchangeTransaction.repository.ExchangeTransactionRepository;
import bumblebee.xchangepass.domain.transaction.repository.CardTransactionRow;
import bumblebee.xchangepass.domain.transaction.repository.ExchangeTransactionRow;
import bumblebee.xchangepass.domain.transaction.repository.WalletTransactionRow;
import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.global.config.QueryDSLConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QueryDSLConfig.class)
class TransactionRepositoryDynamicQueryTest {

    @Autowired
    private CardTransactionRepository cardTransactionRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private ExchangeTransactionRepository exchangeTransactionRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void cardSearchAppliesOnlyProvidedFilters() {
        User user = persistUser("card-user@example.com", "card1", "010-1111-1111");
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);
        entityManager.persist(new CardTransaction(
                user, "STARBUCKS", BigDecimal.valueOf(12_000), "USD",
                BigDecimal.valueOf(16_000), BigDecimal.valueOf(100_000), "A0001",
                CardTransactionType.PAYMENT, now
        ));
        entityManager.persist(new CardTransaction(
                user, "LOCAL MART", BigDecimal.valueOf(12_000), "USD",
                BigDecimal.valueOf(16_000), BigDecimal.valueOf(84_000), "A0002",
                CardTransactionType.PAYMENT, now.minusMinutes(1)
        ));
        flushAndClear();

        List<CardTransactionRow> rows = cardTransactionRepository.findRecentForUser(
                user.getUserId(),
                CardTransactionType.PAYMENT,
                "STAR",
                BigDecimal.valueOf(10_000),
                BigDecimal.valueOf(20_000),
                "USD",
                null,
                null,
                null,
                false,
                null,
                PageRequest.of(0, 30)
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getMerchantName()).isEqualTo("STARBUCKS");
    }

    @Test
    void walletSearchSeparatesSentAndReceivedFilters() {
        User sender = persistUser("sender@example.com", "send1", "010-2222-2222");
        User receiver = persistUser("receiver@example.com", "recv1", "010-3333-3333");
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);
        entityManager.persist(new WalletTransaction(
                sender, receiver, BigDecimal.valueOf(30_000), BigDecimal.valueOf(20), "KRW", "USD",
                WalletTransactionType.TRANSFER, now
        ));
        entityManager.persist(new WalletTransaction(
                receiver, sender, BigDecimal.valueOf(5_000), "KRW", "USD",
                WalletTransactionType.TRANSFER, now.minusMinutes(1)
        ));
        flushAndClear();

        List<WalletTransactionRow> received = walletTransactionRepository.findRecentReceivedByUser(
                receiver.getUserId(),
                WalletTransactionType.TRANSFER,
                BigDecimal.TEN,
                BigDecimal.valueOf(40),
                "USD",
                null,
                null,
                null,
                false,
                null,
                PageRequest.of(0, 30)
        );
        List<WalletTransactionRow> sent = walletTransactionRepository.findRecentSentByUser(
                receiver.getUserId(),
                WalletTransactionType.TRANSFER,
                BigDecimal.valueOf(10_000),
                BigDecimal.valueOf(40_000),
                "USD",
                null,
                null,
                null,
                false,
                null,
                PageRequest.of(0, 30)
        );

        assertThat(received).hasSize(1);
        assertThat(received.get(0).getReceivedAmount()).isEqualByComparingTo("20");
        assertThat(sent).isEmpty();
    }

    @Test
    void exchangeSearchAppliesCompletedStatusAmountCurrencyAndPeriod() {
        User user = persistUser("exchange-user@example.com", "exch1", "010-4444-4444");
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);
        ExchangeTransaction completed = new ExchangeTransaction(
                user, "KRW", "USD", BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(35), BigDecimal.valueOf(0.0007), now.minusMinutes(5)
        );
        completed.complete(now);
        ExchangeTransaction pending = new ExchangeTransaction(
                user, "KRW", "USD", BigDecimal.valueOf(60_000),
                BigDecimal.valueOf(42), BigDecimal.valueOf(0.0007), now.minusMinutes(4)
        );
        entityManager.persist(completed);
        entityManager.persist(pending);
        flushAndClear();

        List<ExchangeTransactionRow> rows = exchangeTransactionRepository.findRecentForUser(
                user.getUserId(),
                BigDecimal.valueOf(10_000),
                BigDecimal.valueOf(100_000),
                "USD",
                now.minusHours(1),
                now.plusHours(1),
                null,
                false,
                null,
                PageRequest.of(0, 30)
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAmount()).isEqualByComparingTo("50000");
    }

    private User persistUser(String email, String nickname, String phoneNumber) {
        User user = User.builder()
                .userEmail(email)
                .userPwd("Aa1!aaaa")
                .userName(nickname)
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
}
