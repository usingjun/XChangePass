package bumblebee.xchangepass.domain.wallet.service;

import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletInOutRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.domain.wallet.wallet.service.WalletService;
import bumblebee.xchangepass.domain.wallet.wallet.service.redisson.RedissonLockService;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.repository.WalletBalanceRepository;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.global.exception.CommonException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WalletRedissonServiceTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private RedissonLockService lockService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private WalletBalanceRepository walletBalanceRepository;
    @Autowired
    private WalletBalanceService balanceService;

    private final BigDecimal CHARGE_AMOUNT = new BigDecimal("10000.00");
    private final BigDecimal TRANSFER_AMOUNT = new BigDecimal("5000.00");
    private final Currency CURRENCY = Currency.getInstance("KRW");

    private User sender;
    private User receiver;
    private Wallet senderWallet;
    private Wallet receiverWallet;

    /**
     * 💡 모든 테스트 실행 전에 사용자 & 지갑을 미리 생성
     */
    @BeforeEach
    void setup() {
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

    // 중복되지 않는 지갑 생성
    private Wallet createWalletForUser(User user, String walletPassword) {
        walletService.createWallet(user, walletPassword);

        return walletRepository.findByUserId(user.getUserId());
    }

    // 랜덤한 ID 생성 (UUID 활용)
    private String generateRandomId() {
        return UUID.randomUUID().toString().substring(0, 6);
    }

    @Test
    void testTransferSuccess() {
        lockService.charge(new WalletInOutRequest(sender.getUserId(), CHARGE_AMOUNT, CURRENCY, CURRENCY, null));

        WalletTransferRequest transferRequest = new WalletTransferRequest(sender.getUserId(), receiver.getUserId(), TRANSFER_AMOUNT, CURRENCY, CURRENCY, null);
        lockService.transfer(transferRequest);

        WalletBalance senderBalance = balanceService.findBalance(senderWallet.getWalletId(), CURRENCY);
        WalletBalance receiverBalance = balanceService.findBalance(receiverWallet.getWalletId(), CURRENCY);

        assertThat(senderBalance.getBalance()).isEqualByComparingTo(CHARGE_AMOUNT.subtract(TRANSFER_AMOUNT));
        assertThat(receiverBalance.getBalance()).isEqualByComparingTo(TRANSFER_AMOUNT);
    }

    @Test
    void testTransferFailureDueToInsufficientFunds() {
        WalletTransferRequest transferRequest = new WalletTransferRequest(sender.getUserId(), receiver.getUserId(), CHARGE_AMOUNT.add(BigDecimal.ONE), CURRENCY, CURRENCY, null);
        Exception exception = assertThrows(RuntimeException.class, () -> lockService.transfer(transferRequest));
        assertThat(exception.getMessage()).contains("충전 금액이 부족합니다.");
    }

    @Test
    @Transactional
    void testChargeWallet() {
        lockService.charge(new WalletInOutRequest(sender.getUserId(), CHARGE_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()));

        WalletBalance balance = balanceService.findBalance(senderWallet.getWalletId(), CURRENCY);
        assertThat(balance.getBalance()).isEqualByComparingTo(CHARGE_AMOUNT);
    }

    /**
     * //     * 💡 동시 송금 처리 테스트
     * //
     */
    @Test
    void 동시에_같은_계좌에_송금이_발생한다() throws InterruptedException {
        // Given: 초기 충전
        WalletBalance balance = balanceService.findBalance(senderWallet.getWalletId(), CURRENCY);
        balanceService.chargeBalance(balance, CHARGE_AMOUNT.multiply(BigDecimal.valueOf(100)));

        // 100명의 사용자가 동시에 송금하도록 설정
        int concurrentUsers = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentUsers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentUsers);
        AtomicInteger transferCount = new AtomicInteger(0);

        Long senderId = senderWallet.getWalletId();
        Long receiverId = receiverWallet.getWalletId();

        WalletTransferRequest transferRequest = new WalletTransferRequest(
                senderId, receiverId, TRANSFER_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()
        );

        for (int i = 0; i < concurrentUsers; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // 🔥 모든 스레드가 동시에 실행되도록 대기
                    lockService.transfer(transferRequest);
                    transferCount.incrementAndGet();
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
        System.out.println("[🔥 총 송금 성공 횟수]: " + transferCount.get());

        BigDecimal expectedSenderBalance = CHARGE_AMOUNT.multiply(BigDecimal.valueOf(100))
                .subtract(TRANSFER_AMOUNT.multiply(BigDecimal.valueOf(transferCount.get())));

        BigDecimal expectedReceiverBalance = TRANSFER_AMOUNT.multiply(BigDecimal.valueOf(transferCount.get()));

        assertThat(senderBalance.getBalance()).isEqualByComparingTo(expectedSenderBalance);
        assertThat(receiverBalance.getBalance()).isEqualByComparingTo(expectedReceiverBalance);

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
    void 송금_도중_발생한_출금은_실패한다() throws Exception {
        lockService.charge(new WalletInOutRequest(
                sender.getUserId(), CHARGE_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()
        ));

        AtomicBoolean isWithdrawFirst = new AtomicBoolean(false);
        AtomicBoolean isTransferFirst = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        ExecutorService executorService = Executors.newFixedThreadPool(2);

        Future<?> transferFuture = executorService.submit(() -> {
            try {
                latch.await();
                Thread.sleep(20); // 🔥 실행 순서를 조정
                System.out.println("🚀 [송금 시작]");
                WalletTransferRequest transferRequest = new WalletTransferRequest(
                        sender.getUserId(), receiver.getUserId(), TRANSFER_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()
                );
                lockService.transfer(transferRequest);
                isTransferFirst.set(true);
            } catch (Exception e) {
                System.err.println("[송금 중 예외 발생]: " + e.getMessage());
            }
        });

        Future<?> withdrawFuture = executorService.submit(() -> {
            try {
                latch.await();
                System.out.println("💸 [출금 시작]");
                WalletInOutRequest withdrawRequest = new WalletInOutRequest(
                        sender.getUserId(), CHARGE_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()
                );
                lockService.withdrawal(withdrawRequest);
                isWithdrawFirst.set(true);
            } catch (Exception e) {
                System.err.println("[출금 중 예외 발생]: " + e.getMessage());
            }
        });

        latch.countDown();
        withdrawFuture.get();
        transferFuture.get();
        executorService.shutdown();

        WalletBalance balance = balanceService.findBalance(senderWallet.getWalletId(), CURRENCY);
        WalletBalance balanceReceiver = balanceService.findBalance(receiverWallet.getWalletId(), CURRENCY);

        if (isWithdrawFirst.get()) {
            assertThat(balance.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        } else if (isTransferFirst.get()) {
            assertThat(balance.getBalance()).isEqualByComparingTo(TRANSFER_AMOUNT);
        }
    }

    /**
     * ✅ 충전과 이체가 동시에 발생할 때 이체가 실패하는지 확인하는 동시성 테스트
     */
    @RepeatedTest(5) // 5번 반복 실행
    void 계좌_충전과_이체가_동시에_발생하면_이체는_실패한다() throws Exception {
        // Given: 초기 잔액 설정
        System.out.println("초기 Sender 잔액 = " + balanceService.findBalance(senderWallet.getWalletId(), CURRENCY).getBalance());

        WalletInOutRequest chargeRequest = new WalletInOutRequest(
                sender.getUserId(), CHARGE_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()
        );

        WalletTransferRequest transferRequest = new WalletTransferRequest(
                sender.getUserId(), receiver.getUserId(), TRANSFER_AMOUNT, CURRENCY, CURRENCY, LocalDateTime.now()
        );

        CountDownLatch latch = new CountDownLatch(1); // 🔥 1로 설정 (이체 먼저 실행)

        Future<?> chargeFuture = executorService.submit(() -> {
            try {
                latch.await(); // 🔥 이체가 끝날 때까지 충전 대기
                System.out.println("💰 충전 시작");
                lockService.charge(chargeRequest);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        Future<?> transferFuture = executorService.submit(() -> {
            try {
                System.out.println("🚀 이체 시작");
                Exception exception = assertThrows(CommonException.class, () -> {
                    lockService.transfer(transferRequest);
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

