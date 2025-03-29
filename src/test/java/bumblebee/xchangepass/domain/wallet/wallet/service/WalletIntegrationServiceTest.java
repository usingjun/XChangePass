package bumblebee.xchangepass.domain.wallet.wallet.service;

import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.repository.WalletBalanceRepository;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletInOutRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.domain.wallet.wallet.service.impl.WalletServiceImpl;
import bumblebee.xchangepass.domain.wallet.wallet.service.impl.lock.PessimisticLockWalletService;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class WalletIntegrationServiceTest {

    @Autowired
    private WalletServiceImpl walletService;
    @Autowired
    private PessimisticLockWalletService pessimisticLockWalletService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private WalletBalanceRepository walletBalanceRepository;
    @Autowired
    private WalletBalanceService balanceService;
    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    private final BigDecimal CHARGE_AMOUNT = new BigDecimal("10000.00");
    private final BigDecimal TRANSFER_AMOUNT = new BigDecimal("5000.00");
    private final Currency CURRENCY = Currency.getInstance("KRW");

    private User sender;
    private User receiver;
    private Wallet senderWallet;
    private Wallet receiverWallet;


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
    void setup() throws InterruptedException {
        Thread.sleep(300);
        walletTransactionRepository.deleteAll();
        walletBalanceRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll(); // 기존 데이터 삭제

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
    }

    // 중복되지 않는 유저 생성
    private User createUser(String email, String password, String username, String nickname, String phone, Sex sex) {
        User user = new User(email, password, username, nickname, phone, sex, new BCryptPasswordEncoder());
        return userRepository.save(user); // 즉시 반영
    }

    private Wallet createWalletForUser(User user, String walletPassword) {
        walletService.createWallet(user, walletPassword);

        return walletRepository.findByUserId(user.getUserId())
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
    }

    private String generateRandomId() {
        return UUID.randomUUID().toString().substring(0, 6);
    }

    @Test
    @DisplayName("잔액이 충분할 때 송금이 성공한다")
    void testTransferSuccess() {
        pessimisticLockWalletService.charge(sender.getUserId(), new WalletInOutRequest(CHARGE_AMOUNT, CURRENCY, CURRENCY, null));

        WalletTransferRequest transferRequest = new WalletTransferRequest(receiver.getUserName().getValue(), receiver.getUserPhoneNumber().getValue(), TRANSFER_AMOUNT, CURRENCY, CURRENCY, null);
        pessimisticLockWalletService.transfer(sender.getUserId(), transferRequest);

        WalletBalance senderBalance = balanceService.findBalance(senderWallet.getWalletId(), CURRENCY);
        WalletBalance receiverBalance = balanceService.findBalance(receiverWallet.getWalletId(), CURRENCY);

        assertThat(senderBalance.getBalance()).isEqualByComparingTo(CHARGE_AMOUNT.subtract(TRANSFER_AMOUNT));
        assertThat(receiverBalance.getBalance()).isEqualByComparingTo(TRANSFER_AMOUNT);
    }

    @Test
    @DisplayName("잔액이 부족할 때 송금이 실패한다")
    void testTransferFailureDueToInsufficientFunds() {
        WalletTransferRequest transferRequest = new WalletTransferRequest(receiver.getUserName().getValue(), receiver.getUserPhoneNumber().getValue(), TRANSFER_AMOUNT, CURRENCY, CURRENCY, null);
        Exception exception = assertThrows(RuntimeException.class, () -> pessimisticLockWalletService.transfer(sender.getUserId(), transferRequest));
        assertThat(exception.getMessage()).contains("충전 금액이 부족합니다.");
    }

    @Test
    @DisplayName("계좌에 충전이 성공한다")
    void testChargeWallet() {
        pessimisticLockWalletService.charge(sender.getUserId(), new WalletInOutRequest(CHARGE_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()));

        WalletBalance balance = balanceService.findBalance(senderWallet.getWalletId(), CURRENCY);
        assertThat(balance.getBalance()).isEqualByComparingTo(CHARGE_AMOUNT);
    }

    /**
     * //     * 💡 동시 송금 처리 테스트
     * //
     */
    @Test
    @DisplayName("여러 사용자가 동시에 같은 계좌로 송금하면 모든 송금이 처리된다")
    void concurrentTransfersToSameWallet() throws InterruptedException {
        // Given: 초기 충전
        WalletBalance balance = balanceService.findBalance(senderWallet.getWalletId(), CURRENCY);
        balanceService.chargeBalance(balance, CHARGE_AMOUNT.multiply(BigDecimal.valueOf(100)));

        // 100명의 사용자가 동시에 송금하도록 설정
        int concurrentUsers = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentUsers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentUsers);

        Long senderId = senderWallet.getWalletId();
        Long receiverId = receiverWallet.getWalletId();

        WalletTransferRequest transferRequest = new WalletTransferRequest(receiver.getUserName().getValue(), receiver.getUserPhoneNumber().getValue(), TRANSFER_AMOUNT, CURRENCY, CURRENCY, null);

        for (int i = 0; i < concurrentUsers; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // 🔥 모든 스레드가 동시에 실행되도록 대기
                    pessimisticLockWalletService.transfer(senderId, transferRequest);
                } catch (Exception e) {
                    System.err.println("[송금 중 예외 발생]: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 모든 스레드 동시에 실행
        startLatch.countDown();
        endLatch.await();
        executorService.shutdown();

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

    /**
     //     * 💡 송금 도중 출금 실패 테스트
     //     */
    private final int THREAD_COUNT = 2; // 동시에 실행할 스레드 개수
    private final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);

    /**
     * ✅ 송금 도중 출금이 발생하면 한쪽이 실패하는지 확인하는 동시성 테스트 (5번 반복)
     */
    @RepeatedTest(5) // 5번 반복 실행
    @DisplayName("송금 도중 출금이 발생하면 둘 중 하나는 실패한다")
    void eitherTransferOrWithdrawalFailsDuringConcurrentExecution() throws Exception {
        WalletInOutRequest chargeRequest = new WalletInOutRequest(
                CHARGE_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()
        );

        pessimisticLockWalletService.charge(sender.getUserId(), chargeRequest);

        AtomicReference<BigDecimal> withdrawAmount = new AtomicReference<>(BigDecimal.ZERO);
        AtomicBoolean isWithdrawFirst = new AtomicBoolean(false);
        AtomicBoolean isTransferFirst = new AtomicBoolean(false);

        CountDownLatch latch = new CountDownLatch(1); // 🔥 1로 설정 (송금이 먼저 실행되도록 설정)

        Future<?> transferFuture = executorService.submit(() -> {
            try {
                System.out.println("🚀 [송금 시작]");
                WalletTransferRequest transferRequest = new WalletTransferRequest(receiver.getUserName().getValue(), receiver.getUserPhoneNumber().getValue(), TRANSFER_AMOUNT, CURRENCY, CURRENCY, null);

                pessimisticLockWalletService.transfer(sender.getUserId(), transferRequest);
                isTransferFirst.set(true);
            } catch (Exception e) {
                System.err.println("[송금 중 예외 발생]: " + e.getMessage());
            } finally {
                latch.countDown(); // 🔥 송금이 끝난 후 출금 시작 신호
            }
        });

        Future<?> withdrawFuture = executorService.submit(() -> {
            try {
                latch.await(); // 🔥 송금이 끝날 때까지 출금 대기
                System.out.println("💸 [출금 시작]");
                WalletInOutRequest withdrawRequest = new WalletInOutRequest(
                        CHARGE_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()
                );
                withdrawAmount.set(pessimisticLockWalletService.withdrawal(sender.getUserId(), withdrawRequest));
                isWithdrawFirst.set(true);
            } catch (Exception e) {
                System.err.println("[출금 중 예외 발생]: " + e.getMessage());
            }
        });

        // 🔥 모든 작업이 완료될 때까지 대기
        withdrawFuture.get();
        transferFuture.get();
        executorService.shutdown();

        // 잔액 확인
        WalletBalance balanceA = balanceService.findBalance(senderWallet.getWalletId(), CURRENCY);
        WalletBalance balanceB = balanceService.findBalance(receiverWallet.getWalletId(), CURRENCY);

        System.out.println("[🏦 최종 Sender 잔액] = " + balanceA.getBalance());
        System.out.println("[🏦 최종 Receiver 잔액] = " + balanceB.getBalance());
        System.out.println("[💰 출금된 금액] = " + withdrawAmount.get());
        System.out.println("[💸 출금이 먼저 실행되었는가?] " + isWithdrawFirst.get());
        System.out.println("[🚀 송금이 먼저 실행되었는가?] " + isTransferFirst.get());

        // 테스트 검증
        if (isWithdrawFirst.get()) {
            // 출금이 먼저 실행되었으면 송금이 실패해야 함
            assertThat(withdrawAmount.get()).isEqualByComparingTo(CHARGE_AMOUNT); // 출금 성공
            assertThat(balanceA.getBalance()).isEqualByComparingTo(BigDecimal.ZERO); // 출금 후 잔액 없음
        } else if (isTransferFirst.get()) {
            // 송금이 먼저 실행되었으면 출금이 실패해야 함
            assertThat(balanceA.getBalance()).isEqualByComparingTo(TRANSFER_AMOUNT); // 송금 성공
            assertThat(withdrawAmount.get()).isEqualTo(BigDecimal.ZERO); // 출금 실패
        } else {
            throw new IllegalStateException("출금과 송금이 모두 실행되지 않음");
        }
    }

    /**
     * ✅ 충전과 이체가 동시에 발생할 때 이체가 실패하는지 확인하는 동시성 테스트
     */
    @RepeatedTest(5) // 5번 반복 실행
    @DisplayName("충전과 이체가 동시에 발생하면 이체는 실패하고 충전은 성공한다")
    void chargeSucceedsAndTransferFailsOnConcurrentRequest() throws Exception {
        // Given: 초기 잔액 설정
        System.out.println("초기 Sender 잔액 = " + balanceService.findBalance(senderWallet.getWalletId(), CURRENCY).getBalance());

        WalletInOutRequest chargeRequest = new WalletInOutRequest(
                CHARGE_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()
        );

        WalletTransferRequest transferRequest = new WalletTransferRequest(receiver.getUserName().getValue(), receiver.getUserPhoneNumber().getValue(), TRANSFER_AMOUNT, CURRENCY, CURRENCY, null);

        CountDownLatch latch = new CountDownLatch(1); // 🔥 1로 설정 (이체 먼저 실행)

        Future<?> chargeFuture = executorService.submit(() -> {
            try {
                latch.await(); // 🔥 이체가 끝날 때까지 충전 대기
                System.out.println("💰 충전 시작");
                pessimisticLockWalletService.charge(sender.getUserId(), chargeRequest);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        Future<?> transferFuture = executorService.submit(() -> {
            try {
                System.out.println("🚀 이체 시작");
                Exception exception = assertThrows(CommonException.class, () -> {
                    pessimisticLockWalletService.transfer(sender.getUserId(), transferRequest);
                });
                assertThat(exception.getMessage()).contains("충전 금액이 부족합니다.");
            } finally {
                latch.countDown(); // 🔥 이체가 먼저 실행되고 나서 충전 시작 신호
            }
        });

        // 🔥 모든 작업이 완료될 때까지 대기
        chargeFuture.get();
        transferFuture.get();
        executorService.shutdown();

        // Then: 충전 후 최종 잔액 검증
        WalletBalance balance = balanceService.findBalance(senderWallet.getWalletId(), CURRENCY);
        WalletBalance balance1 = balanceService.findBalance(receiverWallet.getWalletId(), CURRENCY);
        System.out.println("[🏦 최종 Sender 잔액] = " + balance.getBalance());
        System.out.println("[🏦 최종 Receiver 잔액] = " + balance1.getBalance());

        assertThat(balance.getBalance()).isGreaterThanOrEqualTo(TRANSFER_AMOUNT);
    }

}

