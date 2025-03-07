package bumblebee.xchangepass.domain.ExchangeTransaction.service;

import bumblebee.xchangepass.domain.ExchangeRate.service.ExchangeService;
import bumblebee.xchangepass.domain.ExchangeTransaction.dto.request.ExchangeRequestDTO;
import bumblebee.xchangepass.domain.ExchangeTransaction.dto.response.ExchangeResponseDTO;
import bumblebee.xchangepass.domain.ExchangeTransaction.entitiy.ExchangeTransaction;
import bumblebee.xchangepass.domain.ExchangeTransaction.entitiy.TransactionStatus;
import bumblebee.xchangepass.domain.ExchangeTransaction.repository.ExchangeTransactionRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ExchangeTransactionServiceTest {

    @Autowired
    private ExchangeTransactionService exchangeTransactionService;

    @Autowired
    private ExchangeService exchangeService;

    @Autowired
    private ExchangeTransactionRepository exchangeTransactionRepository;


    @Autowired
    EntityManager entityManager;
    @BeforeEach
    void resetAutoIncrement() {
        exchangeTransactionRepository.deleteAll();
        entityManager.createNativeQuery("ALTER TABLE exchange_transaction ALTER COLUMN exchange_transaction_id RESTART WITH 1").executeUpdate();
    }

    @Test
    @DisplayName("환전하기")
    public void Test1(){

        ExchangeRequestDTO requestDTO = ExchangeRequestDTO.builder()
                .userId(1L)
                .fromCurrency("USD")
                .toCurrency("KRW")
                .amount(BigDecimal.valueOf(100))
                .build();


        ExchangeResponseDTO transaction = exchangeTransactionService.createTransaction(requestDTO);

        assertEquals(1L, transaction.transactionId());
        assertNotNull(transaction.transactionId(), "거래 ID가 생성되어야 함");
        assertNotNull(transaction.amount(), "환전 금액은 필수 입니다.");
        assertEquals(BigDecimal.valueOf(100), transaction.amount(), "요청한 환전 금액과 동일해야 함");
        System.out.println(transaction);
    }

    @Test
    @DisplayName("🚨 환전 금액이 없을 경우 예외 발생 테스트")
    public void Test2(){
        ExchangeRequestDTO requestDTO = ExchangeRequestDTO.builder()
                .userId(1L)
                .fromCurrency("USD")
                .toCurrency("KRW")
                .amount(null)
                .build();

        assertThrows(ErrorCode.TRANSACTION_AMOUNT_NOTFOUND.commonException().getClass(),
                () -> exchangeTransactionService.createTransaction(requestDTO));

    }

    @Test
    @DisplayName("거래 실행 - 정상적인 경우")
    void Test3() {
        // 1️⃣ PENDING 상태의 거래 저장
        ExchangeTransaction transaction = ExchangeTransaction.builder()
                .userId(1L)
                .fromCurrency("USD")
                .toCurrency("KRW")
                .exchangeRate(BigDecimal.valueOf(1300))
                .amount(BigDecimal.valueOf(100))
                .receivedAmount(BigDecimal.valueOf(130000))
                .status(TransactionStatus.PENDING)
                .build();

        ExchangeTransaction savedTransaction = exchangeTransactionRepository.save(transaction);

        // 2️⃣ 거래 실행
        ExchangeResponseDTO response = exchangeTransactionService
                .executeTransaction(savedTransaction.getExchangeTransactionId());


        ExchangeTransaction exchangeTransaction = exchangeTransactionRepository
                .findById(savedTransaction.getExchangeTransactionId())
                .orElseThrow(NoSuchElementException::new);

        assertNotNull(response.transactionId());
        assertEquals(TransactionStatus.COMPLETED, exchangeTransaction.getStatus());
    }

    @Test
    @DisplayName("거래 실행 - 존재하지 않는 거래 ID 예외 발생")
    void Test4() {
        // 존재하지 않는 ID로 거래 실행 요청 → 예외 발생해야 함
        Long invalidTransactionId = 999L;

        assertThrows(ErrorCode.TRANSACTION_USERID_NOT_FOUND.commonException().getClass(), () ->
                exchangeTransactionService.executeTransaction(invalidTransactionId));

    }

    @Test
    @DisplayName("거래 실행 - 이미 완료된 거래 예외 발생")
    void Test5() {
        // 1️⃣ COMPLETED 상태의 거래 저장
        ExchangeTransaction transaction = ExchangeTransaction.builder()
                .userId(1L)
                .fromCurrency("USD")
                .toCurrency("KRW")
                .exchangeRate(BigDecimal.valueOf(1300))
                .amount(BigDecimal.valueOf(100))
                .receivedAmount(BigDecimal.valueOf(130000))
                .status(TransactionStatus.COMPLETED) // ✅ 이미 완료된 상태
                .build();

        ExchangeTransaction savedTransaction = exchangeTransactionRepository.save(transaction);

        assertThrows(ErrorCode.TRANSACTION_ALREADY_COMPLETED.commonException().getClass(),
                () -> exchangeTransactionService.executeTransaction(savedTransaction.getExchangeTransactionId()));
    }
}