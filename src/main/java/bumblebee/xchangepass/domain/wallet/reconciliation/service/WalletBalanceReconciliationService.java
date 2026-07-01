package bumblebee.xchangepass.domain.wallet.reconciliation.service;

import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.repository.WalletBalanceRepository;
import bumblebee.xchangepass.domain.wallet.reconciliation.dto.WalletBalanceReconciliationRunResponse;
import bumblebee.xchangepass.domain.wallet.reconciliation.dto.WalletLedgerAggregationResult;
import bumblebee.xchangepass.domain.wallet.reconciliation.entity.WalletBalanceReconciliationIssue;
import bumblebee.xchangepass.domain.wallet.reconciliation.entity.WalletBalanceReconciliationIssueStatus;
import bumblebee.xchangepass.domain.wallet.reconciliation.repository.WalletBalanceReconciliationIssueRepository;
import bumblebee.xchangepass.domain.wallet.reconciliation.repository.WalletLedgerAggregationRepository;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseStatus;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.repository.WalletTransferRecoveryCaseRepository;
import bumblebee.xchangepass.domain.wallet.transfer.repository.WalletTransferRepository;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WalletBalanceReconciliationService {

    private static final EnumSet<WalletTransferStatus> ACTIVE_TRANSFER_STATUSES = EnumSet.of(
            WalletTransferStatus.REQUESTED,
            WalletTransferStatus.VALIDATING,
            WalletTransferStatus.PROCESSING
    );

    private final WalletBalanceRepository balanceRepository;
    private final WalletLedgerAggregationRepository ledgerAggregationRepository;
    private final WalletTransferRepository transferRepository;
    private final WalletTransferRecoveryCaseRepository recoveryCaseRepository;
    private final WalletBalanceReconciliationIssueRepository issueRepository;
    private final Map<IssueKey, Object> issueLocks = new ConcurrentHashMap<>();

    @Transactional
    public WalletBalanceReconciliationRunResponse reconcileAll() {
        return reconcile(balanceRepository.findAll());
    }

    @Transactional
    public WalletBalanceReconciliationRunResponse reconcileBalance(Long balanceId) {
        WalletBalance balance = balanceRepository.findById(balanceId)
                .orElseThrow(ErrorCode.BALANCE_NOT_FOUND::commonException);
        return reconcile(List.of(balance));
    }

    private WalletBalanceReconciliationRunResponse reconcile(List<WalletBalance> balances) {
        LocalDateTime basisTime = LocalDateTime.now();
        WalletLedgerAggregationResult ledgerCalculation = ledgerAggregationRepository.aggregate();
        Map<LedgerKey, BigDecimal> ledgerBalances = ledgerBalances(ledgerCalculation);
        Set<Long> skippedUserIds = skippedUserIds(transferRepository.findAll());

        int checkedBalanceCount = 0;
        int skippedBalanceCount = 0;
        int issueCount = 0;
        int createdIssueCount = 0;
        int updatedIssueCount = 0;

        for (WalletBalance balance : balances) {
            Wallet wallet = balance.getWallet();
            User user = wallet.getUser();
            Long userId = user.getUserId();
            if (skippedUserIds.contains(userId)) {
                skippedBalanceCount++;
                continue;
            }

            checkedBalanceCount++;
            String currency = balance.getCurrency().getCurrencyCode();
            BigDecimal snapshotAmount = balance.getBalance();
            BigDecimal ledgerCalculatedAmount = ledgerBalances.getOrDefault(
                    new LedgerKey(userId, currency), BigDecimal.ZERO
            );
            BigDecimal differenceAmount = snapshotAmount.subtract(ledgerCalculatedAmount);
            if (differenceAmount.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            issueCount++;
            IssueRecordResult result = recordIssue(
                    wallet.getWalletId(), userId, currency,
                    snapshotAmount, ledgerCalculatedAmount, differenceAmount, basisTime
            );
            if (result == IssueRecordResult.UPDATED) {
                updatedIssueCount++;
            } else {
                createdIssueCount++;
            }
        }

        return new WalletBalanceReconciliationRunResponse(
                checkedBalanceCount,
                skippedBalanceCount,
                ledgerCalculation.uncalculableLedgerCount(),
                issueCount,
                createdIssueCount,
                updatedIssueCount
        );
    }

    private IssueRecordResult recordIssue(Long walletId, Long userId, String currency,
                                          BigDecimal snapshotAmount, BigDecimal ledgerCalculatedAmount,
                                          BigDecimal differenceAmount, LocalDateTime basisTime) {
        IssueKey key = new IssueKey(walletId, currency);
        Object lock = issueLocks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            var existing = issueRepository.findByWalletIdAndCurrencyAndStatus(
                    walletId, currency, WalletBalanceReconciliationIssueStatus.OPEN
            );
            if (existing.isPresent()) {
                existing.get().update(snapshotAmount, ledgerCalculatedAmount, differenceAmount, basisTime);
                return IssueRecordResult.UPDATED;
            }

            issueRepository.save(new WalletBalanceReconciliationIssue(
                    walletId, userId, currency,
                    snapshotAmount, ledgerCalculatedAmount, differenceAmount, basisTime
            ));
            return IssueRecordResult.CREATED;
        }
    }

    private Map<LedgerKey, BigDecimal> ledgerBalances(WalletLedgerAggregationResult ledgerCalculation) {
        return ledgerCalculation.balances().stream()
                .collect(Collectors.toMap(
                        aggregate -> new LedgerKey(aggregate.userId(), aggregate.currency()),
                        aggregate -> aggregate.ledgerCalculatedAmount(),
                        BigDecimal::add
                ));
    }

    private Set<Long> skippedUserIds(List<WalletTransfer> transfers) {
        Map<UUID, WalletTransfer> transferById = new HashMap<>();
        Set<Long> skippedUserIds = new HashSet<>();

        for (WalletTransfer transfer : transfers) {
            transferById.put(transfer.getTransferId(), transfer);
            if (ACTIVE_TRANSFER_STATUSES.contains(transfer.getStatus())) {
                addParticipantUserIds(skippedUserIds, transfer);
            }
        }

        recoveryCaseRepository.findAll().stream()
                .filter(recoveryCase -> recoveryCase.getCaseStatus() != WalletTransferRecoveryCaseStatus.RESOLVED)
                .map(recoveryCase -> transferById.get(recoveryCase.getTransferId()))
                .filter(transfer -> transfer != null)
                .forEach(transfer -> addParticipantUserIds(skippedUserIds, transfer));

        return skippedUserIds;
    }

    private void addParticipantUserIds(Set<Long> skippedUserIds, WalletTransfer transfer) {
        if (transfer.getSenderUserId() != null) {
            skippedUserIds.add(transfer.getSenderUserId());
        }
        if (transfer.getReceiverUserId() != null) {
            skippedUserIds.add(transfer.getReceiverUserId());
        }
    }

    private record LedgerKey(Long userId, String currency) {
    }

    private record IssueKey(Long walletId, String currency) {
    }

    private enum IssueRecordResult {
        CREATED,
        UPDATED
    }
}
