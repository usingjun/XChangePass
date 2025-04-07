package bumblebee.xchangepass.domain.card.service;

import bumblebee.xchangepass.domain.ExchangeRate.dto.response.ExchangeRateResponse;
import bumblebee.xchangepass.domain.ExchangeRate.service.ExchangeService;
import bumblebee.xchangepass.domain.card.dto.request.PaymentRequest;
import bumblebee.xchangepass.domain.card.dto.response.PaymentResponse;
import bumblebee.xchangepass.domain.card.entity.CardStatus;
import bumblebee.xchangepass.domain.cardTransaction.dto.request.PaymentApprovedEvent;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.walletBalance.entity.WalletBalance;
import bumblebee.xchangepass.domain.walletBalance.service.WalletBalanceService;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.security.crypto.AESEncryption;
import bumblebee.xchangepass.global.security.crypto.RSAEncryption;
import bumblebee.xchangepass.global.util.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CardPaymentService {

    private final UserRepository userRepository;
    private final WalletBalanceService walletBalanceService;
    private final ExchangeService exchangeService;
    private final RSAEncryption rsaEncryption;
    private final PasswordEncoder passwordEncoder;
    private final EventPublisher eventPublisher;

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

        WalletBalance balance = walletBalanceService.findBalanceWithLock(wallet.getWalletId(), request.currency());

        if (balance.getBalance().compareTo(request.amount()) < 0) {
            throw ErrorCode.BALANCE_NOT_AVAILABLE.commonException();
        }

        walletBalanceService.withdrawBalance(balance, request.amount());
        BigDecimal krwAmount = calculateKrw(request.amount(), request.currency());

        PaymentApprovedEvent event = PaymentApprovedEvent.of(
                user,
                request,
                krwAmount,
                balance.getBalance(),
                generateApprovalNumber()
        );

        eventPublisher.publishEvent(event);

        return PaymentResponse.fromEvent(event);
    }

    /**
     * ✅ 환율 계산 (기준 통화 → KRW)
     */
    private BigDecimal calculateKrw(BigDecimal amount, Currency currency) {
        ExchangeRateResponse response = exchangeService.getExchangeRateForCountry(currency.getCurrencyCode(), "KRW");

        Double rate = response.conversionRates().get("KRW");

        if (rate == null) {
            throw ErrorCode.EXCHANGE_RATE_NOT_FOUND.commonException();
        }

        BigDecimal rateDecimal = BigDecimal.valueOf(rate);
        return amount.multiply(rateDecimal).setScale(2, RoundingMode.HALF_UP);
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
