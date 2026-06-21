package bumblebee.xchangepass.domain.wallet.fraud;

import bumblebee.xchangepass.domain.wallet.fraud.service.FraudEvaluationResult;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudReason;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudRuleEvaluator;
import bumblebee.xchangepass.domain.wallet.fraud.config.FraudResilienceConfig;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class FraudRuleEvaluatorTest {

    @Autowired
    private FraudRuleEvaluator fraudRuleEvaluator;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RetryRegistry retryRegistry;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private Environment environment;

    @Container
    static PostgreSQLContainer postgresContainer = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("xcp_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
    }

    @BeforeEach
    void clearRedis() {
        redisTemplate.delete(redisTemplate.keys("fraud:test:*"));
        circuitBreakerRegistry.circuitBreaker(FraudResilienceConfig.INSTANCE_NAME).reset();
    }


    @Test
    void singleTransactionIsClear() {
        String key = "fraud:test:single";
        BigDecimal amount = new BigDecimal("10000");

        FraudEvaluationResult result = fraudRuleEvaluator.evaluate(key, amount);

        assertThat(result.suspicious()).isFalse();
        assertThat(result.reasons()).containsExactly(FraudReason.CLEAR);
    }

    @Test
    void detectsHighFrequencyTransactions() {
        String key = "fraud:test:frequency";

        FraudEvaluationResult result = FraudEvaluationResult.clear();

        for (int i = 0; i < 6; i++) {
            result = fraudRuleEvaluator.evaluate(key, BigDecimal.valueOf(1000 + i));
        }

        assertThat(result.suspicious()).isTrue();
        assertThat(result.reasons()).contains(FraudReason.FREQUENCY_EXCEEDED);
    }

    @Test
    void detectsRepeatedAmount() {
        String key = "fraud:test:repeated";
        BigDecimal amount = new BigDecimal("10000");

        fraudRuleEvaluator.evaluate(key, amount);
        fraudRuleEvaluator.evaluate(key, amount);
        fraudRuleEvaluator.evaluate(key, amount);
        FraudEvaluationResult result = fraudRuleEvaluator.evaluate(key, amount);

        assertThat(result.suspicious()).isTrue();
        assertThat(result.reasons()).contains(FraudReason.REPEATED_AMOUNT);
    }

    @Test
    void detectsAccumulatedAmountLimit() {
        String key = "fraud:test:amount";

        fraudRuleEvaluator.evaluate(key, new BigDecimal("400000"));
        FraudEvaluationResult result = fraudRuleEvaluator.evaluate(key, new BigDecimal("100001"));

        assertThat(result.suspicious()).isTrue();
        assertThat(result.reasons()).contains(FraudReason.TOTAL_AMOUNT_EXCEEDED);
    }

    @Test
    void suspiciousTransactionsAreAlsoRecorded() {
        String key = "fraud:test:record-suspicious";

        fraudRuleEvaluator.evaluate(key, new BigDecimal("600000"));

        assertThat(redisTemplate.opsForZSet().size(key)).isEqualTo(1);
    }

    @Test
    void returnsAllMatchedReasonsAndCombinedRiskScore() {
        String key = "fraud:test:multiple-reasons";
        BigDecimal amount = new BigDecimal("200000");

        fraudRuleEvaluator.evaluate(key, amount);
        fraudRuleEvaluator.evaluate(key, amount);
        fraudRuleEvaluator.evaluate(key, amount);
        FraudEvaluationResult result = fraudRuleEvaluator.evaluate(key, amount);

        assertThat(result.reasons()).contains(
                FraudReason.TOTAL_AMOUNT_EXCEEDED,
                FraudReason.REPEATED_AMOUNT
        );
        assertThat(result.riskScore()).isGreaterThan(0);
    }

    @Test
    void exposesCircuitStateCallSlowBlockedAndTransitionMetrics() {
        fraudRuleEvaluator.evaluate("fraud:test:metrics", BigDecimal.TEN);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(FraudResilienceConfig.INSTANCE_NAME);
        circuitBreaker.transitionToOpenState();

        assertThatThrownBy(() -> fraudRuleEvaluator.evaluate("fraud:test:blocked", BigDecimal.TEN))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FRAUD_DETECTION_UNAVAILABLE);

        assertThat(meterRegistry.getMeters())
                .extracting(meter -> meter.getId().getName())
                .contains(
                        "resilience4j.circuitbreaker.state",
                        "resilience4j.circuitbreaker.calls",
                        "resilience4j.circuitbreaker.slow.calls",
                        "resilience4j.circuitbreaker.not.permitted.calls",
                        "fraud.circuitbreaker.transitions"
                );
        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .contains("prometheus", "circuitbreakers", "circuitbreakerevents");
    }

    @Test
    void bindsApprovedCircuitRetryAndExceptionClassificationConfiguration() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(FraudResilienceConfig.INSTANCE_NAME);
        Retry retry = retryRegistry.retry(FraudResilienceConfig.INSTANCE_NAME);

        assertThat(circuitBreaker.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(10);
        assertThat(circuitBreaker.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50);
        assertThat(circuitBreaker.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(2);
        assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(2);
        assertThat(circuitBreaker.getCircuitBreakerConfig().getRecordExceptionPredicate()
                .test(new RedisConnectionFailureException("unavailable"))).isTrue();
        assertThat(circuitBreaker.getCircuitBreakerConfig().getIgnoreExceptionPredicate()
                .test(new DataRetrievalFailureException("invalid Lua result"))).isTrue();
        assertThat(retry.getRetryConfig().getExceptionPredicate()
                .test(new RedisConnectionFailureException("unavailable"))).isTrue();
        assertThat(retry.getRetryConfig().getExceptionPredicate()
                .test(new DataRetrievalFailureException("invalid Lua result"))).isFalse();
    }
}
