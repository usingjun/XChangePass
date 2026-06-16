package bumblebee.xchangepass.domain.transaction.service;

import bumblebee.xchangepass.domain.cardTransaction.repository.CardTransactionRepository;
import bumblebee.xchangepass.domain.exchangeTransaction.repository.ExchangeTransactionRepository;
import bumblebee.xchangepass.domain.transaction.dto.cond.TransactionSearchCondition;
import bumblebee.xchangepass.domain.transaction.dto.response.CardTransactionDto;
import bumblebee.xchangepass.domain.transaction.dto.response.ExchangeTransactionDto;
import bumblebee.xchangepass.domain.transaction.dto.response.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.dto.response.WalletTransactionDto;
import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import bumblebee.xchangepass.domain.transaction.repository.CardTransactionRow;
import bumblebee.xchangepass.domain.transaction.repository.ExchangeTransactionRow;
import bumblebee.xchangepass.domain.transaction.repository.WalletTransactionRow;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final WalletTransactionRepository walletTransactionRepository;
    private final CardTransactionRepository cardTransactionRepository;
    private final ExchangeTransactionRepository exchangeTransactionRepository;

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactions(Long userId, TransactionSearchCondition cond, int size) {
        int limit = Math.max(size, 1);
        PageRequest page = PageRequest.of(0, limit);
        List<TypedTransaction> transactions = new ArrayList<>(limit * 4);
        CursorWindow walletCursor = cursorWindow(cond, TransactionType.WALLET);
        CursorWindow cardCursor = cursorWindow(cond, TransactionType.CARD);
        CursorWindow exchangeCursor = cursorWindow(cond, TransactionType.EXCHANGE);

        if (cond.transactionType() == null || cond.transactionType() == TransactionType.WALLET) {
            walletTransactionRepository.findRecentSentByUser(
                    userId,
                    cond.walletTransactionType(),
                    cond.startDate(),
                    cond.endDate(),
                    cond.cursor(),
                    walletCursor.includeCursorTime(),
                    walletCursor.cursorTransactionId(),
                    page
            ).stream().map(this::toTypedTransaction).forEach(transactions::add);
            walletTransactionRepository.findRecentReceivedByUser(
                    userId,
                    cond.walletTransactionType(),
                    cond.startDate(),
                    cond.endDate(),
                    cond.cursor(),
                    walletCursor.includeCursorTime(),
                    walletCursor.cursorTransactionId(),
                    page
            ).stream().map(this::toTypedTransaction).forEach(transactions::add);
        }

        if (cond.transactionType() == null || cond.transactionType() == TransactionType.CARD) {
            cardTransactionRepository.findRecentForUser(
                    userId,
                    cond.cardTransactionType(),
                    cond.startDate(),
                    cond.endDate(),
                    cond.cursor(),
                    cardCursor.includeCursorTime(),
                    cardCursor.cursorTransactionId(),
                    page
            ).stream().map(this::toTypedTransaction).forEach(transactions::add);
        }

        if (cond.transactionType() == null || cond.transactionType() == TransactionType.EXCHANGE) {
            exchangeTransactionRepository.findRecentForUser(
                    userId,
                    cond.startDate(),
                    cond.endDate(),
                    cond.cursor(),
                    exchangeCursor.includeCursorTime(),
                    exchangeCursor.cursorTransactionId(),
                    page
            ).stream().map(this::toTypedTransaction).forEach(transactions::add);
        }

        return transactions.stream()
                .sorted(Comparator
                        .comparing(TypedTransaction::transactionTime).reversed()
                        .thenComparing(TypedTransaction::transactionType)
                        .thenComparing(TypedTransaction::transactionId, Comparator.reverseOrder()))
                .limit(limit)
                .map(TypedTransaction::response)
                .toList();
    }

    private TypedTransaction toTypedTransaction(WalletTransactionRow transaction) {
        TransactionResponse response = new TransactionResponse(
                transaction.getUserId(),
                currency(transaction.getFromCurrency()),
                currency(transaction.getToCurrency()),
                transaction.getTransactionTime(),
                new WalletTransactionDto(
                        transaction.getCounterpartyUserId(),
                        transaction.getAmount(),
                        TransactionType.WALLET,
                        transaction.getTransactionType()
                )
        );
        return new TypedTransaction(
                TransactionType.WALLET,
                transaction.getTransactionId(),
                transaction.getTransactionTime(),
                response
        );
    }

    private TypedTransaction toTypedTransaction(CardTransactionRow transaction) {
        TransactionResponse response = new TransactionResponse(
                transaction.getUserId(),
                Currency.getInstance("KRW"),
                currency(transaction.getApprovedCurrency()),
                transaction.getTransactionTime(),
                new CardTransactionDto(
                        transaction.getMerchantName(),
                        transaction.getApprovedAmount(),
                        transaction.getBalanceAfter(),
                        TransactionType.CARD,
                        transaction.getTransactionType()
                )
        );
        return new TypedTransaction(
                TransactionType.CARD,
                transaction.getTransactionId(),
                transaction.getTransactionTime(),
                response
        );
    }

    private TypedTransaction toTypedTransaction(ExchangeTransactionRow transaction) {
        TransactionResponse response = new TransactionResponse(
                transaction.getUserId(),
                currency(transaction.getFromCurrency()),
                currency(transaction.getToCurrency()),
                transaction.getTransactionTime(),
                new ExchangeTransactionDto(
                        transaction.getAmount(),
                        transaction.getReceivedAmount(),
                        transaction.getExchangeRate(),
                        TransactionType.EXCHANGE
                )
        );
        return new TypedTransaction(
                TransactionType.EXCHANGE,
                transaction.getTransactionId(),
                transaction.getTransactionTime(),
                response
        );
    }

    private Currency currency(String currencyCode) {
        return currencyCode == null ? null : Currency.getInstance(currencyCode);
    }

    private CursorWindow cursorWindow(TransactionSearchCondition cond, TransactionType sourceType) {
        if (cond.cursor() == null || cond.cursorTransactionType() == null) {
            return new CursorWindow(false, null);
        }

        int order = sourceType.compareTo(cond.cursorTransactionType());
        if (order < 0) {
            return new CursorWindow(false, null);
        }
        if (order > 0) {
            return new CursorWindow(true, null);
        }
        return new CursorWindow(cond.cursorTransactionId() != null, cond.cursorTransactionId());
    }

    private record TypedTransaction(
            TransactionType transactionType,
            Long transactionId,
            LocalDateTime transactionTime,
            TransactionResponse response
    ) {
    }

    private record CursorWindow(
            boolean includeCursorTime,
            Long cursorTransactionId
    ) {
    }
}
