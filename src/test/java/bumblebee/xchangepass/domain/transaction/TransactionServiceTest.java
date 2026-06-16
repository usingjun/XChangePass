package bumblebee.xchangepass.domain.transaction;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import bumblebee.xchangepass.domain.cardTransaction.repository.CardTransactionRepository;
import bumblebee.xchangepass.domain.exchangeTransaction.repository.ExchangeTransactionRepository;
import bumblebee.xchangepass.domain.transaction.dto.cond.TransactionSearchCondition;
import bumblebee.xchangepass.domain.transaction.dto.response.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import bumblebee.xchangepass.domain.transaction.repository.CardTransactionRow;
import bumblebee.xchangepass.domain.transaction.repository.ExchangeTransactionRow;
import bumblebee.xchangepass.domain.transaction.repository.WalletTransactionRow;
import bumblebee.xchangepass.domain.transaction.service.TransactionService;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionServiceTest {

    private final WalletTransactionRepository walletRepository = mock(WalletTransactionRepository.class);
    private final CardTransactionRepository cardRepository = mock(CardTransactionRepository.class);
    private final ExchangeTransactionRepository exchangeRepository = mock(ExchangeTransactionRepository.class);
    private final TransactionService transactionService =
            new TransactionService(walletRepository, cardRepository, exchangeRepository);

    @Test
    void searchesEachRdbTableWithTopNAndMergesByLatestTime() {
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);
        TransactionSearchCondition condition = new TransactionSearchCondition(
                null, null, null, null, null, null, null, null
        );
        WalletTransactionRow walletTransaction = walletTransaction(userId, 1L, now.minusMinutes(2));
        CardTransactionRow cardTransaction = cardTransaction(userId, 2L, now);
        ExchangeTransactionRow exchangeTransaction = exchangeTransaction(userId, 3L, now.minusMinutes(1));

        when(walletRepository.findRecentSentByUser(eq(userId), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(), any(Pageable.class)))
                .thenReturn(List.of(walletTransaction));
        when(walletRepository.findRecentReceivedByUser(eq(userId), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(), any(Pageable.class)))
                .thenReturn(List.of());
        when(cardRepository.findRecentForUser(eq(userId), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(), any(Pageable.class)))
                .thenReturn(List.of(cardTransaction));
        when(exchangeRepository.findRecentForUser(eq(userId), isNull(), isNull(), isNull(), eq(false), isNull(), any(Pageable.class)))
                .thenReturn(List.of(exchangeTransaction));

        List<TransactionResponse> responses = transactionService.getTransactions(userId, condition, 2);

        assertThat(responses).hasSize(2);
        assertThat(responses)
                .extracting(TransactionResponse::getTransactionTime)
                .containsExactly(now, now.minusMinutes(1));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(walletRepository).findRecentSentByUser(eq(userId), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(2);
    }

    @Test
    void searchesOnlyRequestedTransactionType() {
        Long userId = 1L;
        TransactionSearchCondition condition = new TransactionSearchCondition(
                TransactionType.WALLET, null, WalletTransactionType.TRANSFER, null, null, null, null, null
        );
        WalletTransactionRow walletTransaction = walletTransaction(userId, 1L, LocalDateTime.of(2026, 1, 1, 12, 0));

        when(walletRepository.findRecentSentByUser(
                eq(userId),
                eq(WalletTransactionType.TRANSFER),
                isNull(),
                isNull(),
                isNull(),
                eq(false),
                isNull(),
                any(Pageable.class)
        )).thenReturn(List.of(walletTransaction));
        when(walletRepository.findRecentReceivedByUser(
                eq(userId),
                eq(WalletTransactionType.TRANSFER),
                isNull(),
                isNull(),
                isNull(),
                eq(false),
                isNull(),
                any(Pageable.class)
        )).thenReturn(List.of());

        List<TransactionResponse> responses = transactionService.getTransactions(userId, condition, 30);

        assertThat(responses).hasSize(1);
        verify(walletRepository).findRecentSentByUser(
                eq(userId),
                eq(WalletTransactionType.TRANSFER),
                isNull(),
                isNull(),
                isNull(),
                eq(false),
                isNull(),
                any(Pageable.class)
        );
        verify(walletRepository).findRecentReceivedByUser(
                eq(userId),
                eq(WalletTransactionType.TRANSFER),
                isNull(),
                isNull(),
                isNull(),
                eq(false),
                isNull(),
                any(Pageable.class)
        );
        verifyNoCardOrExchangeSearch();
    }

    @Test
    void calculatesKeysetCursorPerTransactionSource() {
        Long userId = 1L;
        LocalDateTime cursorTime = LocalDateTime.of(2026, 1, 1, 12, 0);
        TransactionSearchCondition condition = new TransactionSearchCondition(
                null, null, null, null, null, cursorTime, TransactionType.CARD, 20L
        );

        when(walletRepository.findRecentSentByUser(
                eq(userId), isNull(), isNull(), isNull(), eq(cursorTime), eq(false), isNull(), any(Pageable.class)
        )).thenReturn(List.of());
        when(walletRepository.findRecentReceivedByUser(
                eq(userId), isNull(), isNull(), isNull(), eq(cursorTime), eq(false), isNull(), any(Pageable.class)
        )).thenReturn(List.of());
        when(cardRepository.findRecentForUser(
                eq(userId), isNull(), isNull(), isNull(), eq(cursorTime), eq(true), eq(20L), any(Pageable.class)
        )).thenReturn(List.of());
        when(exchangeRepository.findRecentForUser(
                eq(userId), isNull(), isNull(), eq(cursorTime), eq(true), isNull(), any(Pageable.class)
        )).thenReturn(List.of());

        transactionService.getTransactions(userId, condition, 30);

        verify(walletRepository).findRecentSentByUser(
                eq(userId), isNull(), isNull(), isNull(), eq(cursorTime), eq(false), isNull(), any(Pageable.class)
        );
        verify(cardRepository).findRecentForUser(
                eq(userId), isNull(), isNull(), isNull(), eq(cursorTime), eq(true), eq(20L), any(Pageable.class)
        );
        verify(exchangeRepository).findRecentForUser(
                eq(userId), isNull(), isNull(), eq(cursorTime), eq(true), isNull(), any(Pageable.class)
        );
    }

    private void verifyNoCardOrExchangeSearch() {
        verify(cardRepository, org.mockito.Mockito.never()).findRecentForUser(
                anyLong(), any(), any(), any(), any(), anyBoolean(), any(), any(Pageable.class)
        );
        verify(exchangeRepository, org.mockito.Mockito.never()).findRecentForUser(
                anyLong(), any(), any(), any(), anyBoolean(), any(), any(Pageable.class)
        );
    }

    private WalletTransactionRow walletTransaction(Long userId, Long transactionId, LocalDateTime transactionTime) {
        WalletTransactionRow transaction = mock(WalletTransactionRow.class);
        when(transaction.getTransactionId()).thenReturn(transactionId);
        when(transaction.getUserId()).thenReturn(userId);
        when(transaction.getCounterpartyUserId()).thenReturn(2L);
        when(transaction.getAmount()).thenReturn(BigDecimal.TEN);
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

    private ExchangeTransactionRow exchangeTransaction(Long userId, Long transactionId, LocalDateTime completedAt) {
        ExchangeTransactionRow transaction = mock(ExchangeTransactionRow.class);
        when(transaction.getTransactionId()).thenReturn(transactionId);
        when(transaction.getUserId()).thenReturn(userId);
        when(transaction.getFromCurrency()).thenReturn("KRW");
        when(transaction.getToCurrency()).thenReturn("USD");
        when(transaction.getAmount()).thenReturn(BigDecimal.TEN);
        when(transaction.getReceivedAmount()).thenReturn(BigDecimal.ONE);
        when(transaction.getExchangeRate()).thenReturn(BigDecimal.valueOf(0.001));
        when(transaction.getTransactionTime()).thenReturn(completedAt);
        return transaction;
    }
}
