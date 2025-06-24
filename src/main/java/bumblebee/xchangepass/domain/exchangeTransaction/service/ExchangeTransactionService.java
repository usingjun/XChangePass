package bumblebee.xchangepass.domain.exchangeTransaction.service;


import bumblebee.xchangepass.domain.exchangeRate.service.ExchangeService;
import bumblebee.xchangepass.domain.exchangeTransaction.dto.request.ExchangeRequestDTO;
import bumblebee.xchangepass.domain.transaction.dto.response.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.entity.TransactionDocument;
import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import bumblebee.xchangepass.domain.transaction.mapper.TransactionMetadataMapper;
import bumblebee.xchangepass.domain.transaction.repository.TransactionRepository;
import bumblebee.xchangepass.domain.transaction.service.RedisTransactionQueueService;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletInOutRequest;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.service.WalletService;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExchangeTransactionService {

    private final TransactionRepository transactionRepository;
    private final ExchangeService exchangeRateService;
    private final UserRepository userRepository;
    private final WalletBalanceService walletBalanceService;
    private final WalletService walletService;
    private final RedisTransactionQueueService redisTransactionQueueService;

    private static final String REDIS_KEY_PREFIX = "transactions:insert:";


    public void createTransaction(ExchangeRequestDTO request, Long userId) {

        Map<String, Double> conversionRates = exchangeRateService.getExchangeRateAll(request.fromCurrency())
                .conversionRates();


        Double exchangeRate = conversionRates.get(request.toCurrency());

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

        Map<String, Object> metadata = Map.of(
                "beforeAmount", request.amount(),
                "afterAmount", amount,
                "rate", exchangeRate,
                "transactionType", TransactionType.EXCHANGE
        );

        TransactionResponse response = new TransactionResponse(
                user.getUserId(),
                Currency.getInstance(request.fromCurrency()),
                Currency.getInstance(request.toCurrency()),
                LocalDateTime.now(),
                TransactionMetadataMapper.mapToDto(metadata)
        );

        // Redis로 임시 저장
        String redisKey = REDIS_KEY_PREFIX + user.getUserId();
        redisTransactionQueueService.enqueue(redisKey, response);
    }

    @Transactional
    public void executeTransaction(Long transactionId, Long userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);

        TransactionDocument transaction = transactionRepository.findByTransactionId(transactionId.toString());

        String fromCurrency = transaction.getBeforeCurrency().getCurrencyCode();
        String toCurrency = transaction.getAfterCurrency().getCurrencyCode();
        BigDecimal amount = (BigDecimal) transaction.getMetadata().get("amount");
        BigDecimal receivedAmount = (BigDecimal) transaction.getMetadata().get("afterAmount");

        Wallet wallet = user.getWallet();

        WalletBalance fromBalance = getOrCreateBalance(wallet, fromCurrency);

        if (fromBalance.getBalance().compareTo(amount) < 0) {
            WalletInOutRequest chargeRequest = WalletInOutRequest.builder()
                    .fromCurrency(Currency.getInstance(fromCurrency))
                    .toCurrency(Currency.getInstance(fromCurrency))
                    .amount(amount)
                    .chargeDatetime(LocalDateTime.now())
                    .build();

            walletService.charge(userId, chargeRequest);
        }

        WalletBalance toBalance = getOrCreateBalance(wallet, toCurrency);

        fromBalance.subtractBalance(amount);
        toBalance.addBalance(receivedAmount);
    }

    private WalletBalance getOrCreateBalance(Wallet wallet, String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode);
        if (!walletBalanceService.checkBalance(wallet.getWalletId(), currency)) {
            walletBalanceService.createBalance(wallet, currency);
        }
        return walletBalanceService.findBalance(wallet.getWalletId(), currency);
    }

}
