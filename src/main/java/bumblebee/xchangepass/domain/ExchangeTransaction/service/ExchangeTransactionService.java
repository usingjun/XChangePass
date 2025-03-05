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
    private final ExchangeService exchangeRateService;

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

        Map<String, Double> conversionRatess = exchangeRateService.getExchangeRateAll(request.fromCurrency())
                .conversionRates();


        Double exchangeRate = conversionRatess.get(request.toCurrency());

        if (exchangeRate == null) {
            throw ErrorCode.EXCHANGE_RATE_NOT_FOUND.commonException();
        }

        if(request.amount() == null){
            throw ErrorCode.TRANSACTION_AMOUNT_NOTFOUND.commonException();
        }


        BigDecimal amount = request.amount();
        BigDecimal receivedAmount = amount.multiply(BigDecimal.valueOf(exchangeRate))
                .setScale(2, RoundingMode.HALF_UP);


        ExchangeTransaction transaction = ExchangeTransaction.builder()
                .userId(request.userId())
                .fromCurrency(request.fromCurrency())
                .toCurrency(request.toCurrency())
                .exchangeRate(BigDecimal.valueOf(exchangeRate))
                .amount(amount)
                .receivedAmount(receivedAmount)
                .status(TransactionStatus.PENDING)
                .build();

        ExchangeTransaction savedTransaction = repository.save(transaction);


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
