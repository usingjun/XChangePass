package bumblebee.xchangepass.domain.wallet.transfer.recovery.service;

import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEvent;
import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEventType;
import bumblebee.xchangepass.domain.monitoring.service.TransactionStatusEventService;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferFailureStage;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.transfer.repository.WalletTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletTransferStaleFinalizer {

    private final WalletTransferRepository repository;
    private final TransactionStatusEventService eventService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean finalizeIfUnchanged(UUID transferId, WalletTransferStatus observedStatus,
                                       Long observedVersion, LocalDateTime cutoff) {
        boolean finalized = repository.finalizeStaleTransfer(
                transferId, observedStatus.name(), observedVersion, cutoff
        ) == 1;
        if (finalized) {
            WalletTransfer transfer = repository.findById(transferId).orElse(null);
            if (transfer != null) {
                eventService.record(TransactionStatusEvent.builder(
                                transfer.getTransferId(), TransactionStatusEventType.AUTO_FAILED
                        )
                        .userId(transfer.getSenderUserId())
                        .idempotencyKey(transfer.getIdempotencyKey())
                        .status(observedStatus, WalletTransferStatus.FAILED)
                        .failure(WalletTransferFailureStage.RECOVERY_TIMEOUT,
                                "TRANSACTION_STALE", true)
                        .build());
            }
        }
        return finalized;
    }
}
