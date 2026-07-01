package bumblebee.xchangepass.domain.wallet.reconciliation;

import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.repository.WalletBalanceRepository;
import bumblebee.xchangepass.domain.wallet.reconciliation.entity.WalletBalanceReconciliationIssue;
import bumblebee.xchangepass.domain.wallet.reconciliation.entity.WalletBalanceReconciliationIssueStatus;
import bumblebee.xchangepass.domain.wallet.reconciliation.repository.WalletBalanceReconciliationIssueRepository;
import bumblebee.xchangepass.domain.wallet.reconciliation.service.WalletBalanceReconciliationService;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferFailureStage;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCase;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseSeverity;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseType;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.repository.WalletTransferRecoveryCaseRepository;
import bumblebee.xchangepass.domain.wallet.transfer.repository.WalletTransferRepository;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletBalanceReconciliationServiceTest {

    private final WalletBalanceRepository balanceRepository = mock(WalletBalanceRepository.class);
    private final WalletTransactionRepository transactionRepository = mock(WalletTransactionRepository.class);
    private final WalletTransferRepository transferRepository = mock(WalletTransferRepository.class);
    private final WalletTransferRecoveryCaseRepository recoveryCaseRepository =
            mock(WalletTransferRecoveryCaseRepository.class);
    private final WalletBalanceReconciliationIssueRepository issueRepository =
            mock(WalletBalanceReconciliationIssueRepository.class);

    private final WalletBalanceReconciliationService service = new WalletBalanceReconciliationService(
            balanceRepository,
            transactionRepository,
            transferRepository,
            recoveryCaseRepository,
            issueRepository
    );

    @Test
    void sameSnapshotAndLedgerDoesNotCreateIssue() {
        User user = user(1L);
        WalletBalance balance = balance(10L, user, "KRW", "1000.00");
        when(balanceRepository.findAll()).thenReturn(List.of(balance));
        when(transactionRepository.findAll()).thenReturn(List.of(
                ledger(user, null, "1000.00", null, null, "KRW", WalletTransactionType.DEPOSIT)
        ));
        noSkippedTargets();

        var response = service.reconcileAll();

        assertThat(response.checkedBalanceCount()).isEqualTo(1);
        assertThat(response.issueCount()).isZero();
        verify(issueRepository, never()).save(any());
    }

    @Test
    void snapshotGreaterThanLedgerCreatesPositiveDifferenceIssue() {
        User user = user(1L);
        WalletBalance balance = balance(10L, user, "KRW", "1200.00");
        when(balanceRepository.findAll()).thenReturn(List.of(balance));
        when(transactionRepository.findAll()).thenReturn(List.of(
                ledger(user, null, "1000.00", null, null, "KRW", WalletTransactionType.DEPOSIT)
        ));
        noSkippedTargets();
        when(issueRepository.findByWalletIdAndCurrencyAndStatus(
                10L, "KRW", WalletBalanceReconciliationIssueStatus.OPEN
        )).thenReturn(Optional.empty());

        service.reconcileAll();

        WalletBalanceReconciliationIssue issue = savedIssue();
        assertThat(issue.getWalletId()).isEqualTo(10L);
        assertThat(issue.getUserId()).isEqualTo(1L);
        assertThat(issue.getCurrency()).isEqualTo("KRW");
        assertThat(issue.getSnapshotAmount()).isEqualByComparingTo("1200.00");
        assertThat(issue.getLedgerCalculatedAmount()).isEqualByComparingTo("1000.00");
        assertThat(issue.getDifferenceAmount()).isEqualByComparingTo("200.00");
        assertThat(issue.getStatus()).isEqualTo(WalletBalanceReconciliationIssueStatus.OPEN);
        assertThat(issue.getBasisTime()).isNotNull();
    }

    @Test
    void snapshotLessThanLedgerCreatesNegativeDifferenceIssue() {
        User user = user(1L);
        WalletBalance balance = balance(10L, user, "KRW", "800.00");
        when(balanceRepository.findAll()).thenReturn(List.of(balance));
        when(transactionRepository.findAll()).thenReturn(List.of(
                ledger(user, null, "1000.00", null, null, "KRW", WalletTransactionType.DEPOSIT)
        ));
        noSkippedTargets();
        when(issueRepository.findByWalletIdAndCurrencyAndStatus(
                10L, "KRW", WalletBalanceReconciliationIssueStatus.OPEN
        )).thenReturn(Optional.empty());

        service.reconcileAll();

        assertThat(savedIssue().getDifferenceAmount()).isEqualByComparingTo("-200.00");
    }

    @Test
    void depositLedgerAddsAmount() {
        User user = user(1L);
        WalletBalance balance = balance(10L, user, "KRW", "1500.00");
        when(balanceRepository.findAll()).thenReturn(List.of(balance));
        when(transactionRepository.findAll()).thenReturn(List.of(
                ledger(user, null, "1000.00", null, null, "KRW", WalletTransactionType.DEPOSIT),
                ledger(user, null, "500.00", null, null, "KRW", WalletTransactionType.DEPOSIT)
        ));
        noSkippedTargets();

        service.reconcileAll();

        verify(issueRepository, never()).save(any());
    }

    @Test
    void withdrawalLedgerSubtractsAmount() {
        User user = user(1L);
        WalletBalance balance = balance(10L, user, "KRW", "700.00");
        when(balanceRepository.findAll()).thenReturn(List.of(balance));
        when(transactionRepository.findAll()).thenReturn(List.of(
                ledger(user, null, "1000.00", null, null, "KRW", WalletTransactionType.DEPOSIT),
                ledger(user, null, "300.00", null, null, "KRW", WalletTransactionType.WITHDRAWAL)
        ));
        noSkippedTargets();

        service.reconcileAll();

        verify(issueRepository, never()).save(any());
    }

    @Test
    void transferSubtractsSenderCurrencyAndAddsReceiverCurrency() {
        User sender = user(1L);
        User receiver = user(2L);
        WalletBalance senderBalance = balance(10L, sender, "KRW", "9000.00");
        WalletBalance receiverBalance = balance(20L, receiver, "USD", "7.50");
        when(balanceRepository.findAll()).thenReturn(List.of(senderBalance, receiverBalance));
        when(transactionRepository.findAll()).thenReturn(List.of(
                ledger(sender, null, "10000.00", null, null, "KRW", WalletTransactionType.DEPOSIT),
                ledger(sender, receiver, "1000.00", "7.50", "KRW", "USD", WalletTransactionType.TRANSFER)
        ));
        noSkippedTargets();

        service.reconcileAll();

        verify(issueRepository, never()).save(any());
    }

    @Test
    void sameCurrencyTransferReceivedAmountFallsBackToAmountWhenNull() {
        User sender = user(1L);
        User receiver = user(2L);
        WalletBalance receiverBalance = balance(20L, receiver, "KRW", "1000.00");
        when(balanceRepository.findAll()).thenReturn(List.of(receiverBalance));
        when(transactionRepository.findAll()).thenReturn(List.of(
                ledger(sender, receiver, "1000.00", null, "KRW", "KRW", WalletTransactionType.TRANSFER)
        ));
        noSkippedTargets();

        var response = service.reconcileAll();

        assertThat(response.uncalculableLedgerCount()).isZero();
        verify(issueRepository, never()).save(any());
    }

    @Test
    void foreignCurrencyTransferWithoutReceivedAmountDoesNotCreditReceiverBySentAmount() {
        User sender = user(1L);
        User receiver = user(2L);
        WalletBalance receiverBalance = balance(20L, receiver, "USD", "0.00");
        when(balanceRepository.findAll()).thenReturn(List.of(receiverBalance));
        when(transactionRepository.findAll()).thenReturn(List.of(
                ledger(sender, receiver, "1000.00", null, "KRW", "USD", WalletTransactionType.TRANSFER)
        ));
        noSkippedTargets();

        var response = service.reconcileAll();

        assertThat(response.uncalculableLedgerCount()).isEqualTo(1);
        assertThat(response.issueCount()).isZero();
        verify(issueRepository, never()).save(any());
    }

    @Test
    void foreignCurrencyTransferWithoutReceivedAmountIsReportedAsUncalculable() {
        User sender = user(1L);
        User receiver = user(2L);
        WalletBalance senderBalance = balance(10L, sender, "KRW", "-1000.00");
        WalletBalance receiverBalance = balance(20L, receiver, "USD", "0.00");
        when(balanceRepository.findAll()).thenReturn(List.of(senderBalance, receiverBalance));
        when(transactionRepository.findAll()).thenReturn(List.of(
                ledger(sender, receiver, "1000.00", null, "KRW", "USD", WalletTransactionType.TRANSFER)
        ));
        noSkippedTargets();

        var response = service.reconcileAll();

        assertThat(response.uncalculableLedgerCount()).isEqualTo(1);
        assertThat(response.issueCount()).isZero();
    }

    @Test
    void existingOpenIssueIsUpdatedInsteadOfCreatingNewRow() {
        User user = user(1L);
        WalletBalance balance = balance(10L, user, "KRW", "1200.00");
        WalletBalanceReconciliationIssue existing = new WalletBalanceReconciliationIssue(
                10L, 1L, "KRW",
                amount("1100.00"), amount("1000.00"), amount("100.00"), LocalDateTime.now().minusDays(1)
        );
        when(balanceRepository.findAll()).thenReturn(List.of(balance));
        when(transactionRepository.findAll()).thenReturn(List.of(
                ledger(user, null, "1000.00", null, null, "KRW", WalletTransactionType.DEPOSIT)
        ));
        noSkippedTargets();
        when(issueRepository.findByWalletIdAndCurrencyAndStatus(
                10L, "KRW", WalletBalanceReconciliationIssueStatus.OPEN
        )).thenReturn(Optional.of(existing));

        var response = service.reconcileAll();

        assertThat(response.updatedIssueCount()).isEqualTo(1);
        assertThat(response.createdIssueCount()).isZero();
        assertThat(existing.getSnapshotAmount()).isEqualByComparingTo("1200.00");
        assertThat(existing.getLedgerCalculatedAmount()).isEqualByComparingTo("1000.00");
        assertThat(existing.getDifferenceAmount()).isEqualByComparingTo("200.00");
        verify(issueRepository, never()).save(any());
    }

    @Test
    void reconciliationDoesNotMutateBalanceValue() {
        User user = user(1L);
        WalletBalance balance = balance(10L, user, "KRW", "1200.00");
        when(balanceRepository.findAll()).thenReturn(List.of(balance));
        when(transactionRepository.findAll()).thenReturn(List.of(
                ledger(user, null, "1000.00", null, null, "KRW", WalletTransactionType.DEPOSIT)
        ));
        noSkippedTargets();
        when(issueRepository.findByWalletIdAndCurrencyAndStatus(
                10L, "KRW", WalletBalanceReconciliationIssueStatus.OPEN
        )).thenReturn(Optional.empty());

        service.reconcileAll();

        assertThat(balance.getBalance()).isEqualByComparingTo("1200.00");
        verify(balance, never()).addBalance(any());
        verify(balance, never()).subtractBalance(any());
    }

    @Test
    void activeTransferSenderIsSkipped() {
        User user = user(1L);
        WalletBalance balance = balance(10L, user, "KRW", "1200.00");
        WalletTransfer activeTransfer = new WalletTransfer(UUID.randomUUID(), 1L, UUID.randomUUID(), "a".repeat(64));
        when(balanceRepository.findAll()).thenReturn(List.of(balance));
        when(transactionRepository.findAll()).thenReturn(List.of(
                ledger(user, null, "1000.00", null, null, "KRW", WalletTransactionType.DEPOSIT)
        ));
        when(transferRepository.findAll()).thenReturn(List.of(activeTransfer));
        when(recoveryCaseRepository.findAll()).thenReturn(List.of());

        var response = service.reconcileAll();

        assertThat(response.checkedBalanceCount()).isZero();
        assertThat(response.skippedBalanceCount()).isEqualTo(1);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void activeTransferReceiverIsSkipped() {
        User receiver = user(2L);
        WalletBalance balance = balance(20L, receiver, "KRW", "1200.00");
        WalletTransfer activeTransfer = new WalletTransfer(UUID.randomUUID(), 1L, UUID.randomUUID(), "a".repeat(64));
        activeTransfer.startValidating();
        activeTransfer.assignReceiver(2L);
        when(balanceRepository.findAll()).thenReturn(List.of(balance));
        when(transactionRepository.findAll()).thenReturn(List.of(
                ledger(receiver, null, "1000.00", null, null, "KRW", WalletTransactionType.DEPOSIT)
        ));
        when(transferRepository.findAll()).thenReturn(List.of(activeTransfer));
        when(recoveryCaseRepository.findAll()).thenReturn(List.of());

        var response = service.reconcileAll();

        assertThat(response.checkedBalanceCount()).isZero();
        assertThat(response.skippedBalanceCount()).isEqualTo(1);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void terminalTransfersAreNotSkipped() {
        User sender = user(1L);
        User receiver = user(2L);
        WalletBalance senderBalance = balance(10L, sender, "KRW", "1200.00");
        WalletBalance receiverBalance = balance(20L, receiver, "KRW", "900.00");
        WalletTransfer completedTransfer = new WalletTransfer(UUID.randomUUID(), 1L, UUID.randomUUID(), "a".repeat(64));
        completedTransfer.startValidating();
        completedTransfer.assignReceiver(2L);
        completedTransfer.startProcessing();
        completedTransfer.complete();
        WalletTransfer failedTransfer = new WalletTransfer(UUID.randomUUID(), 2L, UUID.randomUUID(), "b".repeat(64));
        failedTransfer.startValidating();
        failedTransfer.assignReceiver(1L);
        failedTransfer.fail(ErrorCode.TRANSACTION_PROCESSING_FAILED, WalletTransferFailureStage.UNKNOWN, false);
        when(balanceRepository.findAll()).thenReturn(List.of(senderBalance, receiverBalance));
        when(transactionRepository.findAll()).thenReturn(List.of());
        when(transferRepository.findAll()).thenReturn(List.of(completedTransfer, failedTransfer));
        when(recoveryCaseRepository.findAll()).thenReturn(List.of());
        when(issueRepository.findByWalletIdAndCurrencyAndStatus(
                10L, "KRW", WalletBalanceReconciliationIssueStatus.OPEN
        )).thenReturn(Optional.empty());
        when(issueRepository.findByWalletIdAndCurrencyAndStatus(
                20L, "KRW", WalletBalanceReconciliationIssueStatus.OPEN
        )).thenReturn(Optional.empty());

        var response = service.reconcileAll();

        assertThat(response.checkedBalanceCount()).isEqualTo(2);
        assertThat(response.skippedBalanceCount()).isZero();
        assertThat(response.issueCount()).isEqualTo(2);
        verify(issueRepository, times(2)).save(any());
    }

    @Test
    void unresolvedRecoveryCaseSenderIsSkipped() {
        User user = user(1L);
        WalletBalance balance = balance(10L, user, "KRW", "1200.00");
        UUID transferId = UUID.randomUUID();
        WalletTransfer failedTransfer = new WalletTransfer(transferId, 1L, UUID.randomUUID(), "a".repeat(64));
        failedTransfer.startValidating();
        failedTransfer.startProcessing();
        failedTransfer.fail(ErrorCode.TRANSACTION_PROCESSING_FAILED, WalletTransferFailureStage.UNKNOWN, false);
        WalletTransferRecoveryCase recoveryCase = new WalletTransferRecoveryCase(
                transferId,
                WalletTransferRecoveryCaseType.LEDGER_MISMATCH,
                WalletTransferRecoveryCaseSeverity.CRITICAL,
                WalletTransferStatus.FAILED,
                1L,
                1L
        );
        when(balanceRepository.findAll()).thenReturn(List.of(balance));
        when(transactionRepository.findAll()).thenReturn(List.of(
                ledger(user, null, "1000.00", null, null, "KRW", WalletTransactionType.DEPOSIT)
        ));
        when(transferRepository.findAll()).thenReturn(List.of(failedTransfer));
        when(recoveryCaseRepository.findAll()).thenReturn(List.of(recoveryCase));

        var response = service.reconcileAll();

        assertThat(response.checkedBalanceCount()).isZero();
        assertThat(response.skippedBalanceCount()).isEqualTo(1);
        verify(issueRepository, never()).save(any());
    }

    private void noSkippedTargets() {
        when(transferRepository.findAll()).thenReturn(List.of());
        when(recoveryCaseRepository.findAll()).thenReturn(List.of());
    }

    private WalletBalanceReconciliationIssue savedIssue() {
        ArgumentCaptor<WalletBalanceReconciliationIssue> captor =
                ArgumentCaptor.forClass(WalletBalanceReconciliationIssue.class);
        verify(issueRepository).save(captor.capture());
        return captor.getValue();
    }

    private WalletBalance balance(Long walletId, User user, String currency, String balanceAmount) {
        Wallet wallet = mock(Wallet.class);
        WalletBalance balance = mock(WalletBalance.class);
        when(wallet.getWalletId()).thenReturn(walletId);
        when(wallet.getUser()).thenReturn(user);
        when(balance.getWallet()).thenReturn(wallet);
        when(balance.getCurrency()).thenReturn(Currency.getInstance(currency));
        when(balance.getBalance()).thenReturn(amount(balanceAmount));
        return balance;
    }

    private User user(Long userId) {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(userId);
        return user;
    }

    private WalletTransaction ledger(User user, User counterpartyUser, String amount, String receivedAmount,
                                     String fromCurrency, String toCurrency, WalletTransactionType type) {
        return new WalletTransaction(
                user,
                counterpartyUser,
                amount(amount),
                receivedAmount == null ? null : amount(receivedAmount),
                fromCurrency,
                toCurrency,
                type,
                LocalDateTime.now()
        );
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
