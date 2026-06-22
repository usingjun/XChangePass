package bumblebee.xchangepass.domain.wallet.transfer.service;

import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.domain.wallet.transfer.dto.StaleWalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.dto.WalletTransferReconciliationIssue;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.transfer.repository.WalletTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletTransferOperationsService {

    private static final EnumSet<WalletTransferStatus> ACTIVE_STATUSES = EnumSet.of(
            WalletTransferStatus.REQUESTED,
            WalletTransferStatus.VALIDATING,
            WalletTransferStatus.PROCESSING
    );

    private final WalletTransferRepository transferRepository;
    private final WalletTransactionRepository transactionRepository;
    private final MeterRegistry meterRegistry;

    @Value("${wallet.transfer.recovery.stale-threshold-minutes:5}")
    private long staleThresholdMinutes;

    @Transactional(readOnly = true)
    public List<StaleWalletTransfer> findStaleTransfers() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(staleThresholdMinutes);
        return transferRepository
                .findByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(ACTIVE_STATUSES, cutoff).stream()
                .map(StaleWalletTransfer::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WalletTransferReconciliationIssue> findReconciliationIssues() {
        List<WalletTransferReconciliationIssue> issues = transferRepository.findAll().stream()
                .map(transfer -> {
                    long actual = transactionRepository.countByTransferId(transfer.getTransferId());
                    long expected = transfer.getStatus() == WalletTransferStatus.COMPLETED ? 1L : 0L;
                    return new WalletTransferReconciliationIssue(
                            transfer.getTransferId(), transfer.getStatus(), actual, expected
                    );
                })
                .filter(issue -> issue.ledgerCount() != issue.expectedLedgerCount())
                .toList();
        issues.forEach(issue -> log.error(
                "Wallet transfer reconciliation mismatch: transferId={}, status={}, ledgerCount={}, expected={}",
                issue.transferId(), issue.status(), issue.ledgerCount(), issue.expectedLedgerCount()
        ));
        if (!issues.isEmpty()) {
            meterRegistry.counter("wallet.transfer.reconciliation.mismatches")
                    .increment(issues.size());
        }
        return issues;
    }
}
