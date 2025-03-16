package bumblebee.xchangepass.domain.ExchangeRate.service;

import bumblebee.xchangepass.domain.ExchangeRate.dto.response.ExchangeRateResponse;
import bumblebee.xchangepass.domain.ExchangeRate.entity.ExchangeRate;
import bumblebee.xchangepass.domain.ExchangeRate.entity.ExchangeRateTemp;
import bumblebee.xchangepass.domain.ExchangeRate.repository.ExchangeRateTempRepository;
import bumblebee.xchangepass.domain.ExchangeRate.repository.ExchangeRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ExchangeService {

    @Value("${api.key}")
    private String authkey;

    private final ExchangeRepository exchangeRepository;
    private final ExchangeRateTempRepository exchangeRateTempRepository;
    private final ExchangeRateTransactionService exchangeTransactionService;
    private final ApplicationContext applicationContext; // 🔹 Lazy 주입을 위한 ApplicationContext 사용
    private final RestTemplate restTemplate = new RestTemplate();

    @PersistenceContext
    private EntityManager entityManager;

    public ExchangeRateResponse fetchExchangeRates(String baseCurrency) {
        String API_URL = "https://v6.exchangerate-api.com/v6/" + authkey + "/latest/" + baseCurrency;
        try {
            return restTemplate.getForObject(API_URL, ExchangeRateResponse.class);
        } catch (HttpClientErrorException e) {
            throw ErrorCode.EXCHANGE_RATE_EXCEED.commonException();
        } catch (CommonException e) {
            throw ErrorCode.EXCHANGE_RATE_FOR_COUNTRY.commonException();
        }
    }

    @Async("asyncExecutor")
    @Transactional
    public CompletableFuture<Void> fetchAndSaveAllExchangeRates() {

        List<String> currencies = List.of("USD", "KRW");
        for (String baseCurrency : currencies) {
            try {
                ExchangeRateResponse response = fetchExchangeRates(baseCurrency);
                saveRatesToTempDB(baseCurrency, response);
            } catch (CommonException e) {
                throw ErrorCode.EXCHANGE_RATE_NOT_FOUND.commonException();
            }
        }
        exchangeTransactionService.swapExchangeRateTables();

        return CompletableFuture.completedFuture(null);
    }

    @Transactional
    public ExchangeRateResponse getExchangeRateAll(String baseCurrency) {
        List<ExchangeRate> exchangeRates = exchangeRepository.findByBaseCurrency(baseCurrency);

        if (!exchangeRates.isEmpty()) {
            Map<String, Double> conversionRates = exchangeRates.get(0).getExchangeRates();
            return ExchangeRateResponse.builder()
                    .baseCurrency(baseCurrency)
                    .conversionRates(conversionRates)
                    .build();
        } else {
            // 🔹 비동기 방식으로 환율 정보를 가져오도록 변경 (ApplicationContext 사용)
            ExchangeService asyncService = applicationContext.getBean(ExchangeService.class);
            asyncService.fetchAndSaveAllExchangeRates();

            return fetchExchangeRates(baseCurrency);
        }
    }

    @Transactional
    public void saveRatesToTempDB(String baseCurrency, ExchangeRateResponse response) {
        try {
            Map<String, Double> rates = response.conversionRates();
            ExchangeRateTemp exchangeRateTemp = ExchangeRateTemp.builder()
                    .baseCurrency(baseCurrency)
                    .rate(rates)
                    .build();
            exchangeRateTempRepository.save(exchangeRateTemp);
            entityManager.flush();
            entityManager.clear();
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
}
