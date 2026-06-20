package bumblebee.xchangepass.domain.wallet.balance;

import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.repository.WalletBalanceRepository;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.transaction.service.WalletTransactionService;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class WalletBalanceServiceTest {

    @Test
    void transferDebitsSentAmountCreditsReceivedAmountAndStoresBoth() {
        WalletBalanceRepository repository = mock(WalletBalanceRepository.class);
        WalletTransactionService transactionService = mock(WalletTransactionService.class);
        WalletBalanceService service = new WalletBalanceService(repository, transactionService);
        WalletBalance fromBalance = mock(WalletBalance.class);
        WalletBalance toBalance = mock(WalletBalance.class);
        Wallet fromWallet = mock(Wallet.class);
        Wallet toWallet = mock(Wallet.class);
        Currency krw = Currency.getInstance("KRW");
        Currency usd = Currency.getInstance("USD");
        BigDecimal sentAmount = new BigDecimal("10000.00");
        BigDecimal receivedAmount = new BigDecimal("7.50");

        when(fromBalance.getBalance()).thenReturn(new BigDecimal("20000.00"));
        when(fromBalance.getWallet()).thenReturn(fromWallet);
        when(toBalance.getWallet()).thenReturn(toWallet);
        when(fromWallet.getWalletId()).thenReturn(10L);
        when(toWallet.getWalletId()).thenReturn(20L);
        when(fromBalance.getCurrency()).thenReturn(krw);
        when(toBalance.getCurrency()).thenReturn(usd);

        service.transferBalance(fromBalance, toBalance, sentAmount, receivedAmount);

        verify(fromBalance).subtractBalance(sentAmount);
        verify(toBalance).addBalance(receivedAmount);
        verify(repository).save(fromBalance);
        verify(repository).save(toBalance);
        verify(transactionService).saveTransferTransaction(
                10L, 20L, sentAmount, receivedAmount, krw, usd
        );
    }

    @Test
    void transferRejectsInsufficientSentCurrencyBalanceBeforeAnyWrite() {
        WalletBalanceRepository repository = mock(WalletBalanceRepository.class);
        WalletTransactionService transactionService = mock(WalletTransactionService.class);
        WalletBalanceService service = new WalletBalanceService(repository, transactionService);
        WalletBalance fromBalance = mock(WalletBalance.class);
        WalletBalance toBalance = mock(WalletBalance.class);

        when(fromBalance.getBalance()).thenReturn(new BigDecimal("9999.99"));

        assertThatThrownBy(() -> service.transferBalance(
                fromBalance, toBalance, new BigDecimal("10000.00"), new BigDecimal("7.50")
        ))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BALANCE_NOT_AVAILABLE);

        verifyNoInteractions(repository, transactionService);
        verify(fromBalance, never()).subtractBalance(any());
        verify(toBalance, never()).addBalance(any());
    }
}
