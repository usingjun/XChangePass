package bumblebee.xchangepass.domain.ExchangeRate.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;

@Builder
public record ExchangeRateResponse(
        @JsonProperty("base_code")
        String baseCurrency,

        @JsonProperty("conversion_rates")
        Map<String, Double> conversionRates) {

}
