package bumblebee.xchangepass.domain.transaction.dto.response;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import bumblebee.xchangepass.domain.transaction.entity.TransactionType;

import java.math.BigDecimal;
import java.util.Map;

public record CardTransactionDto (
        String merchant,
        BigDecimal amount,
        BigDecimal balanceAfter,
        TransactionType transactionType,
        CardTransactionType cardType
) implements TransactionDataDto{
    @Override
    public Map<String, Object> toMap() {
        return Map.of(
                "merchant", merchant,
                "amount", amount,
                "balanceAfter", balanceAfter,
                "type", transactionType,
                "cardType", cardType
        );
    }
}
