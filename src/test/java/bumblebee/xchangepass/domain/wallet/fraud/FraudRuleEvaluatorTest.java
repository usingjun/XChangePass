package bumblebee.xchangepass.domain.wallet.fraud;

import bumblebee.xchangepass.config.TestUserInitializer;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudEvaluationResult;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudReason;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudRuleEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Import(TestUserInitializer.class)
@SpringBootTest
class FraudRuleEvaluatorTest {

    @Autowired
    private FraudRuleEvaluator fraudRuleEvaluator;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("xcp_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redisContainer::getHost);
        registry.add("spring.redis.port", () -> redisContainer.getMappedPort(6379));
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
    }

    @BeforeEach
    void clearRedis() {
        redisTemplate.delete(redisTemplate.keys("fraud:test:*"));
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
}
