package bumblebee.xchangepass.domain.wallet.fraud;

import bumblebee.xchangepass.domain.wallet.fraud.service.FraudRuleEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FraudRuleEvaluatorTest {

    @Autowired
    private FraudRuleEvaluator fraudRuleEvaluator;

    @Test
    void test_Lua_script_detects_no_fraud_on_single_transaction() {
        Long userId = 1001L;
        BigDecimal amount = new BigDecimal("10000");

        boolean suspicious = fraudRuleEvaluator.isSuspicious(userId, amount);
        System.out.println("🚨 감지 결과: " + suspicious);

        assertThat(suspicious).isFalse();
    }

    @Test
    void test_Lua_script_detects_high_frequency_transactions() {
        Long userId = 1002L;
        BigDecimal amount = new BigDecimal("20000");

        // 5건 연속 트랜잭션으로 빈도 룰 유도
        for (int i = 0; i < 5; i++) {
            fraudRuleEvaluator.isSuspicious(userId, amount);
        }

        boolean suspicious = fraudRuleEvaluator.isSuspicious(userId, amount);

        assertThat(suspicious).isTrue();
    }

    @Test
    void test_performance_of_lua_rule() {
        Long userId = 1003L;
        BigDecimal amount = new BigDecimal("5000");

        long start = System.currentTimeMillis();

        for (int i = 0; i < 1000; i++) {
            fraudRuleEvaluator.isSuspicious(userId, amount);
        }

        long end = System.currentTimeMillis();
        long duration = end - start;

        System.out.println("🔥 1000회 실행 시간: " + duration + "ms");
        assertThat(duration).isLessThan(3000); // 3초 내면 성능 OK
    }

    @Test
    void benchmarkLuaPerformance() {
        Long userId = 1L;
        BigDecimal amount = BigDecimal.valueOf(10000);

        // 성능 측정 시작
        long start = System.currentTimeMillis();

        for (int i = 0; i < 1000; i++) {
            fraudRuleEvaluator.isSuspicious(userId, amount);
        }

        long end = System.currentTimeMillis();
        long elapsed = end - start;

        System.out.println("🔥 Lua 이상 거래 탐지 1000회 실행 시간: " + elapsed + "ms");
    }
}

