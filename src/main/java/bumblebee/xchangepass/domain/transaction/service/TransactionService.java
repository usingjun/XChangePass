package bumblebee.xchangepass.domain.transaction.service;

import bumblebee.xchangepass.domain.transaction.dto.cond.TransactionSearchCondition;
import bumblebee.xchangepass.domain.transaction.dto.response.TransactionResponse;
import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransaction;
import bumblebee.xchangepass.domain.exchangeTransaction.entitiy.ExchangeTransaction;
import bumblebee.xchangepass.domain.transaction.entity.TransactionDocument;
import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import bumblebee.xchangepass.domain.transaction.repository.TransactionRepository;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final MongoTemplate mongoTemplate;
    private final TransactionRepository transactionRepository;

    public void projectToMongo(WalletTransaction transaction) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("amount", transaction.getAmount());
        metadata.put("type", TransactionType.WALLET);
        metadata.put("walletType", transaction.getTransactionType());
        if (transaction.getCounterpartyUser() != null) {
            metadata.put("receiver", transaction.getCounterpartyUser().getUserId());
        }
        save("WALLET:" + transaction.getTransactionId(), transaction.getUser().getUserId(),
                transaction.getFromCurrency(), transaction.getToCurrency(), transaction.getTransactionTime(), metadata);
    }

    public void projectToMongo(CardTransaction transaction) {
        Map<String, Object> metadata = Map.of(
                "merchant", transaction.getMerchantName(),
                "amount", transaction.getApprovedAmount(),
                "balanceAfter", transaction.getBalanceAfter(),
                "type", TransactionType.CARD,
                "cardType", transaction.getTransactionType()
        );
        save("CARD:" + transaction.getTransactionId(), transaction.getUser().getUserId(),
                "KRW", transaction.getApprovedCurrency(), transaction.getTransactionTime(), metadata);
    }

    public void projectToMongo(ExchangeTransaction transaction) {
        Map<String, Object> metadata = Map.of(
                "amount", transaction.getAmount(),
                "afterAmount", transaction.getReceivedAmount(),
                "rate", transaction.getExchangeRate(),
                "type", TransactionType.EXCHANGE
        );
        save("EXCHANGE:" + transaction.getTransactionId(), transaction.getUser().getUserId(),
                transaction.getFromCurrency(), transaction.getToCurrency(), transaction.getCompletedAt(), metadata);
    }

    public List<TransactionResponse> getTransactionByMongo(Long userId, TransactionSearchCondition cond, int size) {
        return transactionRepository.search(userId, cond, size);
    }

    private void save(String transactionId, Long userId, String beforeCurrency, String afterCurrency,
                      java.time.LocalDateTime transactionTime, Map<String, Object> metadata) {
        mongoTemplate.save(new TransactionDocument(
                transactionId,
                userId,
                currency(beforeCurrency),
                currency(afterCurrency),
                transactionTime,
                metadata
        ));
    }

    private Currency currency(String currencyCode) {
        return currencyCode == null ? null : Currency.getInstance(currencyCode);
    }
}
