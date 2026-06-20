package bumblebee.xchangepass.domain.transaction.service;

import bumblebee.xchangepass.domain.transaction.dto.cursor.TransactionCursor;
import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import bumblebee.xchangepass.global.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

@Component
public class TransactionCursorCodec {

    private final ObjectMapper objectMapper;

    public TransactionCursorCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(TransactionCursor cursor) {
        try {
            CursorPayload payload = new CursorPayload(
                    cursor.version(),
                    cursor.transactionTime().toString(),
                    cursor.transactionType().name(),
                    cursor.transactionId()
            );
            byte[] json = objectMapper.writeValueAsBytes(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (JsonProcessingException e) {
            throw ErrorCode.INVALID_TRANSACTION_CURSOR.commonException();
        }
    }

    public TransactionCursor decode(String encodedCursor) {
        if (encodedCursor == null || encodedCursor.isBlank()) {
            return null;
        }

        try {
            byte[] json = Base64.getUrlDecoder().decode(encodedCursor);
            CursorPayload payload = objectMapper.readValue(
                    new String(json, StandardCharsets.UTF_8), CursorPayload.class
            );
            validate(payload);
            return new TransactionCursor(
                    payload.v(),
                    LocalDateTime.parse(payload.time()),
                    TransactionType.valueOf(payload.source()),
                    payload.id()
            );
        } catch (IllegalArgumentException | JsonProcessingException e) {
            throw ErrorCode.INVALID_TRANSACTION_CURSOR.commonException();
        }
    }

    private void validate(CursorPayload payload) {
        if (payload.v() != TransactionCursor.CURRENT_VERSION
                || payload.time() == null
                || payload.source() == null
                || payload.id() == null
                || payload.id() <= 0) {
            throw ErrorCode.INVALID_TRANSACTION_CURSOR.commonException();
        }
    }

    private record CursorPayload(int v, String time, String source, Long id) {
    }
}
