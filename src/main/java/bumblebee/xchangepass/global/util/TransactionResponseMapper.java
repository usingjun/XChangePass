package bumblebee.xchangepass.global.util;

import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Component
public class TransactionResponseMapper {
    private final ObjectMapper objectMapper;

    public TransactionResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TransactionResponse map(Object[] row) {
        try {
            Long userId = ((Number) row[0]).longValue();
            LocalDateTime time = ((Timestamp) row[1]).toLocalDateTime();
            String type = (String) row[2];
            String json = row[3].toString();

            TransactionResponse.TransactionDataDto data =
                    objectMapper.readValue(json, TransactionResponse.TransactionDataDto.class);

            return new TransactionResponse(userId, time, type, data);
        } catch (Exception e) {
            throw new RuntimeException("거래내역 DTO 변환 실패", e);
        }
    }

}
