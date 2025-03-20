package bumblebee.xchangepass.domain.wallet.wallet.dto.request;

import bumblebee.xchangepass.global.validation.ValidCurrency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

public record WalletInOutRequest(
        @DecimalMin(value = "1.00", message = "금액은 1.00 이상이어야 합니다.")
        @Digits(integer = 10, fraction = 2, message = "최대 10자리 정수와 소수점 2자리까지 가능합니다.")
        BigDecimal amount,
        @ValidCurrency
        Currency fromCurrency,
        @ValidCurrency
        Currency toCurrency,
        LocalDateTime chargeDatetime
) {
    public WalletInOutRequest(BigDecimal amount, Currency fromCurrency, Currency toCurrency, LocalDateTime chargeDatetime) {
        this.amount = amount;
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.chargeDatetime = chargeDatetime;
    }
}