package bumblebee.xchangepass.domain.wallet.fraud.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FraudRuleEvaluator {

    private final RedisTemplate<String, String> redisTemplate;

    private String lastDetectedReason;

    public Boolean isSuspicious(Long userId, BigDecimal amount) {
        this.lastDetectedReason = null;

        Long nowEpoch = System.currentTimeMillis() / 1000;
        Boolean isNight = isNightTime();

        DefaultRedisScript<Boolean> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/fraud_check.lua")));
        script.setResultType(Boolean.class);

        Boolean result = redisTemplate.execute(
                script,
                List.of("fraud:user:" + userId),
                amount.toString(),
                String.valueOf(nowEpoch),
                "500000",         // 누적 한도
                "5",              // 5분 내 최대 거래 수
                "true",           // 기록 저장 여부
                isNight ? "1" : "0"
        );

        if ("1".equals(result)) {
            lastDetectedReason = "누적 금액 초과";
        } else if ("2".equals(result)) {
            lastDetectedReason = "5분 내 거래 횟수 초과";
        } else if ("3".equals(result)) {
            lastDetectedReason = "동일 금액 반복";
        } else if ("4".equals(result)) {
            lastDetectedReason = "심야 시간대 거래";
        }

        return Boolean.TRUE.equals(result);
    }

    public String getLastDetectedReason() {
        return lastDetectedReason != null ? lastDetectedReason : "알 수 없음";
    }

    private Boolean isNightTime() {
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.of(2, 30)) && now.isBefore(LocalTime.of(3, 30));
    }
}
