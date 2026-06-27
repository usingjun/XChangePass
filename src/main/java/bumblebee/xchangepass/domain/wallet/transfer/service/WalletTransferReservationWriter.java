package bumblebee.xchangepass.domain.wallet.transfer.service;

import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WalletTransfer create(Long senderUserId, UUID idempotencyKey, String requestHash) {
        return repository.saveAndFlush(new WalletTransfer(
                UUID.randomUUID(), senderUserId, idempotencyKey, requestHash
        ));
    }
}
