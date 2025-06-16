package bumblebee.xchangepass.domain.transaction.mongoV.dto;

import java.math.BigDecimal;

public record ExchangeTransactionDto(
        BigDecimal beforeAmount,
        BigDecimal afterAmount,
        BigDecimal rate
) implements TransactionDataDto {
}
