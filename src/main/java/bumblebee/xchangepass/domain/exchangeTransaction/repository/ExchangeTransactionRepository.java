package bumblebee.xchangepass.domain.exchangeTransaction.repository;

import bumblebee.xchangepass.domain.exchangeTransaction.entitiy.ExchangeTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeTransactionRepository extends JpaRepository<ExchangeTransaction, Long> {

}
