package bumblebee.xchangepass.domain.monitoring.dto;

import java.time.Duration;

public record TransactionStatusHourlySummaryResult(
        Long historyId,
        long processedCount,
        long summaryRowCount,
        Duration elapsed
) {
}
