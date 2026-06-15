package bumblebee.xchangepass.domain.wallet.fraud;

import bumblebee.xchangepass.domain.wallet.fraud.service.*;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FraudDetectionServiceTest {

    private final FraudRuleEvaluator ruleEvaluator = mock(FraudRuleEvaluator.class);
    private final FraudAlertService alertService = mock(FraudAlertService.class);
    private final FraudDetectionService fraudDetectionService =
            new FraudDetectionService(ruleEvaluator, alertService);

    @Test
    void clearTransactionDoesNotSendAlert() {
        FraudDetectEvent event = event();
        when(ruleEvaluator.evaluate("fraud:WALLET:user:1", event.amount()))
                .thenReturn(FraudEvaluationResult.clear());

        fraudDetectionService.verify(event);

        verifyNoInteractions(alertService);
    }

    @Test
    void suspiciousTransactionSendsReasonInAlert() {
        FraudDetectEvent event = event();
        when(ruleEvaluator.evaluate("fraud:WALLET:user:1", event.amount()))
                .thenReturn(FraudEvaluationResult.suspicious(FraudReason.FREQUENCY_EXCEEDED, 40));

        assertThatThrownBy(() -> fraudDetectionService.verify(event))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SUSPICIOUS_TRANSACTION);

        verify(alertService).notifySuspiciousTransaction(new FraudDetectEvent(
                event.userId(),
                event.amount(),
                event.timestamp(),
                FraudReason.FREQUENCY_EXCEEDED.description() + " (위험 점수: 40)",
                event.type()
        ));
    }

    @Test
    void detectionUnavailableBlocksTransactionAndSendsAlert() {
        FraudDetectEvent event = event();
        when(ruleEvaluator.evaluate("fraud:WALLET:user:1", event.amount()))
                .thenThrow(ErrorCode.FRAUD_DETECTION_UNAVAILABLE.commonException());

        assertThatThrownBy(() -> fraudDetectionService.verify(event))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FRAUD_DETECTION_UNAVAILABLE);

        verify(alertService).notifyDetectionUnavailable(event);
    }

    private FraudDetectEvent event() {
        return new FraudDetectEvent(
                1L,
                new BigDecimal("10000"),
                LocalDateTime.of(2026, 6, 16, 12, 0),
                null,
                FraudTransactionType.WALLET
        );
    }
}
