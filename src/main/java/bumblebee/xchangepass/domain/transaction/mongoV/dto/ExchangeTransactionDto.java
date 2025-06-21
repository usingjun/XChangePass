package bumblebee.xchangepass.domain.transaction.mongoV.dto;

import java.math.BigDecimal;
import java.util.Map;

public record ExchangeTransactionDto(
        BigDecimal beforeAmount,
        BigDecimal afterAmount,
        BigDecimal rate
) implements TransactionDataDto {
    @Override
    public Map<String, Object> toMap() {
        return Map.of(
                "beforeAmount", beforeAmount,
                "afterAmount",afterAmount,
                "rate", rate
        );
    }
}
