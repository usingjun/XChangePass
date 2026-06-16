package bumblebee.xchangepass.domain.transaction.repository;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface CardTransactionRow {
    Long getTransactionId();

    Long getUserId();

    String getMerchantName();

    BigDecimal getApprovedAmount();

    String getApprovedCurrency();

    BigDecimal getBalanceAfter();

    CardTransactionType getTransactionType();

    LocalDateTime getTransactionTime();
}
