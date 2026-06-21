package bumblebee.xchangepass.domain.wallet.fraud.service;

import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@Component
public class FraudRedisFailureClassifier {

    public boolean isConnectionOrTimeout(Throwable throwable) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = throwable;
        while (current != null && visited.add(current)) {
            if (current instanceof RedisConnectionFailureException
                    || current instanceof QueryTimeoutException
                    || current instanceof RedisCommandTimeoutException
                    || current instanceof RedisConnectionException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
