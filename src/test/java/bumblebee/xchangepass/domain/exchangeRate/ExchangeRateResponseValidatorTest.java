package bumblebee.xchangepass.domain.exchangeRate;

import bumblebee.xchangepass.domain.exchangeRate.dto.response.ExchangeRateResponse;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncFailureType;
import bumblebee.xchangepass.domain.exchangeRate.service.ExchangeRateResponseValidator;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExchangeRateResponseValidatorTest {

    private final ExchangeRateResponseValidator validator = new ExchangeRateResponseValidator();

    @Test
    void validResponsePasses() {
        var result = validator.validate("USD", response("USD", validRates()));

        assertThat(result.valid()).isTrue();
        assertThat(result.failureType()).isNull();
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void nullResponseFails() {
        var result = validator.validate("USD", null);

        assertThat(result.valid()).isFalse();
        assertThat(result.failureType()).isEqualTo(ExchangeRateSyncFailureType.NULL_RESPONSE);
    }

    @Test
    void baseCurrencyMismatchFails() {
        var result = validator.validate("USD", response("EUR", validRates()));

        assertThat(result.valid()).isFalse();
        assertThat(result.failureType()).isEqualTo(ExchangeRateSyncFailureType.BASE_CURRENCY_MISMATCH);
        assertThat(result.errorMessage()).contains("requested=USD", "actual=EUR");
    }

    @Test
    void nullConversionRatesFail() {
        var result = validator.validate("USD", response("USD", null));

        assertThat(result.valid()).isFalse();
        assertThat(result.failureType()).isEqualTo(ExchangeRateSyncFailureType.MISSING_CONVERSION_RATES);
    }

    @Test
    void emptyConversionRatesFail() {
        var result = validator.validate("USD", response("USD", Map.of()));

        assertThat(result.valid()).isFalse();
        assertThat(result.failureType()).isEqualTo(ExchangeRateSyncFailureType.MISSING_CONVERSION_RATES);
    }

    @Test
    void missingRequiredCurrencyFails() {
        Map<String, Double> rates = validRates();
        rates.remove("KRW");

        var result = validator.validate("USD", response("USD", rates));

        assertThat(result.valid()).isFalse();
        assertThat(result.failureType()).isEqualTo(ExchangeRateSyncFailureType.MISSING_REQUIRED_CURRENCY);
        assertThat(result.errorMessage()).contains("KRW");
    }

    @Test
    void zeroRateFails() {
        Map<String, Double> rates = validRates();
        rates.put("JPY", 0.0);

        var result = validator.validate("USD", response("USD", rates));

        assertThat(result.valid()).isFalse();
        assertThat(result.failureType()).isEqualTo(ExchangeRateSyncFailureType.INVALID_RATE_VALUE);
    }

    @Test
    void negativeRateFails() {
        Map<String, Double> rates = validRates();
        rates.put("JPY", -1.0);

        var result = validator.validate("USD", response("USD", rates));

        assertThat(result.valid()).isFalse();
        assertThat(result.failureType()).isEqualTo(ExchangeRateSyncFailureType.INVALID_RATE_VALUE);
    }

    @Test
    void nanRateFails() {
        Map<String, Double> rates = validRates();
        rates.put("JPY", Double.NaN);

        var result = validator.validate("USD", response("USD", rates));

        assertThat(result.valid()).isFalse();
        assertThat(result.failureType()).isEqualTo(ExchangeRateSyncFailureType.INVALID_RATE_VALUE);
    }

    @Test
    void infiniteRateFails() {
        Map<String, Double> rates = validRates();
        rates.put("JPY", Double.POSITIVE_INFINITY);

        var result = validator.validate("USD", response("USD", rates));

        assertThat(result.valid()).isFalse();
        assertThat(result.failureType()).isEqualTo(ExchangeRateSyncFailureType.INVALID_RATE_VALUE);
    }

    @Test
    void lowercaseValuesAreNormalized() {
        Map<String, Double> rates = new LinkedHashMap<>();
        rates.put("krw", 1390.1);
        rates.put("usd", 1.0);
        rates.put("jpy", 157.2);
        rates.put("eur", 0.92);

        var result = validator.validate("usd", response("usd", rates));

        assertThat(result.valid()).isTrue();
    }

    private ExchangeRateResponse response(String baseCurrency, Map<String, Double> rates) {
        return ExchangeRateResponse.builder()
                .baseCurrency(baseCurrency)
                .conversionRates(rates)
                .build();
    }

    private Map<String, Double> validRates() {
        Map<String, Double> rates = new LinkedHashMap<>();
        rates.put("KRW", 1390.1);
        rates.put("USD", 1.0);
        rates.put("JPY", 157.2);
        rates.put("EUR", 0.92);
        return rates;
    }
}
