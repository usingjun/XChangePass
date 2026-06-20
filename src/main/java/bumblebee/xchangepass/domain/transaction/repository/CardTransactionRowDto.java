package bumblebee.xchangepass.domain.transaction.repository;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CardTransactionRowDto(
        Long transactionId,
        Long userId,
        String merchantName,
        BigDecimal approvedAmount,
        String approvedCurrency,
        BigDecimal balanceAfter,
        CardTransactionType transactionType,
        LocalDateTime transactionTime
) implements CardTransactionRow {
    @Override
    public Long getTransactionId() {
        return transactionId;
    }

    @Override
    public Long getUserId() {
        return userId;
    }

    @Override
    public String getMerchantName() {
        return merchantName;
    }

    @Override
    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    @Override
    public String getApprovedCurrency() {
        return approvedCurrency;
    }

    @Override
    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    @Override
    public CardTransactionType getTransactionType() {
        return transactionType;
    }

    @Override
    public LocalDateTime getTransactionTime() {
        return transactionTime;
    }
}
