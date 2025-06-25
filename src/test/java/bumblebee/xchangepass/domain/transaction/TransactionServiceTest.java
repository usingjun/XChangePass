package bumblebee.xchangepass.domain.transaction;

import bumblebee.xchangepass.config.TestUserInitializer;
import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import bumblebee.xchangepass.domain.transaction.dto.cond.TransactionSearchCondition;
import bumblebee.xchangepass.domain.transaction.dto.response.CardTransactionDto;
import bumblebee.xchangepass.domain.transaction.dto.response.ExchangeTransactionDto;
import bumblebee.xchangepass.domain.transaction.dto.response.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.dto.response.WalletTransactionDto;
import bumblebee.xchangepass.domain.transaction.entity.TransactionDocument;
import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import bumblebee.xchangepass.domain.transaction.mapper.TransactionMetadataMapper;
import bumblebee.xchangepass.domain.transaction.scheduler.TransactionBulkFlushScheduler;
import bumblebee.xchangepass.domain.transaction.service.RedisTransactionQueueService;
import bumblebee.xchangepass.domain.transaction.service.TransactionService;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
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
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Import(TestUserInitializer.class)
public class TransactionServiceTest {

    static final int size = 20;
    private static final String REDIS_KEY_PREFIX = "transactions:insert:";
    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("xcp_test")
            .withUsername("testuser")
            .withPassword("testpass");
    @Container
    static GenericContainer<?> mongoContainer = new GenericContainer<>("mongo:7.0")
            .withExposedPorts(27017);
    @Container
    static GenericContainer<?> rabbitMqContainer = new GenericContainer<>("rabbitmq:3-management")
            .withExposedPorts(5672, 15672)
            .withEnv("RABBITMQ_DEFAULT_USER", "guest")
            .withEnv("RABBITMQ_DEFAULT_PASS", "guest");

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7.2")
            .withExposedPorts(6379);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private RedisTransactionQueueService redisQueueService;
    @Autowired
    private TransactionBulkFlushScheduler scheduler;
    private User sender;
    private User receiver;

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
    }

    @DynamicPropertySource
    static void overrideMongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () ->
                "mongodb://" + mongoContainer.getHost() + ":" + mongoContainer.getMappedPort(27017) + "/xchangepass_test"
        );
    }


    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMqContainer::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbitMqContainer.getMappedPort(5672));
    }

    @DynamicPropertySource
    static void overrideRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redisContainer::getHost);
        registry.add("spring.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(TransactionDocument.class);

        sender = userRepository.findByUserEmail("Test1@gmail.com").orElseThrow();
        receiver = userRepository.findByUserEmail("Test2@gmail.com").orElseThrow();
    }

    @Test
    @DisplayName("단일 거래내역 생성")
    void createTransactionTest() {
        // given
        Long userId = 1L;
        TransactionType type = TransactionType.WALLET;
        Currency before = Currency.getInstance("KRW");
        Currency after = Currency.getInstance("USD");
        Map<String, Object> metadata = Map.of(
                "receiver", receiver.getUserId(),
                "amount", 5000.0,
                "type", TransactionType.WALLET,
                "walletType", WalletTransactionType.DEPOSIT
        );

        //when
        transactionService.saveTransaction(new TransactionResponse(userId, before, after, LocalDateTime.now(), TransactionMetadataMapper.mapToDto(metadata)));

        //then
        List<TransactionDocument> saved = mongoTemplate.findAll(TransactionDocument.class);
        assertFalse(saved.isEmpty());
        assertEquals(userId, saved.get(0).getUserId());
        assertEquals(type.toString(), saved.get(0).getMetadata().get("type"));
    }

    @Test
    @DisplayName("bulkSave 테스트 - TransactionResponse 기반")
    void bulkSaveTest() {
        List<TransactionResponse> responseList = IntStream.range(0, 5)
                .mapToObj(i -> new TransactionResponse(
                        1L,
                        Currency.getInstance("KRW"),
                        Currency.getInstance("USD"),
                        LocalDateTime.now(),
                        new CardTransactionDto("Shop_" + i, BigDecimal.valueOf(1000 + i), BigDecimal.valueOf(5000 - i), TransactionType.CARD, CardTransactionType.PAYMENT)
                )).toList();

        transactionService.bulkSave(responseList);

        List<TransactionDocument> saved = mongoTemplate.findAll(TransactionDocument.class);
        assertEquals(5, saved.size());
        assertEquals("Shop_0", saved.get(0).getMetadata().get("merchant"));
    }

    @Test
    @DisplayName("🔍 거래내역 조회")
    void getTransactionTest() {
        // given
        Currency krw = Currency.getInstance("KRW");
        Currency usd = Currency.getInstance("USD");
        LocalDateTime baseTime = LocalDateTime.now();

        List<TransactionResponse> responseList = IntStream.range(0, 3)
                .boxed()
                .flatMap(i -> Stream.of(
                        // CARD 거래
                        new TransactionResponse(
                                sender.getUserId(),
                                krw,
                                usd,
                                baseTime.minusSeconds(i),
                                new CardTransactionDto(
                                        "Shop_" + i,
                                        BigDecimal.valueOf(1000 + i),
                                        BigDecimal.valueOf(5000 - i),
                                        TransactionType.CARD,
                                        CardTransactionType.PAYMENT
                                )
                        ),
                        // WALLET 거래
                        new TransactionResponse(
                                sender.getUserId(),
                                krw,
                                usd,
                                baseTime.minusSeconds(i + 10),
                                new WalletTransactionDto(
                                        receiver.getUserId(),
                                        BigDecimal.valueOf(2000 + i),
                                        TransactionType.WALLET,
                                        WalletTransactionType.DEPOSIT
                                )
                        ),
                        // EXCHANGE 거래
                        new TransactionResponse(
                                sender.getUserId(),
                                usd,
                                krw,
                                baseTime.minusSeconds(i + 20),
                                new ExchangeTransactionDto(
                                        BigDecimal.valueOf(3000 + i),
                                        BigDecimal.valueOf(3100 + i),
                                        BigDecimal.valueOf(1.1 + i * 0.01),
                                        TransactionType.EXCHANGE
                                )
                        )
                )).toList();

        transactionService.bulkSave(responseList);

        TransactionSearchCondition cond = new TransactionSearchCondition(
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now()
        );

        List<TransactionResponse> transactions = transactionService.getTransactionByMongo(sender.getUserId(), cond, size);

        for (TransactionResponse res : transactions) {
            System.out.println(res);
        }

        assertEquals(9, transactions.size());
    }


    @Test
    @DisplayName("🔍 거래내역 필터링 조회")
    void filterTransactionTest() {
        Currency krw = Currency.getInstance("KRW");
        Currency usd = Currency.getInstance("USD");
        LocalDateTime baseTime = LocalDateTime.now();

        List<TransactionResponse> responseList = IntStream.range(0, 3)
                .boxed()
                .flatMap(i -> Stream.of(
                        // CARD 거래
                        new TransactionResponse(
                                sender.getUserId(),
                                krw,
                                usd,
                                baseTime.minusSeconds(i + 10),
                                new CardTransactionDto(
                                        "Shop_" + i,
                                        BigDecimal.valueOf(1000 + i),
                                        BigDecimal.valueOf(5000 - i),
                                        TransactionType.CARD,
                                        CardTransactionType.PAYMENT
                                )
                        ),
                        // WALLET 거래
                        new TransactionResponse(
                                sender.getUserId(),
                                krw,
                                usd,
                                baseTime.minusSeconds(i + 10),
                                new WalletTransactionDto(
                                        receiver.getUserId(),
                                        BigDecimal.valueOf(2000 + i),
                                        TransactionType.WALLET,
                                        WalletTransactionType.DEPOSIT
                                )
                        ),
                        // EXCHANGE 거래
                        new TransactionResponse(
                                sender.getUserId(),
                                usd,
                                krw,
                                baseTime.minusSeconds(i + 20),
                                new ExchangeTransactionDto(
                                        BigDecimal.valueOf(3000 + i),
                                        BigDecimal.valueOf(3100 + i),
                                        BigDecimal.valueOf(1.1 + i * 0.01),
                                        TransactionType.EXCHANGE
                                )
                        )
                )).toList();

        transactionService.bulkSave(responseList);

        TransactionSearchCondition cond = new TransactionSearchCondition(
                TransactionType.CARD,
                CardTransactionType.PAYMENT,
                null,
                LocalDateTime.of(2000, 1, 1, 0, 0),
                LocalDateTime.of(2100, 1, 1, 0, 0),
                LocalDateTime.now()
        );

        List<TransactionResponse> transactions = transactionService.getTransactionByMongo(sender.getUserId(), cond, size);

        for (TransactionResponse res : transactions) {
            System.out.println(res);
        }

        assertEquals(3, transactions.size());
    }

    @Test
    @DisplayName("🔁 Redis → Scheduler → MongoDB flush 테스트")
    void redisToMongoFlushTest() {
        // given
        TransactionResponse tx = new TransactionResponse(
                sender.getUserId(),
                Currency.getInstance("KRW"),
                Currency.getInstance("USD"),
                LocalDateTime.now(),
                TransactionMetadataMapper.mapToDto(Map.of(
                        "receiver", receiver.getUserId(),
                        "amount", 1234.56,
                        "type", TransactionType.WALLET,
                        "walletType", "DEPOSIT"
                ))
        );


        // when: Redis에 저장
        redisQueueService.enqueue(REDIS_KEY_PREFIX + sender.getUserId(), tx);
        // then: flush 트리거
        scheduler.flushTransactionToDB();

        // and: MongoDB 저장 확인
        List<TransactionDocument> result = mongoTemplate.findAll(TransactionDocument.class);
        assertFalse(result.isEmpty());
        assertEquals(sender.getUserId(), result.get(0).getUserId());
        assertEquals(TransactionType.WALLET.toString(), result.get(0).getMetadata().get("type"));

        System.out.println("✅ 저장된 거래내역 수: " + result.size());
        System.out.println("✅ 저장된 거래내역: " + result.get(0));
    }

}

