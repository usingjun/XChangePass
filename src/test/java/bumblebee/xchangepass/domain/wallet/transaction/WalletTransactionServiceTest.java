package bumblebee.xchangepass.domain.wallet.transaction;

import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.domain.wallet.transaction.service.WalletTransactionService;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WalletTransactionServiceTest {

    @Test
    void storesSentAndReceivedAmountsForTransferLedger() {
        WalletRepository walletRepository = mock(WalletRepository.class);
        WalletTransactionRepository transactionRepository = mock(WalletTransactionRepository.class);
        WalletTransactionService service = new WalletTransactionService(walletRepository, transactionRepository);
        Wallet senderWallet = mock(Wallet.class);
        Wallet receiverWallet = mock(Wallet.class);
        User sender = mock(User.class);
        User receiver = mock(User.class);
        BigDecimal sentAmount = new BigDecimal("10000.00");
        BigDecimal receivedAmount = new BigDecimal("7.50");

        when(walletRepository.findById(10L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findById(20L)).thenReturn(Optional.of(receiverWallet));
        when(senderWallet.getUser()).thenReturn(sender);
        when(receiverWallet.getUser()).thenReturn(receiver);

        service.saveTransferTransaction(
                10L,
                20L,
                sentAmount,
                receivedAmount,
                Currency.getInstance("KRW"),
                Currency.getInstance("USD")
        );

        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionRepository).save(captor.capture());
        WalletTransaction transaction = captor.getValue();
        assertThat(transaction.getUser()).isSameAs(sender);
        assertThat(transaction.getCounterpartyUser()).isSameAs(receiver);
        assertThat(transaction.getAmount()).isEqualByComparingTo(sentAmount);
        assertThat(transaction.getReceivedAmount()).isEqualByComparingTo(receivedAmount);
        assertThat(transaction.getFromCurrency()).isEqualTo("KRW");
        assertThat(transaction.getToCurrency()).isEqualTo("USD");
    }
}
