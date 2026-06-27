package bumblebee.xchangepass.domain.monitoring;

import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEvent;
import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEventType;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferFailureStage;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class TransactionStatusEventDatasetFactory {

    static final int DEFAULT_EVENT_COUNT = 100_000;
    static final int DEFAULT_CHUNK_SIZE = 5_000;
    static final LocalDateTime DEFAULT_START_AT = LocalDateTime.of(2026, 6, 26, 0, 0);
    static final LocalDateTime DEFAULT_END_AT = LocalDateTime.of(2026, 6, 27, 0, 0);

    private TransactionStatusEventDatasetFactory() {
    }

    static List<TransactionStatusEvent> createChunk(int offset, int size,
                                                    LocalDateTime startAt,
                                                    LocalDateTime endAt,
                                                    int totalCount) {
        List<TransactionStatusEvent> events = new ArrayList<>(size);
        for (int index = offset; index < offset + size; index++) {
            events.add(create(index, startAt, endAt, totalCount));
        }
        return events;
    }

    private static TransactionStatusEvent create(int index, LocalDateTime startAt,
                                                 LocalDateTime endAt, int totalCount) {
        TransactionStatusEventType eventType = eventType(index);
        LocalDateTime occurredAt = occurredAt(index, startAt, endAt, totalCount);
        UUID transactionId = deterministicUuid("tx-" + (index / 5));
        TransactionStatusEvent.TransactionStatusEventBuilder builder =
                TransactionStatusEvent.builder(transactionId, eventType)
                        .userId((long) (index % 10_000) + 1)
                        .walletId((long) (index % 1_000) + 1)
                        .idempotencyKey(deterministicUuid("idempotency-" + (index / 5)))
                        .occurredAt(occurredAt);

        applyStatus(builder, eventType);
        applyFailure(builder, index, eventType);
        return builder.build();
    }

    private static TransactionStatusEventType eventType(int index) {
        int bucket = index % 1_000;
        if (bucket < 200) {
            return TransactionStatusEventType.REQUEST_ACCEPTED;
        }
        if (bucket < 400) {
            return TransactionStatusEventType.VALIDATION_STARTED;
        }
        if (bucket < 550) {
            return TransactionStatusEventType.PROCESSING_STARTED;
        }
        if (bucket < 700) {
            return TransactionStatusEventType.LEDGER_SAVED;
        }
        if (bucket < 900) {
            return TransactionStatusEventType.COMPLETED;
        }
        if (bucket < 960) {
            return TransactionStatusEventType.FAILED;
        }
        if (bucket < 980) {
            return TransactionStatusEventType.FRAUD_DETECTION_UNAVAILABLE;
        }
        if (bucket < 985) {
            return TransactionStatusEventType.FRAUD_CHECK_BLOCKED;
        }
        if (bucket < 990) {
            return TransactionStatusEventType.IDEMPOTENT_DUPLICATE_DETECTED;
        }
        if (bucket < 995) {
            return TransactionStatusEventType.AUTO_FAILED;
        }
        return TransactionStatusEventType.OPERATIONAL_EXCEPTION_CREATED;
    }

    private static LocalDateTime occurredAt(int index, LocalDateTime startAt,
                                            LocalDateTime endAt, int totalCount) {
        long totalNanos = Duration.between(startAt, endAt).toNanos();
        long nanosPerEvent = totalNanos / totalCount;
        long remainingNanos = totalNanos % totalCount;
        long offsetNanos = nanosPerEvent * index + remainingNanos * index / totalCount;
        return startAt.plusNanos(offsetNanos);
    }

    private static void applyStatus(TransactionStatusEvent.TransactionStatusEventBuilder builder,
                                    TransactionStatusEventType eventType) {
        switch (eventType) {
            case REQUEST_ACCEPTED -> builder.status(null, WalletTransferStatus.REQUESTED);
            case VALIDATION_STARTED -> builder.status(WalletTransferStatus.REQUESTED, WalletTransferStatus.VALIDATING);
            case PROCESSING_STARTED -> builder.status(WalletTransferStatus.VALIDATING, WalletTransferStatus.PROCESSING);
            case LEDGER_SAVED -> builder.status(WalletTransferStatus.PROCESSING, WalletTransferStatus.PROCESSING);
            case COMPLETED, IDEMPOTENT_DUPLICATE_DETECTED -> builder.status(
                    WalletTransferStatus.PROCESSING, WalletTransferStatus.COMPLETED
            );
            case FAILED, FRAUD_DETECTION_UNAVAILABLE, AUTO_FAILED, OPERATIONAL_EXCEPTION_CREATED -> builder.status(
                    WalletTransferStatus.PROCESSING, WalletTransferStatus.FAILED
            );
            case FRAUD_CHECK_BLOCKED -> builder.status(WalletTransferStatus.VALIDATING, WalletTransferStatus.FAILED);
        }
    }

    private static void applyFailure(TransactionStatusEvent.TransactionStatusEventBuilder builder,
                                     int index,
                                     TransactionStatusEventType eventType) {
        switch (eventType) {
            case FAILED -> applyBusinessFailure(builder, index);
            case FRAUD_DETECTION_UNAVAILABLE -> builder.failure(
                    WalletTransferFailureStage.FRAUD_VALIDATION,
                    "FRAUD_DETECTION_UNAVAILABLE",
                    true
            );
            case FRAUD_CHECK_BLOCKED -> builder.failure(
                    WalletTransferFailureStage.FRAUD_VALIDATION,
                    "SUSPICIOUS_TRANSACTION",
                    false
            );
            case AUTO_FAILED -> builder.failure(
                    WalletTransferFailureStage.RECOVERY_TIMEOUT,
                    "TRANSACTION_STALE",
                    true
            );
            case OPERATIONAL_EXCEPTION_CREATED -> builder.failure(
                    WalletTransferFailureStage.UNKNOWN,
                    "TRANSACTION_PROCESSING_FAILED",
                    false
            );
            default -> {
            }
        }
    }

    private static void applyBusinessFailure(TransactionStatusEvent.TransactionStatusEventBuilder builder,
                                             int index) {
        int bucket = index % 4;
        if (bucket == 0) {
            builder.failure(WalletTransferFailureStage.FRAUD_VALIDATION,
                    "SUSPICIOUS_TRANSACTION", false);
        } else if (bucket == 1) {
            builder.failure(WalletTransferFailureStage.FRAUD_VALIDATION,
                    "FRAUD_DETECTION_UNAVAILABLE", true);
        } else if (bucket == 2) {
            builder.failure(WalletTransferFailureStage.FUNDS_AND_LEDGER_TRANSACTION,
                    "TRANSACTION_PROCESSING_FAILED", false);
        } else {
            builder.failure(WalletTransferFailureStage.RECOVERY_TIMEOUT,
                    "TRANSACTION_STALE", true);
        }
    }

    private static UUID deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
