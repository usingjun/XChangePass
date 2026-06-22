package bumblebee.xchangepass.domain.wallet.transfer.recovery.service;

import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseSeverity;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseType;
import bumblebee.xchangepass.domain.wallet.transfer.repository.WalletTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletTransferRecoveryService {

    private static final EnumSet<WalletTransferStatus> ACTIVE_STATUSES = EnumSet.of(
            WalletTransferStatus.REQUESTED,
            WalletTransferStatus.VALIDATING,
            WalletTransferStatus.PROCESSING
    );
    private static final EnumSet<WalletTransferStatus> TERMINAL_STATUSES = EnumSet.of(
            WalletTransferStatus.COMPLETED,
            WalletTransferStatus.FAILED
    );

    private final WalletTransferRepository transferRepository;
    private final WalletTransactionRepository transactionRepository;
    private final WalletTransferStaleFinalizer staleFinalizer;
    private final WalletTransferRecoveryCaseService recoveryCaseService;
    private final WalletTransferRecoveryMetrics metrics;
    private final AtomicInteger terminalPage = new AtomicInteger();

    @Value("${wallet.transfer.recovery.stale-threshold-minutes:5}")
    private long staleThresholdMinutes;

    @Value("${wallet.transfer.recovery.batch-size:100}")
    private int batchSize;

    public void recover() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(staleThresholdMinutes);
        try {
            recoverStaleActiveTransfers(cutoff);
        } catch (RuntimeException exception) {
            metrics.error("stale_query");
            log.error("Failed to query stale wallet transfers", exception);
        }
        try {
            reconcileTerminalTransfers();
        } catch (RuntimeException exception) {
            metrics.error("reconciliation_query");
            log.error("Failed to query wallet transfers for reconciliation", exception);
        }
    }

    private void recoverStaleActiveTransfers(LocalDateTime cutoff) {
        List<WalletTransfer> candidates = transferRepository
                .findUnresolvedRecoveryCandidates(
                        ACTIVE_STATUSES, cutoff, PageRequest.of(0, batchSize)
                );
        candidates.forEach(transfer -> recoverOne(transfer, cutoff));
    }

    private void recoverOne(WalletTransfer transfer, LocalDateTime cutoff) {
        try {
            long ledgerCount = transactionRepository.countByTransferId(transfer.getTransferId());
            if (isSafeToFinalize(transfer, ledgerCount)) {
                boolean finalized = staleFinalizer.finalizeIfUnchanged(
                        transfer.getTransferId(), transfer.getStatus(), transfer.getVersion(), cutoff
                );
                if (finalized) {
                    metrics.finalized(transfer.getStatus());
                    log.info("Stale wallet transfer finalized: transferId={}, previousStatus={}, version={}",
                            transfer.getTransferId(), transfer.getStatus(), transfer.getVersion());
                } else {
                    metrics.conflict();
                }
                return;
            }

            if (transfer.getStatus() == WalletTransferStatus.PROCESSING && ledgerCount == 0) {
                recoveryCaseService.record(
                        transfer, ledgerCount,
                        WalletTransferRecoveryCaseType.AMBIGUOUS_PROCESSING,
                        WalletTransferRecoveryCaseSeverity.WARNING
                );
            } else if (ledgerCount != 0) {
                recordLedgerMismatch(transfer, ledgerCount);
            }
        } catch (RuntimeException exception) {
            metrics.error("stale_candidate");
            log.error("Wallet transfer recovery failed: transferId={}", transfer.getTransferId(), exception);
        }
    }

    private void reconcileTerminalTransfers() {
        int pageNumber = terminalPage.get();
        List<WalletTransfer> page = transferRepository.findByStatusIn(
                TERMINAL_STATUSES,
                PageRequest.of(pageNumber, batchSize,
                        Sort.by("createdAt").ascending().and(Sort.by("transferId").ascending()))
        );
        if (page.isEmpty() && pageNumber > 0) {
            terminalPage.set(0);
            return;
        }
        page.forEach(this::reconcileTerminalTransfer);
        terminalPage.set(page.size() < batchSize ? 0 : pageNumber + 1);
    }

    private void reconcileTerminalTransfer(WalletTransfer transfer) {
        try {
            long ledgerCount = transactionRepository.countByTransferId(transfer.getTransferId());
            long expectedCount = transfer.getStatus() == WalletTransferStatus.COMPLETED ? 1L : 0L;
            if (ledgerCount != expectedCount) {
                recordLedgerMismatch(transfer, ledgerCount);
            }
        } catch (RuntimeException exception) {
            metrics.error("terminal_reconciliation");
            log.error("Wallet transfer reconciliation failed: transferId={}",
                    transfer.getTransferId(), exception);
        }
    }

    private boolean isSafeToFinalize(WalletTransfer transfer, long ledgerCount) {
        return ledgerCount == 0
                && (transfer.getStatus() == WalletTransferStatus.REQUESTED
                || transfer.getStatus() == WalletTransferStatus.VALIDATING);
    }

    private void recordLedgerMismatch(WalletTransfer transfer, long ledgerCount) {
        recoveryCaseService.record(
                transfer, ledgerCount,
                WalletTransferRecoveryCaseType.LEDGER_MISMATCH,
                WalletTransferRecoveryCaseSeverity.CRITICAL
        );
    }
}
