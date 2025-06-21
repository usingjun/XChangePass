package bumblebee.xchangepass.domain.transaction.mongoV.service;

import bumblebee.xchangepass.domain.transaction.mongoV.dto.response.TransactionResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;


@Service
public class RedisTransactionQueueService {

    private final RedisTemplate<String, TransactionResponse> responseRedisTemplate;
    private static final String REDIS_KEY_NUM = "transaction:insert";


    public RedisTransactionQueueService(
            @Qualifier("transactionRedisTemplate")
            RedisTemplate<String, TransactionResponse> responseRedisTemplate) {
        this.responseRedisTemplate = responseRedisTemplate;
    }


    public void enqueue(TransactionResponse dto) {
        responseRedisTemplate.opsForList().rightPush(REDIS_KEY_NUM, dto);
    }

}
