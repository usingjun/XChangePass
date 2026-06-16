package bumblebee.xchangepass.domain.cardTransaction.repository;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransaction;
import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import bumblebee.xchangepass.domain.transaction.repository.CardTransactionRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CardTransactionRepository extends JpaRepository<CardTransaction, Long> {

    @Query("""
            select transaction.transactionId as transactionId,
                   transaction.user.userId as userId,
                   transaction.merchantName as merchantName,
                   transaction.approvedAmount as approvedAmount,
                   transaction.approvedCurrency as approvedCurrency,
                   transaction.balanceAfter as balanceAfter,
                   transaction.transactionType as transactionType,
                   transaction.transactionTime as transactionTime
            from CardTransaction transaction
            where transaction.user.userId = :userId
              and (:cardType is null or transaction.transactionType = :cardType)
              and (:startDate is null or transaction.transactionTime >= :startDate)
              and (:endDate is null or transaction.transactionTime <= :endDate)
              and (
                    :cursor is null
                    or transaction.transactionTime < :cursor
                    or (
                        :includeCursorTime = true
                        and transaction.transactionTime = :cursor
                        and (:cursorTransactionId is null or transaction.transactionId < :cursorTransactionId)
                    )
              )
            order by transaction.transactionTime desc, transaction.transactionId desc
            """)
    List<CardTransactionRow> findRecentForUser(
            @Param("userId") Long userId,
            @Param("cardType") CardTransactionType cardType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("cursor") LocalDateTime cursor,
            @Param("includeCursorTime") boolean includeCursorTime,
            @Param("cursorTransactionId") Long cursorTransactionId,
            Pageable pageable
    );
}
