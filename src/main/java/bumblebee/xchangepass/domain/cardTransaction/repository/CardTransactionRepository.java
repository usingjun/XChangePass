package bumblebee.xchangepass.domain.cardTransaction.repository;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransaction;
import bumblebee.xchangepass.domain.transaction.entity.ProjectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import jakarta.persistence.LockModeType;

public interface CardTransactionRepository extends JpaRepository<CardTransaction, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select transaction from CardTransaction transaction
            where transaction.projection.status in :statuses
              and transaction.projection.nextAttemptAt <= :now
            order by transaction.transactionId
            """)
    List<CardTransaction> findProjectionTargets(
            @Param("statuses") Collection<ProjectionStatus> statuses,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
