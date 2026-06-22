package bumblebee.xchangepass.domain.wallet.transfer.service;

import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startValidating(UUID transferId) {
        find(transferId).startValidating();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startProcessing(UUID transferId) {
        find(transferId).startProcessing();
    }

    private WalletTransfer find(UUID transferId) {
        return repository.findById(transferId)
                .orElseThrow(ErrorCode.TRANSACTION_PROCESSING_FAILED::commonException);
    }
}
