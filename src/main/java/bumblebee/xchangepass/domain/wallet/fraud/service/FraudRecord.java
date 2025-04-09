package bumblebee.xchangepass.domain.wallet.fraud.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FraudRecord(
        BigDecimal amount,
        LocalDateTime timestamp
) {
    public String serialize() {
        return amount + "," + timestamp.toString();
    }

    public static FraudRecord deserialize(String value) {
        String[] parts = value.split(",", 2);
        return new FraudRecord(new BigDecimal(parts[0]), LocalDateTime.parse(parts[1]));
    }
}