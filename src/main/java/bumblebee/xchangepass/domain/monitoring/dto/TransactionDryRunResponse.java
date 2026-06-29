package bumblebee.xchangepass.domain.monitoring.dto;

import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferFailureStage;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;

import java.util.UUID;

public record TransactionDryRunResponse(
        UUID transactionId,
        WalletTransferStatus currentStatus,
        WalletTransferFailureStage failureStage,
        Boolean retryable,
        boolean hasLedger,
        long ledgerCount,
        boolean canAutoFail,
        AdminTransactionRecommendedAction recommendedAction,
        String reason
) {
}
