package bumblebee.xchangepass.domain.wallet.transaction.repository;

import bumblebee.xchangepass.domain.transaction.entity.ProjectionStatus;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import jakarta.persistence.LockModeType;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select transaction from WalletTransaction transaction
            where transaction.projection.status in :statuses
              and transaction.projection.nextAttemptAt <= :now
            order by transaction.transactionId
            """)
    List<WalletTransaction> findProjectionTargets(
            @Param("statuses") Collection<ProjectionStatus> statuses,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
