package bumblebee.xchangepass.domain.transaction.mongoV.dto;

import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;

import java.math.BigDecimal;
import java.util.Map;

public record WalletTransactionDto (
        String receiver,
        BigDecimal amount,
        WalletTransactionType walletType
) implements TransactionDataDto{
    @Override
    public Map<String, Object> toMap() {
        return Map.of(
                "receiver",receiver,
                "amount", amount,
                "walletType", walletType
        );
    }
}
