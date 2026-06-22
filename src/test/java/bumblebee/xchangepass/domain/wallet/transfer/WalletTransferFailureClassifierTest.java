package bumblebee.xchangepass.domain.wallet.transfer;

import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferFailureStage;
import bumblebee.xchangepass.domain.wallet.transfer.service.WalletTransferFailureClassifier;
import bumblebee.xchangepass.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WalletTransferFailureClassifierTest {

    private final WalletTransferFailureClassifier classifier = new WalletTransferFailureClassifier();

    @Test
    void classifiesRetryableInfrastructureFailures() {
        assertClassification(ErrorCode.FRAUD_DETECTION_UNAVAILABLE,
                WalletTransferFailureStage.FRAUD_VALIDATION, true);
        assertClassification(ErrorCode.LOCK_TIME_OUT,
                WalletTransferFailureStage.LOCK_ACQUISITION, true);
        assertClassification(ErrorCode.THREAD_INTERRUPTED,
                WalletTransferFailureStage.LOCK_ACQUISITION, true);
    }

    @Test
    void classifiesNonRetryableBusinessFailures() {
        assertClassification(ErrorCode.SUSPICIOUS_TRANSACTION,
                WalletTransferFailureStage.FRAUD_VALIDATION, false);
        assertClassification(ErrorCode.BALANCE_NOT_AVAILABLE,
                WalletTransferFailureStage.BALANCE_VALIDATION, false);
        assertClassification(ErrorCode.RECEIVER_NOT_FOUND,
                WalletTransferFailureStage.PARTICIPANT_VALIDATION, false);
    }

    @Test
    void keepsCurrentStageForUnclassifiedFailure() {
        var result = classifier.classify(
                ErrorCode.TRANSACTION_PROCESSING_FAILED,
                WalletTransferFailureStage.FUNDS_AND_LEDGER_TRANSACTION
        );

        assertThat(result.stage()).isEqualTo(WalletTransferFailureStage.FUNDS_AND_LEDGER_TRANSACTION);
        assertThat(result.retryable()).isFalse();
    }

    private void assertClassification(ErrorCode errorCode,
                                      WalletTransferFailureStage expectedStage,
                                      boolean expectedRetryable) {
        var result = classifier.classify(errorCode, WalletTransferFailureStage.UNKNOWN);
        assertThat(result.stage()).isEqualTo(expectedStage);
        assertThat(result.retryable()).isEqualTo(expectedRetryable);
    }
}
