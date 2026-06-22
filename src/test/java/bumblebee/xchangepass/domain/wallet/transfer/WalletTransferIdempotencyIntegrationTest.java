package bumblebee.xchangepass.domain.wallet.transfer;

import bumblebee.xchangepass.domain.card.service.CardService;
import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.repository.WalletBalanceRepository;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectionService;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.domain.wallet.transfer.dto.WalletTransferResponse;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferFailureStage;
import bumblebee.xchangepass.domain.wallet.transfer.repository.WalletTransferRepository;
import bumblebee.xchangepass.domain.wallet.transfer.service.WalletTransferQueryService;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletInOutRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.entity.WalletTransferType;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.domain.wallet.wallet.service.impl.WalletFacadeService;
import bumblebee.xchangepass.domain.wallet.wallet.service.impl.WalletServiceImpl;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class WalletTransferIdempotencyIntegrationTest {

    private static final Currency KRW = Currency.getInstance("KRW");
    private static final BigDecimal INITIAL_AMOUNT = new BigDecimal("10000.00");
    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("1000.00");

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("xcp_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private WalletFacadeService facadeService;
    @Autowired
    private WalletServiceImpl walletService;
    @Autowired
    private WalletBalanceService balanceService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private WalletBalanceRepository balanceRepository;
    @Autowired
    private WalletTransactionRepository transactionRepository;
    @Autowired
    private WalletTransferRepository transferRepository;
    @Autowired
    private WalletTransferQueryService transferQueryService;

    @MockBean
    private FraudDetectionService fraudDetectionService;
    @MockBean
    private CardService cardService;

    private User sender;
    private User receiver;
    private Wallet senderWallet;
    private Wallet receiverWallet;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        transferRepository.deleteAll();
        balanceRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
        reset(fraudDetectionService, cardService);

        sender = createUser("send");
        receiver = createUser("recv");
        senderWallet = createWallet(sender);
        receiverWallet = createWallet(receiver);
    }

    @Test
    void completedDuplicateReturnsSameResultWithoutSecondTransfer() {
        charge(sender, INITIAL_AMOUNT);
        UUID key = UUID.randomUUID();
        WalletTransferRequest request = transferRequest(receiver, TRANSFER_AMOUNT);

        WalletTransferResponse first = facadeService.transfer(sender.getUserId(), key, request);
        WalletTransferResponse duplicate = facadeService.transfer(sender.getUserId(), key, request);

        assertThat(duplicate).isEqualTo(first);
        assertThat(first.status()).isEqualTo(WalletTransferStatus.COMPLETED);
        var stored = transferRepository.findById(first.transferId()).orElseThrow();
        assertThat(stored.getAttemptCount()).isEqualTo(1);
        assertThat(stored.getValidatingAt()).isNotNull();
        assertThat(stored.getProcessingAt()).isNotNull();
        assertThat(stored.getCompletedAt()).isNotNull();
        assertSingleMoneyMovement(INITIAL_AMOUNT.subtract(TRANSFER_AMOUNT), TRANSFER_AMOUNT);
        verify(fraudDetectionService, times(1)).verify(any());
    }

    @Test
    void thirtyConcurrentDuplicatesExecuteMoneyMovementOnce() throws Exception {
        charge(sender, INITIAL_AMOUNT);
        UUID key = UUID.randomUUID();
        WalletTransferRequest request = transferRequest(receiver, TRANSFER_AMOUNT);
        int requests = 30;
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(requests);

        List<Future<Object>> futures = java.util.stream.IntStream.range(0, requests)
                .mapToObj(i -> executor.submit(() -> {
                    start.await();
                    try {
                        return facadeService.transfer(sender.getUserId(), key, request);
                    } catch (CommonException e) {
                        return e.getErrorCode();
                    }
                }))
                .toList();

        start.countDown();
        List<Object> results = new java.util.ArrayList<>();
        for (Future<Object> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        List<WalletTransferResponse> responses = results.stream()
                .filter(WalletTransferResponse.class::isInstance)
                .map(WalletTransferResponse.class::cast)
                .toList();
        assertThat(responses).isNotEmpty();
        assertThat(responses).extracting(WalletTransferResponse::transferId).containsOnly(responses.get(0).transferId());
        assertThat(results).allMatch(result -> result instanceof WalletTransferResponse
                || result == ErrorCode.TRANSACTION_IN_PROGRESS);
        assertThat(transferRepository.count()).isEqualTo(1);
        assertSingleMoneyMovement(INITIAL_AMOUNT.subtract(TRANSFER_AMOUNT), TRANSFER_AMOUNT);
        verify(fraudDetectionService, times(1)).verify(any());
    }

    @Test
    void sameKeyWithDifferentAmountIsRejected() {
        charge(sender, INITIAL_AMOUNT);
        UUID key = UUID.randomUUID();
        facadeService.transfer(sender.getUserId(), key, transferRequest(receiver, TRANSFER_AMOUNT));

        assertThatThrownBy(() -> facadeService.transfer(
                sender.getUserId(), key, transferRequest(receiver, TRANSFER_AMOUNT.add(BigDecimal.ONE))
        ))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertSingleMoneyMovement(INITIAL_AMOUNT.subtract(TRANSFER_AMOUNT), TRANSFER_AMOUNT);
    }

    @Test
    void failedDuplicateReturnsSameFailureWithoutRetryingFraudValidation() {
        UUID key = UUID.randomUUID();
        WalletTransferRequest request = transferRequest(receiver, TRANSFER_AMOUNT);

        assertThatThrownBy(() -> facadeService.transfer(sender.getUserId(), key, request))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BALANCE_NOT_AVAILABLE);
        assertThatThrownBy(() -> facadeService.transfer(sender.getUserId(), key, request))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BALANCE_NOT_AVAILABLE);

        var failed = transferRepository.findAll().get(0);
        assertThat(failed.getStatus()).isEqualTo(WalletTransferStatus.FAILED);
        assertThat(failed.getFailureStage()).isEqualTo(WalletTransferFailureStage.BALANCE_VALIDATION);
        assertThat(failed.getRetryable()).isFalse();
        assertThat(failed.getFailedAt()).isNotNull();
        verify(fraudDetectionService, times(1)).verify(any());
        assertThat(countTransferLedgers()).isZero();
    }

    @Test
    void fraudUnavailableIsRecordedAsRetryableWithoutMoneyMovement() {
        charge(sender, INITIAL_AMOUNT);
        UUID key = UUID.randomUUID();
        doThrow(ErrorCode.FRAUD_DETECTION_UNAVAILABLE.commonException())
                .when(fraudDetectionService).verify(any());

        assertThatThrownBy(() -> facadeService.transfer(
                sender.getUserId(), key, transferRequest(receiver, TRANSFER_AMOUNT)
        ))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FRAUD_DETECTION_UNAVAILABLE);

        var failed = transferRepository.findAll().get(0);
        assertThat(failed.getFailureStage()).isEqualTo(WalletTransferFailureStage.FRAUD_VALIDATION);
        assertThat(failed.getRetryable()).isTrue();
        assertThat(countTransferLedgers()).isZero();
        assertBalances(INITIAL_AMOUNT, BigDecimal.ZERO);
    }

    @Test
    void statusCanOnlyBeReadByOwningSender() {
        charge(sender, INITIAL_AMOUNT);
        WalletTransferResponse response = facadeService.transfer(
                sender.getUserId(), UUID.randomUUID(), transferRequest(receiver, TRANSFER_AMOUNT)
        );

        assertThat(transferQueryService.findStatus(sender.getUserId(), response.transferId()).status())
                .isEqualTo(WalletTransferStatus.COMPLETED);
        assertThatThrownBy(() -> transferQueryService.findStatus(receiver.getUserId(), response.transferId()))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_HISTORY_NOT_FOUND);
    }

    @Test
    void differentUsersMayUseSameIdempotencyKey() {
        charge(sender, INITIAL_AMOUNT);
        charge(receiver, INITIAL_AMOUNT);
        UUID key = UUID.randomUUID();

        WalletTransferResponse first = facadeService.transfer(
                sender.getUserId(), key, transferRequest(receiver, TRANSFER_AMOUNT)
        );
        WalletTransferResponse second = facadeService.transfer(
                receiver.getUserId(), key, transferRequest(sender, TRANSFER_AMOUNT)
        );

        assertThat(first.transferId()).isNotEqualTo(second.transferId());
        assertThat(transferRepository.count()).isEqualTo(2);
        assertThat(countTransferLedgers()).isEqualTo(2);
    }

    private User createUser(String prefix) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(new User(
                prefix + id + "@example.com",
                "Password123!",
                prefix,
                prefix + id,
                prefix.equals("send") ? "010-1111-2222" : "010-3333-4444",
                Sex.MALE,
                new BCryptPasswordEncoder()
        ));
    }

    private Wallet createWallet(User user) {
        walletService.createWallet(user, "1234");
        return walletRepository.findByUserId(user.getUserId()).orElseThrow();
    }

    private void charge(User user, BigDecimal amount) {
        walletService.charge(user.getUserId(), new WalletInOutRequest(amount, KRW, KRW, null));
    }

    private WalletTransferRequest transferRequest(User target, BigDecimal amount) {
        return new WalletTransferRequest(
                target.getUserName().getValue(),
                target.getUserPhoneNumber().getValue(),
                amount,
                KRW,
                KRW,
                null,
                WalletTransferType.GENERAL
        );
    }

    private void assertSingleMoneyMovement(BigDecimal senderAmount, BigDecimal receiverAmount) {
        assertBalances(senderAmount, receiverAmount);
        assertThat(countTransferLedgers()).isEqualTo(1);
    }

    private void assertBalances(BigDecimal senderAmount, BigDecimal receiverAmount) {
        WalletBalance senderBalance = balanceService.findBalance(senderWallet.getWalletId(), KRW);
        WalletBalance receiverBalance = balanceService.findBalance(receiverWallet.getWalletId(), KRW);
        assertThat(senderBalance.getBalance()).isEqualByComparingTo(senderAmount);
        assertThat(receiverBalance.getBalance()).isEqualByComparingTo(receiverAmount);
    }

    private long countTransferLedgers() {
        return transactionRepository.findAll().stream()
                .filter(transaction -> transaction.getTransactionType() == WalletTransactionType.TRANSFER)
                .count();
    }
}
