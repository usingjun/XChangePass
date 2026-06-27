package bumblebee.xchangepass.domain.wallet.transfer.recovery;

import bumblebee.xchangepass.domain.monitoring.service.TransactionStatusEventService;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseSeverity;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseType;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.service.WalletTransferRecoveryCaseService;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.service.WalletTransferRecoveryCaseWriter;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.service.WalletTransferRecoveryMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WalletTransferRecoveryCaseServiceTest {

    @Test
    void uniqueConflictReobservesExistingCase() {
        WalletTransferRecoveryCaseWriter writer = mock(WalletTransferRecoveryCaseWriter.class);
        WalletTransferRecoveryMetrics metrics = mock(WalletTransferRecoveryMetrics.class);
        TransactionStatusEventService eventService = mock(TransactionStatusEventService.class);
        WalletTransferRecoveryCaseService service =
                new WalletTransferRecoveryCaseService(writer, metrics, eventService);
        WalletTransfer transfer = transfer();
        doThrow(new DataIntegrityViolationException("duplicate")).when(writer).create(
                transfer.getTransferId(),
                WalletTransferRecoveryCaseType.AMBIGUOUS_PROCESSING,
                WalletTransferRecoveryCaseSeverity.WARNING,
                transfer.getStatus(),
                transfer.getVersion(),
                0L
        );

        service.record(
                transfer, 0L,
                WalletTransferRecoveryCaseType.AMBIGUOUS_PROCESSING,
                WalletTransferRecoveryCaseSeverity.WARNING
        );

        verify(writer).reobserve(
                transfer.getTransferId(),
                WalletTransferRecoveryCaseType.AMBIGUOUS_PROCESSING,
                WalletTransferRecoveryCaseSeverity.WARNING,
                transfer.getStatus(),
                transfer.getVersion(),
                0L
        );
        verify(metrics).caseReobserved(WalletTransferRecoveryCaseType.AMBIGUOUS_PROCESSING);
    }

    private WalletTransfer transfer() {
        WalletTransfer transfer = new WalletTransfer(
                UUID.randomUUID(), 1L, UUID.randomUUID(), "a".repeat(64)
        );
        ReflectionTestUtils.setField(transfer, "version", 3L);
        return transfer;
    }
}
