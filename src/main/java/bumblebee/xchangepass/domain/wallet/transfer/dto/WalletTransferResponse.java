package bumblebee.xchangepass.domain.wallet.transfer.dto;

import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;

import java.util.UUID;

public record WalletTransferResponse(
        UUID transferId,
        WalletTransferStatus status
) {
}
