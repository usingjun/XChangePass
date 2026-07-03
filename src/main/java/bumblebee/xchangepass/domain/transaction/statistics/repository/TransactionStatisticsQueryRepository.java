package bumblebee.xchangepass.domain.transaction.statistics.repository;

import bumblebee.xchangepass.domain.transaction.statistics.dto.TransactionStatisticsRow;

import java.time.YearMonth;
import java.util.List;

public interface TransactionStatisticsQueryRepository {

    List<TransactionStatisticsRow> findMonthlyStatistics(Long userId, YearMonth fromMonth, YearMonth toMonth);

    List<TransactionStatisticsRow> findMonthlyStatisticsFromMaterializedView(
            Long userId, YearMonth fromMonth, YearMonth toMonth
    );

    List<TransactionStatisticsRow> findMonthlyStatisticsFromSummary(Long userId, YearMonth fromMonth, YearMonth toMonth);

    void refreshMaterializedView();

    void refreshMaterializedViewConcurrently();

    void refreshMonthlySummary(YearMonth fromMonth, YearMonth toMonth);
}
