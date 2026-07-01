package bumblebee.xchangepass.domain.wallet.reconciliation.repository;

import bumblebee.xchangepass.domain.wallet.reconciliation.entity.WalletBalanceReconciliationIssue;
import bumblebee.xchangepass.domain.wallet.reconciliation.entity.WalletBalanceReconciliationIssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletBalanceReconciliationIssueRepository
        extends JpaRepository<WalletBalanceReconciliationIssue, Long> {

    Optional<WalletBalanceReconciliationIssue> findByWalletIdAndCurrencyAndStatus(
            Long walletId, String currency, WalletBalanceReconciliationIssueStatus status
    );
}
