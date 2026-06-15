package bumblebee.xchangepass.domain.wallet.wallet.service.impl;

import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.entity.WalletTransferType;
import bumblebee.xchangepass.domain.wallet.wallet.scheduler.ScheduledTransferService;
import bumblebee.xchangepass.domain.wallet.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletFacadeService {

    private final WalletService walletService;
    private final ScheduledTransferService scheduledTransferService;

    public void transfer(Long senderId, WalletTransferRequest request) {
        if (request.transferType() == WalletTransferType.SCHEDULED) {
            scheduledTransferService.saveSchedule(senderId, request);
        } else {
            walletService.transfer(senderId, request);
        }
    }
}
