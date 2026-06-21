package bumblebee.xchangepass.domain.wallet.fraud.service;

import bumblebee.xchangepass.domain.wallet.fraud.config.FraudResilienceConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FraudCircuitBreakerEventListener {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void subscribe() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(FraudResilienceConfig.INSTANCE_NAME);
        circuitBreaker.getEventPublisher().onStateTransition(this::recordTransition);
    }

    private void recordTransition(CircuitBreakerOnStateTransitionEvent event) {
        String from = event.getStateTransition().getFromState().name();
        String to = event.getStateTransition().getToState().name();
        meterRegistry.counter(
                "fraud.circuitbreaker.transitions",
                "from", from,
                "to", to
        ).increment();
        log.warn("Fraud Redis circuit breaker state transition: from={}, to={}", from, to);
    }
}
