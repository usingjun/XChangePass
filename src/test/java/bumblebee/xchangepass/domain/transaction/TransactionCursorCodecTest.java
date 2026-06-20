package bumblebee.xchangepass.domain.transaction;

import bumblebee.xchangepass.domain.transaction.dto.cursor.TransactionCursor;
import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import bumblebee.xchangepass.domain.transaction.service.TransactionCursorCodec;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionCursorCodecTest {

    private final TransactionCursorCodec codec = new TransactionCursorCodec(new ObjectMapper());

    @Test
    void encodesAndDecodesCursorWithoutExposingPlainJson() {
        TransactionCursor cursor = new TransactionCursor(
                1, LocalDateTime.of(2026, 6, 20, 12, 0), TransactionType.CARD, 123L
        );

        String encoded = codec.encode(cursor);

        assertThat(encoded).doesNotContain("CARD", "2026-06-20");
        assertThat(codec.decode(encoded)).isEqualTo(cursor);
    }

    @Test
    void rejectsMalformedAndUnsupportedCursor() {
        String unsupported = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"v\":2,\"time\":\"2026-06-20T12:00:00\",\"source\":\"CARD\",\"id\":1}"
                        .getBytes(StandardCharsets.UTF_8)
        );

        assertInvalid("not-base64");
        assertInvalid(unsupported);
    }

    private void assertInvalid(String cursor) {
        assertThatThrownBy(() -> codec.decode(cursor))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TRANSACTION_CURSOR);
    }
}
