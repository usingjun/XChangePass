package bumblebee.xchangepass.domain.wallet.transaction;

import bumblebee.xchangepass.domain.wallet.transaction.consumer.SlackNotifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@AutoConfigureMockMvc
class SlackNotifierIntegrationTest {

    @Autowired
    private SlackNotifier slackNotifier;

    @Test
    void sendSlackMessageTest() {
        // given
        String message = "✅ 테스트 메시지입니다 (통합 테스트)";

        // when
        slackNotifier.send(message);

        // then
        // 별도 assertion은 없고 Slack에서 수동으로 확인
    }
}
