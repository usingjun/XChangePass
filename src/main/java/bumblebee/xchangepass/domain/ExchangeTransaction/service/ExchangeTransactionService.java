package bumblebee.xchangepass.domain.ExchangeTransaction.service;


import bumblebee.xchangepass.domain.ExchangeRate.service.ExchangeService;
import bumblebee.xchangepass.domain.ExchangeTransaction.dto.request.ExchangeRequestDTO;
import bumblebee.xchangepass.domain.ExchangeTransaction.dto.response.ExchangeResponseDTO;
import bumblebee.xchangepass.domain.ExchangeTransaction.entitiy.ExchangeTransaction;
import bumblebee.xchangepass.domain.ExchangeTransaction.entitiy.TransactionStatus;
import bumblebee.xchangepass.domain.ExchangeTransaction.repository.ExchangeTransactionRepository;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
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
    private final UserRepository userRepository;

    public ExchangeTransactionService(ExchangeTransactionRepository repository, ExchangeService exchangeRateService, UserRepository userRepository) {
        this.repository = repository;
        this.exchangeRateService = exchangeRateService;
        this.userRepository = userRepository;
    }

    public ExchangeResponseDTO createTransaction(ExchangeRequestDTO request, Long userId) {

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


        User user = userRepository.findByUserId(userId)
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);

        ExchangeTransaction transaction = ExchangeTransaction.builder()
                .user(user)
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
