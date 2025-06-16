package bumblebee.xchangepass.domain.transaction.mongoV.dto;

import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;

import java.math.BigDecimal;

public record WalletTransactionDto (
        String receiver,
        BigDecimal amount,
        WalletTransactionType walletType
) implements TransactionDataDto{
}
