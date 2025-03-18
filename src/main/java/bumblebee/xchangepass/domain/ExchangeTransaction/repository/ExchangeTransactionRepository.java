package bumblebee.xchangepass.domain.ExchangeTransaction.repository;

import bumblebee.xchangepass.domain.ExchangeTransaction.entitiy.ExchangeTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeTransactionRepository extends JpaRepository<ExchangeTransaction, Long> {

}
