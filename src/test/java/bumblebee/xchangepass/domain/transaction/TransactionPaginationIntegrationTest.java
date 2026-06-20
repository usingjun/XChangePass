package bumblebee.xchangepass.domain.transaction;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransaction;
import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import bumblebee.xchangepass.domain.cardTransaction.repository.CardTransactionRepository;
import bumblebee.xchangepass.domain.exchangeTransaction.entitiy.ExchangeTransaction;
import bumblebee.xchangepass.domain.exchangeTransaction.repository.ExchangeTransactionRepository;
import bumblebee.xchangepass.domain.transaction.dto.cond.TransactionSearchCondition;
import bumblebee.xchangepass.domain.transaction.dto.response.CardTransactionDto;
import bumblebee.xchangepass.domain.transaction.dto.response.ExchangeTransactionDto;
import bumblebee.xchangepass.domain.transaction.dto.response.TransactionPageResponse;
import bumblebee.xchangepass.domain.transaction.dto.response.WalletTransactionDto;
import bumblebee.xchangepass.domain.transaction.service.TransactionCursorCodec;
import bumblebee.xchangepass.domain.transaction.service.TransactionService;
import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.global.config.QueryDSLConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(QueryDSLConfig.class)
class TransactionPaginationIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("xcp_transaction_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Autowired
    private WalletTransactionRepository walletRepository;
    @Autowired
    private CardTransactionRepository cardRepository;
    @Autowired
    private ExchangeTransactionRepository exchangeRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void sameTimeTransactionsContinueAcrossPagesWithoutDuplicatesOrGaps() {
        User user = persistUser("history-user@example.com", "user1", "010-1111-1111");
        User receiver = persistUser("history-receiver@example.com", "user2", "010-2222-2222");
        LocalDateTime sameTime = LocalDateTime.of(2026, 6, 20, 12, 0);

        entityManager.persist(new WalletTransaction(
                user, receiver, BigDecimal.valueOf(10_000), BigDecimal.valueOf(7),
                "KRW", "USD", WalletTransactionType.TRANSFER, sameTime
        ));
        entityManager.persist(new CardTransaction(
                user, "STORE", BigDecimal.valueOf(5_000), "KRW",
                BigDecimal.valueOf(5_000), BigDecimal.valueOf(95_000), "APPROVAL-1",
                CardTransactionType.PAYMENT, sameTime
        ));
        ExchangeTransaction exchange = new ExchangeTransaction(
                user, "KRW", "USD", BigDecimal.valueOf(20_000),
                BigDecimal.valueOf(14), BigDecimal.valueOf(0.0007), sameTime.minusMinutes(1)
        );
        exchange.complete(sameTime);
        entityManager.persist(exchange);
        entityManager.flush();
        entityManager.clear();

        TransactionService service = new TransactionService(
                walletRepository,
                cardRepository,
                exchangeRepository,
                new TransactionCursorCodec(new ObjectMapper())
        );

        TransactionPageResponse first = service.getTransactions(user.getUserId(), condition(null), 2);
        TransactionPageResponse second = service.getTransactions(
                user.getUserId(), condition(first.nextCursor()), 2
        );

        assertThat(first.items()).hasSize(2);
        assertThat(first.items().get(0).getData()).isInstanceOf(WalletTransactionDto.class);
        assertThat(first.items().get(1).getData()).isInstanceOf(CardTransactionDto.class);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.nextCursor()).isNotBlank();

        assertThat(second.items()).hasSize(1);
        assertThat(second.items().get(0).getData()).isInstanceOf(ExchangeTransactionDto.class);
        assertThat(second.hasNext()).isFalse();
        assertThat(second.nextCursor()).isNull();

        List<Class<?>> returnedTypes = List.of(
                first.items().get(0).getData().getClass(),
                first.items().get(1).getData().getClass(),
                second.items().get(0).getData().getClass()
        );
        assertThat(returnedTypes).containsExactly(
                WalletTransactionDto.class,
                CardTransactionDto.class,
                ExchangeTransactionDto.class
        );
    }

    @Test
    void sameSourceAndTimeContinueByDescendingTransactionId() {
        User user = persistUser("card-pages@example.com", "pages", "010-3333-3333");
        LocalDateTime sameTime = LocalDateTime.of(2026, 6, 20, 12, 0);
        for (int i = 1; i <= 5; i++) {
            entityManager.persist(new CardTransaction(
                    user, "STORE-" + i, BigDecimal.valueOf(i * 1_000L), "KRW",
                    BigDecimal.valueOf(i * 1_000L), BigDecimal.valueOf(100_000), "APPROVAL-" + i,
                    CardTransactionType.PAYMENT, sameTime
            ));
        }
        entityManager.flush();
        entityManager.clear();

        TransactionService service = new TransactionService(
                walletRepository,
                cardRepository,
                exchangeRepository,
                new TransactionCursorCodec(new ObjectMapper())
        );

        TransactionPageResponse first = service.getTransactions(user.getUserId(), condition(null), 2);
        TransactionPageResponse middle = service.getTransactions(user.getUserId(), condition(first.nextCursor()), 2);
        TransactionPageResponse last = service.getTransactions(user.getUserId(), condition(middle.nextCursor()), 2);

        assertThat(merchants(first)).containsExactly("STORE-5", "STORE-4");
        assertThat(merchants(middle)).containsExactly("STORE-3", "STORE-2");
        assertThat(merchants(last)).containsExactly("STORE-1");
        assertThat(first.hasNext()).isTrue();
        assertThat(middle.hasNext()).isTrue();
        assertThat(last.hasNext()).isFalse();
        assertThat(last.nextCursor()).isNull();
    }

    private List<String> merchants(TransactionPageResponse response) {
        return response.items().stream()
                .map(item -> (CardTransactionDto) item.getData())
                .map(CardTransactionDto::merchant)
                .toList();
    }

    private TransactionSearchCondition condition(String cursor) {
        return new TransactionSearchCondition(
                null, null, null, null, null, null, null, null, null, null, cursor
        );
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
}
