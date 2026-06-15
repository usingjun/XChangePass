package bumblebee.xchangepass.domain.wallet.wallet.scheduler;

import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.entity.ScheduledTransfer;
import bumblebee.xchangepass.domain.wallet.wallet.entity.WalletTransferType;
import bumblebee.xchangepass.domain.wallet.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScheduledTransferExecutor {

    private final WalletService walletService;

    public void execute(ScheduledTransfer scheduled) {
        WalletTransferRequest request = new WalletTransferRequest(
                scheduled.getReceiverName(),
                scheduled.getReceiverPhoneNumber(),
                scheduled.getTransferAmount(),
                scheduled.getFromCurrency(),
                scheduled.getToCurrency(),
                scheduled.getScheduledAt(),
                WalletTransferType.GENERAL
        );
        walletService.transfer(scheduled.getSenderId(), request);
        scheduled.markSuccess();
    }
}
