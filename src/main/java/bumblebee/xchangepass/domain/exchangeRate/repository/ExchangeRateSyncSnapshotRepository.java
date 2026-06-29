package bumblebee.xchangepass.domain.exchangeRate.repository;

import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExchangeRateSyncSnapshotRepository
        extends JpaRepository<ExchangeRateSyncSnapshot, Long> {

    List<ExchangeRateSyncSnapshot> findBySyncHistoryId(Long syncHistoryId);
}
