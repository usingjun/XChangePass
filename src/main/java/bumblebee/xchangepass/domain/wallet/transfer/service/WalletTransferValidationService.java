package bumblebee.xchangepass.domain.wallet.transfer.service;

import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.service.UserService;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudAmountNormalizer;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectEvent;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectionService;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudTransactionType;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class WalletTransferValidationService {

    private final UserService userService;
    private final FraudAmountNormalizer fraudAmountNormalizer;
    private final FraudDetectionService fraudDetectionService;

    public Long resolveReceiver(WalletTransferRequest request) {
        User receiver = userService.readUser(request.receiverName(), request.receiverPhoneNumber());
        return receiver.getUserId();
    }

    public void verifyFraud(Long senderId, WalletTransferRequest request) {
        BigDecimal normalizedAmount = fraudAmountNormalizer.normalize(
                request.transferAmount(), request.fromCurrency()
        );
        fraudDetectionService.verify(new FraudDetectEvent(
                senderId, normalizedAmount, LocalDateTime.now(), null, FraudTransactionType.WALLET
        ));
    }
}
