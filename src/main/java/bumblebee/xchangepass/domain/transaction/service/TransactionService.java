package bumblebee.xchangepass.domain.transaction.service;

import bumblebee.xchangepass.domain.cardTransaction.repository.CardTransactionRepository;
import bumblebee.xchangepass.domain.exchangeTransaction.repository.ExchangeTransactionRepository;
import bumblebee.xchangepass.domain.transaction.dto.cond.TransactionSearchCondition;
import bumblebee.xchangepass.domain.transaction.dto.cursor.TransactionCursor;
import bumblebee.xchangepass.domain.transaction.dto.response.CardTransactionDto;
import bumblebee.xchangepass.domain.transaction.dto.response.ExchangeTransactionDto;
import bumblebee.xchangepass.domain.transaction.dto.response.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.dto.response.TransactionPageResponse;
import bumblebee.xchangepass.domain.transaction.dto.response.WalletTransactionDto;
import bumblebee.xchangepass.domain.transaction.entity.TransactionDirection;
import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import bumblebee.xchangepass.domain.transaction.repository.CardTransactionRow;
import bumblebee.xchangepass.domain.transaction.repository.ExchangeTransactionRow;
import bumblebee.xchangepass.domain.transaction.repository.WalletTransactionRow;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final WalletTransactionRepository walletTransactionRepository;
    private final CardTransactionRepository cardTransactionRepository;
    private final ExchangeTransactionRepository exchangeTransactionRepository;
    private final TransactionCursorCodec cursorCodec;

    @Transactional(readOnly = true)
    public TransactionPageResponse getTransactions(Long userId, TransactionSearchCondition cond, int size) {
        validateSize(size);
        int fetchLimit = size + 1;
        PageRequest page = PageRequest.of(0, fetchLimit);
        List<TypedTransaction> transactions = new ArrayList<>(fetchLimit * 4);
        TransactionCursor cursor = cursorCodec.decode(cond.cursor());
        CursorWindow walletCursor = cursorWindow(cursor, TransactionType.WALLET);
        CursorWindow cardCursor = cursorWindow(cursor, TransactionType.CARD);
        CursorWindow exchangeCursor = cursorWindow(cursor, TransactionType.EXCHANGE);
        boolean sourceHasMore = false;

        if (shouldSearchWallet(cond)) {
            if (cond.direction() == TransactionDirection.ALL || cond.direction() == TransactionDirection.SENT) {
                List<WalletTransactionRow> sent = walletTransactionRepository.findRecentSentByUser(
                        userId,
                        cond.walletTransactionType(),
                        cond.minAmount(),
                        cond.maxAmount(),
                        cond.currency(),
                        cond.startDate(),
                        cond.endDate(),
                        cursorTime(cursor),
                        walletCursor.includeCursorTime(),
                        walletCursor.cursorTransactionId(),
                        page
                );
                sourceHasMore |= sent.size() == fetchLimit;
                sent.stream()
                        .map(transaction -> toTypedTransaction(transaction, userId, TransactionDirection.SENT))
                        .forEach(transactions::add);
            }
            if (cond.direction() == TransactionDirection.ALL || cond.direction() == TransactionDirection.RECEIVED) {
                List<WalletTransactionRow> received = walletTransactionRepository.findRecentReceivedByUser(
                        userId,
                        cond.walletTransactionType(),
                        cond.minAmount(),
                        cond.maxAmount(),
                        cond.currency(),
                        cond.startDate(),
                        cond.endDate(),
                        cursorTime(cursor),
                        walletCursor.includeCursorTime(),
                        walletCursor.cursorTransactionId(),
                        page
                );
                sourceHasMore |= received.size() == fetchLimit;
                received.stream()
                        .map(transaction -> toTypedTransaction(transaction, userId, TransactionDirection.RECEIVED))
                        .forEach(transactions::add);
            }
        }

        if (shouldSearchCard(cond)) {
            List<CardTransactionRow> card = cardTransactionRepository.findRecentForUser(
                    userId,
                    cond.cardTransactionType(),
                    cond.merchantName(),
                    cond.minAmount(),
                    cond.maxAmount(),
                    cond.currency(),
                    cond.startDate(),
                    cond.endDate(),
                    cursorTime(cursor),
                    cardCursor.includeCursorTime(),
                    cardCursor.cursorTransactionId(),
                    page
            );
            sourceHasMore |= card.size() == fetchLimit;
            card.stream().map(this::toTypedTransaction).forEach(transactions::add);
        }

        if (shouldSearchExchange(cond)) {
            List<ExchangeTransactionRow> exchange = exchangeTransactionRepository.findRecentForUser(
                    userId,
                    cond.minAmount(),
                    cond.maxAmount(),
                    cond.currency(),
                    cond.startDate(),
                    cond.endDate(),
                    cursorTime(cursor),
                    exchangeCursor.includeCursorTime(),
                    exchangeCursor.cursorTransactionId(),
                    page
            );
            sourceHasMore |= exchange.size() == fetchLimit;
            exchange.stream().map(this::toTypedTransaction).forEach(transactions::add);
        }

        Map<TransactionKey, TypedTransaction> uniqueTransactions = new LinkedHashMap<>();
        transactions.forEach(transaction -> uniqueTransactions.putIfAbsent(
                new TransactionKey(transaction.transactionType(), transaction.transactionId()), transaction
        ));

        List<TypedTransaction> ordered = uniqueTransactions.values().stream()
                .sorted(Comparator
                        .comparing(TypedTransaction::transactionTime).reversed()
                        .thenComparing(TypedTransaction::transactionType)
                        .thenComparing(TypedTransaction::transactionId, Comparator.reverseOrder()))
                .toList();
        boolean hasNext = sourceHasMore || ordered.size() > size;
        List<TypedTransaction> pageItems = ordered.stream().limit(size).toList();
        String nextCursor = hasNext && !pageItems.isEmpty()
                ? cursorCodec.encode(pageItems.get(pageItems.size() - 1).toCursor())
                : null;

        return new TransactionPageResponse(
                pageItems.stream()
                .map(TypedTransaction::response)
                .toList(),
                nextCursor,
                hasNext
        );
    }

    private void validateSize(int size) {
        if (size < 1 || size > 100) {
            throw ErrorCode.INVALID_TRANSACTION_PAGE_SIZE.commonException();
        }
    }

    private LocalDateTime cursorTime(TransactionCursor cursor) {
        return cursor == null ? null : cursor.transactionTime();
    }

    private boolean shouldSearchWallet(TransactionSearchCondition cond) {
        if (cond.merchantName() != null) {
            return false;
        }
        return cond.transactionType() == null || cond.transactionType() == TransactionType.WALLET;
    }

    private boolean shouldSearchCard(TransactionSearchCondition cond) {
        return cond.transactionType() == null || cond.transactionType() == TransactionType.CARD;
    }

    private boolean shouldSearchExchange(TransactionSearchCondition cond) {
        if (cond.merchantName() != null) {
            return false;
        }
        return cond.transactionType() == null || cond.transactionType() == TransactionType.EXCHANGE;
    }

    private TypedTransaction toTypedTransaction(WalletTransactionRow transaction, Long userId,
                                                TransactionDirection direction) {
        boolean received = direction == TransactionDirection.RECEIVED;
        BigDecimal displayAmount = received && transaction.getReceivedAmount() != null
                ? transaction.getReceivedAmount()
                : transaction.getAmount();
        Long counterpartyUserId = received ? transaction.getUserId() : transaction.getCounterpartyUserId();
        TransactionResponse response = new TransactionResponse(
                userId,
                currency(transaction.getFromCurrency()),
                currency(transaction.getToCurrency()),
                transaction.getTransactionTime(),
                new WalletTransactionDto(
                        counterpartyUserId,
                        displayAmount,
                        direction,
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

    private CursorWindow cursorWindow(TransactionCursor cursor, TransactionType sourceType) {
        if (cursor == null) {
            return new CursorWindow(false, null);
        }

        int order = sourceType.compareTo(cursor.transactionType());
        if (order < 0) {
            return new CursorWindow(false, null);
        }
        if (order > 0) {
            return new CursorWindow(true, null);
        }
        return new CursorWindow(true, cursor.transactionId());
    }

    private record TypedTransaction(
            TransactionType transactionType,
            Long transactionId,
            LocalDateTime transactionTime,
            TransactionResponse response
    ) {
        private TransactionCursor toCursor() {
            return new TransactionCursor(
                    TransactionCursor.CURRENT_VERSION,
                    transactionTime,
                    transactionType,
                    transactionId
            );
        }
    }

    private record TransactionKey(TransactionType transactionType, Long transactionId) {
    }

    private record CursorWindow(
            boolean includeCursorTime,
            Long cursorTransactionId
    ) {
    }
}
