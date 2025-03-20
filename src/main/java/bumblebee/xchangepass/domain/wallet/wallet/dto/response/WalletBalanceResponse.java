package bumblebee.xchangepass.domain.wallet.wallet.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "화폐별 잔액 응답 객체")
public record WalletBalanceResponse(
        @Schema(description = "화폐 종류", example = "KRW, USD")
        String currency,

        @Schema(description = "잔액", example = "10000.00")
        BigDecimal balance
) {
    public WalletBalanceResponse(String currency, BigDecimal balance) {
        this.currency = currency;
        this.balance = balance;
    }
}
