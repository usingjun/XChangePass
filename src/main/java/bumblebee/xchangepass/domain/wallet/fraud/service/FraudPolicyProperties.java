package bumblebee.xchangepass.domain.wallet.fraud.service;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalTime;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "fraud.policy")
public class FraudPolicyProperties {

    @NotBlank
    private String baseCurrency = "KRW";
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal totalAmountLimit = new BigDecimal("500000");
    @Min(1)
    private int frequencyLimit = 5;
    @Min(1)
    private long accumulationWindowSeconds = 600;
    @Min(1)
    private long frequencyWindowSeconds = 300;
    @Min(1)
    private long historyTtlSeconds = 7200;
    @NotNull
    private LocalTime nightStart = LocalTime.of(2, 30);
    @NotNull
    private LocalTime nightEnd = LocalTime.of(3, 30);
    @Min(1)
    private int maxAttempts = 2;
    @Min(0)
    private long retryBackoffMillis = 50;
    @Min(1)
    private int circuitFailureThreshold = 3;
    @Min(1)
    private long circuitOpenMillis = 30000;
    @Min(1)
    private int totalAmountScore = 50;
    @Min(1)
    private int frequencyScore = 40;
    @Min(1)
    private int repeatedAmountScore = 30;
    @Min(1)
    private int nightTimeScore = 20;

    public int riskScore(FraudReason reason) {
        return switch (reason) {
            case CLEAR -> 0;
            case TOTAL_AMOUNT_EXCEEDED -> totalAmountScore;
            case FREQUENCY_EXCEEDED -> frequencyScore;
            case REPEATED_AMOUNT -> repeatedAmountScore;
            case NIGHT_TIME_TRANSACTION -> nightTimeScore;
        };
    }
}
