package bumblebee.xchangepass.domain.wallet.transfer.recovery;

import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseSeverity;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseType;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.service.WalletTransferRecoveryCaseService;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.service.WalletTransferRecoveryMetrics;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.service.WalletTransferRecoveryService;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.service.WalletTransferStaleFinalizer;
import bumblebee.xchangepass.domain.wallet.transfer.repository.WalletTransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletTransferRecoveryServiceTest {

    private WalletTransferRepository transfers;
    private WalletTransactionRepository transactions;
    private WalletTransferStaleFinalizer finalizer;
    private WalletTransferRecoveryCaseService cases;
    private WalletTransferRecoveryMetrics metrics;
    private WalletTransferRecoveryService service;

    @BeforeEach
    void setUp() {
        transfers = mock(WalletTransferRepository.class);
        transactions = mock(WalletTransactionRepository.class);
        finalizer = mock(WalletTransferStaleFinalizer.class);
        cases = mock(WalletTransferRecoveryCaseService.class);
        metrics = mock(WalletTransferRecoveryMetrics.class);
        service = new WalletTransferRecoveryService(transfers, transactions, finalizer, cases, metrics);
        ReflectionTestUtils.setField(service, "staleThresholdMinutes", 5L);
        ReflectionTestUtils.setField(service, "batchSize", 100);
        when(transfers.findByStatusIn(anyCollection(), any())).thenReturn(List.of());
    }

    @Test
    void finalizesOnlyPreMoneyStateWithoutLedger() {
        WalletTransfer transfer = transfer(WalletTransferStatus.VALIDATING);
        staleCandidates(transfer);
        when(transactions.countByTransferId(transfer.getTransferId())).thenReturn(0L);
        when(finalizer.finalizeIfUnchanged(any(), any(), any(), any())).thenReturn(true);

        service.recover();

        verify(finalizer).finalizeIfUnchanged(
                eq(transfer.getTransferId()), eq(WalletTransferStatus.VALIDATING),
                eq(transfer.getVersion()), any()
        );
        verify(metrics).finalized(WalletTransferStatus.VALIDATING);
        verify(cases, never()).record(any(), anyLong(), any(), any());
    }

    @Test
    void processingWithoutLedgerCreatesAmbiguousCaseWithoutFinalization() {
        WalletTransfer transfer = transfer(WalletTransferStatus.PROCESSING);
        staleCandidates(transfer);
        when(transactions.countByTransferId(transfer.getTransferId())).thenReturn(0L);

        service.recover();

        verify(finalizer, never()).finalizeIfUnchanged(any(), any(), any(), any());
        verify(cases).record(
                transfer, 0L,
                WalletTransferRecoveryCaseType.AMBIGUOUS_PROCESSING,
                WalletTransferRecoveryCaseSeverity.WARNING
        );
    }

    @Test
    void activeTransferWithLedgerCreatesCriticalMismatch() {
        WalletTransfer transfer = transfer(WalletTransferStatus.REQUESTED);
        staleCandidates(transfer);
        when(transactions.countByTransferId(transfer.getTransferId())).thenReturn(1L);

        service.recover();

        verify(finalizer, never()).finalizeIfUnchanged(any(), any(), any(), any());
        verify(cases).record(
                transfer, 1L,
                WalletTransferRecoveryCaseType.LEDGER_MISMATCH,
                WalletTransferRecoveryCaseSeverity.CRITICAL
        );
    }

    private void staleCandidates(WalletTransfer transfer) {
        when(transfers.findUnresolvedRecoveryCandidates(
                anyCollection(), any(LocalDateTime.class), any()
        )).thenReturn(List.of(transfer));
    }

    private WalletTransfer transfer(WalletTransferStatus status) {
        WalletTransfer transfer = new WalletTransfer(
                UUID.randomUUID(), 1L, UUID.randomUUID(), "a".repeat(64)
        );
        if (status == WalletTransferStatus.VALIDATING || status == WalletTransferStatus.PROCESSING) {
            transfer.startValidating();
        }
        if (status == WalletTransferStatus.PROCESSING) {
            transfer.startProcessing();
        }
        ReflectionTestUtils.setField(transfer, "version", 3L);
        return transfer;
    }
}
