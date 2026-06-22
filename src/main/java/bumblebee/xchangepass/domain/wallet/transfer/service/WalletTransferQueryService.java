package bumblebee.xchangepass.domain.wallet.transfer.service;

import bumblebee.xchangepass.domain.wallet.transfer.dto.WalletTransferStatusResponse;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.repository.WalletTransferRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletTransferQueryService {

    private final WalletTransferRepository repository;

    @Transactional(readOnly = true)
    public WalletTransferStatusResponse findStatus(Long senderUserId, UUID transferId) {
        WalletTransfer transfer = repository.findByTransferIdAndSenderUserId(transferId, senderUserId)
                .orElseThrow(ErrorCode.TRANSACTION_HISTORY_NOT_FOUND::commonException);
        return WalletTransferStatusResponse.from(transfer);
    }
}
