package bumblebee.xchangepass.domain.transaction.mongoV.service;

import bumblebee.xchangepass.domain.transaction.mongoV.dto.response.TransactionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TransactionBulkFlushScheduler {

    private static final String REDIS_KEY = "transaction:insert:";
    private final TransactionMongoService transactionMongoService;
    private final RedisTemplate<String, TransactionResponse> responseRedisTemplate;

    public TransactionBulkFlushScheduler(
            @Qualifier("transactionRedisTemplate") RedisTemplate<String, TransactionResponse> responseRedisTemplate,
            TransactionMongoService transactionMongoService
    ) {
        this.responseRedisTemplate = responseRedisTemplate;
        this.transactionMongoService = transactionMongoService;
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void flushTransactionToDB() {
        List<TransactionResponse> buffer = new ArrayList<>();

        while (true) {
            TransactionResponse response = responseRedisTemplate.opsForList().leftPop(REDIS_KEY);
            if (response == null) {
                break;
            }
            buffer.add(response);
        }

        if (!buffer.isEmpty()) {
            log.info("MongoDB에 {}건의 트랜잭션을 bulk 저장합니다", buffer.size());
            transactionMongoService.bulkSave(buffer);
        }
    }
}
