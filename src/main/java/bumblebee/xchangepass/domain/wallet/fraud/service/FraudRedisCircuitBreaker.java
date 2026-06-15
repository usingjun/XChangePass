package bumblebee.xchangepass.domain.wallet.fraud.service;

import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FraudRedisCircuitBreaker {

    private final FraudPolicyProperties properties;

    private int consecutiveFailures;
    private long openUntilMillis;

    public synchronized void checkAllowed() {
        if (System.currentTimeMillis() < openUntilMillis) {
            throw ErrorCode.FRAUD_DETECTION_UNAVAILABLE.commonException();
        }
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        openUntilMillis = 0;
    }

    public synchronized void recordFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= properties.getCircuitFailureThreshold()) {
            openUntilMillis = System.currentTimeMillis() + properties.getCircuitOpenMillis();
            consecutiveFailures = 0;
        }
    }
}
