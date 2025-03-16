package bumblebee.xchangepass.domain.ExchangeTransaction.dto.request;

import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.entity.value.UserPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Schema(description = "환전 실행하기 위한 요청 객체")
@Builder
public record ExchangeRequestDTO(
        User userId,

        @Schema(description = "환전할 통화", example = "USD")
        @NotNull(message = "환전할 통화는 필수 입력 값입니다.")
        String fromCurrency,

        @Schema(description = "환전 받을 통화", example = "KRW")
        @NotNull(message = "환전 받을 통화는 필수 입력 값입니다.")
        String toCurrency,

        @Schema(description = "환전할 금액", example = "3000")
        @NotNull(message = "환전할 금액은 필수 입력 값입니다.")
        BigDecimal amount
) {
}
