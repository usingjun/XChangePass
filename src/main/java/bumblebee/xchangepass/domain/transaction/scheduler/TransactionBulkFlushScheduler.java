package bumblebee.xchangepass.domain.transaction.scheduler;

import bumblebee.xchangepass.domain.transaction.consumer.SlackNotifier;
import bumblebee.xchangepass.domain.transaction.dto.response.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static bumblebee.xchangepass.global.common.Constants.TRANSACTION_MAIN_QUEUE;

@Slf4j
@Component
public class TransactionBulkFlushScheduler {

    private static final String REDIS_KEY = "transactions:insert:";
    private final RedisTemplate<String, TransactionResponse> redisTemplate;
    private final TransactionService transactionService;
    private final RabbitTemplate rabbitTemplate;
    private final SlackNotifier slackNotifier;


    public TransactionBulkFlushScheduler(
            @Qualifier("transactionRedisTemplate") RedisTemplate<String, TransactionResponse> responseRedisTemplate,
            TransactionService transactionService,
            RabbitTemplate rabbitTemplate,
            SlackNotifier slackNotifier
    ) {
        this.redisTemplate = responseRedisTemplate;
        this.transactionService = transactionService;
        this.rabbitTemplate = rabbitTemplate;
        this.slackNotifier= slackNotifier;
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void flushTransactionToDB() {
        List<TransactionResponse> buffer = new ArrayList<>();

        // 🔍 전체 유저의 insert 키를 조회
        var keys = redisTemplate.keys(REDIS_KEY + "*");
        if (keys == null || keys.isEmpty()) {
            log.info("📭 처리할 Redis 키 없음");
            return;
        }

        for (String key : keys) {
            while (true) {
                TransactionResponse response = redisTemplate.opsForList().leftPop(key);
                if (response == null) break;
                buffer.add(response);
            }
        }

        if (!buffer.isEmpty()) {
            try {
                log.info("✅ MongoDB에 {}건 저장 시도", buffer.size());
                transactionService.bulkSave(buffer);
            } catch (Exception e) {
                log.warn("❌ MongoDB 저장 실패. 재시도 큐로 전송합니다: {}", e.getMessage());

                for (TransactionResponse tx : buffer) {
                    try {
                        rabbitTemplate.convertAndSend(TRANSACTION_MAIN_QUEUE, tx);
                    } catch (Exception ex) {
                        String errorMessage = String.format(
                                "🐇 RabbitMQ 전송 실패 - userId: %s, error: %s",
                                tx.getUserId(),
                                ex.getMessage()
                        );

                        slackNotifier.failToSaveTransaction(errorMessage);

                        // 복구 로직
                        try {
                            redisTemplate.opsForList().rightPush(REDIS_KEY, tx);
                        } catch (Exception redisEx) {
                            String redisError = String.format("⚠️ Redis 재삽입 실패 - 유실 위험! userId: %s, error: %s", tx.getUserId(), redisEx.getMessage());
                            slackNotifier.failToSaveTransaction(redisError);
                        }
                    }
                }
            }
        }
    }
}
