package bumblebee.xchangepass.domain.exchangeRate.service;

import bumblebee.xchangepass.domain.exchangeRate.dto.response.ExchangeRateResponse;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRate;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateTemp;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRateTempRepository;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRepository;
import bumblebee.xchangepass.domain.exchangeRate.util.Country;
import bumblebee.xchangepass.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Executor;

@Service
public class ExchangeService {

    @Value("${api.key}")
    private String authkey;

    private final ExchangeRepository exchangeRepository;
    private final ExchangeRateTransactionService exchangeTransactionService;
    private final ApplicationContext applicationContext;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Executor executor;
    private final ExchangeRateTempRepository exchangeRateTempRepository;

    @Autowired
    public ExchangeService(@Qualifier("asyncExecutor") Executor executor,
                           ExchangeRepository exchangeRepository,
                           ExchangeRateTempRepository exchangeRateTempRepository,
                           ExchangeRateTransactionService exchangeTransactionService,
                           ApplicationContext applicationContext) {
        this.exchangeRepository = exchangeRepository;
        this.exchangeRateTempRepository = exchangeRateTempRepository;
        this.exchangeTransactionService = exchangeTransactionService;
        this.applicationContext = applicationContext;
        this.executor = executor;

    }

    public ExchangeRateResponse fetchExchangeRates(String baseCurrency) {
        String API_URL = "https://v6.exchangerate-api.com/v6/" + authkey + "/latest/" + baseCurrency;
        try {
            return restTemplate.getForObject(API_URL, ExchangeRateResponse.class);
        } catch (HttpClientErrorException e) {
            throw ErrorCode.EXCHANGE_RATE_EXCEED.commonException();
        } catch (Exception e) {
            throw ErrorCode.EXCHANGE_RATE_FOR_COUNTRY.commonException();
        }
    }

    public CompletableFuture<Void> fetchAndSaveAllExchangeRates() {

        List<String> currencies = Country.create();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (String baseCurrency : currencies) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    ExchangeService self = applicationContext.getBean(ExchangeService.class);
                    self.fetchAndSaveExchangeRate(baseCurrency);
                } catch (Exception e) {
                    throw ErrorCode.EXCHANGE_RATE_NOT_FOUND.commonException();
                }
            }, executor);
            futures.add(future);
        }

        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        allOf.thenRun(exchangeTransactionService::swapExchangeRateTables);

        return allOf;
    }

    @Transactional
    public void fetchAndSaveExchangeRate(String baseCurrency) {
        ExchangeRateResponse response = fetchExchangeRates(baseCurrency);
        saveRatesToTempDB(baseCurrency, response);

        ExchangeService self = applicationContext.getBean(ExchangeService.class);
        self.evictExchangeRateCache(baseCurrency);
    }
    @CacheEvict(value = "exchangeRates", key = "#baseCurrency")
    public void evictExchangeRateCache(String baseCurrency) {
    }

    @Cacheable(value = "exchangeRates", key = "#baseCurrency", sync = true)
    public ExchangeRateResponse getExchangeRateAll(String baseCurrency) {

        List<ExchangeRate> exchangeRates = exchangeRepository.findByBaseCurrency(baseCurrency);
        if (!exchangeRates.isEmpty()) {
            Map<String, Double> conversionRates = exchangeRates.get(0).getExchangeRates();
            return ExchangeRateResponse.builder()
                    .baseCurrency(baseCurrency)
                    .conversionRates(conversionRates)
                    .build();
        } else {
            fetchAndSaveAllExchangeRates();

            return fetchExchangeRates(baseCurrency);
        }
    }


    public void saveRatesToTempDB(String baseCurrency, ExchangeRateResponse response) {
        try {
            Map<String, Double> rates = response.conversionRates();
            ExchangeRateTemp exchangeRateTemp = ExchangeRateTemp.builder()
                    .baseCurrency(baseCurrency)
                    .rate(rates)
                    .build();
            exchangeRateTempRepository.save(exchangeRateTemp);
        } catch (Exception e) {
            throw ErrorCode.EXCHANGE_SAVE_FAIL.commonException();
        }
    }

    @Cacheable(value = "exchangeRates", key = "'rate::' + #baseCurrency", unless = "#result == null")
    public ExchangeRateResponse getExchangeRateForCountry(String baseCurrency, String targetCurrency) {
        ExchangeRate rateEntity = exchangeRepository
                .findByBaseCurrencyAndKey(baseCurrency, targetCurrency)
                .stream()
                .findFirst()
                .orElseThrow(ErrorCode.EXCHANGE_RATE_FOR_COUNTRY::commonException);

        Double rate = rateEntity.getExchangeRates().get(targetCurrency);

        Map<String, Double> filteredRates = new HashMap<>();
        filteredRates.put(targetCurrency, rate);

        return ExchangeRateResponse.builder()
                .baseCurrency(baseCurrency)
                .conversionRates(filteredRates)
                .build();
    }


    @Transactional(readOnly = true)
    public BigDecimal getExchangeMoney(Currency baseCurrency, Currency targetCurrency, BigDecimal amount) {
        ExchangeService bean = applicationContext.getBean(ExchangeService.class);

        BigDecimal rate = BigDecimal.valueOf(bean.getExchangeRateForCountry(baseCurrency.toString(), targetCurrency.toString())
                .conversionRates().get(targetCurrency.toString()));
        return rate.multiply(amount);
    }
}
