package bumblebee.xchangepass.domain.wallet.fraud;

import bumblebee.xchangepass.domain.wallet.fraud.service.FraudEvaluationResult;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudReason;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudRuleEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "local.integration", matches = "true")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/xcp_verify",
        "spring.datasource.username=iyongjun",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6379",
        "spring.task.scheduling.enabled=false"
})
class FraudRuleEvaluatorLocalRedisTest {

    @Autowired
    private FraudRuleEvaluator fraudRuleEvaluator;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private String key;

    @BeforeEach
    void setUp() {
        key = "fraud:local-test:" + UUID.randomUUID();
    }

    @Test
    void luaScriptDetectsFrequencyRepeatedAmountAndRecordsSuspiciousAttempt() {
        FraudEvaluationResult result = FraudEvaluationResult.clear();
        for (int i = 0; i < 6; i++) {
            result = fraudRuleEvaluator.evaluate(key, new BigDecimal("10000"));
        }

        assertThat(result.suspicious()).isTrue();
        assertThat(result.reasons()).contains(
                FraudReason.FREQUENCY_EXCEEDED,
                FraudReason.REPEATED_AMOUNT
        );
        assertThat(redisTemplate.opsForZSet().size(key)).isEqualTo(6);
    }

    @Test
    void luaScriptDetectsAccumulatedAmountLimit() {
        fraudRuleEvaluator.evaluate(key, new BigDecimal("400000"));

        FraudEvaluationResult result = fraudRuleEvaluator.evaluate(key, new BigDecimal("100001"));

        assertThat(result.suspicious()).isTrue();
        assertThat(result.reasons()).contains(FraudReason.TOTAL_AMOUNT_EXCEEDED);
        assertThat(redisTemplate.opsForZSet().size(key)).isEqualTo(2);
    }
}
