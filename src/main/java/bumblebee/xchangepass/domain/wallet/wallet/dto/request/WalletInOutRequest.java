package bumblebee.xchangepass.domain.wallet.wallet.dto.request;

import bumblebee.xchangepass.global.validation.ValidCurrency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

@Schema(description = "입금, 출금 요청 객체")
public record WalletInOutRequest(
        @Schema(description = "입금 및 출금할 금액", example = "10000.00")
        @DecimalMin(value = "1.00", message = "금액은 1.00 이상이어야 합니다.")
        @Digits(integer = 10, fraction = 2, message = "최대 10자리 정수와 소수점 2자리까지 가능합니다.")
        BigDecimal amount,

        @Schema(description = "가지고 계신 화폐 종류", example = "KRW, USD")
        @ValidCurrency
        Currency fromCurrency,

        @Schema(description = "바꾸고 싶어 하시는 화폐 종류", example = "KRW, USD")
        @ValidCurrency
        Currency toCurrency,

        @Schema(description = "입금 및 출금 날짜", example = "2024-02-20T12:34:56")
        LocalDateTime chargeDatetime
) {
    public WalletInOutRequest(BigDecimal amount, Currency fromCurrency, Currency toCurrency, LocalDateTime chargeDatetime) {
        this.amount = amount;
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.chargeDatetime = chargeDatetime;
    }
}