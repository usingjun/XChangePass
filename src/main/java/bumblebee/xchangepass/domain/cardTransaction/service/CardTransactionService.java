package bumblebee.xchangepass.domain.cardTransaction.service;

import bumblebee.xchangepass.domain.cardTransaction.dto.request.PaymentApprovedEvent;
import bumblebee.xchangepass.domain.cardTransaction.dto.response.CardTransactionDetailResponse;
import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransaction;
import bumblebee.xchangepass.domain.cardTransaction.repository.CardTransactionRepository;
import bumblebee.xchangepass.domain.transaction.mongoV.dto.response.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.mongoV.mapper.TransactionMetadataMapper;
import bumblebee.xchangepass.domain.transaction.mongoV.service.TransactionMongoService;
import bumblebee.xchangepass.domain.transaction.rdbmsV.entity.TransactionType;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final CardTransactionRepository transactionRepository;
    private final TransactionMongoService transactionService;
    private final RedisTemplate<String, TransactionResponse> redisTemplate;

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
                "cardType", event.cardTransactionType()
        );

        TransactionResponse response = new TransactionResponse(
                event.user().getUserId(),
                TransactionType.CARD,
                Currency.getInstance("KRW"),
                event.approvedCurrency(),
                event.transactionTime(),
                TransactionMetadataMapper.mapToDto(TransactionType.CARD, metadata)
        );

        String redisKey = REDIS_KEY_PREFIX + event.user().getUserId();
        redisTemplate.opsForList().rightPush(redisKey, response);

        transactionService.saveTransaction(event.user().getUserId(), TransactionType.CARD, Currency.getInstance("KRW"), event.approvedCurrency(), metadata);

        CardTransaction transaction = CardTransaction.builder()
                .user(event.user())
                .merchantName(event.merchantName())
                .approvedAmount(event.approvedAmount())
                .approvedCurrency(event.approvedCurrency())
                .krwAmount(event.krwAmount())
                .transactionTime(event.transactionTime())
                .approvalNumber(event.approvalNumber())
                .balanceAfter(event.balanceAfter())
                .cardTransactionType(event.cardTransactionType())
                .build();

        transactionRepository.save(transaction);

        log.info("💾 거래내역 저장 완료 - 승인번호: {}", event.approvalNumber());
    }

    /**
     * ✅ 거래 내역 무한 스크롤 조회 (커서 기반 최신순)
     */
//    public List<CardTransactionSummaryResponse> getUserTransactions(Long userId, Long lastTransactionId, int size) {
//
//        List<CardTransactionSummaryResponse> transactions =
//                transactionRepository.getUserTransactions(userId, lastTransactionId, size);
//
//        if (transactions.isEmpty()) {
//            throw ErrorCode.CARD_TRANSACTION_NOT_FOUND.commonException();
//        }
//
//        return transactions;
//    }


    /**
     * ✅ 개별 거래 내역 상세 조회
     */
    @Transactional(readOnly = true)
    public CardTransactionDetailResponse getTransactionDetail(Long loginUserId, Long transactionId) {
        CardTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(ErrorCode.CARD_TRANSACTION_NOT_FOUND::commonException);

        if (!transaction.getUser().getUserId().equals(loginUserId)) {
            throw ErrorCode.USER_FORBIDDEN.commonException();
        }

        return CardTransactionDetailResponse.from(transaction);
    }

}
