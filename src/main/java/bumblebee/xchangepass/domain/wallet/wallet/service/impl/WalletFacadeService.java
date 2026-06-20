package bumblebee.xchangepass.domain.wallet.wallet.service.impl;

import bumblebee.xchangepass.domain.wallet.transfer.dto.WalletTransferResponse;
import bumblebee.xchangepass.domain.wallet.transfer.service.WalletTransferFailureService;
import bumblebee.xchangepass.domain.wallet.transfer.service.WalletTransferIdempotencyService;
import bumblebee.xchangepass.domain.wallet.transfer.service.WalletTransferReservation;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.entity.WalletTransferType;
import bumblebee.xchangepass.domain.wallet.wallet.scheduler.ScheduledTransferService;
import bumblebee.xchangepass.domain.wallet.wallet.service.WalletService;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletFacadeService {

    private final WalletService walletService;
    private final ScheduledTransferService scheduledTransferService;
    private final WalletTransferIdempotencyService idempotencyService;
    private final WalletTransferFailureService failureService;

    public void transfer(Long senderId, WalletTransferRequest request) {
        if (request.transferType() != WalletTransferType.SCHEDULED) {
            throw ErrorCode.INVALID_TRANSFER_REQUEST.commonException();
        }
        scheduledTransferService.saveSchedule(senderId, request);
    }

    public WalletTransferResponse transfer(Long senderId, UUID idempotencyKey, WalletTransferRequest request) {
        validateImmediateTransfer(request);
        WalletTransferReservation reservation = idempotencyService.reserve(senderId, idempotencyKey, request);
        if (!reservation.owner()) {
            return reservation.existingResponse();
        }

        try {
            return walletService.transfer(reservation.transferId(), senderId, request);
        } catch (CommonException e) {
            markFailed(reservation.transferId(), e.getErrorCode());
            throw e;
        } catch (RuntimeException e) {
            markFailed(reservation.transferId(), ErrorCode.TRANSACTION_PROCESSING_FAILED);
            throw e;
        }
    }

    public void scheduleTransfer(Long senderId, WalletTransferRequest request) {
        if (request.transferType() != WalletTransferType.SCHEDULED) {
            throw ErrorCode.INVALID_TRANSFER_REQUEST.commonException();
        }
        scheduledTransferService.saveSchedule(senderId, request);
    }

    private void validateImmediateTransfer(WalletTransferRequest request) {
        if (request.transferType() != WalletTransferType.GENERAL || request.transferDatetime() != null) {
            throw ErrorCode.INVALID_TRANSFER_REQUEST.commonException();
        }
    }

    private void markFailed(UUID transferId, ErrorCode errorCode) {
        try {
            failureService.markFailed(transferId, errorCode);
        } catch (RuntimeException failureRecordingException) {
            log.error("Failed to record wallet transfer failure: transferId={}", transferId,
                    failureRecordingException);
        }
    }
}
