package bumblebee.xchangepass.domain.wallet.fraud.service;

import java.math.BigDecimal;

public record FraudRedisCommand(
        String redisKey,
        BigDecimal amount,
        long nowEpochSeconds,
        boolean nightTime,
        String recordId
) {
}
