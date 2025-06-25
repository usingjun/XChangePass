package bumblebee.xchangepass.domain.transaction.service;

import bumblebee.xchangepass.domain.transaction.dto.response.TransactionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class RedisTransactionQueueService {

    private final RedisTemplate<String, TransactionResponse> responseRedisTemplate;

    public RedisTransactionQueueService(
            @Qualifier("transactionRedisTemplate")
            RedisTemplate<String, TransactionResponse> responseRedisTemplate) {
        this.responseRedisTemplate = responseRedisTemplate;
    }


    public void enqueue(String redisKey, TransactionResponse dto) {
        responseRedisTemplate.opsForList().rightPush(redisKey, dto);
    }

}
