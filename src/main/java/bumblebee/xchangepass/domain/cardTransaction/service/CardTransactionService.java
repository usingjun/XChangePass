package bumblebee.xchangepass.domain.cardTransaction.service;

import bumblebee.xchangepass.domain.cardTransaction.dto.request.PaymentApprovedEvent;
import bumblebee.xchangepass.domain.transaction.dto.response.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import bumblebee.xchangepass.domain.transaction.mapper.TransactionMetadataMapper;
import bumblebee.xchangepass.domain.transaction.service.RedisTransactionQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardTransactionService {

    private static final String REDIS_KEY_PREFIX = "transactions:insert:";
    private final RedisTransactionQueueService redisTransactionQueueService;

    /**
     * ✅ 결제 승인 이벤트 수신 시 거래내역 생성
     * {@link PaymentApprovedEvent} 이벤트를 비동기로 수신
     */
    @Async("asyncExecutor")
    @Transactional
    @EventListener(PaymentApprovedEvent.class)
    public void handlePaymentApprovedEvent(PaymentApprovedEvent event) {

        Map<String, Object> metadata = Map.of(
                "merchant", event.merchantName(),
                "currencyAmount", event.approvedAmount(),
                "balanceAfter", event.balanceAfter(),
                "transactionType", TransactionType.CARD,
                "cardType", event.cardTransactionType()
        );

        TransactionResponse response = new TransactionResponse(
                event.user().getUserId(),
                Currency.getInstance("KRW"),
                event.approvedCurrency(),
                event.transactionTime(),
                TransactionMetadataMapper.mapToDto(metadata)
        );

        // Redis로 임시 저장
        String redisKey = REDIS_KEY_PREFIX + event.user().getUserId();
        redisTransactionQueueService.enqueue(redisKey, response);

        log.info("💾 거래내역 저장 완료 - 승인번호: {}", event.approvalNumber());
    }

}
