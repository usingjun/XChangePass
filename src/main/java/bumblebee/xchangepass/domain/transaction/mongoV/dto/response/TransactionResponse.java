package bumblebee.xchangepass.domain.transaction.mongoV.dto.response;

import bumblebee.xchangepass.domain.transaction.mongoV.dto.TransactionDataDto;
import bumblebee.xchangepass.domain.transaction.rdbmsV.entity.TransactionType;

import java.time.LocalDateTime;
import java.util.Currency;

public record TransactionResponse(
        Long userId,
        LocalDateTime transactionTime,
        TransactionType transactionType,
        Currency beforeCurrency,
        Currency afterCurrency,
        TransactionDataDto data
) {
}
