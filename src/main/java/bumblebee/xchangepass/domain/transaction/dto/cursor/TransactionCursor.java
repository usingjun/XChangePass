package bumblebee.xchangepass.domain.transaction.dto.cursor;

import bumblebee.xchangepass.domain.transaction.entity.TransactionType;

import java.time.LocalDateTime;

public record TransactionCursor(
        int version,
        LocalDateTime transactionTime,
        TransactionType transactionType,
        Long transactionId
) {
    public static final int CURRENT_VERSION = 1;
}
