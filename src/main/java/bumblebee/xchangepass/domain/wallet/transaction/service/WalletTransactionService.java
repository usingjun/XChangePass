package bumblebee.xchangepass.domain.wallet.transaction.service;

import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.domain.wallet.wallet.WalletTransactionProducer;
import bumblebee.xchangepass.domain.wallet.wallet.dto.response.WalletTransactionResponse;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletTransactionService {

    private final WalletTransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionProducer transactionProducer;

    @Transactional
    public void saveTransaction(Long myWalletId, Long counterWalletId, BigDecimal amount, Currency fromCurrency, Currency toCurrency, WalletTransactionType transactionType) {

        transactionProducer.sendAsyncTransaction(
                myWalletId,
                counterWalletId,
                amount,
                fromCurrency,
                toCurrency,
                transactionType
        );
    }

    @Transactional
    public List<WalletTransactionResponse> getTransaction(Long userId) {
        Wallet wallet = walletRepository.findByUserIdWithLock(userId);
        return transactionRepository.getWalletTransaction(wallet.getWalletId())
                .stream().map(WalletTransactionResponse::fromEntity)
                .toList();
    }

}