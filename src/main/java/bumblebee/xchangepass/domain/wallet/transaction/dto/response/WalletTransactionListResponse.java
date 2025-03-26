package bumblebee.xchangepass.domain.wallet.transaction.dto.response;

import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionStatus;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

public record WalletTransactionListResponse(

        Long walletTransactionId,

        Wallet myWallet,

        Wallet counterWallet,

        BigDecimal amount,

        Currency fromCurrency,

        Currency toCurrency,

        WalletTransactionType transactionType,

        WalletTransactionStatus status,

        LocalDateTime updatedAt

) {
    public WalletTransactionListResponse fromEntity(WalletTransaction transaction) {
        return new WalletTransactionListResponse(
                transaction.getWalletTransactionId(),
                transaction.getMyWallet(),
                transaction.getCounterWallet(),
                transaction.getAmount(),
                transaction.getFromCurrency(),
                transaction.getToCurrency(),
                transaction.getTransactionType(),
                transaction.getStatus(),
                transaction.getUpdatedAt()
        );
    }
}
