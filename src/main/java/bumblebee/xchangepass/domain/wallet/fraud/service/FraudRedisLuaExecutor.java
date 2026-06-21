package bumblebee.xchangepass.domain.wallet.fraud.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FraudRedisLuaExecutor {

    private static final DefaultRedisScript<String> FRAUD_CHECK_SCRIPT = createScript();

    private final RedisTemplate<String, String> redisTemplate;
    private final FraudPolicyProperties properties;

    public String execute(FraudRedisCommand command) {
        String result = redisTemplate.execute(
                FRAUD_CHECK_SCRIPT,
                List.of(command.redisKey(), command.redisKey() + ":results"),
                command.amount().toString(),
                String.valueOf(command.nowEpochSeconds()),
                properties.getTotalAmountLimit().toString(),
                String.valueOf(properties.getFrequencyLimit()),
                command.nightTime() ? "1" : "0",
                command.recordId(),
                String.valueOf(properties.getAccumulationWindowSeconds()),
                String.valueOf(properties.getFrequencyWindowSeconds()),
                String.valueOf(properties.getHistoryTtlSeconds())
        );
        if (result == null) {
            throw new DataRetrievalFailureException("Redis fraud script returned no result");
        }
        return result;
    }

    private static DefaultRedisScript<String> createScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/fraud_check.lua")));
        script.setResultType(String.class);
        return script;
    }
}
