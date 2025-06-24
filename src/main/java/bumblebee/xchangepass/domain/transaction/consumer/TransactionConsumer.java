package bumblebee.xchangepass.domain.transaction.consumer;

import bumblebee.xchangepass.domain.transaction.dto.response.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import static bumblebee.xchangepass.global.common.Constants.TRANSACTION_MAIN_QUEUE;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionConsumer {

    private final TransactionService transactionService;

    @RabbitListener(queues = TRANSACTION_MAIN_QUEUE)
    public void processTransaction(TransactionResponse message) {
        try {
            transactionService.saveTransaction(message);
        } catch (Exception e) {
            log.error("❌ 트랜잭션 처리 중 예외 발생. DLQ로 보냅니다. message={}", message, e);
            throw new AmqpRejectAndDontRequeueException("트랜잭션 처리 실패", e);
        }
    }

}
