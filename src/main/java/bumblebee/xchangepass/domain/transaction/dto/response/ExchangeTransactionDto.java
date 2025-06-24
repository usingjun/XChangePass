package bumblebee.xchangepass.domain.transaction.dto.response;

import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.math.BigDecimal;
import java.util.Map;

@JsonTypeName("exchange")
public record ExchangeTransactionDto(
        BigDecimal beforeAmount,
        BigDecimal afterAmount,
        BigDecimal rate,
        TransactionType transactionType
) implements TransactionDataDto {
    @Override
    public Map<String, Object> toMap() {
        return Map.of(
                "beforeAmount", beforeAmount,
                "afterAmount",afterAmount,
                "rate", rate,
                "type", transactionType
        );
    }
}
