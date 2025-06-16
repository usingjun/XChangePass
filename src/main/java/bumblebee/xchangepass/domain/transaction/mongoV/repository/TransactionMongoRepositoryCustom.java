package bumblebee.xchangepass.domain.transaction.mongoV.repository;

import bumblebee.xchangepass.domain.transaction.mongoV.dto.response.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionSearchCondition;

import java.util.List;

public interface TransactionMongoRepositoryCustom {
    List<TransactionResponse> search(Long userId, TransactionSearchCondition cond, int size);
}
