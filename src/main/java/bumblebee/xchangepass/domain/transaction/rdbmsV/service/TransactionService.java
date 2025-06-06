package bumblebee.xchangepass.domain.transaction.rdbmsV.service;

import bumblebee.xchangepass.domain.cardTransaction.repository.CardTransactionRepository;
import bumblebee.xchangepass.domain.exchangeTransaction.repository.ExchangeTransactionRepository;
import bumblebee.xchangepass.domain.transaction.mongoV.TransactionDocument;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionSearchCondition;
import bumblebee.xchangepass.domain.transaction.rdbmsV.repository.TransactionRepository;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final MongoTemplate mongoTemplate;
    private final TransactionRepository transactionRepository;
    private final CardTransactionRepository cardTransactionRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final ExchangeTransactionRepository exchangeTransactionRepository;

    public List<TransactionResponse> getTransaction(Long userId, TransactionSearchCondition cond, int size) {
        List<TransactionResponse> cards = cardTransactionRepository.search(userId, cond, size);
        List<TransactionResponse> wallets = walletTransactionRepository.search(userId, cond, size);
        List<TransactionResponse> exchanges = exchangeTransactionRepository.search(userId, cond, size);

        return Stream.concat(Stream.concat(cards.stream(), wallets.stream()), exchanges.stream())
                .sorted(Comparator.comparing(TransactionResponse::transactionTime).reversed())
                .limit(size)
                .toList();
    }


    //mongo
    public void saveTransaction(String userId, String type, BigDecimal amount, Map<String, Object> metadata) {
        TransactionDocument tx = new TransactionDocument(userId, type, amount, LocalDateTime.now(), metadata);

        mongoTemplate.save(tx);
    }


}
