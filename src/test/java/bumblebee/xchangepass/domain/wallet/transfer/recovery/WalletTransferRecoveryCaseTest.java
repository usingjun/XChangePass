package bumblebee.xchangepass.domain.wallet.transfer.recovery;

import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCase;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseSeverity;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseStatus;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTransferRecoveryCaseTest {

    @Test
    void reobservesAndReopensResolvedCase() {
        WalletTransferRecoveryCase recoveryCase = recoveryCase();
        ReflectionTestUtils.setField(recoveryCase, "detectionCount", 1L);
        recoveryCase.resolve("checked");

        recoveryCase.reobserve(
                WalletTransferRecoveryCaseSeverity.CRITICAL,
                WalletTransferStatus.PROCESSING,
                4L,
                1L
        );

        assertThat(recoveryCase.getCaseStatus()).isEqualTo(WalletTransferRecoveryCaseStatus.OPEN);
        assertThat(recoveryCase.getDetectionCount()).isEqualTo(2);
        assertThat(recoveryCase.getSeverity()).isEqualTo(WalletTransferRecoveryCaseSeverity.CRITICAL);
        assertThat(recoveryCase.getResolutionNote()).isNull();
    }

    @Test
    void supportsAcknowledgementAndResolutionWithoutTransactionMutation() {
        WalletTransferRecoveryCase recoveryCase = recoveryCase();

        recoveryCase.acknowledge();
        recoveryCase.resolve("ledger checked");

        assertThat(recoveryCase.getCaseStatus()).isEqualTo(WalletTransferRecoveryCaseStatus.RESOLVED);
        assertThat(recoveryCase.getResolutionNote()).isEqualTo("ledger checked");
        assertThat(recoveryCase.getResolvedAt()).isNotNull();
        assertThatThrownBy(recoveryCase::acknowledge).isInstanceOf(IllegalStateException.class);
    }

    private WalletTransferRecoveryCase recoveryCase() {
        return new WalletTransferRecoveryCase(
                UUID.randomUUID(),
                WalletTransferRecoveryCaseType.AMBIGUOUS_PROCESSING,
                WalletTransferRecoveryCaseSeverity.WARNING,
                WalletTransferStatus.PROCESSING,
                3L,
                0L
        );
    }
}
