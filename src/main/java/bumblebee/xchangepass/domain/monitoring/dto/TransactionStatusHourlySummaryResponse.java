package bumblebee.xchangepass.domain.monitoring.dto;

import bumblebee.xchangepass.domain.monitoring.entity.TransactionMonitoringSummaryHourly;

import java.time.LocalDateTime;

public record TransactionStatusHourlySummaryResponse(
        LocalDateTime summaryHour,
        String transactionType,
        String eventType,
        String status,
        String failureStage,
        String errorCode,
        long count,
        long retryableCount,
        long nonRetryableCount
) {

    public static TransactionStatusHourlySummaryResponse from(TransactionMonitoringSummaryHourly summary) {
        return new TransactionStatusHourlySummaryResponse(
                summary.getSummaryHour(),
                summary.getTransactionType(),
                summary.getEventType(),
                summary.getStatus(),
                summary.getFailureStage(),
                summary.getErrorCode(),
                summary.getCount(),
                summary.getRetryableCount(),
                summary.getNonRetryableCount()
        );
    }
}
