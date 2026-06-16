package bumblebee.xchangepass.domain.exchangeTransaction.repository;

import bumblebee.xchangepass.domain.exchangeTransaction.entitiy.ExchangeTransaction;
import bumblebee.xchangepass.domain.transaction.repository.ExchangeTransactionRow;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ExchangeTransactionRepository extends JpaRepository<ExchangeTransaction, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transaction from ExchangeTransaction transaction where transaction.transactionId = :transactionId")
    ExchangeTransaction findByIdForUpdate(@Param("transactionId") Long transactionId);

    @Query("""
            select transaction.transactionId as transactionId,
                   transaction.user.userId as userId,
                   transaction.fromCurrency as fromCurrency,
                   transaction.toCurrency as toCurrency,
                   transaction.amount as amount,
                   transaction.receivedAmount as receivedAmount,
                   transaction.exchangeRate as exchangeRate,
                   transaction.completedAt as transactionTime
            from ExchangeTransaction transaction
            where transaction.user.userId = :userId
              and transaction.status = bumblebee.xchangepass.domain.exchangeTransaction.entitiy.ExchangeTransactionStatus.COMPLETED
              and (:startDate is null or transaction.completedAt >= :startDate)
              and (:endDate is null or transaction.completedAt <= :endDate)
              and (
                    :cursor is null
                    or transaction.completedAt < :cursor
                    or (
                        :includeCursorTime = true
                        and transaction.completedAt = :cursor
                        and (:cursorTransactionId is null or transaction.transactionId < :cursorTransactionId)
                    )
              )
            order by transaction.completedAt desc, transaction.transactionId desc
            """)
    List<ExchangeTransactionRow> findRecentForUser(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("cursor") LocalDateTime cursor,
            @Param("includeCursorTime") boolean includeCursorTime,
            @Param("cursorTransactionId") Long cursorTransactionId,
            Pageable pageable
    );
}
