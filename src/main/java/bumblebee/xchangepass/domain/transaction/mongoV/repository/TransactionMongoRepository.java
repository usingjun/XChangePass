package bumblebee.xchangepass.domain.transaction.mongoV.repository;

import bumblebee.xchangepass.domain.transaction.mongoV.entity.TransactionDocument;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionSearchCondition;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TransactionMongoRepository extends MongoRepository<TransactionDocument, Long> , TransactionMongoRepositoryCustom {
    List<TransactionDocument> findByUserIdOrderByTransactionTimeDesc(Long userId, TransactionSearchCondition cond, int size);
}
