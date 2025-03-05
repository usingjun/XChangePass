package bumblebee.xchangepass.domain.ExchangeTransaction.service;


import bumblebee.xchangepass.domain.ExchangeRate.service.ExchangeService;
import bumblebee.xchangepass.domain.ExchangeTransaction.dto.request.ExchangeRequestDTO;
import bumblebee.xchangepass.domain.ExchangeTransaction.dto.response.ExchangeResponseDTO;
import bumblebee.xchangepass.domain.ExchangeTransaction.entitiy.ExchangeTransaction;
import bumblebee.xchangepass.domain.ExchangeTransaction.entitiy.TransactionStatus;
import bumblebee.xchangepass.domain.ExchangeTransaction.repository.ExchangeTransactionRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service

public class ExchangeTransactionService {

    private final ExchangeTransactionRepository repository;
    private final ExchangeService exchangeRateService;  // 🚀 환율 조회 서비스 추가

    public ExchangeTransactionService(ExchangeTransactionRepository repository, ExchangeService exchangeRateService) {
        this.repository = repository;
        this.exchangeRateService = exchangeRateService;
    }

    public ExchangeResponseDTO createTransaction(ExchangeRequestDTO request) {

        // 🔥 같은 사용자의 PENDING 상태 거래가 있는지 확인
//        ExchangeTransaction existingTransaction = repository
//                .findByUserIdAndFromCurrencyAndToCurrencyAndStatus(
//                        request.userId(),
//                        request.fromCurrency(),
//                        request.toCurrency(),
//                        TransactionStatus.PENDING
//                );
//        if (existingTransaction != null) {
//            return ExchangeResponseDTO.toEntity(existingTransaction);
//        }
        // 1 Exchangerate-api에서 환율 정보 가져오기
        Map<String, Double> conversionRatess = exchangeRateService.getExchangeRateAll(request.fromCurrency())
                .conversionRates();

        // 2 요청한 toCurrency에 대한 환율 찾기
        Double exchangeRate = conversionRatess.get(request.toCurrency());

        if (exchangeRate == null) {
            throw ErrorCode.EXCHANGE_RATE_NOT_FOUND.commonException();
        }

        if(request.amount() == null){
            throw ErrorCode.TRANSACTION_AMOUNT_NOTFOUND.commonException();
        }

        // 3 환전 금액 계산
        BigDecimal amount = request.amount();
        BigDecimal receivedAmount = amount.multiply(BigDecimal.valueOf(exchangeRate))
                .setScale(2, RoundingMode.HALF_UP);

        // 4 트랜잭션 객체 생성 및 저장
        ExchangeTransaction transaction = ExchangeTransaction.builder()
                .userId(request.userId())
                .fromCurrency(request.fromCurrency())
                .toCurrency(request.toCurrency())
                .exchangeRate(BigDecimal.valueOf(exchangeRate))  // 🚀 환율 저장
                .amount(amount)  // 환전할 금액
                .receivedAmount(receivedAmount)  // 환전 후 받을 금액
                .status(TransactionStatus.PENDING)
                .build();

        ExchangeTransaction savedTransaction = repository.save(transaction);

        // 5 DTO 변환 후 반환
        return ExchangeResponseDTO.toEntity(savedTransaction);
    }

    @Transactional
    public ExchangeResponseDTO executeTransaction(Long transactionId) {
        ExchangeTransaction transaction = repository.findById(transactionId)
                .orElseThrow(ErrorCode.TRANSACTION_HISTORY_NOT_FOUND::commonException);

        if (!TransactionStatus.PENDING.equals(transaction.getStatus())) {
            throw ErrorCode.TRANSACTION_ALREADY_COMPLETED.commonException();
        }

        transaction.setStatus(TransactionStatus.COMPLETED);
        repository.save(transaction);

        return ExchangeResponseDTO.toEntity(transaction);
    }
}
