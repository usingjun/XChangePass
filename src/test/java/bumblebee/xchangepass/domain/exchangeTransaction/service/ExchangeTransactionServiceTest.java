package bumblebee.xchangepass.domain.exchangeTransaction.service;

import bumblebee.xchangepass.config.TestUserInitializer;
import bumblebee.xchangepass.domain.exchangeRate.service.ExchangeService;
import bumblebee.xchangepass.domain.exchangeTransaction.dto.request.ExchangeRequestDTO;
import bumblebee.xchangepass.domain.exchangeTransaction.dto.response.ExchangeResponseDTO;
import bumblebee.xchangepass.domain.exchangeTransaction.entitiy.ExchangeTransaction;
import bumblebee.xchangepass.domain.exchangeTransaction.entitiy.TransactionStatus;
import bumblebee.xchangepass.domain.exchangeTransaction.repository.ExchangeTransactionRepository;
import bumblebee.xchangepass.domain.user.dto.request.UserRegisterRequest;
import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.user.service.UserRegisterService;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.service.WalletService;
import bumblebee.xchangepass.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Import(TestUserInitializer.class)
class ExchangeTransactionServiceTest {

    @Autowired
    private ExchangeTransactionService exchangeTransactionService;

    @Autowired
    private ExchangeService exchangeService;

    @Autowired
    private UserRegisterService registerService;

    @Autowired
    private ExchangeTransactionRepository exchangeTransactionRepository;

    @Autowired
    private UserRepository userRepository;


    @Autowired
    EntityManager entityManager;
    @Autowired
    private WalletService walletService;
    @Autowired
    private WalletBalanceService walletBalanceService;

    @BeforeEach
    void resetAutoIncrement() {
        exchangeTransactionRepository.deleteAll();
        entityManager.createNativeQuery("ALTER TABLE exchange_transaction ALTER COLUMN exchange_transaction_id RESTART WITH 1").executeUpdate();
    }

    @Test
    @DisplayName("환전하기")
    public void Test1(){

        ExchangeRequestDTO requestDTO = ExchangeRequestDTO.builder()
                .fromCurrency("USD")
                .toCurrency("KRW")
                .amount(BigDecimal.valueOf(100))
                .build();

        Long userId = 1L;

        ExchangeResponseDTO transaction = exchangeTransactionService.createTransaction(requestDTO,userId);

        assertEquals(1L, transaction.transactionId());
        assertNotNull(transaction.transactionId(), "거래 ID가 생성되어야 함");
        assertNotNull(transaction.amount(), "환전 금액은 필수 입니다.");
        assertEquals(BigDecimal.valueOf(100), transaction.amount(), "요청한 환전 금액과 동일해야 함");
        System.out.println(transaction);
    }

    @Test
    @DisplayName("🚨 환전 금액이 없을 경우 예외 발생 테스트")
    public void Test2(){
        Long userId = 1L;
        ExchangeRequestDTO requestDTO = ExchangeRequestDTO.builder()
                .fromCurrency("USD")
                .toCurrency("KRW")
                .amount(null)
                .build();

        assertThrows(ErrorCode.TRANSACTION_AMOUNT_NOTFOUND.commonException().getClass(),
                () -> exchangeTransactionService.createTransaction(requestDTO,userId));

    }

    @Test
    @DisplayName("거래 실행 - 정상적인 경우")
    void Test3() {
        Long userId = 1L;
        User user = userRepository.findByUserId(userId)
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);
        // 1️⃣ PENDING 상태의 거래 저장
        ExchangeTransaction transaction = ExchangeTransaction.builder()
                .user(user)
                .fromCurrency("KRW")
                .toCurrency("USD")
                .exchangeRate(BigDecimal.valueOf(1300))
                .amount(BigDecimal.valueOf(100))
                .receivedAmount(BigDecimal.valueOf(130000))
                .status(TransactionStatus.PENDING)
                .build();

        ExchangeTransaction savedTransaction = exchangeTransactionRepository.save(transaction);

        // 2️⃣ 거래 실행
        System.out.println(savedTransaction.getFromCurrency());
        System.out.println(savedTransaction.getToCurrency());
        System.out.println(savedTransaction.getReceivedAmount()+"SSSSS");
        ExchangeResponseDTO response = exchangeTransactionService
                .executeTransaction(savedTransaction.getExchangeTransactionId(),userId);


        ExchangeTransaction exchangeTransaction = exchangeTransactionRepository
                .findById(savedTransaction.getExchangeTransactionId())
                .orElseThrow(NoSuchElementException::new);


