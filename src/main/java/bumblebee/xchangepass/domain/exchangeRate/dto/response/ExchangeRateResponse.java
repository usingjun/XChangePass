package bumblebee.xchangepass.domain.exchangeRate.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.Map;

@Builder
public record ExchangeRateResponse(
        @Schema(description = "환율 기준 나라", example = "USD")
        @JsonProperty("base_code")
        String baseCurrency,

        @Schema(description = "특정 나라에 대한 환율들", example = "{KRW, 1400},{JWP, 1300},{USP, 230}, ...")
        @JsonProperty("conversion_rates")
        Map<String, Double> conversionRates) {

}
