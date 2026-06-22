package bumblebee.xchangepass.domain.wallet.transfer.dto;

import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record StaleWalletTransfer(
        UUID transferId,
        WalletTransferStatus status,
        LocalDateTime lastTransitionAt,
        Long version
) {
    public static StaleWalletTransfer from(WalletTransfer transfer) {
        return new StaleWalletTransfer(
                transfer.getTransferId(), transfer.getStatus(), transfer.getUpdatedAt(), transfer.getVersion()
        );
    }
}
