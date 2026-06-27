package bumblebee.xchangepass.domain.wallet.transfer.service;

import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEvent;
import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEventType;
import bumblebee.xchangepass.domain.monitoring.service.TransactionStatusEventService;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.transfer.repository.WalletTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletTransferReservationWriter {

    private final WalletTransferRepository repository;
    private final TransactionStatusEventService eventService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WalletTransfer create(Long senderUserId, UUID idempotencyKey, String requestHash) {
        WalletTransfer transfer = repository.saveAndFlush(new WalletTransfer(
                UUID.randomUUID(), senderUserId, idempotencyKey, requestHash
        ));
        eventService.record(TransactionStatusEvent.builder(
                        transfer.getTransferId(), TransactionStatusEventType.REQUEST_ACCEPTED
                )
                .userId(senderUserId)
                .idempotencyKey(idempotencyKey)
                .status(null, WalletTransferStatus.REQUESTED)
                .build());
        return transfer;
    }
}
