package bumblebee.xchangepass.domain.ExchangeRate.service;

import bumblebee.xchangepass.domain.ExchangeRate.dto.response.ExchangeRateResponse;
import bumblebee.xchangepass.domain.ExchangeRate.entity.ExchangeRate;
import bumblebee.xchangepass.domain.ExchangeRate.repository.ExchangeRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@EnableAsync
public class ExchangeService {
    @Value("${api.key}")
    private String authkey;

    private final ExchangeRepository exchangeRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    public ExchangeRateResponse fetchExchangeRates(String baseCurrency) {
        String API_URL = "https://v6.exchangerate-api.com/v6/" + authkey + "/latest/" + baseCurrency;
        try {
            return restTemplate.getForObject(API_URL, ExchangeRateResponse.class);
        } catch (HttpClientErrorException e) {
            throw ErrorCode.EXCHANGE_RATE_NOT_FOUND.commonException();
        }
    }
    @Scheduled(cron = "0 0 0 * * ?")
    public void fetchAndSaveAllExchangeRates(){
        exchangeRepository.deleteAll();
        List<String> list = create();
        for (String baseCurrency : list) {
            try {
                ExchangeRateResponse response = fetchExchangeRates(baseCurrency);
                saveRatesToDB(baseCurrency, response);
            } catch (Exception e) {
            }
        }
    }
    /**
     * ✅ 환율 데이터 조회 및 필요시 API 호출
     */
    @Transactional
    public ExchangeRateResponse getExchangeRateAll(String baseCurrency) {
        // 1️⃣ 최신 데이터 조회 (30분 이내)
        LocalDateTime cacheThreshold = LocalDateTime.now().minusMinutes(30);
        List<ExchangeRate> BaseCurrency = exchangeRepository.findByBaseCurrency(baseCurrency);
        if (!BaseCurrency.isEmpty()) {
            // 리스트의 첫 번째 ExchangeRate 객체에서 환율 데이터를 가져옴
            Map<String, Double> conversionRates = BaseCurrency.get(0).getExchangeRates();

            return ExchangeRateResponse.builder()
                    .baseCurrency(baseCurrency)
                    .conversionRates(conversionRates)
                    .build();
        }else{
            fetchAndSaveAllExchangeRates();
        }
        ExchangeRateResponse response = fetchExchangeRates(baseCurrency);
        return response;
    }

    public void saveRatesToDB(String baseCurrency, ExchangeRateResponse response) {
        try {
            // JSON 문자열이 아니라 Map 형태로 저장
            Map<String, Double> rates = response.conversionRates();

            ExchangeRate exchangeRate = ExchangeRate.builder()
                    .baseCurrency(baseCurrency)
                    .rate(rates)  // 🔥 Map 형태로 저장
                    .build();

            exchangeRepository.save(exchangeRate);
        } catch (CommonException e) {
            throw ErrorCode.EXCHANGE_SAVE_FAIL.commonException();
        }
    }

    @Transactional
    public ExchangeRateResponse getExchangeRateForCountry(String baseCurrency, String targetCurrency) {
        ExchangeRateResponse response = getExchangeRateAll(baseCurrency);

        if (response != null && response.conversionRates().containsKey(targetCurrency)) {
            Double rate = response.conversionRates().get(targetCurrency);
            Map<String, Double> filteredRates = new HashMap<>();
            filteredRates.put(targetCurrency, rate);

            return ExchangeRateResponse.builder()
                    .baseCurrency(baseCurrency)
                    .conversionRates(filteredRates)
                    .build();
        }
        throw ErrorCode.EXCHANGE_RATE_FOR_COUNTRY.commonException();
    }
    public static List<String>  create(){
        List<String> currencyList = List.of(
                "USD", "AED", "AFN", "ALL", "AMD", "ANG", "AOA", "ARS", "AUD", "AWG",
                "AZN", "BAM", "BBD", "BDT", "BGN", "BHD", "BIF", "BMD", "BND", "BOB",
                "BRL", "BSD", "BTN", "BWP", "BYN", "BZD", "CAD", "CDF", "CHF", "CLP",
                "CNY", "COP", "CRC", "CUP", "CVE", "CZK", "DJF", "DKK", "DOP", "DZD",
                "EGP", "ERN", "ETB", "EUR", "FJD", "FKP", "FOK", "GBP", "GEL", "GGP",
                "GHS", "GIP", "GMD", "GNF", "GTQ", "GYD", "HKD", "HNL", "HRK", "HTG",
                "HUF", "IDR", "ILS", "IMP", "INR", "IQD", "IRR", "ISK", "JEP", "JMD",
                "JOD", "JPY", "KES", "KGS", "KHR", "KID", "KMF", "KRW", "KWD", "KYD",
                "KZT", "LAK", "LBP", "LKR", "LRD", "LSL", "LYD", "MAD", "MDL", "MGA",
                "MKD", "MMK", "MNT", "MOP", "MRU", "MUR", "MVR", "MWK", "MXN", "MYR",
                "MZN", "NAD", "NGN", "NIO", "NOK", "NPR", "NZD", "OMR", "PAB", "PEN",
                "PGK", "PHP", "PKR", "PLN", "PYG", "QAR", "RON", "RSD", "RUB", "RWF",
                "SAR", "SBD", "SCR", "SDG", "SEK", "SGD", "SHP", "SLE", "SLL", "SOS",
                "SRD", "SSP", "STN", "SYP", "SZL", "THB", "TJS", "TMT", "TND", "TOP",
                "TRY", "TTD", "TVD", "TWD", "TZS", "UAH", "UGX", "UYU", "UZS", "VES",
                "VND", "VUV", "WST", "XAF", "XCD", "XDR", "XOF", "XPF", "YER", "ZAR",
                "ZMW", "ZWL"
        );
        return currencyList;
    }
}
