package bumblebee.xchangepass.domain.wallet.fraud.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FraudRuleEvaluator {

    private final FraudPolicyProperties properties;
    private final FraudRedisResilienceExecutor resilienceExecutor;

    public FraudEvaluationResult evaluate(String redisKey, BigDecimal amount) {
        FraudRedisCommand command = new FraudRedisCommand(
                redisKey,
                amount,
                System.currentTimeMillis() / 1000,
                isNightTime(),
                UUID.randomUUID().toString()
        );
        String result = resilienceExecutor.execute(command);

        Set<FraudReason> reasons = FraudReason.fromCodes(result);
        if (reasons.contains(FraudReason.CLEAR)) {
            return FraudEvaluationResult.clear();
        }
        int riskScore = reasons.stream().mapToInt(properties::riskScore).sum();
        return FraudEvaluationResult.suspicious(reasons, riskScore);
    }

    private Boolean isNightTime() {
        LocalTime now = LocalTime.now();
        LocalTime start = properties.getNightStart();
        LocalTime end = properties.getNightEnd();
        if (start.isBefore(end)) {
            return now.isAfter(start) && now.isBefore(end);
        }
        return now.isAfter(start) || now.isBefore(end);
    }

}
