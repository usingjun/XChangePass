package bumblebee.xchangepass.domain.wallet.transfer.service;

import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEvent;
import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEventType;
import bumblebee.xchangepass.domain.monitoring.service.TransactionStatusEventService;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.service.UserService;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudAmountNormalizer;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectEvent;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectionService;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudTransactionType;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferFailureStage;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletTransferValidationService {

    private final UserService userService;
    private final FraudAmountNormalizer fraudAmountNormalizer;
    private final FraudDetectionService fraudDetectionService;
    private final TransactionStatusEventService eventService;

    public Long resolveReceiver(WalletTransferRequest request) {
        User receiver = userService.readUser(request.receiverName(), request.receiverPhoneNumber());
        return receiver.getUserId();
    }

    public void verifyFraud(Long senderId, UUID transferId, UUID idempotencyKey,
                            WalletTransferRequest request) {
        BigDecimal normalizedAmount = fraudAmountNormalizer.normalize(
                request.transferAmount(), request.fromCurrency()
        );
        try {
            fraudDetectionService.verify(new FraudDetectEvent(
                    senderId, normalizedAmount, LocalDateTime.now(), null, FraudTransactionType.WALLET
            ));
        } catch (CommonException exception) {
            recordFraudBlock(senderId, transferId, idempotencyKey, exception.getErrorCode());
            throw exception;
        }
    }

    private void recordFraudBlock(Long senderId, UUID transferId, UUID idempotencyKey,
                                  ErrorCode errorCode) {
        TransactionStatusEventType eventType = errorCode == ErrorCode.FRAUD_DETECTION_UNAVAILABLE
                ? TransactionStatusEventType.FRAUD_DETECTION_UNAVAILABLE
                : TransactionStatusEventType.FRAUD_CHECK_BLOCKED;
        eventService.recordBestEffort(TransactionStatusEvent.builder(transferId, eventType)
                .userId(senderId)
                .idempotencyKey(idempotencyKey)
                .status(WalletTransferStatus.VALIDATING, WalletTransferStatus.VALIDATING)
                .failure(WalletTransferFailureStage.FRAUD_VALIDATION, errorCode.name(),
                        errorCode == ErrorCode.FRAUD_DETECTION_UNAVAILABLE)
                .build());
    }
}
