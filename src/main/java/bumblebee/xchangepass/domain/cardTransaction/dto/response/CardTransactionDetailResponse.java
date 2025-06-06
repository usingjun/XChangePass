package bumblebee.xchangepass.domain.cardTransaction.dto.response;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransaction;
import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

@Schema(description = "거래내역 상세 응답 DTO")
public record CardTransactionDetailResponse(

        @Schema(description = "가맹점 이름", example = "LAWSON")
        String merchantName,

        @Schema(description = "결제 금액", example = "4385.00")
        BigDecimal approvedAmount,

        @Schema(description = "결제 통화", example = "JPY")
        Currency approvedCurrency,

        @Schema(description = "KRW 환산 금액", example = "3769.50")
        BigDecimal krwAmount,

        @Schema(description = "승인 번호", example = "APRV-20250404123456")
        String approvalNumber,

        @Schema(description = "거래 일시", example = "2025-01-03T16:10:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime transactionTime,

        @Schema(description = "잔액", example = "500000")
        BigDecimal balanceAfter,

        @Schema(description = "거래 유형", example = "PAYMENT")
        CardTransactionType cardTransactionType

) {
    public static CardTransactionDetailResponse from(CardTransaction tx) {
        return new CardTransactionDetailResponse(
                tx.getMerchantName(),
                tx.getApprovedAmount(),
                tx.getApprovedCurrency(),
                tx.getKrwAmount(),
                tx.getApprovalNumber(),
                tx.getTransactionTime(),
                tx.getBalanceAfter(),
                tx.getCardTransactionType()
        );
    }
}
