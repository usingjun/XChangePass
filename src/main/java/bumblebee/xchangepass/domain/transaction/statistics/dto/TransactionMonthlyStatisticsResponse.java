package bumblebee.xchangepass.domain.transaction.statistics.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

public record TransactionMonthlyStatisticsResponse(
        Long userId,
        YearMonth bucketMonth,
        TransactionStatisticsSourceType sourceType,
        String transactionType,
        String currency,
        TransactionStatisticsDirection direction,
        BigDecimal amountSum,
        long transactionCount,
        LocalDateTime dataAsOf
) {
}
