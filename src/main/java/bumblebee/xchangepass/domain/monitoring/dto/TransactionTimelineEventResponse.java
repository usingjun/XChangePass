package bumblebee.xchangepass.domain.monitoring.dto;

import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEvent;
import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEventType;
import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferFailureStage;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionTimelineEventResponse(
        Long id,
        UUID transactionId,
        TransactionType transactionType,
        Long userId,
        Long walletId,
        TransactionStatusEventType eventType,
        WalletTransferStatus previousStatus,
        WalletTransferStatus currentStatus,
        WalletTransferFailureStage failureStage,
        String errorCode,
        Boolean retryable,
        UUID idempotencyKey,
        LocalDateTime occurredAt
) {
    public static TransactionTimelineEventResponse from(TransactionStatusEvent event) {
        return new TransactionTimelineEventResponse(
                event.getId(),
                event.getTransactionId(),
                event.getTransactionType(),
                event.getUserId(),
                event.getWalletId(),
                event.getEventType(),
                event.getPreviousStatus(),
                event.getCurrentStatus(),
                event.getFailureStage(),
                event.getErrorCode(),
                event.getRetryable(),
                event.getIdempotencyKey(),
                event.getOccurredAt()
        );
    }
}
