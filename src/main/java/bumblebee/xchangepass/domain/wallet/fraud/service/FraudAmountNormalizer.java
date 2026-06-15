package bumblebee.xchangepass.domain.wallet.fraud.service;

import bumblebee.xchangepass.domain.exchangeRate.service.ExchangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Currency;

@Service
@RequiredArgsConstructor
public class FraudAmountNormalizer {

    private final ExchangeService exchangeService;
    private final FraudPolicyProperties properties;

    public BigDecimal normalize(BigDecimal amount, Currency currency) {
        Currency baseCurrency = Currency.getInstance(properties.getBaseCurrency());
        return currency.equals(baseCurrency)
                ? amount
                : exchangeService.getExchangeMoney(currency, baseCurrency, amount);
    }
}
