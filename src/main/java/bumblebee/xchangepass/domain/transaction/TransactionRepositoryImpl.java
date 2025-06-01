package bumblebee.xchangepass.domain.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TransactionRepositoryImpl implements TransactionRepository {

    @PersistenceContext
    private final EntityManager em;

    private ObjectMapper objectMapper;


    @Override
    public List<TransactionResponse> getUnifiedTransaction(Long userId, int offset, int size) {
        String sql = """
                    SELECT *
                    FROM (
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
                    
                      UNION ALL
                    
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
                
                      UNION ALL
                    
                      SELECT
                        et.user_id,
                        et.exchange_date AS transaction_time,
                        'EXCHANGE' AS transaction_type,
                        jsonb_build_object(
                          'amount', et.received_amount
                        ) AS data
                      FROM exchange_transaction et
                      WHERE et.user_id = :userId
                    ) unified
                    ORDER BY transaction_time DESC
                    LIMIT :size OFFSET :offset
                    
                """;
        Query query = em.createNativeQuery(sql);
        query.setParameter("userId", userId);
        query.setParameter("offset", offset);
        query.setParameter("size", size);
        @SuppressWarnings("unchecked")
        List<Object[]> resultList = query.getResultList();

        return resultList.stream().map(row -> {
            Long userIdRaw = ((Number) row[0]).longValue();
            LocalDateTime time = ((Timestamp) row[1]).toLocalDateTime();
            String type = (String) row[2];
            String json = row[3].toString();

            TransactionResponse.TransactionDataDto data;
            try {
                data = objectMapper.readValue(json, TransactionResponse.TransactionDataDto.class);
            } catch (Exception e) {
                throw new RuntimeException("JSON parsing failed", e);
            }

            return new TransactionResponse(userIdRaw, time, type, data);
        }).toList();
    }
}
