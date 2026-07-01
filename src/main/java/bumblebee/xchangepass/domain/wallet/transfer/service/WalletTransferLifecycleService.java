package bumblebee.xchangepass.domain.wallet.transfer.service;

import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEvent;
import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEventType;
import bumblebee.xchangepass.domain.monitoring.service.TransactionStatusEventService;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
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
public class WalletTransferLifecycleService {

    private final WalletTransferRepository repository;
    private final TransactionStatusEventService eventService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startValidating(UUID transferId) {
        find(transferId).startValidating();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void assignReceiver(UUID transferId, Long receiverUserId) {
        find(transferId).assignReceiver(receiverUserId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startProcessing(UUID transferId) {
        WalletTransfer transfer = find(transferId);
        WalletTransferStatus previousStatus = transfer.getStatus();
        transfer.startProcessing();
        eventService.record(TransactionStatusEvent.builder(
                        transfer.getTransferId(), TransactionStatusEventType.PROCESSING_STARTED
                )
                .userId(transfer.getSenderUserId())
                .idempotencyKey(transfer.getIdempotencyKey())
                .status(previousStatus, transfer.getStatus())
                .build());
    }

    private WalletTransfer find(UUID transferId) {
        return repository.findById(transferId)
                .orElseThrow(ErrorCode.TRANSACTION_PROCESSING_FAILED::commonException);
    }
}
