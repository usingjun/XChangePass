package bumblebee.xchangepass.domain.exchangeRate.service;

import bumblebee.xchangepass.domain.exchangeRate.dto.response.ExchangeRateResponse;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncFailureType;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class ExchangeRateResponseValidator {

    private static final Set<String> REQUIRED_CURRENCIES = Set.of("KRW", "USD", "JPY", "EUR");

    public ExchangeRateValidationResult validate(String requestedBaseCurrency,
                                                 ExchangeRateResponse response) {
        if (response == null) {
            return ExchangeRateValidationResult.invalid(
                    ExchangeRateSyncFailureType.NULL_RESPONSE,
                    "Exchange rate response is null"
            );
        }

        String requested = normalize(requestedBaseCurrency);
        String actual = normalize(response.baseCurrency());
        if (requested == null || actual == null || !requested.equals(actual)) {
            return ExchangeRateValidationResult.invalid(
                    ExchangeRateSyncFailureType.BASE_CURRENCY_MISMATCH,
                    "Base currency mismatch: requested=" + requested + ", actual=" + actual
            );
        }

        Map<String, Double> rates = response.conversionRates();
        if (rates == null || rates.isEmpty()) {
            return ExchangeRateValidationResult.invalid(
                    ExchangeRateSyncFailureType.MISSING_CONVERSION_RATES,
                    "conversion_rates is missing"
            );
        }

        for (String requiredCurrency : REQUIRED_CURRENCIES) {
            if (!containsCurrency(rates, requiredCurrency)) {
                return ExchangeRateValidationResult.invalid(
                        ExchangeRateSyncFailureType.MISSING_REQUIRED_CURRENCY,
                        "Required currency is missing: " + requiredCurrency
                );
            }
        }

        for (Map.Entry<String, Double> entry : rates.entrySet()) {
            Double rate = entry.getValue();
            if (rate == null || rate <= 0 || rate.isNaN() || rate.isInfinite()) {
                return ExchangeRateValidationResult.invalid(
                        ExchangeRateSyncFailureType.INVALID_RATE_VALUE,
                        "Invalid rate value: currency=" + entry.getKey() + ", value=" + rate
                );
            }
        }

        return ExchangeRateValidationResult.success();
    }

    private boolean containsCurrency(Map<String, Double> rates, String currency) {
        return rates.keySet().stream()
                .map(this::normalize)
                .anyMatch(currency::equals);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
