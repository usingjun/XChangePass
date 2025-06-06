package bumblebee.xchangepass.domain.cardTransaction.dto.request;

import bumblebee.xchangepass.domain.card.dto.request.PaymentRequest;
import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import bumblebee.xchangepass.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

@Schema(description = "결제 승인 이벤트 데이터")
@Builder
public record PaymentApprovedEvent(

        @Schema(description = "유저 객체", implementation = User.class)
        @NotNull(message = "유저는 null일 수 없습니다.")
        User user,

        @Schema(description = "가맹점 이름", example = "XChangeMart")
        @NotBlank(message = "가맹점 이름은 필수입니다.")
        String merchantName,

        @Schema(description = "승인된 금액", example = "100.00")
        @NotNull(message = "승인 금액은 필수입니다.")
        BigDecimal approvedAmount,

        @Schema(description = "승인된 통화", example = "USD")
        @NotNull(message = "승인 통화는 필수입니다.")
        Currency approvedCurrency,

        @Schema(description = "KRW 환산 금액", example = "130000")
        @NotNull(message = "KRW 금액은 필수입니다.")
        BigDecimal krwAmount,

        @Schema(description = "결제 승인 시각", example = "2025-04-04T12:34:56")
        @NotNull(message = "결제 시간은 필수입니다.")
        LocalDateTime transactionTime,

        @Schema(description = "승인 번호", example = "APRV-20250404123456")
        @NotBlank(message = "승인 번호는 필수입니다.")
        String approvalNumber,

        @Schema(description = "잔액", example = "500000")
        @NotNull(message = "잔액은 필수입니다.")
        BigDecimal balanceAfter,

        @Schema(description = "거래 유형", example = "PAYMENT")
        @NotNull(message = "거래 유형은 필수입니다.")
        CardTransactionType cardTransactionType

) {
        public static PaymentApprovedEvent of(User user, PaymentRequest request, BigDecimal krwAmount, BigDecimal afterBalance, String approvalNumber) {
                return PaymentApprovedEvent.builder()
                        .user(user)
                        .merchantName(request.merchantName())
                        .approvedAmount(request.amount())
                        .approvedCurrency(request.currency())
                        .krwAmount(krwAmount)
                        .transactionTime(LocalDateTime.now())
                        .approvalNumber(approvalNumber)
                        .balanceAfter(afterBalance)
                        .cardTransactionType(request.cardTransactionType())
                        .build();
        }
}
