package bumblebee.xchangepass.domain.wallet.transfer.dto;

import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferFailureStage;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record WalletTransferStatusResponse(
        UUID transferId,
        WalletTransferStatus status,
        WalletTransferFailureStage failureStage,
        String failureCode,
        Boolean retryable,
        int attemptCount,
        LocalDateTime requestedAt,
        LocalDateTime validatingAt,
        LocalDateTime processingAt,
        LocalDateTime completedAt,
        LocalDateTime failedAt,
        LocalDateTime updatedAt
) {
    public static WalletTransferStatusResponse from(WalletTransfer transfer) {
        return new WalletTransferStatusResponse(
                transfer.getTransferId(), transfer.getStatus(), transfer.getFailureStage(),
                transfer.getFailureCode(), transfer.getRetryable(), transfer.getAttemptCount(),
                transfer.getCreatedAt(), transfer.getValidatingAt(), transfer.getProcessingAt(),
                transfer.getCompletedAt(), transfer.getFailedAt(), transfer.getUpdatedAt()
        );
    }
}
