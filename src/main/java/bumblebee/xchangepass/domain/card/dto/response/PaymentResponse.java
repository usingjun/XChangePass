package bumblebee.xchangepass.domain.card.dto.response;

import bumblebee.xchangepass.domain.cardTransaction.dto.request.PaymentApprovedEvent;
import bumblebee.xchangepass.domain.cardTransaction.entity.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

@Schema(description = "결제 응답 객체")
public record PaymentResponse(

        @Schema(description = "사용자 이름", example = "홍길동")
        String userName,

        @Schema(description = "가맹점 이름", example = "XChangeMart")
        String merchantName,

        @Schema(description = "승인된 금액", example = "100.00")
        BigDecimal approvedAmount,

        @Schema(description = "통화", example = "USD")
        Currency approvedCurrency,

        @Schema(description = "KRW 환산 금액", example = "134500")
        BigDecimal krwAmount,

        @Schema(description = "결제 승인 시각", example = "2025-04-04T14:30:00")
        LocalDateTime transactionTime,

        @Schema(description = "승인 번호", example = "A1B2C3D4E5F6")
        String approvalNumber,

        @Schema(description = "잔액", example = "865500")
        BigDecimal balanceAfter,

        @Schema(description = "거래 유형", example = "PAYMENT")
        TransactionType transactionType

) {

    public static PaymentResponse fromEvent(PaymentApprovedEvent event) {
        return new PaymentResponse(
                event.user().getUserName().getValue(),
                event.merchantName(),
                event.approvedAmount(),
                event.approvedCurrency(),
                event.krwAmount(),
                event.transactionTime(),
                event.approvalNumber(),
                event.balanceAfter(),
                event.transactionType()
        );
    }

}

