package bumblebee.xchangepass.domain.transaction.mongoV.entity;

import bumblebee.xchangepass.domain.transaction.rdbmsV.entity.TransactionType;
import jakarta.persistence.Id;
import lombok.Getter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Currency;
import java.util.Map;

@Getter
@Document(collection = "transactions")
@CompoundIndex(
        def = "{'userId': 1, 'transactionTime': -1}",
        name = "idx_user_time"
)
public class TransactionDocument {
    @Id
    private String transactionId;

    private Long userId;
    private TransactionType type;
    private Currency beforeCurrency;
    private Currency afterCurrency;
    private LocalDateTime transactionTime;

    private Map<String, Object> metadata;

    protected TransactionDocument() {
    }

    public TransactionDocument(Long userId, TransactionType type, Currency beforeCurrency, Currency afterCurrency, LocalDateTime transactionTime, Map<String, Object> metadata) {
        this.userId = userId;
        this.type = type;
        this.beforeCurrency = beforeCurrency;
        this.afterCurrency = afterCurrency;
        this.transactionTime = transactionTime;
        this.metadata = metadata;
    }
}
