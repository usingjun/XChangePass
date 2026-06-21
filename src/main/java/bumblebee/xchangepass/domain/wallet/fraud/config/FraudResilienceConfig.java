package bumblebee.xchangepass.domain.wallet.fraud.config;

import bumblebee.xchangepass.domain.wallet.fraud.service.FraudRedisFailureClassifier;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FraudResilienceConfig {

    public static final String INSTANCE_NAME = "fraudRedis";

    @Bean
    CircuitBreakerConfigCustomizer fraudCircuitBreakerConfigCustomizer(
            FraudRedisFailureClassifier classifier
    ) {
        return CircuitBreakerConfigCustomizer.of(
                INSTANCE_NAME,
                builder -> builder
                        .recordException(classifier::isConnectionOrTimeout)
                        .ignoreException(exception -> !classifier.isConnectionOrTimeout(exception))
        );
    }

    @Bean
    RetryConfigCustomizer fraudRetryConfigCustomizer(FraudRedisFailureClassifier classifier) {
        return RetryConfigCustomizer.of(
                INSTANCE_NAME,
                builder -> builder.retryOnException(
                        exception -> classifier.isConnectionOrTimeout((Throwable) exception)
                )
        );
    }
}
