package bumblebee.xchangepass.domain.wallet.transaction.consumer;

import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectEvent;
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
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", "🚨 이상 거래 감지");

        payload.put("blocks", List.of(
                Map.of(
                        "type", "header",
                        "text", Map.of(
                                "type", "plain_text",
                                "text", "🚨 이상 거래 탐지",
                                "emoji", true
                        )
                ),
                Map.of(
                        "type", "section",
                        "fields", List.of(
                                Map.of("type", "mrkdwn", "text", "*사용자 ID:*\n" + event.userId()),
                                Map.of("type", "mrkdwn", "text", "*금액:*\n" + event.amount()),
                                Map.of("type", "mrkdwn", "text", "*시각:*\n" + event.timestamp()),
                                Map.of("type", "mrkdwn", "text", "*사유:*\n" + event.detail())
                        )
                ),
                Map.of(
                        "type", "context",
                        "elements", List.of(
                                Map.of("type", "mrkdwn", "text", ":shield: 이상 거래는 자동으로 기록되고 있습니다.")
                        )
                )
        ));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> request = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(webhookUrl, request, String.class);
        } catch (Exception e) {
            log.error("Slack 전송 실패", e);
        }
    }
}