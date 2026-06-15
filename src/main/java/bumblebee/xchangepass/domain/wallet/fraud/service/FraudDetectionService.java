package bumblebee.xchangepass.domain.wallet.fraud.service;

import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final FraudRuleEvaluator ruleEvaluator;
    private final FraudAlertService alertService;

    public void verify(FraudDetectEvent event) {
        String key = "fraud:" + event.type().name() + ":user:" + event.userId();

        FraudEvaluationResult result;
        try {
            result = ruleEvaluator.evaluate(key, event.amount());
        } catch (CommonException e) {
            if (e.getErrorCode() == ErrorCode.FRAUD_DETECTION_UNAVAILABLE) {
                alertService.notifyDetectionUnavailable(event);
            }
            throw e;
        }

        if (result.suspicious()) {
            alertService.notifySuspiciousTransaction(new FraudDetectEvent(
                    event.userId(),
                    event.amount(),
                    event.timestamp(),
                    result.description(),
                    event.type()
            ));
            throw ErrorCode.SUSPICIOUS_TRANSACTION.commonException();
        }
    }
}
