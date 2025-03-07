package bumblebee.xchangepass.domain.ExchangeTransaction.repository;

import bumblebee.xchangepass.domain.ExchangeTransaction.entitiy.ExchangeTransaction;
import bumblebee.xchangepass.domain.ExchangeTransaction.entitiy.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeTransactionRepository extends JpaRepository<ExchangeTransaction, Long> {

    ExchangeTransaction findByUserIdAndFromCurrencyAndToCurrencyAndStatus(
            Long userId,
            String fromCurrency,
            String toCurrency,
            TransactionStatus status
    );
}
