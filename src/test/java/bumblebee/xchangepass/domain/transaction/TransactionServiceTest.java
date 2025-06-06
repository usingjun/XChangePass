package bumblebee.xchangepass.domain.transaction;

import bumblebee.xchangepass.config.TestUserInitializer;
import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransaction;
import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import bumblebee.xchangepass.domain.cardTransaction.repository.CardTransactionRepository;
import bumblebee.xchangepass.domain.exchangeTransaction.entitiy.ExchangeTransaction;
import bumblebee.xchangepass.domain.exchangeTransaction.entitiy.TransactionStatus;
import bumblebee.xchangepass.domain.exchangeTransaction.repository.ExchangeTransactionRepository;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionSearchCondition;
import bumblebee.xchangepass.domain.transaction.rdbmsV.entity.TransactionType;
import bumblebee.xchangepass.domain.transaction.rdbmsV.service.TransactionService;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionStatus;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Import(TestUserInitializer.class)
public class TransactionServiceTest {

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("xcp_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @Container
    static GenericContainer<?> rabbitMqContainer = new GenericContainer<>("rabbitmq:3-management")
            .withExposedPorts(5672, 15672)
            .withEnv("RABBITMQ_DEFAULT_USER", "guest")
            .withEnv("RABBITMQ_DEFAULT_PASS", "guest");
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WalletTransactionRepository walletTxRepo;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private CardTransactionRepository cardTxRepo;
    @Autowired
    private ExchangeTransactionRepository exchangeTxRepo;
    private User sender;
    private User receiver;
    static final int size = 20;

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMqContainer::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbitMqContainer.getMappedPort(5672));
    }

    @BeforeEach
    void setUp() {
        cardTxRepo.deleteAll();
        walletTxRepo.deleteAll();
        exchangeTxRepo.deleteAll();

        sender = userRepository.findByUserEmail("Test1@gmail.com").orElseThrow();
        receiver = userRepository.findByUserEmail("Test2@gmail.com").orElseThrow();

        LocalDateTime baseTime = LocalDateTime.of(2025, 5, 1, 10, 0);
        Currency krw = Currency.getInstance("KRW");
        Currency usd = Currency.getInstance("USD");
        Random rand = new Random();

        // 지갑 거래
        for (int i = 1; i <= 3; i++) {
            WalletTransaction tx = new WalletTransaction(
                    sender, receiver,
                    BigDecimal.valueOf(1000 + i * 100),
                    usd, krw,
                    WalletTransactionType.TRANSFER
            );
            tx.updateStatus(WalletTransactionStatus.SUCCESS);
            tx.setUpdatedAt(baseTime.minusSeconds(i));
            walletTxRepo.save(tx);
        }

        // 카드 거래
        for (int i = 1; i <= 3; i++) {
            CardTransaction tx = CardTransaction.builder()
                    .user(sender)
                    .merchantName("테스트상점" + i)
                    .approvedAmount(BigDecimal.valueOf(rand.nextInt(10000) + 1000))
                    .approvedCurrency(krw)
                    .krwAmount(BigDecimal.valueOf(rand.nextInt(10000) + 1000))
                    .transactionTime(baseTime.minusSeconds(i))
                    .approvalNumber("APPROVAL-" + i)
                    .balanceAfter(BigDecimal.valueOf(500000 - i * 1000L))
                    .cardTransactionType(CardTransactionType.PAYMENT)
                    .build();
            cardTxRepo.save(tx);
        }

        // 환전 거래
        for (int i = 1; i <= 3; i++) {
            ExchangeTransaction tx = ExchangeTransaction.builder()
                    .user(sender)
                    .fromCurrency("USD")
                    .toCurrency("KRW")
                    .exchangeRate(BigDecimal.valueOf(1320 + i))
                    .amount(BigDecimal.valueOf(100 + i))
                    .receivedAmount(BigDecimal.valueOf((100 + i) * (1320 + i)))
                    .exchangeDate(baseTime.minusSeconds(i))
                    .status(TransactionStatus.COMPLETED)
                    .build();
            exchangeTxRepo.save(tx);
        }
    }

    @Test
    @DisplayName("🔍 거래내역 조회")
    void getTransactionTest() {
        Long userId = sender.getUserId();
        TransactionSearchCondition cond = new TransactionSearchCondition(
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now()
        );

        List<TransactionResponse> transactions = transactionService.getTransaction(userId, cond, size);

        for (TransactionResponse res : transactions) {
            System.out.println(res);
        }

        assertEquals(9, transactions.size());
    }


    @Test
    @DisplayName("🔍 거래내역 필터링 조회")
    void filterTransactionTest() {
        Long userId = sender.getUserId();
        TransactionSearchCondition cond = new TransactionSearchCondition(
                TransactionType.CARD,
                CardTransactionType.PAYMENT,
                null,
                LocalDateTime.of(2000, 1, 1, 0, 0),
                LocalDateTime.of(2100, 1, 1, 0, 0),
                LocalDateTime.now()
        );

        List<TransactionResponse> transactions = transactionService.getTransaction(userId, cond, size);

        for (TransactionResponse res : transactions) {
            System.out.println(res);
        }

        assertEquals(3, transactions.size());
    }
}

