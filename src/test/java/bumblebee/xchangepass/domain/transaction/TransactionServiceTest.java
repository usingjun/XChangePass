package bumblebee.xchangepass.domain.transaction;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransaction;
import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import bumblebee.xchangepass.domain.exchangeTransaction.entitiy.ExchangeTransaction;
import bumblebee.xchangepass.domain.transaction.entity.TransactionDocument;
import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import bumblebee.xchangepass.domain.transaction.repository.TransactionRepository;
import bumblebee.xchangepass.domain.transaction.service.TransactionService;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TransactionServiceTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final TransactionService transactionService =
            new TransactionService(mongoTemplate, mock(TransactionRepository.class));

    @Test
    void walletRdbTransactionIsProjectedToUnifiedMongoDocument() {
        User user = mock(User.class);
        User receiver = mock(User.class);
        when(user.getUserId()).thenReturn(1L);
        when(receiver.getUserId()).thenReturn(2L);
        WalletTransaction transaction = new WalletTransaction(
                user, receiver, new BigDecimal("10000"), "KRW", "USD",
                WalletTransactionType.TRANSFER, LocalDateTime.of(2026, 1, 1, 12, 0)
        );

        transactionService.projectToMongo(transaction);

        TransactionDocument document = projectedDocument();
        assertThat(document.getUserId()).isEqualTo(1L);
        assertThat(document.getMetadata()).containsEntry("type", TransactionType.WALLET);
        assertThat(document.getMetadata()).containsEntry("receiver", 2L);
    }

    @Test
    void cardRdbTransactionIsProjectedToUnifiedMongoDocument() {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(1L);
        CardTransaction transaction = new CardTransaction(
                user, "XChangeMart", new BigDecimal("10"), "USD",
                new BigDecimal("14000"), new BigDecimal("90"), "APPROVAL-1",
                CardTransactionType.PAYMENT, LocalDateTime.of(2026, 1, 1, 12, 0)
        );

        transactionService.projectToMongo(transaction);

        TransactionDocument document = projectedDocument();
        assertThat(document.getMetadata()).containsEntry("type", TransactionType.CARD);
        assertThat(document.getMetadata()).containsEntry("merchant", "XChangeMart");
    }

    @Test
    void completedExchangeRdbTransactionIsProjectedToUnifiedMongoDocument() {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(1L);
        ExchangeTransaction transaction = new ExchangeTransaction(
                user, "USD", "KRW", new BigDecimal("10"), new BigDecimal("14000"),
                new BigDecimal("1400"), LocalDateTime.of(2026, 1, 1, 11, 0)
        );
        transaction.complete(LocalDateTime.of(2026, 1, 1, 12, 0));

        transactionService.projectToMongo(transaction);

        TransactionDocument document = projectedDocument();
        assertThat(document.getMetadata()).containsEntry("type", TransactionType.EXCHANGE);
        assertThat(document.getMetadata()).containsEntry("afterAmount", new BigDecimal("14000"));
    }

    private TransactionDocument projectedDocument() {
        var captor = org.mockito.ArgumentCaptor.forClass(TransactionDocument.class);
        verify(mongoTemplate).save(captor.capture());
        return captor.getValue();
    }
}
