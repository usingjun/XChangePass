package bumblebee.xchangepass.domain.monitoring.dto;

import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCase;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseSeverity;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseStatus;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseType;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecoveryCaseOperationResponse(
        UUID caseId,
        UUID transactionId,
        WalletTransferRecoveryCaseType caseType,
        WalletTransferRecoveryCaseSeverity severity,
        WalletTransferRecoveryCaseStatus caseStatus,
        WalletTransferStatus observedTransferStatus,
        Long observedTransferVersion,
        long observedLedgerCount,
        LocalDateTime firstDetectedAt,
        LocalDateTime lastDetectedAt,
        long detectionCount,
        String resolutionNote,
        LocalDateTime resolvedAt
) {

    public static RecoveryCaseOperationResponse from(WalletTransferRecoveryCase recoveryCase) {
        return new RecoveryCaseOperationResponse(
                recoveryCase.getCaseId(),
                recoveryCase.getTransferId(),
                recoveryCase.getCaseType(),
                recoveryCase.getSeverity(),
                recoveryCase.getCaseStatus(),
                recoveryCase.getObservedTransferStatus(),
                recoveryCase.getObservedTransferVersion(),
                recoveryCase.getObservedLedgerCount(),
                recoveryCase.getFirstDetectedAt(),
                recoveryCase.getLastDetectedAt(),
                recoveryCase.getDetectionCount(),
                recoveryCase.getResolutionNote(),
                recoveryCase.getResolvedAt()
        );
    }
}
