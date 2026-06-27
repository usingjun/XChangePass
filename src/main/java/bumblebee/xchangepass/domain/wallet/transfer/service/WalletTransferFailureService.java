package bumblebee.xchangepass.domain.wallet.transfer.service;

import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEvent;
import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEventType;
import bumblebee.xchangepass.domain.monitoring.service.TransactionStatusEventService;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferFailureStage;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.transfer.repository.WalletTransferRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletTransferFailureService {

    private final WalletTransferRepository repository;
    private final TransactionStatusEventService eventService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID transferId, ErrorCode errorCode,
                           WalletTransferFailureStage failureStage, boolean retryable) {
        WalletTransfer transfer = repository.findById(transferId)
                .orElseThrow(ErrorCode.TRANSACTION_PROCESSING_FAILED::commonException);
        WalletTransferStatus previousStatus = transfer.getStatus();
        transfer.fail(errorCode, failureStage, retryable);
        eventService.record(TransactionStatusEvent.builder(
                        transfer.getTransferId(), TransactionStatusEventType.FAILED
                )
                .userId(transfer.getSenderUserId())
                .idempotencyKey(transfer.getIdempotencyKey())
                .status(previousStatus, transfer.getStatus())
                .failure(failureStage, errorCode.name(), retryable)
                .build());
    }
}
