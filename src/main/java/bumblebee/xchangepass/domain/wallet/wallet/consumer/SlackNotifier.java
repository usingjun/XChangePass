package bumblebee.xchangepass.domain.wallet.wallet.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

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

    public void send(String message) {
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
}