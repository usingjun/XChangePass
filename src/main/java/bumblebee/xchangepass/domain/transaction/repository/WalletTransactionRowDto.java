package bumblebee.xchangepass.domain.transaction.repository;

import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletTransactionRowDto(
        Long transactionId,
        Long userId,
        Long counterpartyUserId,
        BigDecimal amount,
        BigDecimal receivedAmount,
        String fromCurrency,
        String toCurrency,
        WalletTransactionType transactionType,
        LocalDateTime transactionTime
) implements WalletTransactionRow {
    @Override
    public Long getTransactionId() {
        return transactionId;
    }

    @Override
    public Long getUserId() {
        return userId;
    }

    @Override
    public Long getCounterpartyUserId() {
        return counterpartyUserId;
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public BigDecimal getReceivedAmount() {
        return receivedAmount;
    }

    @Override
    public String getFromCurrency() {
        return fromCurrency;
    }

    @Override
    public String getToCurrency() {
        return toCurrency;
    }

    @Override
    public WalletTransactionType getTransactionType() {
        return transactionType;
    }

    @Override
    public LocalDateTime getTransactionTime() {
        return transactionTime;
    }
}
