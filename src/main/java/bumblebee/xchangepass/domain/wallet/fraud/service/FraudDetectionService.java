package bumblebee.xchangepass.domain.wallet.fraud.service;

import bumblebee.xchangepass.domain.wallet.transaction.consumer.SlackNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final RedisFraudStorageService redisFraudStorage;
    private final FraudRuleEvaluator ruleEvaluator;
    private final SlackNotifier slackNotifier;

    public void detect(FraudDetectEvent event) {
        redisFraudStorage.store(event.userId(), event.amount());

        boolean isFraud = ruleEvaluator.isSuspicious(event.userId(), event.amount());

        if (isFraud) {
            String reason = ruleEvaluator.getLastDetectedReason();
            slackNotifier.notifyFraud(new FraudDetectEvent(
                    event.userId(),
                    event.amount(),
                    event.timestamp(),
                    reason
            ));
        }
    }
}
