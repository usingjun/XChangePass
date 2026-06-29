package bumblebee.xchangepass.domain.exchangeRate.repository;

import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncHistory;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExchangeRateSyncHistoryRepository
        extends JpaRepository<ExchangeRateSyncHistory, Long> {

    List<ExchangeRateSyncHistory> findByStatusOrderByStartedAtDesc(ExchangeRateSyncStatus status);
}
