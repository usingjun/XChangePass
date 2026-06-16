package bumblebee.xchangepass.domain.wallet.transaction.repository;

import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.transaction.repository.WalletTransactionRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    @Query("""
            select transaction.transactionId as transactionId,
                   transaction.user.userId as userId,
                   transaction.counterpartyUser.userId as counterpartyUserId,
                   transaction.amount as amount,
                   transaction.fromCurrency as fromCurrency,
                   transaction.toCurrency as toCurrency,
                   transaction.transactionType as transactionType,
                   transaction.transactionTime as transactionTime
            from WalletTransaction transaction
            where transaction.user.userId = :userId
              and (:walletType is null or transaction.transactionType = :walletType)
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
    List<WalletTransactionRow> findRecentSentByUser(
            @Param("userId") Long userId,
            @Param("walletType") WalletTransactionType walletType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("cursor") LocalDateTime cursor,
            @Param("includeCursorTime") boolean includeCursorTime,
            @Param("cursorTransactionId") Long cursorTransactionId,
            Pageable pageable
    );

    @Query("""
            select transaction.transactionId as transactionId,
                   transaction.user.userId as userId,
                   transaction.counterpartyUser.userId as counterpartyUserId,
                   transaction.amount as amount,
                   transaction.fromCurrency as fromCurrency,
                   transaction.toCurrency as toCurrency,
                   transaction.transactionType as transactionType,
                   transaction.transactionTime as transactionTime
            from WalletTransaction transaction
            where transaction.counterpartyUser.userId = :userId
              and (:walletType is null or transaction.transactionType = :walletType)
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
    List<WalletTransactionRow> findRecentReceivedByUser(
            @Param("userId") Long userId,
            @Param("walletType") WalletTransactionType walletType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("cursor") LocalDateTime cursor,
            @Param("includeCursorTime") boolean includeCursorTime,
            @Param("cursorTransactionId") Long cursorTransactionId,
            Pageable pageable
    );
}
