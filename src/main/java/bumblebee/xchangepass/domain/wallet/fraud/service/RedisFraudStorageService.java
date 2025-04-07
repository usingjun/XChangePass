package bumblebee.xchangepass.domain.wallet.fraud.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RedisFraudStorageService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final Duration TTL = Duration.ofMinutes(10);

    public void store(Long userId, BigDecimal amount) {
        String key = "fraud:" + userId;
        String value = new FraudRecord(amount, LocalDateTime.now()).serialize();
        redisTemplate.opsForList().rightPush(key, value);
        redisTemplate.expire(key, TTL);
    }

    public List<FraudRecord> getRecentTransactions(Long userId){
        String key = "fraud:" + userId;
        List<String> raw = redisTemplate.opsForList().range(key, 0, -1);
        return raw.stream()
                .map(FraudRecord::deserialize)
                .toList();
    }
}
