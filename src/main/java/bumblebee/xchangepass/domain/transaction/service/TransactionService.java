package bumblebee.xchangepass.domain.transaction.service;

import bumblebee.xchangepass.domain.transaction.dto.cond.TransactionSearchCondition;
import bumblebee.xchangepass.domain.transaction.dto.response.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.entity.TransactionDocument;
import bumblebee.xchangepass.domain.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.BulkOperations;
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

        // 2. [핵심 변경] BulkOperations 생성 (BulkMode.UNORDERED 설정)
        // UNORDERED: 순서 상관없이 병렬로 넣음. 중간에 실패해도 나머지는 계속 넣음.
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, TransactionDocument.class);

        // 3. 데이터 추가 및 실행
        bulkOps.insert(documents);
        bulkOps.execute();
    }

    public List<TransactionResponse> getTransactionByMongo(Long userId, TransactionSearchCondition cond, int size) {
        return transactionRepository.search(userId, cond, size);
    }

}