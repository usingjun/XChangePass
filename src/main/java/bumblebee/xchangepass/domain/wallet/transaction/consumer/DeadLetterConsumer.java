package bumblebee.xchangepass.domain.wallet.transaction.consumer;

import bumblebee.xchangepass.domain.wallet.transaction.dto.WalletTransactionMessage;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterConsumer {

    private final RabbitTemplate rabbitTemplate;
    private final SlackNotifier slackNotifier;

    @RabbitListener(queues = "wallet-transaction-dlx-queue")
    public void handleDeadLetter(WalletTransactionMessage message,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long tag,
                                 @Header("x-death") List<Map<String, Object>> xDeathHeader,
                                 Channel channel) throws IOException {

        int retryCount = 0;
        if (xDeathHeader != null && !xDeathHeader.isEmpty()) {
            Map<String, Object> death = xDeathHeader.get(0);
            retryCount = ((Long) death.get("count")).intValue();
        }

        if (retryCount < 3) {
            log.warn("♻️ DLQ 재시도 {}회차: {}", retryCount + 1, message);
            rabbitTemplate.convertAndSend("wallet-transaction-retry-queue", message);
        } else {
            log.error("🚨 DLQ 재시도 초과, 슬랙 알림 전송: {}", message);
            slackNotifier.failToSaveTransaction("🚨 DLQ 처리 실패: " + message);
        }

        // 수동 ack
        channel.basicAck(tag, false);
    }
}
