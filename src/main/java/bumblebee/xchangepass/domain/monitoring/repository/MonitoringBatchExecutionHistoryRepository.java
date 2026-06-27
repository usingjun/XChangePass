package bumblebee.xchangepass.domain.monitoring.repository;

import bumblebee.xchangepass.domain.monitoring.entity.MonitoringBatchExecutionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoringBatchExecutionHistoryRepository
        extends JpaRepository<MonitoringBatchExecutionHistory, Long> {
}
