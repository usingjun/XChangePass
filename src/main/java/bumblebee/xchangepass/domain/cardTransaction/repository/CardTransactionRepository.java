package bumblebee.xchangepass.domain.cardTransaction.repository;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardTransactionRepository extends JpaRepository<CardTransaction, Long>, CardTransactionRepositoryCustom {

}
