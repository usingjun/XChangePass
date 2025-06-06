package bumblebee.xchangepass.domain.cardTransaction.dto.response;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransaction;
import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

@Schema(description = "거래내역 요약 응답 DTO")
public record CardTransactionSummaryResponse(

        @Schema(description = "거래내역 ID", example = "42")
        Long transactionId,

        @Schema(description = "가맹점 이름", example = "FAMILYMART")
        String merchantName,

        @Schema(description = "결제 금액", example = "2980.00")
        BigDecimal approvedAmount,

        @Schema(description = "결제 통화", example = "JPY")
        Currency approvedCurrency,

        @Schema(description = "결제 일시", example = "2025-01-03T15:45:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime transactionTime,

        @Schema(description = "거래 유형", example = "PAYMENT")
        CardTransactionType cardTransactionType

) {
    public static CardTransactionSummaryResponse from(CardTransaction tx) {
        return new CardTransactionSummaryResponse(
                tx.getTransactionId(),
                tx.getMerchantName(),
                tx.getApprovedAmount(),
                tx.getApprovedCurrency(),
                tx.getTransactionTime(),
                tx.getCardTransactionType()
        );
    }
}
