package bumblebee.xchangepass.domain.wallet.fraud.service;

import bumblebee.xchangepass.domain.transaction.consumer.SlackNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FraudAlertService {

    private final SlackNotifier slackNotifier;

    @Async("asyncExecutor")
    public void notifySuspiciousTransaction(FraudDetectEvent event) {
        slackNotifier.notifyFraud(event);
    }

    @Async("asyncExecutor")
    public void notifyDetectionUnavailable(FraudDetectEvent event) {
        slackNotifier.notifyFraudDetectionUnavailable(event);
    }
}
