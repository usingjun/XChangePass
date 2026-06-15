package bumblebee.xchangepass.domain.wallet.transaction.service;

import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletTransactionService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    @Transactional
    public void saveTransaction(Long myWalletId, Long counterWalletId, BigDecimal amount, Currency fromCurrency, Currency toCurrency, WalletTransactionType transactionType) {
        Wallet myWallet = walletRepository.findById(myWalletId)
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
        User sender = myWallet.getUser();

        if (transactionType == WalletTransactionType.TRANSFER && counterWalletId == null)
            throw ErrorCode.RECEIVER_NOT_FOUND.commonException();

        User receiver = (counterWalletId != null)
                ? walletRepository.findById(counterWalletId)
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException).getUser()
                : null;

        transactionRepository.save(new WalletTransaction(
                sender,
                receiver,
                amount,
                currencyCode(fromCurrency),
                currencyCode(toCurrency),
                transactionType,
                LocalDateTime.now()
        ));
    }

    private String currencyCode(Currency currency) {
        return currency == null ? null : currency.getCurrencyCode();
    }
}
