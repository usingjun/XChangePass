package bumblebee.xchangepass.domain.monitoring.entity;

public enum TransactionStatusEventType {
    PROCESSING_STARTED,
    LEDGER_SAVED,
    FAILED,
    IDEMPOTENT_DUPLICATE_DETECTED,
    FRAUD_CHECK_BLOCKED,
    FRAUD_DETECTION_UNAVAILABLE,
    AUTO_FAILED,
    OPERATIONAL_EXCEPTION_CREATED
}
