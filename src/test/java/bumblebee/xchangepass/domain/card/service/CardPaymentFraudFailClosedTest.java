package bumblebee.xchangepass.domain.card.service;

import bumblebee.xchangepass.domain.card.dto.request.PaymentRequest;
import bumblebee.xchangepass.domain.card.entity.CardType;
import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import bumblebee.xchangepass.domain.cardTransaction.service.CardTransactionService;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudAmountNormalizer;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectionService;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import bumblebee.xchangepass.global.security.crypto.RSAEncryption;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CardPaymentFraudFailClosedTest {

    @Test
    void fraudDetectionUnavailableStopsCardPaymentBeforeBalanceLockAndWithdrawal() {
        UserRepository userRepository = mock(UserRepository.class);
        WalletBalanceService balanceService = mock(WalletBalanceService.class);
        FraudAmountNormalizer amountNormalizer = mock(FraudAmountNormalizer.class);
        FraudDetectionService fraudDetectionService = mock(FraudDetectionService.class);
        RSAEncryption rsaEncryption = mock(RSAEncryption.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        CardTransactionService transactionService = mock(CardTransactionService.class);
        User user = mock(User.class);
        Wallet wallet = mock(Wallet.class);
        PaymentRequest request = request();

        CardPaymentService paymentService = spy(new CardPaymentService(
                userRepository,
                balanceService,
                amountNormalizer,
                fraudDetectionService,
                rsaEncryption,
                passwordEncoder,
                transactionService
        ));

        when(userRepository.findByNameAndPhoneNumber(request.userName(), request.phoneNumber()))
                .thenReturn(Optional.of(user));
        when(user.getWallet()).thenReturn(wallet);
        when(user.getUserId()).thenReturn(1L);
        when(wallet.getWalletPassword()).thenReturn("encoded-password");
        when(passwordEncoder.matches(request.walletPassword(), "encoded-password")).thenReturn(true);
        when(amountNormalizer.normalize(request.amount(), request.currency())).thenReturn(request.amount());
        doReturn(true).when(paymentService).isCardValid(user, request);
        doThrow(ErrorCode.FRAUD_DETECTION_UNAVAILABLE.commonException())
                .when(fraudDetectionService).verify(any());

        assertThatThrownBy(() -> paymentService.processPayment(request))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FRAUD_DETECTION_UNAVAILABLE);

        verifyNoInteractions(balanceService, transactionService);
    }

    private PaymentRequest request() {
        return new PaymentRequest(
                "홍길동",
                "010-1234-5678",
                "1111-2222-3333-4444",
                "123",
                CardType.MOBILE,
                BigDecimal.valueOf(10_000),
                Currency.getInstance("KRW"),
                "STORE",
                "1234",
                CardTransactionType.PAYMENT
        );
    }
}
