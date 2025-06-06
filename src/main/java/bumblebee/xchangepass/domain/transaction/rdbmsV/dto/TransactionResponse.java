package bumblebee.xchangepass.domain.transaction.rdbmsV.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long userId,
        LocalDateTime transactionTime,
        String transactionType,
        TransactionDataDto data
) {
    public record TransactionDataDto(
            String merchant,
            BigDecimal amount,
            String currency,
            BigDecimal balanceAfter,
            String fromCurrency,
            String toCurrency,
            BigDecimal rate
    ){
    }
}

