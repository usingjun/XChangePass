package bumblebee.xchangepass.domain.wallet.wallet.service;

import bumblebee.xchangepass.config.TestUserInitializer;
import bumblebee.xchangepass.domain.user.dto.request.UserRegisterRequest;
import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.service.UserRegisterService;
import bumblebee.xchangepass.domain.user.service.UserService;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.entity.ScheduledTransfer;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.entity.WalletTransferType;
import bumblebee.xchangepass.domain.wallet.wallet.repository.ScheduledTransferRepository;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.domain.wallet.wallet.scheduler.ScheduledTransferService;
import bumblebee.xchangepass.domain.wallet.wallet.service.impl.WalletFacadeService;
import bumblebee.xchangepass.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import(TestUserInitializer.class)
public class ScheduledTransferServiceTest {

    @Autowired private ScheduledTransferService scheduledTransferService;
    @Autowired private ScheduledTransferRepository scheduledTransferRepository;
    @Autowired private WalletFacadeService walletFacadeService;
    @Autowired private UserRegisterService userRegisterService;
    @Autowired private UserService userService;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private WalletBalanceService balanceService;

    private Long senderId;
    private Wallet testWallet1;
    private Wallet testWallet2;
    Currency krw = Currency.getInstance("KRW");

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("xcp_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);

        registry.add("spring.redis.host", redisContainer::getHost);
        registry.add("spring.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    @BeforeEach
    void setUp() {
        userRegisterService.signupUser(new UserRegisterRequest(
                "sender@example.com", "Password123!", "보내는사람", "010-1111-1111", Sex.MALE, "1234"
        ));
        userRegisterService.signupUser(new UserRegisterRequest(
                "receiver@example.com", "Password123!", "받는사람", "010-2222-2222", Sex.FEMALE, "1234"
        ));

        var user1 = userService.readUser("보내는사람", "010-1111-1111");
        var user2 = userService.readUser("받는사람", "010-2222-2222");

        senderId = user1.getUserId();

        testWallet1 = walletRepository.findByUserId(user1.getUserId())
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
        testWallet2 = walletRepository.findByUserId(user2.getUserId())
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

        if (!balanceService.checkBalance(testWallet1.getWalletId(), krw))
            balanceService.createBalance(testWallet1, krw);
        if (!balanceService.checkBalance(testWallet2.getWalletId(), krw))
            balanceService.createBalance(testWallet2, krw);

        // 🔥 충전 추가
        balanceService.chargeBalance(
                balanceService.findBalanceWithLock(testWallet1.getWalletId(), krw),
                new BigDecimal("10000")
        );
    }

    @Test
    void 예약송금_등록_및_처리_정상동작() {
        WalletTransferRequest request = new WalletTransferRequest(
                "받는사람",
                "010-2222-2222",
                BigDecimal.valueOf(1000),
                Currency.getInstance("KRW"),
                Currency.getInstance("KRW"),
                LocalDateTime.now().minusSeconds(5),
                WalletTransferType.SCHEDULED
        );

       walletFacadeService.transfer(senderId, request);

        List<ScheduledTransfer> list = scheduledTransferRepository.findAll();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getStatus().name()).isEqualTo("PENDING");

        scheduledTransferService.processScheduledTransfers();

        ScheduledTransfer after = scheduledTransferRepository.findById(list.get(0).getScheduledTransferId()).orElseThrow();
        assertThat(after.getStatus().name()).isEqualTo("SUCCESS");
    }
}
