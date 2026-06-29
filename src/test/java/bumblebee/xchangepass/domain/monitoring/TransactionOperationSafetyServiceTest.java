package bumblebee.xchangepass.domain.monitoring;

import bumblebee.xchangepass.domain.monitoring.dto.AdminTransactionRecommendedAction;
import bumblebee.xchangepass.domain.monitoring.entity.AdminOperationActionType;
import bumblebee.xchangepass.domain.monitoring.entity.AdminOperationTargetType;
import bumblebee.xchangepass.domain.monitoring.service.AdminOperationAuditLogService;
import bumblebee.xchangepass.domain.monitoring.service.TransactionOperationSafetyService;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferFailureStage;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.transfer.repository.WalletTransferRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionOperationSafetyServiceTest {

    @Mock
    private WalletTransferRepository walletTransferRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @Mock
    private AdminOperationAuditLogService auditLogService;

    private TransactionOperationSafetyService safetyService;

    @BeforeEach
    void setUp() {
        safetyService = new TransactionOperationSafetyService(
                walletTransferRepository,
                walletTransactionRepository,
                auditLogService
        );
    }

    @Test
    void requestedWithoutLedgerAllowsManualFail() {
        UUID transferId = UUID.randomUUID();
        givenTransfer(transferId, transfer(WalletTransferStatus.REQUESTED));
        when(walletTransactionRepository.countByTransferId(transferId)).thenReturn(0L);

        var response = safetyService.dryRun(transferId, 1L, "check");

        assertThat(response.canAutoFail()).isTrue();
        assertThat(response.recommendedAction()).isEqualTo(AdminTransactionRecommendedAction.MARK_FAILED_ALLOWED);
        assertThat(response.hasLedger()).isFalse();
        assertThat(response.ledgerCount()).isZero();
    }

    @Test
    void validatingWithoutLedgerAllowsManualFail() {
        UUID transferId = UUID.randomUUID();
        givenTransfer(transferId, transfer(WalletTransferStatus.VALIDATING));
        when(walletTransactionRepository.countByTransferId(transferId)).thenReturn(0L);

        var response = safetyService.dryRun(transferId, 1L, "check");

        assertThat(response.canAutoFail()).isTrue();
        assertThat(response.recommendedAction()).isEqualTo(AdminTransactionRecommendedAction.MARK_FAILED_ALLOWED);
    }

    @Test
    void processingWithoutLedgerKeepsOperationalException() {
        UUID transferId = UUID.randomUUID();
        givenTransfer(transferId, transfer(WalletTransferStatus.PROCESSING));
        when(walletTransactionRepository.countByTransferId(transferId)).thenReturn(0L);

        var response = safetyService.dryRun(transferId, 1L, "check");

        assertThat(response.canAutoFail()).isFalse();
        assertThat(response.recommendedAction()).isEqualTo(AdminTransactionRecommendedAction.KEEP_OPERATIONAL_EXCEPTION);
    }

    @Test
    void completedTransactionNeedsNoAction() {
        UUID transferId = UUID.randomUUID();
        givenTransfer(transferId, transfer(WalletTransferStatus.COMPLETED));
        when(walletTransactionRepository.countByTransferId(transferId)).thenReturn(0L);

        var response = safetyService.dryRun(transferId, 1L, "check");

        assertThat(response.canAutoFail()).isFalse();
        assertThat(response.recommendedAction()).isEqualTo(AdminTransactionRecommendedAction.NO_ACTION);
    }

    @Test
    void failedTransactionNeedsNoAction() {
        UUID transferId = UUID.randomUUID();
        WalletTransfer transfer = transfer(WalletTransferStatus.FAILED);
        givenTransfer(transferId, transfer);
        when(walletTransactionRepository.countByTransferId(transferId)).thenReturn(0L);

        var response = safetyService.dryRun(transferId, 1L, "check");

        assertThat(response.canAutoFail()).isFalse();
        assertThat(response.currentStatus()).isEqualTo(WalletTransferStatus.FAILED);
        assertThat(response.failureStage()).isEqualTo(WalletTransferFailureStage.RECOVERY_TIMEOUT);
        assertThat(response.retryable()).isTrue();
        assertThat(response.recommendedAction()).isEqualTo(AdminTransactionRecommendedAction.NO_ACTION);
    }

    @Test
    void ledgerBlocksManualFailEvenBeforeProcessing() {
        UUID transferId = UUID.randomUUID();
        givenTransfer(transferId, transfer(WalletTransferStatus.VALIDATING));
        when(walletTransactionRepository.countByTransferId(transferId)).thenReturn(1L);

        var response = safetyService.dryRun(transferId, 1L, "check");

        assertThat(response.canAutoFail()).isFalse();
        assertThat(response.hasLedger()).isTrue();
        assertThat(response.ledgerCount()).isEqualTo(1);
        assertThat(response.recommendedAction()).isEqualTo(AdminTransactionRecommendedAction.KEEP_OPERATIONAL_EXCEPTION);
    }

    @Test
    void dryRunRecordsAuditLog() {
        UUID transferId = UUID.randomUUID();
        givenTransfer(transferId, transfer(WalletTransferStatus.REQUESTED));
        when(walletTransactionRepository.countByTransferId(transferId)).thenReturn(0L);

        safetyService.dryRun(transferId, 7L, "manual check");

        verify(auditLogService).recordSuccess(
                7L,
                AdminOperationActionType.DRY_RUN_TRANSACTION,
                AdminOperationTargetType.TRANSACTION,
                transferId.toString(),
                WalletTransferStatus.REQUESTED.name(),
                WalletTransferStatus.REQUESTED.name(),
                "manual check",
                "{\"reason\":\"manual check\"}"
        );
    }

    @Test
    void rejectsNullAdminId() {
        assertThatThrownBy(() -> safetyService.dryRun(UUID.randomUUID(), null, "check"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("adminId");
    }

    private void givenTransfer(UUID transferId, WalletTransfer transfer) {
        when(walletTransferRepository.findById(transferId)).thenReturn(Optional.of(transfer));
    }

    private WalletTransfer transfer(WalletTransferStatus status) {
        WalletTransfer transfer = new WalletTransfer(
                UUID.randomUUID(),
                1L,
                UUID.randomUUID(),
                UUID.randomUUID().toString().replace("-", "")
        );
        if (status == WalletTransferStatus.VALIDATING) {
            transfer.startValidating();
        } else if (status == WalletTransferStatus.PROCESSING) {
            transfer.startValidating();
            transfer.startProcessing();
        } else if (status == WalletTransferStatus.COMPLETED) {
            transfer.startValidating();
            transfer.startProcessing();
            transfer.complete();
        } else if (status == WalletTransferStatus.FAILED) {
            transfer.fail(ErrorCode.TRANSACTION_STALE, WalletTransferFailureStage.RECOVERY_TIMEOUT, true);
        }
        return transfer;
    }
}
