package bumblebee.xchangepass.domain.wallet.transaction.consumer;

import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectEvent;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackNotifier {

    @Value("${slack.webhook.url}")
    private String webhookUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public void failToSaveTransaction(String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", ":rotating_light: *DLQ 경고 발생*");
        payload.put("blocks", List.of(
                Map.of(
                        "type", "section",
                        "text", Map.of(
                                "type", "mrkdwn",
                                "text", "*📛 DLQ 메시지 알림!*\n```" + message + "```"
                        )
                ),
                Map.of(
                        "type", "context",
                        "elements", List.of(
                                Map.of("type", "mrkdwn", "text", ":clock1: " + LocalDateTime.now())
                        )
                )
        ));

        try {
            restTemplate.postForEntity(webhookUrl, payload, String.class);
        } catch (Exception e) {
            log.error("Slack 전송 실패", e);
        }
    }

    public void notifyFraud(FraudDetectEvent event) {
        Map<String, Object> body=Map.of(
                "text", String.format("🚨 이상 거래 감지!\n사용자 ID: %d\n금액: %s", event.userId(), event.amount())
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<?> request = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(webhookUrl, request, String.class);
    }
}