package bumblebee.xchangepass.domain.transaction.repository;

import bumblebee.xchangepass.domain.transaction.dto.response.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.dto.cond.TransactionSearchCondition;

import java.util.List;

public interface TransactionRepositoryCustom {
    List<TransactionResponse> search(Long userId, TransactionSearchCondition cond, int size);
}
