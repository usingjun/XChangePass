package bumblebee.xchangepass.domain.exchangeRate.controller;

import bumblebee.xchangepass.domain.exchangeRate.dto.response.ExchangeRateResponse;
import bumblebee.xchangepass.domain.exchangeRate.service.ExchangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exchange-rate")
@RequiredArgsConstructor
@Tag(name = "Exchange", description = "Exchange management API")
public class ExchangeController {

    private final ExchangeService exchangeService;

    @Operation(summary = "환율 조회", description = "특정 나라에 대한 모든 나라의 환율들 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "429", description = "환율 조회 초과",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"E004\"," +
                                                              "\n  \"message\": \"환율 요청 초과\"\n}"))
            )
    })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{baseCurrency}")
    public ExchangeRateResponse getExchangeRateAll(@PathVariable String baseCurrency) {
        return exchangeService.getExchangeRateAll(baseCurrency);
    }

    @Operation(summary = "환율 조회", description = "특정 나라와 특정 나라의 환율 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "400", description = "특정 나라에 대한 환율 정보 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"E004\"," +
                                                              "\n  \"message\": \"특정 나라에 대한 환율 정보 없음\"\n}"))
            )
    })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/rate")
    public ExchangeRateResponse getExchangeRate(
            @RequestParam String baseCurrency,
            @RequestParam String targetCurrency
    ) {
        return exchangeService.getExchangeRateForCountry(baseCurrency, targetCurrency);
    }
}