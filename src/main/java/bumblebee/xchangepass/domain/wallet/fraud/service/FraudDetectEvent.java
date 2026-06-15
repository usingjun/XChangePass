package bumblebee.xchangepass.domain.wallet.fraud.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FraudDetectEvent(
        Long userId,
        BigDecimal amount,
        LocalDateTime timestamp,
        String detail,
        FraudTransactionType type
) {}
