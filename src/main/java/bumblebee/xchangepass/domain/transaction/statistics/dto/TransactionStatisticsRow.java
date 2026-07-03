package bumblebee.xchangepass.domain.transaction.statistics.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

public record TransactionStatisticsRow(
        Long userId,
        YearMonth bucketMonth,
        TransactionStatisticsSourceType sourceType,
        String transactionType,
        String currency,
        TransactionStatisticsDirection direction,
        BigDecimal amountSum,
        long transactionCount
) {

    public TransactionMonthlyStatisticsResponse toResponse(LocalDateTime dataAsOf) {
        return new TransactionMonthlyStatisticsResponse(
                userId,
                bucketMonth,
                sourceType,
                transactionType,
                currency,
                direction,
                amountSum,
                transactionCount,
                dataAsOf
        );
    }
}
