package bumblebee.xchangepass.domain.wallet.transaction.consumer;

import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionStatus;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.domain.wallet.transaction.dto.WalletTransactionMessage;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletTransactionConsumer {

    private final WalletTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @RabbitListener(queues = "wallet-transaction-queue")
    @Transactional
    public void failOnPurpose(WalletTransactionMessage message) {
        throw new AmqpRejectAndDontRequeueException("실패 테스트");
    }
    public void processTransaction(WalletTransactionMessage message) {
        try {
            User sender = userRepository.findByUserId(message.senderId())
                    .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);

            User receiver = (WalletTransactionType.valueOf(message.transactionType()) == WalletTransactionType.TRANSFER)
                    ? userRepository.findByUserId(message.receiverId()).orElseThrow(ErrorCode.USER_NOT_FOUND::commonException)
                    : null;

            WalletTransaction transaction = new WalletTransaction(
                    sender,
                    receiver,
                    message.amount(),
                    message.fromCurrency() != null ? Currency.getInstance(message.fromCurrency()) : null,
                    Currency.getInstance(message.toCurrency()),
                    WalletTransactionType.valueOf(message.transactionType())
            );

            transaction.updateStatus(WalletTransactionStatus.SUCCESS);
            transactionRepository.save(transaction);
        } catch (Exception e) {
            log.error("❌ 트랜잭션 처리 중 예외 발생. DLQ로 보냅니다. message={}", message, e);
            throw new AmqpRejectAndDontRequeueException("트랜잭션 처리 실패", e);
        }
    }

}
