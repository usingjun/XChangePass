package bumblebee.xchangepass.domain.wallet.transaction.repository;

import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.repository.search.WalletTransactionRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long>, WalletTransactionRepositoryCustom {
    @Query("""
                select t from WalletTransaction t
                where t.sender.userId=:userId or t.receiver.userId=:userId
                order by t.updatedAt DESC
            """)
    List<WalletTransaction> getWalletTransaction(@Param("userId") Long userId);

}