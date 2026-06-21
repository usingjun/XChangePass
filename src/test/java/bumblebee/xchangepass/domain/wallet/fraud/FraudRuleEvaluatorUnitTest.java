package bumblebee.xchangepass.domain.wallet.fraud;

import bumblebee.xchangepass.domain.wallet.fraud.config.FraudResilienceConfig;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudAlertService;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudCircuitBreakerEventListener;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectEvent;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectionService;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudPolicyProperties;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudRedisCommand;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudRedisFailureClassifier;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudRedisLuaExecutor;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudRedisResilienceExecutor;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudRuleEvaluator;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudTransactionType;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.lettuce.core.RedisCommandTimeoutException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FraudRuleEvaluatorUnitTest {

    @Test
    void temporaryRedisFailureRetriesThenRecordsOneCircuitSuccess() {
        TestContext context = context(defaultCircuitConfig(), defaultRetryConfig());
        when(context.luaExecutor().execute(any()))
                .thenThrow(new RedisConnectionFailureException("temporary"))
                .thenReturn("0");

        String result = context.executor().execute(command());

        assertThat(result).isEqualTo("0");
        verify(context.luaExecutor(), times(2)).execute(any());
        assertThat(context.circuitBreaker().getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(context.circuitBreaker().getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void classifierFindsConnectionAndTimeoutInCauseChainOnly() {
        FraudRedisFailureClassifier classifier = new FraudRedisFailureClassifier();

        assertThat(classifier.isConnectionOrTimeout(
                new RuntimeException(new RedisConnectionFailureException("connection"))
        )).isTrue();
        assertThat(classifier.isConnectionOrTimeout(
                new RuntimeException(new RedisCommandTimeoutException("timeout"))
        )).isTrue();
        assertThat(classifier.isConnectionOrTimeout(
                new DataRetrievalFailureException("invalid result")
        )).isFalse();
    }

    @Test
    void exhaustedRetryRecordsOneCircuitFailureAndFailsClosed() {
        TestContext context = context(defaultCircuitConfig(), defaultRetryConfig());
        when(context.luaExecutor().execute(any()))
                .thenThrow(new RedisConnectionFailureException("unavailable"));

        assertUnavailable(() -> context.executor().execute(command()));

        verify(context.luaExecutor(), times(2)).execute(any());
        assertThat(context.circuitBreaker().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void failureRateOpensCircuitAndOpenStateSkipsRedis() {
        CircuitBreakerConfig config = circuitConfig(2, 2, 50, 2, Duration.ofSeconds(30), false);
        TestContext context = context(config, defaultRetryConfig());
        when(context.luaExecutor().execute(any()))
                .thenThrow(new RedisConnectionFailureException("unavailable"));

        assertUnavailable(() -> context.executor().execute(command()));
        assertUnavailable(() -> context.executor().execute(command()));
        assertThat(context.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertUnavailable(() -> context.executor().execute(command()));
        verify(context.luaExecutor(), times(4)).execute(any());
        assertThat(context.circuitBreaker().getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);
    }

    @Test
    void openStateAutomaticallyTransitionsToHalfOpenAfterWaitDuration() throws Exception {
        CircuitBreakerConfig config = circuitConfig(2, 2, 50, 2, Duration.ofMillis(50), true);
        TestContext context = context(config, defaultRetryConfig());
        context.circuitBreaker().transitionToOpenState();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (context.circuitBreaker().getState() != CircuitBreaker.State.HALF_OPEN
                && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }

        assertThat(context.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    void halfOpenAllowsOnlyConfiguredConcurrentProbeCalls() throws Exception {
        CircuitBreakerConfig config = circuitConfig(2, 2, 50, 2, Duration.ofSeconds(30), false);
        TestContext context = context(config, defaultRetryConfig());
        context.circuitBreaker().transitionToOpenState();
        context.circuitBreaker().transitionToHalfOpenState();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch enteredRedis = new CountDownLatch(2);
        CountDownLatch releaseRedis = new CountDownLatch(1);
        when(context.luaExecutor().execute(any())).thenAnswer(invocation -> {
            enteredRedis.countDown();
            releaseRedis.await(2, TimeUnit.SECONDS);
            return "0";
        });

        var pool = Executors.newFixedThreadPool(3);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return context.executor().execute(command());
                } catch (CommonException exception) {
                    return exception.getErrorCode();
                }
            }));
        }

        start.countDown();
        assertThat(enteredRedis.await(2, TimeUnit.SECONDS)).isTrue();
        releaseRedis.countDown();

        List<Object> results = new ArrayList<>();
        for (Future<Object> future : futures) {
            results.add(future.get(2, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(results).containsExactlyInAnyOrder("0", "0", ErrorCode.FRAUD_DETECTION_UNAVAILABLE);
        verify(context.luaExecutor(), times(2)).execute(any());
    }

    @Test
    void successfulHalfOpenProbesCloseCircuit() {
        CircuitBreakerConfig config = circuitConfig(2, 2, 50, 2, Duration.ofSeconds(30), false);
        TestContext context = context(config, defaultRetryConfig());
        context.circuitBreaker().transitionToOpenState();
        context.circuitBreaker().transitionToHalfOpenState();
        when(context.luaExecutor().execute(any())).thenReturn("0");

        context.executor().execute(command());
        context.executor().execute(command());

        assertThat(context.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void failedHalfOpenProbeReopensCircuit() {
        CircuitBreakerConfig config = circuitConfig(1, 1, 50, 1, Duration.ofSeconds(30), false);
        TestContext context = context(config, defaultRetryConfig());
        context.circuitBreaker().transitionToOpenState();
        context.circuitBreaker().transitionToHalfOpenState();
        when(context.luaExecutor().execute(any()))
                .thenThrow(new RedisConnectionFailureException("still unavailable"));

        assertUnavailable(() -> context.executor().execute(command()));

        assertThat(context.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void suspiciousBusinessResultDoesNotIncreaseCircuitFailureRate() {
        TestContext context = context(defaultCircuitConfig(), defaultRetryConfig());
        when(context.luaExecutor().execute(any())).thenReturn("1");
        FraudPolicyProperties properties = new FraudPolicyProperties();
        FraudRuleEvaluator evaluator = new FraudRuleEvaluator(properties, context.executor());
        FraudAlertService alertService = mock(FraudAlertService.class);
        FraudDetectionService detectionService = new FraudDetectionService(evaluator, alertService);
        FraudDetectEvent event = new FraudDetectEvent(
                1L, BigDecimal.TEN, LocalDateTime.now(), "transfer", FraudTransactionType.WALLET
        );

        assertThatThrownBy(() -> detectionService.verify(event))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SUSPICIOUS_TRANSACTION);
        assertThat(context.circuitBreaker().getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(context.circuitBreaker().getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void unexpectedLuaResultFailureIsNotRetriedOrCountedAsCircuitFailure() {
        TestContext context = context(defaultCircuitConfig(), defaultRetryConfig());
        when(context.luaExecutor().execute(any()))
                .thenThrow(new DataRetrievalFailureException("invalid result"));

        assertUnavailable(() -> context.executor().execute(command()));

        verify(context.luaExecutor(), times(1)).execute(any());
        assertThat(context.circuitBreaker().getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void slowCallAndStateTransitionMetricsAreRecorded() throws Exception {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofMillis(10))
                .slowCallRateThreshold(100)
                .recordException(new FraudRedisFailureClassifier()::isConnectionOrTimeout)
                .build();
        TestContext context = context(config, defaultRetryConfig());
        when(context.luaExecutor().execute(any())).thenAnswer(invocation -> {
            Thread.sleep(20);
            return "0";
        });

        context.executor().execute(command());

        assertThat(context.circuitBreaker().getMetrics().getNumberOfSlowSuccessfulCalls()).isEqualTo(1);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        FraudCircuitBreakerEventListener listener = new FraudCircuitBreakerEventListener(
                context.circuitBreakerRegistry(), meterRegistry
        );
        listener.subscribe();
        context.circuitBreaker().transitionToOpenState();

        assertThat(meterRegistry.get("fraud.circuitbreaker.transitions")
                .tag("from", "CLOSED")
                .tag("to", "OPEN")
                .counter()
                .count()).isEqualTo(1);
    }

    private TestContext context(CircuitBreakerConfig circuitConfig, RetryConfig retryConfig) {
        CircuitBreakerRegistry circuitRegistry = CircuitBreakerRegistry.of(circuitConfig);
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        FraudRedisLuaExecutor luaExecutor = mock(FraudRedisLuaExecutor.class);
        FraudRedisResilienceExecutor executor = new FraudRedisResilienceExecutor(
                luaExecutor, circuitRegistry, retryRegistry
        );
        CircuitBreaker circuitBreaker = circuitRegistry.circuitBreaker(FraudResilienceConfig.INSTANCE_NAME);
        return new TestContext(executor, luaExecutor, circuitRegistry, circuitBreaker);
    }

    private CircuitBreakerConfig defaultCircuitConfig() {
        return circuitConfig(10, 5, 50, 2, Duration.ofSeconds(30), false);
    }

    private CircuitBreakerConfig circuitConfig(
            int windowSize,
            int minimumCalls,
            float failureRate,
            int halfOpenCalls,
            Duration openDuration,
            boolean automaticHalfOpen
    ) {
        FraudRedisFailureClassifier classifier = new FraudRedisFailureClassifier();
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(windowSize)
                .minimumNumberOfCalls(minimumCalls)
                .failureRateThreshold(failureRate)
                .waitDurationInOpenState(openDuration)
                .permittedNumberOfCallsInHalfOpenState(halfOpenCalls)
                .automaticTransitionFromOpenToHalfOpenEnabled(automaticHalfOpen)
                .recordException(classifier::isConnectionOrTimeout)
                .ignoreException(exception -> !classifier.isConnectionOrTimeout(exception))
                .build();
    }

    private RetryConfig defaultRetryConfig() {
        FraudRedisFailureClassifier classifier = new FraudRedisFailureClassifier();
        return RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ZERO)
                .retryOnException(classifier::isConnectionOrTimeout)
                .build();
    }

    private FraudRedisCommand command() {
        return new FraudRedisCommand(
                "fraud:WALLET:user:1",
                BigDecimal.TEN,
                System.currentTimeMillis() / 1000,
                false,
                "record-1"
        );
    }

    private void assertUnavailable(ThrowingCall call) {
        assertThatThrownBy(call::execute)
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FRAUD_DETECTION_UNAVAILABLE);
    }

    private record TestContext(
            FraudRedisResilienceExecutor executor,
            FraudRedisLuaExecutor luaExecutor,
            CircuitBreakerRegistry circuitBreakerRegistry,
            CircuitBreaker circuitBreaker
    ) {
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void execute();
    }
}
