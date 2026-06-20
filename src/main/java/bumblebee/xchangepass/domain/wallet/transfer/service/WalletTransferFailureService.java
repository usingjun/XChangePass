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
public class WalletTransferFailureService {

    private final WalletTransferRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID transferId, ErrorCode errorCode) {
        WalletTransfer transfer = repository.findById(transferId)
                .orElseThrow(ErrorCode.TRANSACTION_PROCESSING_FAILED::commonException);
        transfer.fail(errorCode);
    }
}
