package bumblebee.xchangepass.domain.ExchangeRate.controller;

import bumblebee.xchangepass.domain.ExchangeRate.dto.response.ExchangeRateResponse;
import bumblebee.xchangepass.domain.ExchangeRate.service.ExchangeService;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exchange-rate")
@RequiredArgsConstructor
public class ExchangeController {

    private final ExchangeService exchangeService;

    // 특정 나라 -> 전체 나라들에 대한 환율
    @GetMapping("/{baseCurrency}")
    public ExchangeRateResponse getExchangeRateAll(@PathVariable String baseCurrency) {
        return exchangeService.getExchangeRateAll(baseCurrency);
    }

    // 위의 서비스에서 -> 원하는 나라 환율 조회
    @GetMapping("/rate")
    public ExchangeRateResponse getExchangeRate(
            @RequestParam String baseCurrency,
            @RequestParam String targetCurrency
    ) {
        ExchangeRateResponse response = exchangeService.getExchangeRateForCountry(baseCurrency, targetCurrency);

        if (response == null) {
           throw ErrorCode.EXCHANGE_RATE_NOT_FOUND.commonException();
        }
        return response;
    }
}
