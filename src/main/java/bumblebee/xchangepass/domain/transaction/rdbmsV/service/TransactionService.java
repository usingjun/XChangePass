package bumblebee.xchangepass.domain.transaction.rdbmsV.service;

import bumblebee.xchangepass.domain.cardTransaction.repository.CardTransactionRepository;
import bumblebee.xchangepass.domain.exchangeTransaction.repository.ExchangeTransactionRepository;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionSearchCondition;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class TransactionService {

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
}
