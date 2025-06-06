package bumblebee.xchangepass.domain.wallet.transaction.repository.search;

import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionSearchCondition;

import java.util.List;

public interface WalletTransactionRepositoryCustom {
    List<TransactionResponse> search(Long userId, TransactionSearchCondition condition, int size);
}
