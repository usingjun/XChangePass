package bumblebee.xchangepass.domain.wallet.transfer.recovery.service;

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean finalizeIfUnchanged(UUID transferId, WalletTransferStatus observedStatus,
                                       Long observedVersion, LocalDateTime cutoff) {
        return repository.finalizeStaleTransfer(
                transferId, observedStatus.name(), observedVersion, cutoff
        ) == 1;
    }
}
