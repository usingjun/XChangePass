package bumblebee.xchangepass.domain.exchangeRate.service;

import bumblebee.xchangepass.domain.exchangeRate.dto.response.ExchangeRateResponse;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRate;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateTemp;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRateTempRepository;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRepository;
import bumblebee.xchangepass.domain.exchangeRate.util.Country;
import bumblebee.xchangepass.domain.exchangeRate.util.ExchangeRateLockManager;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Executor;

import static bumblebee.xchangepass.global.common.Constants.url;

@Service
public class ExchangeService {

    @Value("${api.key}")
    private String authkey;

    private final ExchangeRepository exchangeRepository;
    private final ExchangeRateTransactionService exchangeTransactionService;
    private final ApplicationContext applicationContext;
    private final RestTemplate restTemplate;
    private final Executor executor;
    private final ExchangeRateTempRepository exchangeRateTempRepository;
    private final ExchangeRateLockManager lockManager;
    private final CacheManager cacheManager;

    @Autowired
    public ExchangeService(@Qualifier("asyncExecutor") Executor executor,
                           ExchangeRepository exchangeRepository,
                           ExchangeRateTempRepository exchangeRateTempRepository,
                           ExchangeRateTransactionService exchangeTransactionService,
                           ApplicationContext applicationContext,
                           ExchangeRateLockManager lockManager,
                           CacheManager cacheManager,
                           RestTemplate restTemplate) {
        this.exchangeRepository = exchangeRepository;
        this.exchangeRateTempRepository = exchangeRateTempRepository;
        this.exchangeTransactionService = exchangeTransactionService;
        this.applicationContext = applicationContext;
        this.executor = executor;
        this.restTemplate = restTemplate;
        this.lockManager = lockManager;
        this.cacheManager = cacheManager;
    }

    public ExchangeRateResponse fetchExchangeRates(String baseCurrency) {
        String API_URL = url + authkey + "/latest/" + baseCurrency;
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
        evictExchangeRateCache(baseCurrency);
    }

    public void evictExchangeRateCache(String baseCurrency) {
        try {
            Cache cache = cacheManager.getCache("exchangeRates");
            if (cache != null) {
                cache.evict("all::" + baseCurrency);
                List<String> currencies = List.of("USD","KRW");
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                for (String targetCurrency : currencies) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        cache.evict("rate::" + baseCurrency + "::" + targetCurrency);
                    }, executor);

                    futures.add(future);
                }

                // 모든 작업 완료까지 대기
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        } catch (RedisConnectionFailureException e) {
            throw ErrorCode.REDIS_EVICT_ERROR.commonException();
        } catch (Exception e) {
            throw ErrorCode.CACHE_EVICT_ERROR.commonException();
        }
    }


    @Cacheable(value = "exchangeRates", key = "'all::' + #baseCurrency", sync = true)
    public ExchangeRateResponse getExchangeRateAll(String baseCurrency) {
        List<ExchangeRate> exchangeRates = exchangeRepository.findByBaseCurrency(baseCurrency);
        if (!exchangeRates.isEmpty()) {
            return toResponse(baseCurrency, exchangeRates);
        }

        boolean lockAcquired = lockManager.tryAcquireLock();

        if (lockAcquired) {
            try {
                exchangeRates = exchangeRepository.findByBaseCurrency(baseCurrency);
                if (!exchangeRates.isEmpty()) {
                    return toResponse(baseCurrency, exchangeRates);
                }

                fetchAndSaveAllExchangeRates().join();

                exchangeRates = exchangeRepository.findByBaseCurrency(baseCurrency);
                if (!exchangeRates.isEmpty()) {
                    return toResponse(baseCurrency, exchangeRates);
                }

            } finally {
                lockManager.releaseLock();
            }
        } else {
            int retry = 50;
            for (int i = 0; i < retry; i++) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                exchangeRates = exchangeRepository.findByBaseCurrency(baseCurrency);
                if (!exchangeRates.isEmpty()) {
                    return toResponse(baseCurrency, exchangeRates);
                }
            }
        }
        throw ErrorCode.EXCHANGE_RATE_NOT_FOUND.commonException();
    }

    private ExchangeRateResponse toResponse(String baseCurrency, List<ExchangeRate> exchangeRates) {
        Map<String, Double> conversionRates = exchangeRates.get(0).getExchangeRates();
        return ExchangeRateResponse.builder()
                .baseCurrency(baseCurrency)
                .conversionRates(conversionRates)
                .build();
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

    @Cacheable(value = "exchangeRates", key = "'rate::' + #baseCurrency + '::' + #targetCurrency", unless = "#result == null")
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
