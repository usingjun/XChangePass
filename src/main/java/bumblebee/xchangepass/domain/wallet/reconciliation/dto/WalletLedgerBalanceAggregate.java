package bumblebee.xchangepass.domain.wallet.reconciliation.dto;

import java.math.BigDecimal;

public record WalletLedgerBalanceAggregate(
        Long userId,
        String currency,
        BigDecimal ledgerCalculatedAmount
) {
}
