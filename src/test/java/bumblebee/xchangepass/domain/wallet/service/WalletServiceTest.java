package bumblebee.xchangepass.domain.wallet.service;

import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.wallet.dto.request.WalletChargeRequest;
import bumblebee.xchangepass.domain.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.repository.WalletRepository;
import bumblebee.xchangepass.domain.walletBalance.entity.WalletBalance;
import bumblebee.xchangepass.domain.walletBalance.repository.WalletBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    private final BigDecimal transferAmount = new BigDecimal("5000.00");
    private final BigDecimal chargeAmount = new BigDecimal("5000000.00");
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletBalanceRepository walletBalanceRepository;
    @InjectMocks
    private WalletService walletService;
    private User userA;
    private User userB;

    @BeforeEach
    void setup() {
        userA = new User(
                1L, "userA@example.com", "passwordA1!", "UserA",
                "A_Nickname", "010-1234-5678", 25, Sex.MALE,
                new BCryptPasswordEncoder()
        );

        userB = new User(
                2L, "userB@example.com", "passwordB1!", "UserB",
                "B_Nickname", "010-9876-5432", 30, Sex.FEMALE,
                new BCryptPasswordEncoder()
        );
    }

    @Test
    void 동시에_같은_계좌에_송금이_발생한다() throws InterruptedException {
        Wallet walletA = new Wallet(1L, userA);
        Wallet walletB = new Wallet(2L, userB);

        Currency usd = Currency.getInstance("USD");

        // ✅ Mock 객체가 아니라 실제 WalletBalance 객체 사용
        WalletBalance walletBalanceA = new WalletBalance(1L, walletA, usd);
        walletBalanceA.addBalance(BigDecimal.ZERO); // 초기 잔액 설정

        WalletBalance walletBalanceB = new WalletBalance(2L, walletB, usd);
        walletBalanceB.addBalance(BigDecimal.ZERO); // 초기 잔액 설정

        when(walletRepository.findByUserId(userA.getUserId())).thenReturn(walletA);
        when(walletRepository.findByUserId(userB.getUserId())).thenReturn(walletB);
        when(walletBalanceRepository.findByWalletIdAndCurrency(walletA.getWalletId(), usd))
                .thenReturn(walletBalanceA);
        when(walletBalanceRepository.findByWalletIdAndCurrency(walletB.getWalletId(), usd))
                .thenReturn(walletBalanceB);


        WalletChargeRequest chargeRequest = new WalletChargeRequest(
                userA.getUserId(), chargeAmount, usd, Currency.getInstance("USD"), LocalDateTime.now()
        );

        walletService.charge(chargeRequest); // ✅ 충전 실행

        System.out.println("walletBalanceA = " + walletBalanceA.getBalance());
        System.out.println("walletBalanceB = " + walletBalanceB.getBalance());

        int concurrentUsers = 100;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch countDownLatch = new CountDownLatch(concurrentUsers);

        for (int i = 0; i < concurrentUsers; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    System.out.println(Thread.currentThread().getName() + " - 송금 요청 시작");

                    WalletTransferRequest transferRequest = new WalletTransferRequest(
                            userA.getUserId(), userB.getUserId(), transferAmount, usd, usd, LocalDateTime.now()
                    );

                    System.out.println("walletBalanceA = " + walletBalanceRepository.findByWalletIdAndCurrency(walletA.walletId, usd).getBalance());
                    System.out.println("walletBalanceB = " + walletBalanceRepository.findByWalletIdAndCurrency(walletB.walletId, usd).getBalance());
                    walletService.transfer(transferRequest);

                    System.out.println(Thread.currentThread().getName() + " - 송금 완료");
                } catch (Exception e) {
                    System.err.println("송금 중 예외 발생: " + e.getMessage());
                } finally {
                    countDownLatch.countDown(); // ✅ 예외 발생해도 countDownLatch가 실행되도록 finally 블록 사용
                }
            }, executorService));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        countDownLatch.await();
        executorService.shutdown();

        System.out.println("walletBalanceA = " + walletBalanceA.getBalance());
        System.out.println("walletBalanceB = " + walletBalanceB.getBalance());

        // then
        // ✅ 예상 결과 검증 (walletA 잔액 감소, walletB 잔액 증가)
        assertThat(walletBalanceA.getBalance()).isEqualByComparingTo(
                chargeAmount.subtract(transferAmount.multiply(new BigDecimal(concurrentUsers)))
        );
        assertThat(walletBalanceB.getBalance()).isEqualByComparingTo(
                transferAmount.multiply(new BigDecimal(concurrentUsers))
        );
    }

    @Test
    void 송금_도중_발생한_출금은_실패한다() {
        // given
        Wallet walletA = new Wallet(1L, userA);
        Wallet walletB = new Wallet(2L, userB);
        Currency usd = Currency.getInstance("USD");

        WalletBalance walletBalanceA = new WalletBalance(1L, walletA, usd);
        WalletBalance walletBalanceB = new WalletBalance(2L, walletB, usd);

        when(walletRepository.findByUserId(userA.getUserId())).thenReturn(walletA);
        when(walletRepository.findByUserId(userB.getUserId())).thenReturn(walletB);
        when(walletBalanceRepository.findByWalletIdAndCurrency(walletA.getWalletId(), usd))
                .thenReturn(walletBalanceA);
        when(walletBalanceRepository.findByWalletIdAndCurrency(walletB.getWalletId(), usd))
                .thenReturn(walletBalanceB);

        WalletChargeRequest chargeRequest = new WalletChargeRequest(
                userA.getUserId(), BigDecimal.valueOf(10000), usd, usd, LocalDateTime.now()
        );

        walletService.charge(chargeRequest); // ✅ 충전 실행

        // when + then
        var transferAmount = BigDecimal.valueOf(5000);
        AtomicReference<BigDecimal> withdrawAmount = new AtomicReference<>(BigDecimal.ZERO);

        var future1 = CompletableFuture.runAsync(() ->
                {
                    WalletChargeRequest withdrawRequest = new WalletChargeRequest(
                            userA.getUserId(), BigDecimal.valueOf(10000), usd, usd, LocalDateTime.now()
                    );
                    withdrawAmount.set(walletService.withdrawal(withdrawRequest));
                }
        );


        var future2 = CompletableFuture.runAsync(() ->
        {
            try {
                WalletTransferRequest transferRequest = new WalletTransferRequest(
                        userA.getUserId(), userB.getUserId(), transferAmount, usd, usd, LocalDateTime.now()
                );

                walletService.transfer(transferRequest);
            } catch (Exception e) {
                System.err.println("송금 중 예외 발생: " + e.getMessage());
            }
        });

        CompletableFuture.allOf(future1, future2).join();  // wait

        var receiverAmount = walletBalanceRepository.findByWalletIdAndCurrency(walletB.walletId, usd).getBalance();
        var senderAmount = walletBalanceRepository.findByWalletIdAndCurrency(walletA.walletId, usd).getBalance();

        System.out.println("walletBalanceA = " + senderAmount);
        System.out.println("receiverAmount = " + receiverAmount);
        System.out.println("withdrawAmount = " + withdrawAmount.get());
        assertThat(receiverAmount).isEqualTo(transferAmount);  // 송금 성공
        assertThat(withdrawAmount.get()).isEqualTo(BigDecimal.ZERO);   // 출금 실패
    }

    @Test
    void transaction() {
    }

    @Test
    void charge() {
    }

    @Test
    void transfer() {
    }

    @Test
    void balance() {
    }

}