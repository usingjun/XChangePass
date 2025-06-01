package bumblebee.xchangepass.domain.wallet.transaction.repository.search;

import bumblebee.xchangepass.domain.wallet.transaction.dto.request.WalletTransactionSearchCondition;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WalletTransactionRepositoryCustom {
    Page<WalletTransaction> search(Long userId, WalletTransactionSearchCondition condition, Pageable pageable);
}
