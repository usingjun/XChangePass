package bumblebee.xchangepass.domain.transaction.repository;

import bumblebee.xchangepass.domain.transaction.entity.TransactionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TransactionRepository extends MongoRepository<TransactionDocument, String> , TransactionRepositoryCustom {
    TransactionDocument findByTransactionId(String transactionId);
}
