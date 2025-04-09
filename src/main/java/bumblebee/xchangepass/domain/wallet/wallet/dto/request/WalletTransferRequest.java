package bumblebee.xchangepass.domain.wallet.wallet.dto.request;

import bumblebee.xchangepass.domain.wallet.wallet.entity.WalletTransferType;
import bumblebee.xchangepass.global.validation.ValidCurrency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

@Schema(description = "송금 요청 객체")
public record WalletTransferRequest(
        @Schema(description = "받으시는 분 성함", example = "홍길동")
        @NotNull
        String receiverName,

        @Schema(description = "받으시는 분 전화번호", example = "010-1234-1234")
        @NotNull
        String receiverPhoneNumber,

        @Schema(description = "송금할 금액", example = "10000.00")
        @DecimalMin(value = "1.00", message = "금액은 1.00 이상이어야 합니다.")
        @Digits(integer = 10, fraction = 2, message = "최대 10자리 정수와 소수점 2자리까지 가능합니다.")
        BigDecimal transferAmount,

        @Schema(description = "가지고 계신 화폐 종류", example = "KRW, USD")
        @ValidCurrency
        Currency fromCurrency,

        @Schema(description = "보내고자 하는 화폐 종류", example = "KRW, USD")
        @ValidCurrency
        Currency toCurrency,

        @Schema(description = "송금 날짜", example = "2024-02-20T12:34:56")
        @FutureOrPresent
        LocalDateTime transferDatetime,

        @Schema(description = "송금 타입", example = "GENERAL")
        WalletTransferType transferType
) {
}
