package bumblebee.xchangepass.domain.wallet.transaction;

import bumblebee.xchangepass.config.TestUserInitializer;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.balance.repository.WalletBalanceRepository;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.domain.wallet.transaction.service.WalletTransactionService;
import bumblebee.xchangepass.domain.wallet.transaction.consumer.DeadLetterConsumer;
import bumblebee.xchangepass.domain.wallet.transaction.consumer.SlackNotifier;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransactionMessage;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TestUserInitializer.class)
public class WalletTransactionIntegrationTest {

    @Autowired private UserRepository userRepository;

    @Autowired private WalletRepository walletRepository;

    @Autowired private WalletBalanceRepository balanceRepository;

    @Autowired private WalletBalanceService balanceService;

    @Autowired private WalletTransactionService transactionService;

    @Autowired private WalletTransactionRepository transactionRepository;

    @MockBean
    private SlackNotifier slackNotifier;

    private Wallet testWallet1;
    private Wallet testWallet2;
    Currency krw = Currency.getInstance("KRW");

    @BeforeAll
    void setup() {
        User user1 = userRepository.findByUserEmail("Test1@gmail.com").orElseThrow();
        User user2 = userRepository.findByUserEmail("Test2@gmail.com").orElseThrow();

        testWallet1 = walletRepository.findByUserId(user1.getUserId());
        testWallet2 = walletRepository.findByUserId(user2.getUserId());

        if (!balanceService.checkBalance(testWallet1.getWalletId(), krw))
            balanceService.createBalance(testWallet1, krw);
        if (!balanceService.checkBalance(testWallet2.getWalletId(), krw))
            balanceService.createBalance(testWallet2, krw);
    }

    @BeforeEach
    void clearTransactions() {
        transactionRepository.deleteAll(); // 트랜잭션만 초기화
        balanceRepository.zeroBalance(testWallet1.getWalletId(), krw);
        balanceRepository.zeroBalance(testWallet2.getWalletId(), krw);
    }

    @Test
    @DisplayName("💰 1. 충전(DEPOSIT) 트랜잭션 비동기 저장 확인")
    void depositTransactionTest() throws InterruptedException {
        var balance = balanceService.findBalanceWithLock(testWallet1.getWalletId(), Currency.getInstance("KRW"));
        System.out.println("balance.getBalance() = " + balance.getBalance());
        balanceService.chargeBalance(balance, new BigDecimal("5000"));

        Thread.sleep(3000);

        var transactions = transactionRepository.getWalletTransaction(testWallet1.getWalletId());
        assertEquals(1, transactions.size());
        assertEquals(WalletTransactionType.DEPOSIT, transactions.get(0).getTransactionType());
    }

    @Test
    @DisplayName("💸 2-1. 인출(WITHDRAW) - 성공")
    void withdrawSuccessTest() throws InterruptedException {
        var balance = balanceService.findBalanceWithLock(testWallet1.getWalletId(), Currency.getInstance("KRW"));
        System.out.println("balance.getBalance() = " + balance.getBalance());
        balanceService.chargeBalance(balance, new BigDecimal("5000"));
        System.out.println("balance.getBalance() = " + balance.getBalance());

        balanceService.withdrawBalance(balance, new BigDecimal("2000"));

        Thread.sleep(3000);

        var transactions = transactionRepository.getWalletTransaction(testWallet1.getWalletId());
        assertEquals(2, transactions.size()); // DEPOSIT + WITHDRAW
    }

    @Test
    @DisplayName("🚫 2-2. 인출(WITHDRAW) - 잔액 부족으로 실패")
    void withdrawFailTest() throws InterruptedException {
        var balance = balanceService.findBalanceWithLock(testWallet1.getWalletId(), Currency.getInstance("KRW"));
        System.out.println("balance.getBalance() = " + balance.getBalance());
        try {
            balanceService.withdrawBalance(balance, new BigDecimal("9999999"));
        } catch (Exception ignored) {}

        Thread.sleep(3000);

        List<WalletTransaction> txs = transactionRepository.getWalletTransaction(testWallet1.getWalletId());
        assertTrue(txs.isEmpty());
    }

