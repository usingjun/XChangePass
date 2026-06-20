package bumblebee.xchangepass.domain.transaction.repository;

import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface WalletTransactionRow {
    Long getTransactionId();

    Long getUserId();

    Long getCounterpartyUserId();

    BigDecimal getAmount();

    BigDecimal getReceivedAmount();

    String getFromCurrency();

    String getToCurrency();

    WalletTransactionType getTransactionType();

    LocalDateTime getTransactionTime();
}
