package bumblebee.xchangepass.domain.exchangeTransaction.repository;

import bumblebee.xchangepass.domain.exchangeTransaction.repository.search.ExchangeTransactionSearchCondition;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionSearchCondition;

import java.util.List;

public interface ExchangeTransactionRepositoryCustom {
    List<TransactionResponse> search(Long userId, TransactionSearchCondition cond, int size);
}
