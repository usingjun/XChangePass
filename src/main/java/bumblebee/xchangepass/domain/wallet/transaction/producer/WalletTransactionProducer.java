package bumblebee.xchangepass.domain.wallet.transaction.producer;

import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.dto.WalletTransactionMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Currency;

@Service
@RequiredArgsConstructor
public class WalletTransactionProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendAsyncTransaction(Long myWalletId, Long counterWalletId, BigDecimal amount,
                                     Currency fromCurrency, Currency toCurrency,
                                     WalletTransactionType transactionType) {

        WalletTransactionMessage message = new WalletTransactionMessage(
                myWalletId,
                counterWalletId,
                amount,
                fromCurrency != null ? fromCurrency.getCurrencyCode() : null,
                toCurrency.getCurrencyCode(),
                transactionType.name()
        );

        rabbitTemplate.convertAndSend("wallet-transaction-queue", message);
    }

}