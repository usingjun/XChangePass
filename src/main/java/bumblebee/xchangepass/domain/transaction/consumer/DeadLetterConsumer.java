package bumblebee.xchangepass.domain.transaction.consumer;

import bumblebee.xchangepass.domain.transaction.dto.response.TransactionResponse;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static bumblebee.xchangepass.global.common.Constants.DLQ_NAME;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterConsumer {

    private final SlackNotifier slackNotifier;

    @RabbitListener(queues = DLQ_NAME)
    public void handleDeadLetter(TransactionResponse message,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long tag,
                                 @Header("x-death") List<Map<String, Object>> xDeathHeader,
                                 Channel channel) throws IOException {

        int retryCount = 0;
        if (xDeathHeader != null) {
            retryCount = xDeathHeader.stream()
                    .mapToInt(x -> ((Long) x.get("count")).intValue())
                    .sum();

            log.warn("🚨 DLQ 진입 메시지 재시도 총 횟수: {}", retryCount);
        }

        log.error("❌ DLQ 최종 실패, 슬랙 전송: {}", message);
        slackNotifier.failToSaveTransaction("🚨 DLQ 처리 실패: " + message);

        // 수동 ack
        channel.basicAck(tag, false);
    }
}
