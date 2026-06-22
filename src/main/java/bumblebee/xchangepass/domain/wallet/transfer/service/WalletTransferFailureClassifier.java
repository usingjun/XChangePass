package bumblebee.xchangepass.domain.wallet.transfer.service;

import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferFailureStage;
import bumblebee.xchangepass.global.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class WalletTransferFailureClassifier {

    public FailureClassification classify(ErrorCode errorCode, WalletTransferFailureStage currentStage) {
        WalletTransferFailureStage stage = switch (errorCode) {
            case FRAUD_DETECTION_UNAVAILABLE, SUSPICIOUS_TRANSACTION -> WalletTransferFailureStage.FRAUD_VALIDATION;
            case LOCK_TIME_OUT, THREAD_INTERRUPTED -> WalletTransferFailureStage.LOCK_ACQUISITION;
            case BALANCE_NOT_FOUND, BALANCE_NOT_AVAILABLE -> WalletTransferFailureStage.BALANCE_VALIDATION;
            case USER_NOT_FOUND, RECEIVER_NOT_FOUND, WALLET_NOT_FOUND ->
                    WalletTransferFailureStage.PARTICIPANT_VALIDATION;
            default -> currentStage;
        };
        boolean retryable = errorCode == ErrorCode.FRAUD_DETECTION_UNAVAILABLE
                || errorCode == ErrorCode.LOCK_TIME_OUT
                || errorCode == ErrorCode.THREAD_INTERRUPTED;
        return new FailureClassification(stage, retryable);
    }

    public record FailureClassification(
            WalletTransferFailureStage stage,
            boolean retryable
    ) {
    }
}
