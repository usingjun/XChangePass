package bumblebee.xchangepass.domain.transaction.mongoV.dto;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;

import java.math.BigDecimal;

public record CardTransactionDto (
        String merchant,
        BigDecimal amount,
        BigDecimal balanceAfter,
        CardTransactionType cardType
) implements TransactionDataDto{
}
