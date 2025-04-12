package bumblebee.xchangepass.domain.wallet.wallet.service.impl;

import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.entity.WalletTransferType;
import bumblebee.xchangepass.domain.wallet.wallet.scheduler.ScheduledTransferService;
import bumblebee.xchangepass.domain.wallet.wallet.service.WalletService;
import bumblebee.xchangepass.domain.wallet.wallet.service.WalletServiceFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletFacadeService {

    private final WalletServiceFactory walletServiceFactory;
    private final ScheduledTransferService scheduledTransferService;

    public void transfer(Long senderId, WalletTransferRequest request) {
        if (request.transferType() == WalletTransferType.SCHEDULED) {
            scheduledTransferService.saveSchedule(senderId, request);
        } else {
            WalletService service = walletServiceFactory.getService("namedLock");
            service.transfer(senderId, request);
        }
    }
}
