package bumblebee.xchangepass.domain.exchangeTransaction.repository;

import bumblebee.xchangepass.domain.exchangeTransaction.entitiy.ExchangeTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExchangeTransactionRepository extends JpaRepository<ExchangeTransaction, Long>, ExchangeTransactionRepositoryCustom {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ExchangeTransaction t where t.exchangeTransactionId = :id")
    Optional<ExchangeTransaction> findByIdForUpdate(@Param("id") Long id);

}
