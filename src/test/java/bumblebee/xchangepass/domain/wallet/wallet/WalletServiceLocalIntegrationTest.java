package bumblebee.xchangepass.domain.wallet.wallet;

import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.repository.WalletBalanceRepository;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudEvaluationResult;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudReason;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudRuleEvaluator;
import bumblebee.xchangepass.domain.wallet.transaction.service.WalletTransactionService;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletInOutRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.entity.WalletTransferType;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.domain.wallet.wallet.service.impl.WalletServiceImpl;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "local.integration", matches = "true")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/xcp_verify",
        "spring.datasource.username=iyongjun",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.task.scheduling.enabled=false"
})
class WalletServiceLocalIntegrationTest {

    private static final Currency KRW = Currency.getInstance("KRW");

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

    private User sender;
    private User receiver;
    private Wallet senderWallet;
    private Wallet receiverWallet;

    @BeforeEach
    void setUp() {
        walletBalanceRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.flush();
        reset(fraudRuleEvaluator, walletTransactionService);
        when(fraudRuleEvaluator.evaluate(any(), any())).thenReturn(FraudEvaluationResult.clear());

        sender = createUser("sender");
        receiver = createUser("receiver");
        senderWallet = createWallet(sender);
        receiverWallet = createWallet(receiver);
    }

    @Test
    void concurrentTransfersKeepBalancesConsistent() throws Exception {
        int concurrentRequests = 100;
        BigDecimal transferAmount = new BigDecimal("1000.00");
        BigDecimal initialAmount = transferAmount.multiply(BigDecimal.valueOf(concurrentRequests));
        walletService.charge(sender.getUserId(), new WalletInOutRequest(initialAmount, KRW, KRW, null));

        WalletTransferRequest request = transferRequest(transferAmount);
        CountDownLatch startLatch = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(concurrentRequests);
        List<?> futures = java.util.stream.IntStream.range(0, concurrentRequests)
                .mapToObj(i -> executor.submit(() -> {
                    startLatch.await();
                    walletService.transfer(sender.getUserId(), request);
                    return null;
                }))
                .toList();

        startLatch.countDown();
        for (Object future : futures) {
            ((java.util.concurrent.Future<?>) future).get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(balance(senderWallet).getBalance()).isEqualByComparingTo("0.00");
        assertThat(balance(receiverWallet).getBalance()).isEqualByComparingTo(initialAmount);
    }

    @Test
    void suspiciousTransfersAreBlockedBeforeBalanceChange() {
        int suspiciousAttempts = 30;
        BigDecimal initialAmount = new BigDecimal("10000.00");
        walletService.charge(sender.getUserId(), new WalletInOutRequest(initialAmount, KRW, KRW, null));
        when(fraudRuleEvaluator.evaluate(any(), any()))
                .thenReturn(FraudEvaluationResult.suspicious(FraudReason.FREQUENCY_EXCEEDED, 40));

        AtomicInteger blockedCount = new AtomicInteger();
        for (int i = 0; i < suspiciousAttempts; i++) {
            assertThatThrownBy(() -> walletService.transfer(sender.getUserId(), transferRequest(new BigDecimal("1000.00"))))
                    .isInstanceOf(CommonException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.SUSPICIOUS_TRANSACTION);
            blockedCount.incrementAndGet();
        }

        assertThat(blockedCount).hasValue(suspiciousAttempts);
        assertThat(balance(senderWallet).getBalance()).isEqualByComparingTo(initialAmount);
        assertThat(balance(receiverWallet).getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void fraudDetectionFailuresBlockTransfersBeforeBalanceChange() {
        int unavailableAttempts = 30;
        BigDecimal initialAmount = new BigDecimal("10000.00");
        walletService.charge(sender.getUserId(), new WalletInOutRequest(initialAmount, KRW, KRW, null));
        when(fraudRuleEvaluator.evaluate(any(), any()))
                .thenThrow(ErrorCode.FRAUD_DETECTION_UNAVAILABLE.commonException());

        AtomicInteger blockedCount = new AtomicInteger();
        for (int i = 0; i < unavailableAttempts; i++) {
            assertThatThrownBy(() -> walletService.transfer(sender.getUserId(), transferRequest(new BigDecimal("1000.00"))))
                    .isInstanceOf(CommonException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FRAUD_DETECTION_UNAVAILABLE);
            blockedCount.incrementAndGet();
        }

        assertThat(blockedCount).hasValue(unavailableAttempts);
        assertThat(balance(senderWallet).getBalance()).isEqualByComparingTo(initialAmount);
        assertThat(balance(receiverWallet).getBalance()).isEqualByComparingTo("0.00");
    }

    private User createUser(String prefix) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(new User(
                prefix + id + "@example.com",
                "Password123!",
                prefix,
                prefix + id,
                "010-" + id.substring(0, 4) + "-" + id.substring(4),
                Sex.MALE,
                new BCryptPasswordEncoder()
        ));
    }

    private Wallet createWallet(User user) {
        walletService.createWallet(user, "1234");
        return walletRepository.findByUserId(user.getUserId())
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
    }

    private WalletTransferRequest transferRequest(BigDecimal amount) {
        return new WalletTransferRequest(
                receiver.getUserName().getValue(),
                receiver.getUserPhoneNumber().getValue(),
                amount,
                KRW,
                KRW,
                null,
                WalletTransferType.GENERAL
        );
    }

    private WalletBalance balance(Wallet wallet) {
        return balanceService.findBalance(wallet.getWalletId(), KRW);
    }
}
