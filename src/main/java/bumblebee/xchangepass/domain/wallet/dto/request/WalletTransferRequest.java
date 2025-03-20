package bumblebee.xchangepass.domain.wallet.dto.request;

import bumblebee.xchangepass.global.validation.ValidCurrency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

public record WalletTransferRequest(
        @NotNull
        Long receiverWalletId,
        @DecimalMin(value = "1.00", message = "금액은 1.00 이상이어야 합니다.")
        @Digits(integer = 10, fraction = 2, message = "최대 10자리 정수와 소수점 2자리까지 가능합니다.")
        BigDecimal transferAmount,
        @ValidCurrency
        Currency fromCurrency,
        @ValidCurrency
        Currency toCurrency,
        LocalDateTime transferDatetime
) {
}
