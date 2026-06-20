package bumblebee.xchangepass.domain.wallet.wallet;

import bumblebee.xchangepass.domain.card.service.CardService;
import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.transaction.service.WalletTransactionService;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.repository.WalletBalanceRepository;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudEvaluationResult;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudReason;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudRuleEvaluator;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletInOutRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.entity.WalletTransferType;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.domain.wallet.wallet.service.impl.WalletServiceImpl;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class WalletServiceConcurrencyTest {

    @Autowired
    private WalletServiceImpl walletService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private WalletBalanceRepository walletBalanceRepository;
    @Autowired
    private WalletBalanceService balanceService;

    @MockBean
    private FraudRuleEvaluator fraudRuleEvaluator;
    @MockBean
    private WalletTransactionService walletTransactionService;
    @MockBean
    private CardService cardService;

    private final BigDecimal CHARGE_AMOUNT = new BigDecimal("10000.00");
    private final BigDecimal TRANSFER_AMOUNT = new BigDecimal("5000.00");
    private final Currency CURRENCY = Currency.getInstance("KRW");

    private User sender;
    private User receiver;
    private Wallet senderWallet;
    private Wallet receiverWallet;


    @Container
    static PostgreSQLContainer postgresContainer = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("xcp_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
    }


    @BeforeEach
    void setup() throws InterruptedException {
        Thread.sleep(300);
        walletBalanceRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll(); // 기존 데이터 삭제
        userRepository.flush();

        // 유저 생성
        sender = createUser("sender@example.com", "passwordA123!", "sen", "S" + generateRandomId(), "010-1111-2222",  Sex.MALE);
        receiver = createUser("receiver@example.com", "passwordA123!", "rec", "R" + generateRandomId(), "010-3333-4444",  Sex.FEMALE);

        // 🔥 저장된 유저가 실제 존재하는지 확인
        userRepository.findByUserEmail(sender.getUserEmail().getValue())
                .orElseThrow(() -> new IllegalStateException("Sender가 DB에 존재하지 않습니다."));
        userRepository.findByUserEmail(receiver.getUserEmail().getValue())
                .orElseThrow(() -> new IllegalStateException("Receiver가 DB에 존재하지 않습니다."));

        // 지갑 생성 및 검증
        senderWallet = createWalletForUser(sender, "1234");
        receiverWallet = createWalletForUser(receiver, "1234");
        System.out.println("senderWallet = " + senderWallet.getWalletId());
        System.out.println("receiverWallet = " + receiverWallet.getWalletId());
        when(fraudRuleEvaluator.evaluate(any(), any())).thenReturn(FraudEvaluationResult.clear());
        reset(walletTransactionService);
    }

    // 중복되지 않는 유저 생성
    private User createUser(String email, String password, String username, String nickname, String phone, Sex sex) {
        User user = new User(email, password, username, nickname, phone, sex, new BCryptPasswordEncoder());
        return userRepository.save(user); // 즉시 반영
    }

    // 중복되지 않는 지갑 생성
    private Wallet createWalletForUser(User user, String walletPassword) {
        walletService.createWallet(user, walletPassword);

        return walletRepository.findByUserId(user.getUserId())
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
    }

    // 랜덤한 ID 생성 (UUID 활용)
    private String generateRandomId() {
        return UUID.randomUUID().toString().substring(0, 6);
    }

    @Test
    @DisplayName("잔액이 충분할 때 송금이 성공한다")
    void testTransferSuccess() {
        walletService.charge(sender.getUserId(), new WalletInOutRequest(CHARGE_AMOUNT, CURRENCY, CURRENCY, null));

        WalletTransferRequest transferRequest = new WalletTransferRequest(receiver.getUserName().getValue(), receiver.getUserPhoneNumber().getValue(), TRANSFER_AMOUNT, CURRENCY, CURRENCY, null, WalletTransferType.GENERAL);
        walletService.transfer(sender.getUserId(), transferRequest);

        WalletBalance senderBalance = balanceService.findBalance(senderWallet.getWalletId(), CURRENCY);
        WalletBalance receiverBalance = balanceService.findBalance(receiverWallet.getWalletId(), CURRENCY);

        assertThat(senderBalance.getBalance()).isEqualByComparingTo(CHARGE_AMOUNT.subtract(TRANSFER_AMOUNT));
        assertThat(receiverBalance.getBalance()).isEqualByComparingTo(TRANSFER_AMOUNT);
    }

    @Test
    @DisplayName("잔액이 부족할 때 송금이 실패한다")
    void testTransferFailureDueToInsufficientFunds() {
        WalletTransferRequest transferRequest = new WalletTransferRequest(receiver.getUserName().getValue(), receiver.getUserPhoneNumber().getValue(), TRANSFER_AMOUNT, CURRENCY, CURRENCY, null, WalletTransferType.GENERAL);
        Exception exception = assertThrows(RuntimeException.class, () -> walletService.transfer(sender.getUserId(), transferRequest));
        assertThat(exception.getMessage()).contains("충전 금액이 부족합니다.");
    }

    @Test
    @Transactional
    @DisplayName("계좌에 충전이 성공한다")
    void testChargeWallet() {
        walletService.charge(sender.getUserId(), new WalletInOutRequest(CHARGE_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()));

        WalletBalance balance = balanceService.findBalance(senderWallet.getWalletId(), CURRENCY);
        assertThat(balance.getBalance()).isEqualByComparingTo(CHARGE_AMOUNT);
    }

    /**
     * //     * 💡 동시 송금 처리 테스트
     * //
     */
    @Test
    @DisplayName("여러 사용자가 동시에 같은 계좌로 송금하면 모든 송금이 처리된다")
    void concurrentTransfersToSameWallet() throws Exception {
        // Given: 초기 충전
        WalletBalance balance = balanceService.findBalance(senderWallet.getWalletId(), CURRENCY);
        balanceService.chargeBalance(balance, CHARGE_AMOUNT.multiply(BigDecimal.valueOf(100)));

        // 100명의 사용자가 동시에 송금하도록 설정
        int concurrentUsers = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentUsers);
        CountDownLatch startLatch = new CountDownLatch(1);

        WalletTransferRequest transferRequest = new WalletTransferRequest(receiver.getUserName().getValue(), receiver.getUserPhoneNumber().getValue(), TRANSFER_AMOUNT, CURRENCY, CURRENCY, null, WalletTransferType.GENERAL);

        List<? extends Future<?>> futures = java.util.stream.IntStream.range(0, concurrentUsers)
                .mapToObj(i -> executorService.submit(() -> {
                    startLatch.await();
                    walletService.transfer(sender.getUserId(), transferRequest);
                    return null;
                }))
                .toList();

        startLatch.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executorService.shutdown();
        assertThat(executorService.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // Then: 잔액 검증
        WalletBalance senderBalance = balanceService.findBalance(senderWallet.getWalletId(), CURRENCY);
        WalletBalance receiverBalance = balanceService.findBalance(receiverWallet.getWalletId(), CURRENCY);

        System.out.println("receiverBalance = " + receiverBalance.getBalance());
        System.out.println("senderBalance = " + senderBalance.getBalance());

        assertThat(senderBalance.getBalance()).isEqualByComparingTo(
                CHARGE_AMOUNT.multiply(BigDecimal.valueOf(concurrentUsers)).subtract(TRANSFER_AMOUNT.multiply(BigDecimal.valueOf(concurrentUsers)))
        );

        assertThat(receiverBalance.getBalance()).isEqualByComparingTo(
                TRANSFER_AMOUNT.multiply(BigDecimal.valueOf(concurrentUsers))
        );
    }

    @RepeatedTest(5)
    @DisplayName("양방향 송금이 동시에 실행돼도 데드락 없이 잔액이 일치한다")
    void bidirectionalTransfersAcquireWalletLocksInSameOrder() throws Exception {
        walletService.charge(sender.getUserId(), new WalletInOutRequest(
                CHARGE_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()
        ));
        walletService.charge(receiver.getUserId(), new WalletInOutRequest(
                CHARGE_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()
        ));

        WalletTransferRequest senderToReceiver = new WalletTransferRequest(
                receiver.getUserName().getValue(),
                receiver.getUserPhoneNumber().getValue(),
                TRANSFER_AMOUNT,
                CURRENCY,
                CURRENCY,
                null,
                WalletTransferType.GENERAL
        );
        WalletTransferRequest receiverToSender = new WalletTransferRequest(
                sender.getUserName().getValue(),
                sender.getUserPhoneNumber().getValue(),
                TRANSFER_AMOUNT,
                CURRENCY,
                CURRENCY,
                null,
                WalletTransferType.GENERAL
        );
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> forward = executor.submit(() -> {
            startLatch.await();
            walletService.transfer(sender.getUserId(), senderToReceiver);
            return null;
        });
        Future<?> reverse = executor.submit(() -> {
            startLatch.await();
            walletService.transfer(receiver.getUserId(), receiverToSender);
            return null;
        });

        startLatch.countDown();
        forward.get(30, TimeUnit.SECONDS);
        reverse.get(30, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(balanceService.findBalance(senderWallet.getWalletId(), CURRENCY).getBalance())
                .isEqualByComparingTo(CHARGE_AMOUNT);
        assertThat(balanceService.findBalance(receiverWallet.getWalletId(), CURRENCY).getBalance())
                .isEqualByComparingTo(CHARGE_AMOUNT);
    }

    /**
     //     * 💡 송금 도중 출금 실패 테스트
     //     */
    @RepeatedTest(5)
    @DisplayName("송금 도중 출금이 발생하면 둘 중 하나는 실패한다")
    void eitherTransferOrWithdrawalFailsDuringConcurrentExecution() throws Exception {
        walletService.charge(sender.getUserId(), new WalletInOutRequest(
                CHARGE_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()
        ));

        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        Future<?> transferFuture = executorService.submit(() -> {
            try {
                latch.await();
                WalletTransferRequest transferRequest = new WalletTransferRequest(receiver.getUserName().getValue(), receiver.getUserPhoneNumber().getValue(), TRANSFER_AMOUNT, CURRENCY, CURRENCY, null, WalletTransferType.GENERAL);
                walletService.transfer(sender.getUserId(), transferRequest);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        });

        Future<?> withdrawFuture = executorService.submit(() -> {
            try {
                latch.await();
                WalletInOutRequest withdrawRequest = new WalletInOutRequest(
                        CHARGE_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()
                );
                walletService.withdrawal(sender.getUserId(), withdrawRequest);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        });

        latch.countDown();
        withdrawFuture.get();
        transferFuture.get();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        WalletBalance senderBalance = balanceService.findBalance(senderWallet.getWalletId(), CURRENCY);
        WalletBalance receiverBalance = balanceService.findBalance(receiverWallet.getWalletId(), CURRENCY);

        assertThat(successCount).hasValue(1);
        assertThat(failureCount).hasValue(1);
        boolean withdrawalSucceeded =
                senderBalance.getBalance().compareTo(BigDecimal.ZERO) == 0
                        && receiverBalance.getBalance().compareTo(BigDecimal.ZERO) == 0;
        boolean transferSucceeded =
                senderBalance.getBalance().compareTo(TRANSFER_AMOUNT) == 0
                        && receiverBalance.getBalance().compareTo(TRANSFER_AMOUNT) == 0;
        assertThat(withdrawalSucceeded || transferSucceeded).isTrue();
    }

    /**
     * ✅ 충전과 이체가 동시에 발생할 때 이체가 실패하는지 확인하는 동시성 테스트
     */
    @RepeatedTest(5)
    @DisplayName("충전과 이체가 동시에 발생하면 이체는 실패하고 충전은 성공한다")
    void chargeSucceedsAndTransferFailsOnConcurrentRequest() throws Exception {
        WalletInOutRequest chargeRequest = new WalletInOutRequest(
                CHARGE_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()
        );

        WalletTransferRequest transferRequest = new WalletTransferRequest(receiver.getUserName().getValue(), receiver.getUserPhoneNumber().getValue(), TRANSFER_AMOUNT, CURRENCY, CURRENCY, null, WalletTransferType.GENERAL);

        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        Future<?> chargeFuture = executorService.submit(() -> {
            try {
                latch.await();
                walletService.charge(sender.getUserId(), chargeRequest);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        Future<?> transferFuture = executorService.submit(() -> {
            try {
                Exception exception = assertThrows(CommonException.class, () -> {
                    walletService.transfer(sender.getUserId(), transferRequest);
                });
                assertThat(exception.getMessage()).contains("충전 금액이 부족합니다.");
            } finally {
                latch.countDown();
            }
        });

        chargeFuture.get();
        transferFuture.get();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        WalletBalance senderBalance = balanceService.findBalance(senderWallet.getWalletId(), CURRENCY);
        WalletBalance receiverBalance = balanceService.findBalance(receiverWallet.getWalletId(), CURRENCY);

        assertThat(senderBalance.getBalance()).isEqualByComparingTo(CHARGE_AMOUNT);
        assertThat(receiverBalance.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("거래 원장 저장이 실패하면 충전과 신규 잔액 생성이 함께 롤백된다")
    void chargeRollsBackWhenLedgerSaveFails() {
        doThrow(new IllegalStateException("ledger save failed"))
                .when(walletTransactionService)
                .saveTransaction(anyLong(), isNull(), any(), isNull(), any(), any());

        WalletInOutRequest request = new WalletInOutRequest(
                CHARGE_AMOUNT,
                Currency.getInstance("USD"),
                Currency.getInstance("USD"),
                LocalDateTime.now()
        );

        assertThrows(IllegalStateException.class, () -> walletService.charge(sender.getUserId(), request));

        assertThat(walletBalanceRepository.existsByCurrency(senderWallet.getWalletId(), Currency.getInstance("USD")))
                .isFalse();
    }

    @Test
    @DisplayName("송금 원장 저장이 실패하면 송신자와 수신자 잔액이 함께 롤백된다")
    void transferRollsBackBothBalancesWhenLedgerSaveFails() {
        walletService.charge(sender.getUserId(), new WalletInOutRequest(
                CHARGE_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()
        ));
        doThrow(new IllegalStateException("ledger save failed"))
                .when(walletTransactionService)
                .saveTransferTransaction(anyLong(), anyLong(), any(), any(), any(), any(), isNull());

        WalletTransferRequest request = new WalletTransferRequest(
                receiver.getUserName().getValue(),
                receiver.getUserPhoneNumber().getValue(),
                TRANSFER_AMOUNT,
                CURRENCY,
                CURRENCY,
                null,
                WalletTransferType.GENERAL
        );

        assertThrows(IllegalStateException.class, () -> walletService.transfer(sender.getUserId(), request));

        assertThat(balanceService.findBalance(senderWallet.getWalletId(), CURRENCY).getBalance())
                .isEqualByComparingTo(CHARGE_AMOUNT);
        assertThat(balanceService.findBalance(receiverWallet.getWalletId(), CURRENCY).getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("이상 거래가 감지되면 송금 잔액 변경을 차단한다")
    void suspiciousTransferIsBlockedBeforeBalanceChange() {
        walletService.charge(sender.getUserId(), new WalletInOutRequest(
                CHARGE_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()
        ));
        when(fraudRuleEvaluator.evaluate(any(), any()))
                .thenReturn(FraudEvaluationResult.suspicious(FraudReason.FREQUENCY_EXCEEDED, 40));

        WalletTransferRequest request = new WalletTransferRequest(
                receiver.getUserName().getValue(),
                receiver.getUserPhoneNumber().getValue(),
                TRANSFER_AMOUNT,
                CURRENCY,
                CURRENCY,
                null,
                WalletTransferType.GENERAL
        );

        CommonException exception = assertThrows(
                CommonException.class,
                () -> walletService.transfer(sender.getUserId(), request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SUSPICIOUS_TRANSACTION);
        assertThat(balanceService.findBalance(senderWallet.getWalletId(), CURRENCY).getBalance())
                .isEqualByComparingTo(CHARGE_AMOUNT);
        assertThat(balanceService.findBalance(receiverWallet.getWalletId(), CURRENCY).getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Redis 이상 거래 검증이 실패하면 송금을 안전하게 차단한다")
    void transferFailsClosedWhenFraudDetectionIsUnavailable() {
        walletService.charge(sender.getUserId(), new WalletInOutRequest(
                CHARGE_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()
        ));
        when(fraudRuleEvaluator.evaluate(any(), any()))
                .thenThrow(ErrorCode.FRAUD_DETECTION_UNAVAILABLE.commonException());

        WalletTransferRequest request = new WalletTransferRequest(
                receiver.getUserName().getValue(),
                receiver.getUserPhoneNumber().getValue(),
                TRANSFER_AMOUNT,
                CURRENCY,
                CURRENCY,
                null,
                WalletTransferType.GENERAL
        );

        CommonException exception = assertThrows(
                CommonException.class,
                () -> walletService.transfer(sender.getUserId(), request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FRAUD_DETECTION_UNAVAILABLE);
        assertThat(balanceService.findBalance(senderWallet.getWalletId(), CURRENCY).getBalance())
                .isEqualByComparingTo(CHARGE_AMOUNT);
        assertThat(balanceService.findBalance(receiverWallet.getWalletId(), CURRENCY).getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

}
