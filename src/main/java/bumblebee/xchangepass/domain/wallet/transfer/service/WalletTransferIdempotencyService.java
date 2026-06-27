package bumblebee.xchangepass.domain.wallet.transfer.service;

import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEvent;
import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEventType;
import bumblebee.xchangepass.domain.monitoring.service.TransactionStatusEventService;
import bumblebee.xchangepass.domain.wallet.transfer.dto.WalletTransferResponse;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.transfer.repository.WalletTransferRepository;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletTransferIdempotencyService {

    private final WalletTransferReservationWriter reservationWriter;
    private final WalletTransferRepository repository;
    private final WalletTransferRequestHasher requestHasher;
    private final TransactionStatusEventService eventService;

    public WalletTransferReservation reserve(Long senderUserId, UUID idempotencyKey,
                                             WalletTransferRequest request) {
        String requestHash = requestHasher.hash(request);
        try {
            WalletTransfer created = reservationWriter.create(senderUserId, idempotencyKey, requestHash);
            return WalletTransferReservation.owner(created.getTransferId());
        } catch (DataIntegrityViolationException e) {
            WalletTransfer existing = repository
                    .findBySenderUserIdAndIdempotencyKey(senderUserId, idempotencyKey)
                    .orElseThrow(() -> e);
            recordDuplicate(existing);
            return resolveExisting(existing, requestHash);
        }
    }

    private void recordDuplicate(WalletTransfer existing) {
        eventService.recordBestEffort(TransactionStatusEvent.builder(
                        existing.getTransferId(), TransactionStatusEventType.IDEMPOTENT_DUPLICATE_DETECTED
                )
                .userId(existing.getSenderUserId())
                .idempotencyKey(existing.getIdempotencyKey())
                .status(existing.getStatus(), existing.getStatus())
                .failure(existing.getFailureStage(), existing.getFailureCode(), existing.getRetryable())
                .build());
    }

    private WalletTransferReservation resolveExisting(WalletTransfer existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw ErrorCode.IDEMPOTENCY_KEY_REUSED.commonException();
        }

        return switch (existing.getStatus()) {
            case COMPLETED -> WalletTransferReservation.completed(new WalletTransferResponse(
                    existing.getTransferId(), WalletTransferStatus.COMPLETED
            ));
            case REQUESTED, VALIDATING, PROCESSING -> throw ErrorCode.TRANSACTION_IN_PROGRESS.commonException();
            case FAILED -> throw previousFailure(existing.getFailureCode()).commonException();
        };
    }

    private ErrorCode previousFailure(String failureCode) {
        if (failureCode == null) {
            return ErrorCode.TRANSACTION_PROCESSING_FAILED;
        }
        try {
            return ErrorCode.valueOf(failureCode);
        } catch (IllegalArgumentException e) {
            return ErrorCode.TRANSACTION_PROCESSING_FAILED;
        }
    }
}
