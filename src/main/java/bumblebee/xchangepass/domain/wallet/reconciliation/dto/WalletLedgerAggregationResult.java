package bumblebee.xchangepass.domain.wallet.reconciliation.dto;

import java.util.List;

public record WalletLedgerAggregationResult(
        List<WalletLedgerBalanceAggregate> balances,
        int uncalculableLedgerCount
) {
}
