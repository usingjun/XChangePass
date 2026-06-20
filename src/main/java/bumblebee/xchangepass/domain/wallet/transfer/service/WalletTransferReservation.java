package bumblebee.xchangepass.domain.wallet.transfer.service;

import bumblebee.xchangepass.domain.wallet.transfer.dto.WalletTransferResponse;

import java.util.UUID;

public record WalletTransferReservation(
        UUID transferId,
        boolean owner,
        WalletTransferResponse existingResponse
) {
    public static WalletTransferReservation owner(UUID transferId) {
        return new WalletTransferReservation(transferId, true, null);
    }

    public static WalletTransferReservation completed(WalletTransferResponse response) {
        return new WalletTransferReservation(response.transferId(), false, response);
    }
}
