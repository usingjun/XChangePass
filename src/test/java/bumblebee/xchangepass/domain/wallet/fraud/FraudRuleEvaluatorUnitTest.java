package bumblebee.xchangepass.domain.wallet.fraud;

import bumblebee.xchangepass.domain.wallet.fraud.service.FraudRuleEvaluator;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudPolicyProperties;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudRedisCircuitBreaker;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FraudRuleEvaluatorUnitTest {

    @Test
    void redisFailureRetriesThenFailsClosed() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));
        FraudPolicyProperties properties = properties();
        FraudRedisCircuitBreaker circuitBreaker = new FraudRedisCircuitBreaker(properties);
        FraudRuleEvaluator evaluator = new FraudRuleEvaluator(redisTemplate, properties, circuitBreaker);

        assertThatThrownBy(() -> evaluator.evaluate("fraud:WALLET:user:1", java.math.BigDecimal.TEN))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FRAUD_DETECTION_UNAVAILABLE);

        verify(redisTemplate, times(2))
                .execute(any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void openCircuitBlocksRedisCallsUntilRecoveryWindow() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));
        FraudPolicyProperties properties = properties();
        FraudRedisCircuitBreaker circuitBreaker = new FraudRedisCircuitBreaker(properties);
        FraudRuleEvaluator evaluator = new FraudRuleEvaluator(redisTemplate, properties, circuitBreaker);

        assertThatThrownBy(() -> evaluator.evaluate("fraud:WALLET:user:1", java.math.BigDecimal.TEN))
                .isInstanceOf(CommonException.class);
        assertThatThrownBy(() -> evaluator.evaluate("fraud:WALLET:user:1", java.math.BigDecimal.TEN))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FRAUD_DETECTION_UNAVAILABLE);

        verify(redisTemplate, times(2))
                .execute(any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private FraudPolicyProperties properties() {
        FraudPolicyProperties properties = new FraudPolicyProperties();
        properties.setMaxAttempts(2);
        properties.setRetryBackoffMillis(0);
        properties.setCircuitFailureThreshold(2);
        properties.setCircuitOpenMillis(60000);
        return properties;
    }
}
