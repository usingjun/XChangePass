package bumblebee.xchangepass.domain.transaction.mongoV.dto;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;

import java.math.BigDecimal;
import java.util.Map;

public record CardTransactionDto (
        String merchant,
        BigDecimal amount,
        BigDecimal balanceAfter,
        CardTransactionType cardType
) implements TransactionDataDto{
    @Override
    public Map<String, Object> toMap() {
        return Map.of(
                "merchant", merchant,
                "amount", amount,
                "balanceAfter", balanceAfter,
                "cardType", cardType
        );
    }
}
