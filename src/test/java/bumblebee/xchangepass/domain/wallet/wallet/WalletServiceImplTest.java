package bumblebee.xchangepass.domain.wallet.wallet;

import bumblebee.xchangepass.domain.card.service.CardService;
import bumblebee.xchangepass.domain.exchangeRate.service.ExchangeService;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.service.UserService;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudAmountNormalizer;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectionService;
import bumblebee.xchangepass.domain.wallet.transfer.repository.WalletTransferRepository;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.entity.WalletTransferType;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.domain.wallet.wallet.service.TransactionAdvisoryLock;
import bumblebee.xchangepass.domain.wallet.wallet.service.impl.WalletServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WalletServiceImplTest {

    @Test
    void transferAcquiresWalletLocksInAscendingIdOrder() {
        WalletRepository walletRepository = mock(WalletRepository.class);
        WalletBalanceService balanceService = mock(WalletBalanceService.class);
        TransactionAdvisoryLock advisoryLock = mock(TransactionAdvisoryLock.class);
        FraudDetectionService fraudDetectionService = mock(FraudDetectionService.class);
        UserService userService = mock(UserService.class);
        WalletServiceImpl walletService = new WalletServiceImpl(
                walletRepository,
                mock(CardService.class),
                balanceService,
                mock(BCryptPasswordEncoder.class),
                advisoryLock,
                mock(FraudAmountNormalizer.class),
                fraudDetectionService,
                mock(ExchangeService.class),
                userService,
                mock(WalletTransferRepository.class)
        );

        User receiver = mock(User.class);
        Wallet senderWallet = mock(Wallet.class);
        Wallet receiverWallet = mock(Wallet.class);
        WalletBalance senderBalance = mock(WalletBalance.class);
        WalletBalance receiverBalance = mock(WalletBalance.class);
        Currency currency = Currency.getInstance("KRW");
        WalletTransferRequest request = new WalletTransferRequest(
                "receiver", "010-0000-0000", BigDecimal.TEN,
                currency, currency, null, WalletTransferType.GENERAL
        );

        when(receiver.getUserId()).thenReturn(2L);
        when(userService.readUser(request.receiverName(), request.receiverPhoneNumber())).thenReturn(receiver);
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(receiverWallet));
        when(senderWallet.getWalletId()).thenReturn(20L);
        when(receiverWallet.getWalletId()).thenReturn(10L);
        when(balanceService.findBalance(20L, currency)).thenReturn(senderBalance);
        when(balanceService.findBalance(10L, currency)).thenReturn(receiverBalance);
        when(senderBalance.getBalance()).thenReturn(BigDecimal.valueOf(100));

        walletService.transfer(1L, request);

        var inOrder = inOrder(advisoryLock);
        inOrder.verify(advisoryLock).acquire(10L);
        inOrder.verify(advisoryLock).acquire(20L);
        verify(balanceService).transferBalance(
                senderBalance, receiverBalance, BigDecimal.TEN, BigDecimal.TEN, null
        );
    }

    @Test
    void transferDebitsSourceAmountAndCreditsConvertedAmount() {
        WalletRepository walletRepository = mock(WalletRepository.class);
        WalletBalanceService balanceService = mock(WalletBalanceService.class);
        FraudAmountNormalizer normalizer = mock(FraudAmountNormalizer.class);
        ExchangeService exchangeService = mock(ExchangeService.class);
        UserService userService = mock(UserService.class);
        WalletServiceImpl walletService = new WalletServiceImpl(
                walletRepository,
                mock(CardService.class),
                balanceService,
                mock(BCryptPasswordEncoder.class),
                mock(TransactionAdvisoryLock.class),
                normalizer,
                mock(FraudDetectionService.class),
                exchangeService,
                userService,
                mock(WalletTransferRepository.class)
        );

        User receiver = mock(User.class);
        Wallet senderWallet = mock(Wallet.class);
        Wallet receiverWallet = mock(Wallet.class);
        WalletBalance senderBalance = mock(WalletBalance.class);
        WalletBalance receiverBalance = mock(WalletBalance.class);
        Currency krw = Currency.getInstance("KRW");
        Currency usd = Currency.getInstance("USD");
        BigDecimal sentAmount = new BigDecimal("10000.00");
        BigDecimal receivedAmount = new BigDecimal("7.50");
        WalletTransferRequest request = new WalletTransferRequest(
                "receiver", "010-0000-0000", sentAmount,
                krw, usd, null, WalletTransferType.GENERAL
        );

        when(receiver.getUserId()).thenReturn(2L);
        when(userService.readUser(request.receiverName(), request.receiverPhoneNumber())).thenReturn(receiver);
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(receiverWallet));
        when(senderWallet.getWalletId()).thenReturn(10L);
        when(receiverWallet.getWalletId()).thenReturn(20L);
        when(balanceService.findBalance(10L, krw)).thenReturn(senderBalance);
        when(balanceService.findBalance(20L, usd)).thenReturn(receiverBalance);
        when(senderBalance.getBalance()).thenReturn(new BigDecimal("20000.00"));
        when(exchangeService.getExchangeMoney(krw, usd, sentAmount)).thenReturn(receivedAmount);

        walletService.transfer(1L, request);

        verify(balanceService).transferBalance(
                senderBalance, receiverBalance, sentAmount, receivedAmount, null
        );
        assertThat(sentAmount).isNotEqualByComparingTo(receivedAmount);
    }
}
