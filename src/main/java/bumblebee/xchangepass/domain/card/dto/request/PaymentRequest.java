package bumblebee.xchangepass.domain.card.dto.request;

import bumblebee.xchangepass.domain.card.entity.CardType;
import bumblebee.xchangepass.domain.cardTransaction.entity.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.Currency;

@Schema(description = "결제 요청 객체")
public record PaymentRequest(

        @Schema(description = "사용자 이름", example = "홍길동")
        @NotBlank(message = "사용자 이름은 필수 입력 값입니다.")
        String userName,

        @Schema(description = "사용자 전화번호", example = "010-0000-0001")
        @NotBlank(message = "전화번호는 필수 입력 값입니다.")
        @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "전화번호는 010-0000-0000 형식이어야 합니다.")
        String phoneNumber,

        @Schema(description = "카드 번호", example = "8734-7469-4121-3667")
        @NotBlank(message = "카드 번호는 필수 입력 값입니다.")
        @Pattern(regexp = "^\\d{4}-\\d{4}-\\d{4}-\\d{4}$", message = "카드 번호는 XXXX-XXXX-XXXX-XXXX 형식이어야 합니다.")
        String cardNumber,

        @Schema(description = "CVC 코드", example = "123")
        @NotBlank(message = "CVC는 필수 입력 값입니다.")
        String cvc,

        @Schema(description = "카드 타입", example = "PHYSICAL")
        @NotNull(message = "카드 타입은 필수 입력 값입니다.")
        CardType cardType,

        @Schema(description = "결제 금액", example = "100.00")
        @DecimalMin(value = "0.01", message = "결제 금액은 0.01 이상이어야 합니다.")
        @Digits(integer = 10, fraction = 2, message = "소수점 둘째 자리까지 입력 가능합니다.")
        @NotNull(message = "결제 금액은 필수 입력 값입니다.")
        BigDecimal amount,

        @Schema(description = "결제 통화", example = "USD")
        @NotNull(message = "결제 통화는 필수 입력 값입니다.")
        Currency currency,

        @Schema(description = "가맹점 이름", example = "XChangeMart")
        @NotBlank(message = "가맹점 이름은 필수 입력 값입니다.")
        String merchantName,

        @Schema(description = "지갑 비밀번호", example = "1234")
        @NotBlank(message = "지갑 비밀번호는 필수 입력 값입니다.")
        String walletPassword,

        @Schema(description = "거래 유형", example = "PAYMENT")
        @NotNull(message = "거래 유형은 필수 입력 값입니다.")
        TransactionType transactionType

) {}