    @Test
    @DisplayName("💱 3-1. 송금(TRANSFER) - 성공")
    void transferSuccessTest() throws InterruptedException {
        var fromBalance = balanceService.findBalanceWithLock(testWallet1.getWalletId(), Currency.getInstance("KRW"));
        var toBalance = balanceService.findBalanceWithLock(testWallet2.getWalletId(), Currency.getInstance("KRW"));
        System.out.println("fromBalance.getBalance() = " + fromBalance.getBalance());

        balanceService.chargeBalance(fromBalance, new BigDecimal("5000"));
        balanceService.transferBalance(fromBalance, toBalance, new BigDecimal("1000"));
        System.out.println("fromBalance.getBalance() = " + fromBalance.getBalance());
        System.out.println("toBalance.getBalance() = " + toBalance.getBalance());


        Thread.sleep(3000);

        List<WalletTransaction> senderTransaction = transactionRepository.getWalletTransaction(testWallet1.getWalletId());
        List<WalletTransaction> receiverTransaction = transactionRepository.getWalletTransaction(testWallet2.getWalletId());

        System.out.println("senderTransaction = " + senderTransaction.get(1).getWalletTransactionId());
        System.out.println("receiverTransaction = " + receiverTransaction.get(0).getWalletTransactionId());

        assertEquals(1, senderTransaction.stream()
                .filter(tx -> tx.getTransactionType() == WalletTransactionType.TRANSFER &&
                              tx.getMyWallet().getWalletId().equals(testWallet1.getWalletId()) &&
                              tx.getCounterWallet() != null &&
                              tx.getCounterWallet().getWalletId().equals(testWallet2.getWalletId()))
                .count(), "보내는 쪽 트랜잭션이 정확하지 않습니다");

        assertEquals(1, receiverTransaction.stream()
                .filter(tx -> tx.getTransactionType() == WalletTransactionType.TRANSFER &&
                              tx.getMyWallet().getWalletId().equals(testWallet1.getWalletId()) &&
                              tx.getCounterWallet() != null &&
                              tx.getCounterWallet().getWalletId().equals(testWallet2.getWalletId()))
                .count(), "받는 쪽 트랜잭션이 정확하지 않습니다");
    }

    @Test
    @DisplayName("📤 4. DLQ/Retry 시나리오 (수동 트리거 필요)")
    void retryAndDLQTest() throws InterruptedException, IOException {
        // 실패 메시지를 직접 전송하는 코드 필요
        // 실패 케이스 전송 → retry → DLQ 확인 → 슬랙 전송까지 수동 확인
        // 혹은 mock/slack-notifier 사용

        WalletTransactionMessage message = new WalletTransactionMessage(testWallet1.getWalletId(),null, new BigDecimal("10000000"),krw.getCurrencyCode(),krw.getCurrencyCode(),WalletTransactionType.WITHDRAWAL.toString()); // 실패 메시지

        Map<String, Object> xDeath = Map.of("count", 3L);
        List<Map<String, Object>> xDeathHeader = List.of(xDeath);
        var channel = Mockito.mock(Channel.class);

        DeadLetterConsumer consumer = new DeadLetterConsumer(new RabbitTemplate(), slackNotifier);
        consumer.handleDeadLetter(message, 1L, xDeathHeader, channel);

        verify(slackNotifier, times(1)).send(contains("DLQ 처리 실패"));
    }

    @Test
    @DisplayName("🔍 5. 거래내역 필터링 조회")
    void filterTransactionTest() throws InterruptedException {
        var balance = balanceService.findBalanceWithLock(testWallet1.getWalletId(), Currency.getInstance("KRW"));
        System.out.println("balance.getBalance() = " + balance.getBalance());

        balanceService.chargeBalance(balance, new BigDecimal("1000"));
        Thread.sleep(2000);

        var transactions = transactionRepository.findAll(); // 필요시 커스텀 조회로 교체
        var deposits = transactions.stream().filter(t -> t.getTransactionType() == WalletTransactionType.DEPOSIT).toList();

        assertEquals(1, deposits.size());
    }

}
