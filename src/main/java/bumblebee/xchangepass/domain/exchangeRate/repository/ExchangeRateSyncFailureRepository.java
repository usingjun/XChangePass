package bumblebee.xchangepass.domain.exchangeRate.repository;

import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncFailure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExchangeRateSyncFailureRepository
        extends JpaRepository<ExchangeRateSyncFailure, Long> {

    List<ExchangeRateSyncFailure> findBySyncHistoryId(Long syncHistoryId);

    List<ExchangeRateSyncFailure> findByResolvedFalse();
}
