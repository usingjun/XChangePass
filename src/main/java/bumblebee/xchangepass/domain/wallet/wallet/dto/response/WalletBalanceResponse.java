package bumblebee.xchangepass.domain.wallet.wallet.dto.response;

import java.math.BigDecimal;

public record WalletBalanceResponse(
        String currency,
        BigDecimal balance
) {
    public WalletBalanceResponse(String currency, BigDecimal balance) {
        this.currency = currency;
        this.balance = balance;
    }
}
