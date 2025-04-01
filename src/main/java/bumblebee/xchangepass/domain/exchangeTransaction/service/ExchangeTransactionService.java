package bumblebee.xchangepass.domain.exchangeTransaction.service;


import bumblebee.xchangepass.domain.exchangeRate.service.ExchangeService;
import bumblebee.xchangepass.domain.exchangeTransaction.dto.request.ExchangeRequestDTO;
import bumblebee.xchangepass.domain.exchangeTransaction.dto.response.ExchangeResponseDTO;
import bumblebee.xchangepass.domain.exchangeTransaction.entitiy.ExchangeTransaction;
import bumblebee.xchangepass.domain.exchangeTransaction.entitiy.TransactionStatus;
import bumblebee.xchangepass.domain.exchangeTransaction.repository.ExchangeTransactionRepository;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.repository.WalletBalanceRepository;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletInOutRequest;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.service.WalletService;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExchangeTransactionService {

    private final ExchangeTransactionRepository repository;
    private final ExchangeService exchangeRateService;
    private final UserRepository userRepository;
    private final WalletBalanceService walletBalanceService;
    private final WalletService walletService;
    private final WalletBalanceRepository balanceRepository;


    public ExchangeResponseDTO createTransaction(ExchangeRequestDTO request, Long userId) {

        Map<String, Double> conversionRatess = exchangeRateService.getExchangeRateAll(request.fromCurrency())
                .conversionRates();


        Double exchangeRate = conversionRatess.get(request.toCurrency());

        if (exchangeRate == null) {
            throw ErrorCode.EXCHANGE_RATE_NOT_FOUND.commonException();
        }

        if (request.amount() == null) {
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
    public ExchangeResponseDTO executeTransaction(Long transactionId, Long userId) {
        ExchangeTransaction transaction = repository.findById(transactionId)
                .orElseThrow(ErrorCode.TRANSACTION_HISTORY_NOT_FOUND::commonException);

        if (!TransactionStatus.PENDING.equals(transaction.getStatus())) {
            throw ErrorCode.TRANSACTION_ALREADY_COMPLETED.commonException();
        }

        User user = userRepository.findByUserId(userId)
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);

        String fromCurrency = transaction.getFromCurrency();
        String toCurrency = transaction.getToCurrency();
        BigDecimal amount = transaction.getAmount();
        BigDecimal receivedAmount = transaction.getReceivedAmount();

        Wallet wallet = user.getWallet();

        WalletBalance fromBalance;
        try {
            fromBalance = walletBalanceService.findBalance(wallet.getWalletId(), Currency.getInstance(fromCurrency));
        } catch (CommonException e) {
            walletBalanceService.createBalance(wallet, Currency.getInstance(fromCurrency));
            fromBalance = walletBalanceService.findBalance(wallet.getWalletId(), Currency.getInstance(fromCurrency));
        }

        if (fromBalance.getBalance().compareTo(amount) < 0) {
            WalletInOutRequest chargeRequest = WalletInOutRequest.builder()
//                    .userId(userId)
                    .fromCurrency(Currency.getInstance(fromCurrency))
                    .toCurrency(Currency.getInstance(fromCurrency))
                    .amount(amount)
                    .chargeDatetime(LocalDateTime.now())
                    .build();

//            walletService.charge(chargeRequest);
        }

        WalletBalance toBalance;
        try {
            toBalance = walletBalanceService.findBalance(wallet.getWalletId(), Currency.getInstance(toCurrency));
        } catch (CommonException e) {
            walletBalanceService.createBalance(wallet, Currency.getInstance(toCurrency));
            toBalance = walletBalanceService.findBalance(wallet.getWalletId(), Currency.getInstance(toCurrency));
        }

        fromBalance.subtractBalance(amount);
        toBalance.addBalance(receivedAmount);


        transaction.changeStatus(TransactionStatus.COMPLETED);


        return ExchangeResponseDTO.toEntity(transaction);
    }
}
