package bumblebee.xchangepass.domain.transaction.rdbmsV.repository;

import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionSearchCondition;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionType;
import bumblebee.xchangepass.global.util.TransactionResponseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TransactionRepositoryImpl implements TransactionRepository {

    @PersistenceContext
    private final EntityManager em;

    private final ObjectMapper objectMapper;


    @Override
    public List<TransactionResponse> getUnifiedTransaction(Long userId, TransactionSearchCondition cond, Pageable pageable) {
        LocalDateTime cursorTime = cond.cursor() != null ? cond.cursor() : LocalDateTime.now();

        StringBuilder sql = new StringBuilder("SELECT * FROM (");

        boolean needUnion = false;

        // CARD
        if (cond.transactionType() == null || cond.transactionType() == TransactionType.CARD) {
            if (needUnion) sql.append(" UNION ALL ");
            sql.append("""
                        SELECT
                          ct.user_id,
                          ct.transaction_time,
                          'CARD' AS transaction_type,
                          jsonb_build_object(
                            'receiver', ct.merchant_name,
                            'amount', ct.approved_amount
                          ) AS data
                        FROM card_transaction ct
                        WHERE ct.user_id = :userId
                    """);

            if (cond.cardTransactionType() != null) {
                sql.append(" AND ct.transaction_type = :cardTransactionType ");
            }
            if (cond.startDate() != null) {
                sql.append(" AND ct.transaction_time >= :startDate ");
            }
            if (cond.endDate() != null) {
                sql.append(" AND ct.transaction_time <= :endDate ");
            }
            sql.append(" AND ct.transaction_time < :cursorTime ");
            needUnion = true;
        }

        // WALLET
        if (cond.transactionType() == null || cond.transactionType() == TransactionType.WALLET) {
            if (needUnion) sql.append(" UNION ALL ");
            sql.append("""
                        SELECT
                          wt.sender AS user_id,
                          wt.updated_at AS transaction_time,
                          'WALLET' AS transaction_type,
                          jsonb_build_object(
                            'receiver', ru.user_nickname,
                            'amount', wt.amount
                          ) AS data
                        FROM wallet_transaction wt
                        JOIN users ru ON ru.user_id = wt.receiver
                        WHERE wt.sender = :userId
                    """);

            if (cond.walletTransactionType() != null) {
                sql.append(" AND wt.transaction_type = :walletTransactionType ");
            }
            if (cond.startDate() != null) {
                sql.append(" AND wt.updated_at >= :startDate ");
            }
            if (cond.endDate() != null) {
                sql.append(" AND wt.updated_at <= :endDate ");
            }
            sql.append(" AND wt.updated_at < :cursorTime ");
            needUnion = true;
        }

        // EXCHANGE
        if (cond.transactionType() == null || cond.transactionType() == TransactionType.EXCHANGE) {
            if (needUnion) sql.append(" UNION ALL ");
            sql.append("""
                        SELECT
                          et.user_id,
                          et.exchange_date AS transaction_time,
                          'EXCHANGE' AS transaction_type,
                          jsonb_build_object(
                            'amount', et.received_amount
                          ) AS data
                        FROM exchange_transaction et
                        WHERE et.user_id = :userId
                    """);

            if (cond.startDate() != null) {
                sql.append(" AND et.exchange_date >= :startDate ");
            }
            if (cond.endDate() != null) {
                sql.append(" AND et.exchange_date <= :endDate ");
            }
            sql.append(" AND et.exchange_date < :cursorTime ");
        }

        // 마무리: 정렬 및 페이지네이션
        sql.append("""
                    ) unified
                    ORDER BY transaction_time DESC
                    LIMIT :size
                """);

        Query query = em.createNativeQuery(sql.toString());
        query.setParameter("userId", userId);
        query.setParameter("cursorTime", cursorTime);
        query.setParameter("size", pageable.getPageSize());

        // Optional parameters
        if (cond.startDate() != null) query.setParameter("startDate", cond.startDate());
        if (cond.endDate() != null) query.setParameter("endDate", cond.endDate());
        if (cond.cardTransactionType() != null)
            query.setParameter("cardTransactionType", cond.cardTransactionType().name());
        if (cond.walletTransactionType() != null)
            query.setParameter("walletTransactionType", cond.walletTransactionType().name());

        @SuppressWarnings("unchecked")
        List<Object[]> resultList = query.getResultList();

        TransactionResponseMapper mapper = new TransactionResponseMapper(objectMapper);

        return resultList.stream().map(mapper::map).toList();
    }
}
