package bumblebee.xchangepass.domain.transaction.mongoV.service;

import bumblebee.xchangepass.domain.transaction.mongoV.dto.response.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.mongoV.entity.TransactionDocument;
import bumblebee.xchangepass.domain.transaction.mongoV.repository.TransactionMongoRepository;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionSearchCondition;
import bumblebee.xchangepass.domain.transaction.rdbmsV.entity.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TransactionMongoService {

    private final MongoTemplate mongoTemplate;
    private final TransactionMongoRepository transactionRepository;

    //mongoDB
    public void saveTransaction(Long userId, TransactionType type, Currency beforeCurrency, Currency afterCurrency,  Map<String, Object> metadata) {
        TransactionDocument tx = new TransactionDocument(userId, type, beforeCurrency, afterCurrency, LocalDateTime.now(), metadata);
        mongoTemplate.save(tx);
    }

    public void bulkSave(List<TransactionResponse> responseList) {
        List<TransactionDocument> documents = responseList.stream()
                .map(response -> new TransactionDocument(
                        response.userId(),
                        response.transactionType(),
                        response.beforeCurrency(),
                        response.afterCurrency(),
                        response.transactionTime(),
                        response.data().toMap()
                )).toList();

        mongoTemplate.insertAll(documents);
    }

    public List<TransactionResponse> getTransactionByMongo(Long userId, TransactionSearchCondition cond, int size) {
        return transactionRepository.search(userId, cond, size);
    }

}