package bumblebee.xchangepass.domain.wallet.transaction;

import bumblebee.xchangepass.config.TestUserInitializer;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.balance.repository.WalletBalanceRepository;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionStatus;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.domain.wallet.transaction.service.WalletTransactionService;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TestUserInitializer.class)
public class WalletTransactionIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletBalanceService balanceService;

    @Autowired
    private WalletTransactionService transactionService;

    @Autowired
    private WalletTransactionRepository transactionRepository;


    @BeforeEach
    void clearTransactions() {
        transactionRepository.deleteAll(); // 트랜잭션만 초기화
    }

    @Test
    @DisplayName("TestUser1의 지갑에 충전하면 거래내역이 비동기로 저장되는지 확인")
    void testChargeAndAsyncTransactionSaved() throws InterruptedException {
        // given
        User user = userRepository.findByUserEmail("Test1@gmail.com")
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);

        Wallet wallet = walletRepository.findByUserId(user.getUserId());

        Currency currency = Currency.getInstance("KRW");

        if (!balanceService.checkBalance(wallet.getWalletId(), currency)) {
            balanceService.createBalance(wallet, currency);
        }

        var balance = balanceService.findBalanceWithLock(wallet.getWalletId(), currency);

        // when
        balanceService.chargeBalance(balance, new BigDecimal("5000")); // 💰 동기 잔액 증가
        System.out.println("balance.getBalance().toString() = " + balance.getBalance().toString());

        // then
        Thread.sleep(3000); // ⏱ 메시지 큐 비동기 소비 기다림

        List<WalletTransaction> transactions = transactionRepository.getWalletTransaction(wallet.getWalletId());
        assertEquals(1, transactions.size());

        WalletTransaction tx = transactions.get(0);
        assertEquals(WalletTransactionType.DEPOSIT, tx.getTransactionType());
        assertEquals(WalletTransactionStatus.SUCCESS, tx.getStatus());
        assertEquals("KRW", tx.getToCurrency().getCurrencyCode());
        assertEquals(wallet.getWalletId(), tx.getMyWallet().getWalletId());

        System.out.println("[✓] 거래내역 저장 확인 완료 → txId: " + tx.getWalletTransactionId());
    }

}
