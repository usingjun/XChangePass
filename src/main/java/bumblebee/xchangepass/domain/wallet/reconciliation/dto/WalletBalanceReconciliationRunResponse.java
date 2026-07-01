package bumblebee.xchangepass.domain.wallet.reconciliation.dto;

public record WalletBalanceReconciliationRunResponse(
        int checkedBalanceCount,
        int skippedBalanceCount,
        int uncalculableLedgerCount,
        int issueCount,
        int createdIssueCount,
        int updatedIssueCount
) {
}
