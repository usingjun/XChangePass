package bumblebee.xchangepass.domain.transaction.statistics.repository;

import bumblebee.xchangepass.domain.transaction.statistics.dto.TransactionStatisticsRow;

import java.time.YearMonth;
import java.util.List;

public interface TransactionStatisticsQueryRepository {

    List<TransactionStatisticsRow> findMonthlyStatistics(Long userId, YearMonth fromMonth, YearMonth toMonth);
}
