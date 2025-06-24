package bumblebee.xchangepass.domain.transaction.service;

import bumblebee.xchangepass.domain.transaction.dto.cond.TransactionSearchCondition;
import bumblebee.xchangepass.domain.transaction.dto.response.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.entity.TransactionDocument;
import bumblebee.xchangepass.domain.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final MongoTemplate mongoTemplate;
    private final TransactionRepository transactionRepository;

    //mongoDB
    public void saveTransaction(TransactionResponse response) {
        TransactionDocument tx = new TransactionDocument(response.getUserId(), response.getBeforeCurrency(), response.getAfterCurrency(), response.getTransactionTime(), response.getData().toMap());
        mongoTemplate.save(tx);
    }

    public void bulkSave(List<TransactionResponse> responseList) {
        List<TransactionDocument> documents = responseList.stream()
                .map(response -> new TransactionDocument(
                        response.getUserId(),
                        response.getBeforeCurrency(),
                        response.getAfterCurrency(),
                        response.getTransactionTime(),
                        response.getData().toMap()
                )).toList();

        mongoTemplate.insertAll(documents);
    }

    public List<TransactionResponse> getTransactionByMongo(Long userId, TransactionSearchCondition cond, int size) {
        return transactionRepository.search(userId, cond, size);
    }

}