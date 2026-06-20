package bumblebee.xchangepass.domain.transaction.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExchangeTransactionRowDto(
        Long transactionId,
        Long userId,
        String fromCurrency,
        String toCurrency,
        BigDecimal amount,
        BigDecimal receivedAmount,
        BigDecimal exchangeRate,
        LocalDateTime transactionTime
) implements ExchangeTransactionRow {
    @Override
    public Long getTransactionId() {
        return transactionId;
    }

    @Override
    public Long getUserId() {
        return userId;
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
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public BigDecimal getReceivedAmount() {
        return receivedAmount;
    }

    @Override
    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    @Override
    public LocalDateTime getTransactionTime() {
        return transactionTime;
    }
}
