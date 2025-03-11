package bumblebee.xchangepass.domain.wallet.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

public record WalletInOutRequest(
        Long userId,
        BigDecimal amount,
        Currency fromCurrency,
        Currency toCurrency,
        LocalDateTime chargeDatetime
) {
    public WalletInOutRequest(Long userId, BigDecimal amount, Currency fromCurrency, Currency toCurrency, LocalDateTime chargeDatetime) {
        this.userId = userId;
        this.amount = amount;
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.chargeDatetime = chargeDatetime;
    }
}