        assertNotNull(response.transactionId());
        assertEquals(TransactionStatus.COMPLETED, exchangeTransaction.getStatus());
    }

    @Test
    @DisplayName("거래 실행 - 존재하지 않는 거래 ID 예외 발생")
    void Test4() {
        Long userId = 1L;
        // 존재하지 않는 ID로 거래 실행 요청 → 예외 발생해야 함
        Long invalidTransactionId = 999L;

        assertThrows(ErrorCode.TRANSACTION_USERID_NOT_FOUND.commonException().getClass(), () ->
                exchangeTransactionService.executeTransaction(invalidTransactionId,userId));

    }

    @Test
    @DisplayName("거래 실행 - 이미 완료된 거래 예외 발생")
    void Test5() {
        Long userId = 1L;

        User user = userRepository.findByUserId(userId)
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);
        // 1️⃣ COMPLETED 상태의 거래 저장
        ExchangeTransaction transaction = ExchangeTransaction.builder()
                .user(user)
                .fromCurrency("USD")
                .toCurrency("KRW")
                .exchangeRate(BigDecimal.valueOf(1300))
                .amount(BigDecimal.valueOf(100))
                .receivedAmount(BigDecimal.valueOf(130000))
                .status(TransactionStatus.COMPLETED) // ✅ 이미 완료된 상태
                .build();

        ExchangeTransaction savedTransaction = exchangeTransactionRepository.save(transaction);

        assertThrows(ErrorCode.TRANSACTION_ALREADY_COMPLETED.commonException().getClass(),
                () -> exchangeTransactionService.executeTransaction(savedTransaction.getExchangeTransactionId(),userId));
    }

    @Test
    @DisplayName("환전 실행시 지갑 업데이트 ")
    void Test6() {
        Long userId = 1L;
        User user = userRepository.findByUserId(userId)
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);

        ExchangeTransaction transaction1 = ExchangeTransaction.builder()
                .user(user)
                .fromCurrency("KRW")
                .toCurrency("USD")
                .exchangeRate(BigDecimal.valueOf(1300))
                .amount(BigDecimal.valueOf(100))
                .receivedAmount(BigDecimal.valueOf(130000))
                .status(TransactionStatus.PENDING)
                .build();

        ExchangeTransaction savedTransaction1 = exchangeTransactionRepository.save(transaction1);


        ExchangeResponseDTO response1 = exchangeTransactionService
                .executeTransaction(savedTransaction1.getExchangeTransactionId(), userId);


        Wallet wallet = user.getWallet();
        WalletBalance balanceFromCurrency1 = walletBalanceService.findBalance(wallet.getWalletId(), Currency.getInstance(response1.fromCurrency()));
        WalletBalance balanceToCurrency1 = walletBalanceService.findBalance(wallet.getWalletId(), Currency.getInstance(response1.toCurrency()));

        System.out.println("First Transaction (KRW -> USD):");
        System.out.println("KRW Balance: " + balanceFromCurrency1.getBalance());
        System.out.println("USD Balance: " + balanceToCurrency1.getBalance());


        ExchangeTransaction transaction2 = ExchangeTransaction.builder()
                .user(user)
                .fromCurrency("USD")
                .toCurrency("AED")
                .exchangeRate(BigDecimal.valueOf(100))
                .amount(BigDecimal.valueOf(100))
                .receivedAmount(BigDecimal.valueOf(10000))
                .status(TransactionStatus.PENDING)
                .build();


        ExchangeTransaction savedTransaction2 = exchangeTransactionRepository.save(transaction2);


        ExchangeResponseDTO response2 = exchangeTransactionService
                .executeTransaction(savedTransaction2.getExchangeTransactionId(), userId);


        WalletBalance balanceFromCurrency2 = walletBalanceService.findBalance(wallet.getWalletId(), Currency.getInstance(response2.fromCurrency()));
        WalletBalance balanceToCurrency2 = walletBalanceService.findBalance(wallet.getWalletId(), Currency.getInstance(response2.toCurrency()));

        System.out.println("Second Transaction (USD -> AED):");
        System.out.println("USD Balance: " + balanceFromCurrency2.getBalance());
        System.out.println("AED Balance: " + balanceToCurrency2.getBalance());

        ExchangeTransaction transaction3 = ExchangeTransaction.builder()
                .user(user)
                .fromCurrency("AMD")
                .toCurrency("ANG")
                .exchangeRate(BigDecimal.valueOf(2424))
                .amount(BigDecimal.valueOf(12))
                .receivedAmount(BigDecimal.valueOf(152356356))
                .status(TransactionStatus.PENDING)
                .build();


        ExchangeTransaction savedTransaction3 = exchangeTransactionRepository.save(transaction3);


        ExchangeResponseDTO response3 = exchangeTransactionService
                .executeTransaction(savedTransaction3.getExchangeTransactionId(), userId);


        WalletBalance balanceFromCurrency3 = walletBalanceService.findBalance(wallet.getWalletId(), Currency.getInstance(response3.fromCurrency()));
        WalletBalance balanceToCurrency3 = walletBalanceService.findBalance(wallet.getWalletId(), Currency.getInstance(response3.toCurrency()));

        System.out.println("Second Transaction (AMD -> ANG):");
        System.out.println("AMD Balance: " + balanceFromCurrency3.getBalance());
        System.out.println("AMG Balance: " + balanceToCurrency3.getBalance());

        assertEquals(TransactionStatus.COMPLETED, savedTransaction1.getStatus());
        assertEquals(TransactionStatus.COMPLETED, savedTransaction2.getStatus());
        assertEquals(TransactionStatus.COMPLETED, savedTransaction3.getStatus());
    }


}