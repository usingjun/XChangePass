package bumblebee.xchangepass.domain.transaction.rdbmsV.repository;

import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionSearchCondition;
import bumblebee.xchangepass.domain.wallet.transaction.dto.request.WalletTransactionSearchCondition;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TransactionRepository {
    List<TransactionResponse> getUnifiedTransaction(Long userId, TransactionSearchCondition cond, Pageable pageable);
}
