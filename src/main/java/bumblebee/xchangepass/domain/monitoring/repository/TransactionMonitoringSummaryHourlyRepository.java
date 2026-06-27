package bumblebee.xchangepass.domain.monitoring.repository;

import bumblebee.xchangepass.domain.monitoring.entity.TransactionMonitoringSummaryHourly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionMonitoringSummaryHourlyRepository
        extends JpaRepository<TransactionMonitoringSummaryHourly, Long> {

    @Modifying
    @Query("""
            delete from TransactionMonitoringSummaryHourly summary
            where summary.summaryHour >= :fromHour
              and summary.summaryHour < :toHour
            """)
    int deleteBySummaryHourRange(@Param("fromHour") LocalDateTime fromHour,
                                 @Param("toHour") LocalDateTime toHour);

    List<TransactionMonitoringSummaryHourly> findBySummaryHourGreaterThanEqualAndSummaryHourLessThanOrderBySummaryHourAsc(
            LocalDateTime fromHour, LocalDateTime toHour
    );
}
