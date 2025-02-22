package bumblebee.xchangepass.domain.wallet.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

public record WalletChargeRequest(
        Long userId,
        BigDecimal chargeAmount,
        Currency fromCurrency,
        Currency toCurrency,
        LocalDateTime chargeDatetime
) {
    public WalletChargeRequest(Long userId, BigDecimal chargeAmount, Currency fromCurrency, Currency toCurrency, LocalDateTime chargeDatetime) {
        this.userId = userId;
        this.chargeAmount = chargeAmount;
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.chargeDatetime = chargeDatetime;
    }
}
