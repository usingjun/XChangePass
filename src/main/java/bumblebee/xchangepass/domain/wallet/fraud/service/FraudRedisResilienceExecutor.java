package bumblebee.xchangepass.domain.wallet.fraud.service;

import bumblebee.xchangepass.domain.wallet.fraud.config.FraudResilienceConfig;
import bumblebee.xchangepass.global.error.ErrorCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class FraudRedisResilienceExecutor {

    private final FraudRedisLuaExecutor luaExecutor;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public FraudRedisResilienceExecutor(
            FraudRedisLuaExecutor luaExecutor,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry
    ) {
        this.luaExecutor = luaExecutor;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(FraudResilienceConfig.INSTANCE_NAME);
        this.retry = retryRegistry.retry(FraudResilienceConfig.INSTANCE_NAME);
    }

    public String execute(FraudRedisCommand command) {
        Supplier<String> redisCall = () -> luaExecutor.execute(command);
        Supplier<String> retried = Retry.decorateSupplier(retry, redisCall);
        Supplier<String> guarded = CircuitBreaker.decorateSupplier(circuitBreaker, retried);

        try {
            return guarded.get();
        } catch (CallNotPermittedException exception) {
            throw ErrorCode.FRAUD_DETECTION_UNAVAILABLE.commonException();
        } catch (RuntimeException exception) {
            throw ErrorCode.FRAUD_DETECTION_UNAVAILABLE.commonException();
        }
    }
}
