package bumblebee.xchangepass.domain.card.service;

//import bumblebee.xchangepass.domain.ExchangeRate.dto.response.ExchangeRateResponse;
//import bumblebee.xchangepass.domain.ExchangeRate.service.ExchangeService;
import bumblebee.xchangepass.domain.card.dto.request.PaymentRequest;
import bumblebee.xchangepass.domain.card.dto.response.PaymentResponse;
import bumblebee.xchangepass.domain.card.entity.CardStatus;
import bumblebee.xchangepass.domain.cardTransaction.dto.request.PaymentApprovedEvent;
import bumblebee.xchangepass.domain.cardTransaction.service.CardTransactionService;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectEvent;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudAmountNormalizer;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectionService;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudTransactionType;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.security.crypto.AESEncryption;
import bumblebee.xchangepass.global.security.crypto.RSAEncryption;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CardPaymentService {

    private final UserRepository userRepository;
    private final WalletBalanceService walletBalanceService;
    private final FraudAmountNormalizer fraudAmountNormalizer;
    private final FraudDetectionService fraudDetectionService;
    private final RSAEncryption rsaEncryption;
    private final PasswordEncoder passwordEncoder;
    private final CardTransactionService cardTransactionService;

    /**
     * ✅ 카드 결제 요청 처리
     */
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        User user = userRepository.findByNameAndPhoneNumber(request.userName(), request.phoneNumber())
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);

        if (!isCardValid(user, request)) {
            throw ErrorCode.CARD_NOT_FOUND.commonException();
        }

        Wallet wallet = user.getWallet();

        if (!passwordEncoder.matches(request.walletPassword(), wallet.getWalletPassword())) {
            throw ErrorCode.INVALID_WALLET_PASSWORD.commonException();
        }

        BigDecimal krwAmount = fraudAmountNormalizer.normalize(request.amount(), request.currency());

        fraudDetectionService.verify(new FraudDetectEvent(
                user.getUserId(),
                krwAmount,
                LocalDateTime.now(),
                null,
                FraudTransactionType.CARD
        ));

        WalletBalance balance = walletBalanceService.findBalanceWithLock(wallet.getWalletId(), request.currency());

        if (balance.getBalance().compareTo(request.amount()) < 0) {
            throw ErrorCode.BALANCE_NOT_AVAILABLE.commonException();
        }

        walletBalanceService.withdrawBalanceForCardPayment(balance, request.amount());
        PaymentApprovedEvent event = PaymentApprovedEvent.of(
                user,
                request,
                krwAmount,
                balance.getBalance(),
                generateApprovalNumber()
        );

        cardTransactionService.handlePaymentApprovedEvent(event);

        return PaymentResponse.fromEvent(event);
    }

    /**
     * ✅ 승인 번호 생성
     */
    private String generateApprovalNumber() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    /**
     * ✅ 카드 유효성 검사
     */
    public boolean isCardValid(User user, PaymentRequest request) {
        return user.getWallet().getCards().stream()
                .filter(c -> c.getCardType() == request.cardType() && c.getCardStatus() == CardStatus.ACTIVE)
                .anyMatch(c -> {
                    SecretKey key = rsaEncryption.decryptAESKeyWithKMS(c.getEncryptionData().getEncryptedAesKey());
                    String number = AESEncryption.decryptData(c.getCardNumber(), key, c.getEncryptionData().getIv());
                    String cvc = AESEncryption.decryptData(c.getCvc(), key, c.getEncryptionData().getIv());
                    return number.equals(request.cardNumber()) && cvc.equals(request.cvc());
                });
    }

}
