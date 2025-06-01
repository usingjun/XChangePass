package bumblebee.xchangepass.domain.transaction;

import java.util.List;

public interface TransactionRepository {
    List<TransactionResponse> getUnifiedTransaction(Long userId, int offset, int size);
//    List<TransactionResponse> getTransactionByType(Long userId, )
}
