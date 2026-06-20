package bumblebee.xchangepass.domain.transaction;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import bumblebee.xchangepass.domain.cardTransaction.repository.CardTransactionRepository;
import bumblebee.xchangepass.domain.exchangeTransaction.repository.ExchangeTransactionRepository;
import bumblebee.xchangepass.domain.transaction.dto.cond.TransactionSearchCondition;
import bumblebee.xchangepass.domain.transaction.dto.cursor.TransactionCursor;
import bumblebee.xchangepass.domain.transaction.dto.response.TransactionPageResponse;
import bumblebee.xchangepass.domain.transaction.dto.response.WalletTransactionDto;
import bumblebee.xchangepass.domain.transaction.entity.TransactionDirection;
import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import bumblebee.xchangepass.domain.transaction.repository.CardTransactionRow;
import bumblebee.xchangepass.domain.transaction.repository.ExchangeTransactionRow;
import bumblebee.xchangepass.domain.transaction.repository.WalletTransactionRow;
import bumblebee.xchangepass.domain.transaction.service.TransactionCursorCodec;
import bumblebee.xchangepass.domain.transaction.service.TransactionService;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TransactionServiceTest {

    private final WalletTransactionRepository walletRepository = mock(WalletTransactionRepository.class);
    private final CardTransactionRepository cardRepository = mock(CardTransactionRepository.class);
    private final ExchangeTransactionRepository exchangeRepository = mock(ExchangeTransactionRepository.class);
    private final TransactionCursorCodec cursorCodec = new TransactionCursorCodec(new ObjectMapper());
    private final TransactionService transactionService =
            new TransactionService(walletRepository, cardRepository, exchangeRepository, cursorCodec);

    @Test
    void searchesEachSourceWithSizePlusOneAndReturnsNextCursor() {
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);
        TransactionSearchCondition condition = condition(null, null, null);
        WalletTransactionRow wallet = walletTransaction(userId, 1L, now.minusMinutes(2), BigDecimal.TEN);
        CardTransactionRow card = cardTransaction(userId, 2L, now);
        ExchangeTransactionRow exchange = exchangeTransaction(userId, 3L, now.minusMinutes(1));

        when(walletRepository.findRecentSentByUser(
                eq(userId), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(false), isNull(), any(Pageable.class)
        )).thenReturn(List.of(wallet));
        when(walletRepository.findRecentReceivedByUser(
                eq(userId), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(false), isNull(), any(Pageable.class)
        )).thenReturn(List.of());
        when(cardRepository.findRecentForUser(
                eq(userId), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(false), isNull(), any(Pageable.class)
        )).thenReturn(List.of(card));
        when(exchangeRepository.findRecentForUser(
                eq(userId), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(),
                any(Pageable.class)
        )).thenReturn(List.of(exchange));

        TransactionPageResponse response = transactionService.getTransactions(userId, condition, 2);

        assertThat(response.items())
                .extracting(item -> item.getTransactionTime())
                .containsExactly(now, now.minusMinutes(1));
        assertThat(response.hasNext()).isTrue();
        TransactionCursor cursor = cursorCodec.decode(response.nextCursor());
        assertThat(cursor.transactionType()).isEqualTo(TransactionType.EXCHANGE);
        assertThat(cursor.transactionId()).isEqualTo(3L);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(walletRepository).findRecentSentByUser(
                eq(userId), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(false), isNull(), pageable.capture()
        );
        assertThat(pageable.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void merchantFilterSearchesOnlyCardSource() {
        Long userId = 1L;
        TransactionSearchCondition condition = new TransactionSearchCondition(
                null, null, " STAR ", null, null, null, null, null, null, null, null
        );
        when(cardRepository.findRecentForUser(
                eq(userId), isNull(), eq("STAR"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(false), isNull(), any(Pageable.class)
        )).thenReturn(List.of());

        TransactionPageResponse response = transactionService.getTransactions(userId, condition, 30);

        assertThat(response.items()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        verify(cardRepository).findRecentForUser(
                eq(userId), isNull(), eq("STAR"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(false), isNull(), any(Pageable.class)
        );
        verifyNoInteractions(walletRepository, exchangeRepository);
    }

    @Test
    void receivedDirectionUsesReceivedAmountAndSenderAsCounterparty() {
        Long userId = 2L;
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);
        WalletTransactionRow received = walletTransaction(1L, 10L, now, BigDecimal.valueOf(7));
        TransactionSearchCondition condition = new TransactionSearchCondition(
                TransactionType.WALLET, null, null, null, null, null, TransactionDirection.RECEIVED,
                WalletTransactionType.TRANSFER, null, null, null
        );
        when(walletRepository.findRecentReceivedByUser(
                eq(userId), eq(WalletTransactionType.TRANSFER), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), eq(false), isNull(), any(Pageable.class)
        )).thenReturn(List.of(received));

        TransactionPageResponse response = transactionService.getTransactions(userId, condition, 30);

        WalletTransactionDto wallet = (WalletTransactionDto) response.items().get(0).getData();
        assertThat(response.items().get(0).getUserId()).isEqualTo(userId);
        assertThat(wallet.amount()).isEqualByComparingTo("7");
        assertThat(wallet.direction()).isEqualTo(TransactionDirection.RECEIVED);
        assertThat(wallet.counterpartyUserId()).isEqualTo(1L);
        verify(walletRepository, never()).findRecentSentByUser(
                anyLong(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(Pageable.class)
        );
        verifyNoInteractions(cardRepository, exchangeRepository);
    }

    @Test
    void passesDecodedCursorPositionToEachSource() {
        Long userId = 1L;
        LocalDateTime cursorTime = LocalDateTime.of(2026, 1, 1, 12, 0);
        String encoded = cursorCodec.encode(new TransactionCursor(1, cursorTime, TransactionType.CARD, 20L));
        TransactionSearchCondition condition = condition(null, null, encoded);

        when(walletRepository.findRecentSentByUser(
                eq(userId), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(cursorTime),
                eq(false), isNull(), any(Pageable.class)
        )).thenReturn(List.of());
        when(walletRepository.findRecentReceivedByUser(
                eq(userId), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(cursorTime),
                eq(false), isNull(), any(Pageable.class)
        )).thenReturn(List.of());
        when(cardRepository.findRecentForUser(
                eq(userId), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(cursorTime),
                eq(true), eq(20L), any(Pageable.class)
        )).thenReturn(List.of());
        when(exchangeRepository.findRecentForUser(
                eq(userId), isNull(), isNull(), isNull(), isNull(), isNull(), eq(cursorTime), eq(true), isNull(),
                any(Pageable.class)
        )).thenReturn(List.of());

        transactionService.getTransactions(userId, condition, 30);

        verify(walletRepository).findRecentSentByUser(
                eq(userId), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(cursorTime),
                eq(false), isNull(), any(Pageable.class)
        );
        verify(cardRepository).findRecentForUser(
                eq(userId), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(cursorTime),
                eq(true), eq(20L), any(Pageable.class)
        );
        verify(exchangeRepository).findRecentForUser(
                eq(userId), isNull(), isNull(), isNull(), isNull(), isNull(), eq(cursorTime), eq(true), isNull(),
                any(Pageable.class)
        );
    }

    @Test
    void rejectsPageSizeOutsideAllowedRange() {
        TransactionSearchCondition condition = condition(null, null, null);

        assertThatThrownBy(() -> transactionService.getTransactions(1L, condition, 0))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TRANSACTION_PAGE_SIZE);
        assertThatThrownBy(() -> transactionService.getTransactions(1L, condition, 101))
                .isInstanceOf(CommonException.class);
    }

    private TransactionSearchCondition condition(TransactionType type, TransactionDirection direction, String cursor) {
        return new TransactionSearchCondition(
                type, null, null, null, null, null, direction, null, null, null, cursor
        );
    }

    private WalletTransactionRow walletTransaction(Long senderId, Long transactionId,
                                                   LocalDateTime transactionTime, BigDecimal receivedAmount) {
        WalletTransactionRow transaction = mock(WalletTransactionRow.class);
        when(transaction.getTransactionId()).thenReturn(transactionId);
        when(transaction.getUserId()).thenReturn(senderId);
        when(transaction.getCounterpartyUserId()).thenReturn(2L);
        when(transaction.getAmount()).thenReturn(BigDecimal.TEN);
        when(transaction.getReceivedAmount()).thenReturn(receivedAmount);
        when(transaction.getFromCurrency()).thenReturn("KRW");
        when(transaction.getToCurrency()).thenReturn("USD");
        when(transaction.getTransactionType()).thenReturn(WalletTransactionType.TRANSFER);
        when(transaction.getTransactionTime()).thenReturn(transactionTime);
        return transaction;
    }

    private CardTransactionRow cardTransaction(Long userId, Long transactionId, LocalDateTime transactionTime) {
        CardTransactionRow transaction = mock(CardTransactionRow.class);
        when(transaction.getTransactionId()).thenReturn(transactionId);
        when(transaction.getUserId()).thenReturn(userId);
        when(transaction.getMerchantName()).thenReturn("STORE");
        when(transaction.getApprovedAmount()).thenReturn(BigDecimal.TEN);
        when(transaction.getApprovedCurrency()).thenReturn("KRW");
        when(transaction.getBalanceAfter()).thenReturn(BigDecimal.valueOf(100));
        when(transaction.getTransactionType()).thenReturn(CardTransactionType.PAYMENT);
        when(transaction.getTransactionTime()).thenReturn(transactionTime);
        return transaction;
    }

    private ExchangeTransactionRow exchangeTransaction(Long userId, Long transactionId,
                                                       LocalDateTime transactionTime) {
        ExchangeTransactionRow transaction = mock(ExchangeTransactionRow.class);
        when(transaction.getTransactionId()).thenReturn(transactionId);
        when(transaction.getUserId()).thenReturn(userId);
        when(transaction.getFromCurrency()).thenReturn("KRW");
        when(transaction.getToCurrency()).thenReturn("USD");
        when(transaction.getAmount()).thenReturn(BigDecimal.TEN);
        when(transaction.getReceivedAmount()).thenReturn(BigDecimal.ONE);
        when(transaction.getExchangeRate()).thenReturn(BigDecimal.valueOf(0.001));
        when(transaction.getTransactionTime()).thenReturn(transactionTime);
        return transaction;
    }
}
