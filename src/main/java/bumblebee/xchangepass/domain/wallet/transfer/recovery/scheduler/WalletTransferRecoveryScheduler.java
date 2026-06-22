package bumblebee.xchangepass.domain.wallet.transfer.recovery.scheduler;

import bumblebee.xchangepass.domain.wallet.transfer.recovery.service.WalletTransferRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "wallet.transfer.recovery.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class WalletTransferRecoveryScheduler {

    private final WalletTransferRecoveryService recoveryService;

    @Scheduled(
            fixedDelayString = "${wallet.transfer.recovery.fixed-delay:60000}",
            initialDelayString = "${wallet.transfer.recovery.initial-delay:60000}"
    )
    public void recover() {
        recoveryService.recover();
    }
}
