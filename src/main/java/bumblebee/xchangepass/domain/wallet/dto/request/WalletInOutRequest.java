package bumblebee.xchangepass.domain.wallet.dto.request;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

@Builder
public record WalletInOutRequest(
        Long userId,
        BigDecimal amount,
        Currency fromCurrency,
        Currency toCurrency,
        LocalDateTime chargeDatetime
){}