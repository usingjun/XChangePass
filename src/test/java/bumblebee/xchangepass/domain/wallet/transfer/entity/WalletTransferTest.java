package bumblebee.xchangepass.domain.wallet.transfer.entity;

import bumblebee.xchangepass.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTransferTest {

    @Test
    void recordsLifecycleTimestampsAndAttempt() {
        WalletTransfer transfer = transfer();
        transfer.onCreate();

        transfer.startValidating();
        transfer.startProcessing();
        transfer.complete();

        assertThat(transfer.getStatus()).isEqualTo(WalletTransferStatus.COMPLETED);
        assertThat(transfer.getAttemptCount()).isEqualTo(1);
        assertThat(transfer.getValidatingAt()).isNotNull();
        assertThat(transfer.getProcessingAt()).isNotNull();
        assertThat(transfer.getCompletedAt()).isNotNull();
        assertThat(transfer.getFailureCode()).isNull();
    }

    @Test
    void recordsFailureContext() {
        WalletTransfer transfer = transfer();

        transfer.startValidating();
        transfer.fail(ErrorCode.FRAUD_DETECTION_UNAVAILABLE,
                WalletTransferFailureStage.FRAUD_VALIDATION, true);

        assertThat(transfer.getStatus()).isEqualTo(WalletTransferStatus.FAILED);
        assertThat(transfer.getFailureStage()).isEqualTo(WalletTransferFailureStage.FRAUD_VALIDATION);
        assertThat(transfer.getFailureCode()).isEqualTo(ErrorCode.FRAUD_DETECTION_UNAVAILABLE.name());
        assertThat(transfer.getRetryable()).isTrue();
        assertThat(transfer.getFailedAt()).isNotNull();
    }

    @Test
    void rejectsInvalidTransitionAndCompletedFailure() {
        WalletTransfer transfer = transfer();

        assertThatThrownBy(transfer::startProcessing).isInstanceOf(IllegalStateException.class);

        transfer.startValidating();
        transfer.startProcessing();
        transfer.complete();

        assertThatThrownBy(() -> transfer.fail(
                ErrorCode.TRANSACTION_PROCESSING_FAILED,
                WalletTransferFailureStage.UNKNOWN,
                false
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void doesNotOverwriteAnExistingFailure() {
        WalletTransfer transfer = transfer();
        transfer.startValidating();
        transfer.fail(
                ErrorCode.TRANSACTION_STALE,
                WalletTransferFailureStage.RECOVERY_TIMEOUT,
                true
        );

        assertThatThrownBy(() -> transfer.fail(
                ErrorCode.TRANSACTION_PROCESSING_FAILED,
                WalletTransferFailureStage.UNKNOWN,
                false
        )).isInstanceOf(IllegalStateException.class);
        assertThat(transfer.getFailureCode()).isEqualTo(ErrorCode.TRANSACTION_STALE.name());
        assertThat(transfer.getFailureStage()).isEqualTo(WalletTransferFailureStage.RECOVERY_TIMEOUT);
    }

    private WalletTransfer transfer() {
        return new WalletTransfer(UUID.randomUUID(), 1L, UUID.randomUUID(), "a".repeat(64));
    }
}
