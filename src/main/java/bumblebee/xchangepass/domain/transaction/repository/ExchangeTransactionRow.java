package bumblebee.xchangepass.domain.transaction.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface ExchangeTransactionRow {
    Long getTransactionId();

    Long getUserId();

    String getFromCurrency();

    String getToCurrency();

    BigDecimal getAmount();

    BigDecimal getReceivedAmount();

    BigDecimal getExchangeRate();

    LocalDateTime getTransactionTime();
}
