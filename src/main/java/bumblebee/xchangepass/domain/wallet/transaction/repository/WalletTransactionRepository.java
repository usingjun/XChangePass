package bumblebee.xchangepass.domain.wallet.transaction.repository;

import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long>, WalletTransactionRepositoryCustom {
    long countByTransferId(UUID transferId);
}
