package bumblebee.xchangepass.domain.wallet.fraud.service;

import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FraudRuleEvaluator {

    private static final DefaultRedisScript<String> FRAUD_CHECK_SCRIPT = createScript();

    private final RedisTemplate<String, String> redisTemplate;
    private final FraudPolicyProperties properties;
    private final FraudRedisCircuitBreaker circuitBreaker;

    public FraudEvaluationResult evaluate(String redisKey, BigDecimal amount) {
        circuitBreaker.checkAllowed();
        Long nowEpoch = System.currentTimeMillis() / 1000;
        Boolean isNight = isNightTime();
        String recordId = UUID.randomUUID().toString();

        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                String result = redisTemplate.execute(
                        FRAUD_CHECK_SCRIPT,
                        List.of(redisKey, redisKey + ":results"),
                        amount.toString(),
                        String.valueOf(nowEpoch),
                        properties.getTotalAmountLimit().toString(),
                        String.valueOf(properties.getFrequencyLimit()),
                        isNight ? "1" : "0",
                        recordId,
                        String.valueOf(properties.getAccumulationWindowSeconds()),
                        String.valueOf(properties.getFrequencyWindowSeconds()),
                        String.valueOf(properties.getHistoryTtlSeconds())
                );
                if (result == null) {
                    throw new DataRetrievalFailureException("Redis fraud script returned no result");
                }
                circuitBreaker.recordSuccess();
                Set<FraudReason> reasons = FraudReason.fromCodes(result);
                if (reasons.contains(FraudReason.CLEAR)) {
                    return FraudEvaluationResult.clear();
                }
                int riskScore = reasons.stream().mapToInt(properties::riskScore).sum();
                return FraudEvaluationResult.suspicious(reasons, riskScore);
            } catch (DataAccessException e) {
                circuitBreaker.recordFailure();
                if (attempt == properties.getMaxAttempts()) {
                    throw ErrorCode.FRAUD_DETECTION_UNAVAILABLE.commonException();
                }
                backoff();
            }
        }

        throw ErrorCode.FRAUD_DETECTION_UNAVAILABLE.commonException();
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

    private void backoff() {
        try {
            Thread.sleep(properties.getRetryBackoffMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ErrorCode.FRAUD_DETECTION_UNAVAILABLE.commonException();
        }
    }

    private static DefaultRedisScript<String> createScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/fraud_check.lua")));
        script.setResultType(String.class);
        return script;
    }
}
