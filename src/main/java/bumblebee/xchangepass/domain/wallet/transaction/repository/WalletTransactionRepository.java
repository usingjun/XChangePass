package bumblebee.xchangepass.domain.wallet.transaction.repository;

import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    @Query("""
                select t from WalletTransaction t
                where t.myWallet.walletId=:walletId or t.counterWallet.walletId=:walletId
                order by t.updatedAt DESC
            """)
    List<WalletTransaction> getWalletTransaction(@Param("walletId") Long walletId);

}