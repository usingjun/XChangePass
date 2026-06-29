package bumblebee.xchangepass.domain.exchangeRate;

import bumblebee.xchangepass.domain.exchangeRate.dto.response.ExchangeRateResponse;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRate;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncFailureType;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncHistory;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncSnapshot;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateTemp;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRateTempRepository;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRepository;
import bumblebee.xchangepass.domain.exchangeRate.service.ExchangeRateResponseValidator;
import bumblebee.xchangepass.domain.exchangeRate.service.ExchangeRateSyncRecordService;
import bumblebee.xchangepass.domain.exchangeRate.service.ExchangeRateTransactionService;
import bumblebee.xchangepass.domain.exchangeRate.service.ExchangeService;
import bumblebee.xchangepass.domain.exchangeRate.service.ExchangeRateValidationResult;
import bumblebee.xchangepass.domain.exchangeRate.util.Country;
import bumblebee.xchangepass.domain.exchangeRate.util.ExchangeRateLockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeServiceSafeSyncTest {

    @Mock
    private ExchangeRepository exchangeRepository;

    @Mock
    private ExchangeRateTempRepository exchangeRateTempRepository;

    @Mock
    private ExchangeRateTransactionService exchangeRateTransactionService;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ExchangeRateLockManager lockManager;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ExchangeRateSyncRecordService recordService;

    private ExchangeRateSyncHistory history;
    private ExchangeService exchangeService;

    @BeforeEach
    void setUp() {
        history = new ExchangeRateSyncHistory(Country.create().size());
        Executor directExecutor = Runnable::run;
        exchangeService = new ExchangeService(
                directExecutor,
                exchangeRepository,
                exchangeRateTempRepository,
                exchangeRateTransactionService,
                applicationContext,
                lockManager,
                cacheManager,
                restTemplate,
                new ExchangeRateResponseValidator(),
                recordService
        );
        when(recordService.start(Country.create().size())).thenReturn(history);
        when(recordService.recordReceivedSnapshot(any(), anyString(), any()))
                .thenAnswer(invocation -> ExchangeRateSyncSnapshot.received(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        "{\"base_code\":\"" + invocation.getArgument(1) + "\"}",
                        "hash"
                ));
        lenient().when(exchangeRateTempRepository.save(any(ExchangeRateTemp.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void swapsAndCompletesHistoryWhenAllExchangeRatesAreValid() {
        when(restTemplate.getForObject(anyString(), eq(ExchangeRateResponse.class)))
                .thenAnswer(invocation -> validResponse(baseCurrencyFrom(invocation.getArgument(0))));

        exchangeService.fetchAndSaveAllExchangeRates().join();

        verify(exchangeRateTransactionService).swapExchangeRateTables();
        verify(recordService).complete(history, Country.create().size(), true);
        verify(recordService, never()).partialFail(any(), anyInt(), anyInt(), anyString());
        verify(recordService, never()).fail(any(), anyString());
    }

    @Test
    void doesNotSwapAndPartiallyFailsWhenAnyExchangeRateResponseIsInvalid() {
        when(restTemplate.getForObject(anyString(), eq(ExchangeRateResponse.class)))
                .thenAnswer(invocation -> {
                    String baseCurrency = baseCurrencyFrom(invocation.getArgument(0));
                    if ("JPY".equals(baseCurrency)) {
                        return response(baseCurrency, ratesWithoutKrw());
                    }
                    return validResponse(baseCurrency);
                });

        exchangeService.fetchAndSaveAllExchangeRates().join();

        verify(exchangeRateTransactionService, never()).swapExchangeRateTables();
        verify(recordService).partialFail(history, Country.create().size() - 1, 1, "Some exchange rates failed");
        ArgumentCaptor<ExchangeRateValidationResult> resultCaptor =
                ArgumentCaptor.forClass(ExchangeRateValidationResult.class);
        verify(recordService).recordValidationFailure(eq(history), eq("JPY"), anyString(), resultCaptor.capture());
        assertThat(resultCaptor.getValue().failureType())
                .isEqualTo(ExchangeRateSyncFailureType.MISSING_REQUIRED_CURRENCY);
    }

    @Test
    void doesNotSwapWhenConversionRatesAreNull() {
        when(restTemplate.getForObject(anyString(), eq(ExchangeRateResponse.class)))
                .thenAnswer(invocation -> {
                    String baseCurrency = baseCurrencyFrom(invocation.getArgument(0));
                    if ("JPY".equals(baseCurrency)) {
                        return response(baseCurrency, null);
                    }
                    return validResponse(baseCurrency);
                });

        exchangeService.fetchAndSaveAllExchangeRates().join();

        verify(exchangeRateTransactionService, never()).swapExchangeRateTables();
        verify(recordService).partialFail(history, Country.create().size() - 1, 1, "Some exchange rates failed");
        ArgumentCaptor<ExchangeRateValidationResult> resultCaptor =
                ArgumentCaptor.forClass(ExchangeRateValidationResult.class);
        verify(recordService).recordValidationFailure(eq(history), eq("JPY"), anyString(), resultCaptor.capture());
        assertThat(resultCaptor.getValue().failureType())
                .isEqualTo(ExchangeRateSyncFailureType.MISSING_CONVERSION_RATES);
    }

    @Test
    void doesNotSwapWhenRateValueIsInvalid() {
        when(restTemplate.getForObject(anyString(), eq(ExchangeRateResponse.class)))
                .thenAnswer(invocation -> {
                    String baseCurrency = baseCurrencyFrom(invocation.getArgument(0));
                    if ("JPY".equals(baseCurrency)) {
                        Map<String, Double> rates = validRates();
                        rates.put("KRW", 0.0);
                        return response(baseCurrency, rates);
                    }
                    return validResponse(baseCurrency);
                });

        exchangeService.fetchAndSaveAllExchangeRates().join();

        verify(exchangeRateTransactionService, never()).swapExchangeRateTables();
        verify(recordService).partialFail(history, Country.create().size() - 1, 1, "Some exchange rates failed");
        ArgumentCaptor<ExchangeRateValidationResult> resultCaptor =
                ArgumentCaptor.forClass(ExchangeRateValidationResult.class);
        verify(recordService).recordValidationFailure(eq(history), eq("JPY"), anyString(), resultCaptor.capture());
        assertThat(resultCaptor.getValue().failureType())
                .isEqualTo(ExchangeRateSyncFailureType.INVALID_RATE_VALUE);
    }

    @Test
    void existingActiveExchangeRateIsStillUsedAfterFailedSync() {
        when(restTemplate.getForObject(anyString(), eq(ExchangeRateResponse.class)))
                .thenAnswer(invocation -> {
                    String baseCurrency = baseCurrencyFrom(invocation.getArgument(0));
                    if ("JPY".equals(baseCurrency)) {
                        return response(baseCurrency, ratesWithoutKrw());
                    }
                    return validResponse(baseCurrency);
                });
        when(exchangeRepository.findByBaseCurrencyAndKey("USD", "KRW"))
                .thenReturn(java.util.List.of(ExchangeRate.builder()
                        .baseCurrency("USD")
                        .rate(Map.of("KRW", 1390.1))
                        .build()));

        exchangeService.fetchAndSaveAllExchangeRates().join();
        ExchangeRateResponse activeRate = exchangeService.getExchangeRateForCountry("USD", "KRW");

        verify(exchangeRateTransactionService, never()).swapExchangeRateTables();
        assertThat(activeRate.baseCurrency()).isEqualTo("USD");
        assertThat(activeRate.conversionRates()).containsEntry("KRW", 1390.1);
    }

    @Test
    void doesNotSwapAndRecordsApiFailureWhenAnyExchangeRateCallFails() {
        when(restTemplate.getForObject(anyString(), eq(ExchangeRateResponse.class)))
                .thenAnswer(invocation -> {
                    String baseCurrency = baseCurrencyFrom(invocation.getArgument(0));
                    if ("JPY".equals(baseCurrency)) {
                        throw new RuntimeException("quota exceeded");
                    }
                    return validResponse(baseCurrency);
                });

        exchangeService.fetchAndSaveAllExchangeRates().join();

        verify(exchangeRateTransactionService, never()).swapExchangeRateTables();
        verify(recordService).partialFail(history, Country.create().size() - 1, 1, "Some exchange rates failed");
        verify(recordService).recordFailedSnapshot(eq(history), eq("JPY"), anyString());
        verify(recordService).recordFailure(
                eq(history),
                eq("JPY"),
                eq(ExchangeRateSyncFailureType.API_ERROR),
                eq(null),
                anyString(),
                eq(true)
        );
    }

    @Test
    void doesNotSwapAndRecordsSaveFailureWhenTempSaveFails() {
        when(restTemplate.getForObject(anyString(), eq(ExchangeRateResponse.class)))
                .thenAnswer(invocation -> validResponse(baseCurrencyFrom(invocation.getArgument(0))));
        when(exchangeRateTempRepository.save(any(ExchangeRateTemp.class)))
                .thenAnswer(invocation -> {
                    ExchangeRateTemp temp = invocation.getArgument(0);
                    if ("JPY".equals(temp.getBaseCurrency())) {
                        throw new IllegalStateException("save failed");
                    }
                    return temp;
                });

        exchangeService.fetchAndSaveAllExchangeRates().join();

        verify(exchangeRateTransactionService, never()).swapExchangeRateTables();
        verify(recordService).partialFail(history, Country.create().size() - 1, 1, "Some exchange rates failed");
        verify(recordService).recordFailure(
                eq(history),
                eq("JPY"),
                eq(ExchangeRateSyncFailureType.SAVE_FAILED),
                anyString(),
                anyString(),
                eq(true)
        );
    }

    @Test
    void recordsSwapFailureWhenSwapFailsAfterAllResponsesSucceeded() {
        when(restTemplate.getForObject(anyString(), eq(ExchangeRateResponse.class)))
                .thenAnswer(invocation -> validResponse(baseCurrencyFrom(invocation.getArgument(0))));
        doThrow(new IllegalStateException("swap failed"))
                .when(exchangeRateTransactionService)
                .swapExchangeRateTables();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> exchangeService.fetchAndSaveAllExchangeRates().join()
                )
                .hasRootCauseMessage("swap failed");

        verify(recordService).recordFailure(
                eq(history),
                eq("ALL"),
                eq(ExchangeRateSyncFailureType.SWAP_FAILED),
                eq(null),
                eq("swap failed"),
                eq(true)
        );
        verify(recordService).fail(history, "swap failed");
    }

    private String baseCurrencyFrom(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private ExchangeRateResponse validResponse(String baseCurrency) {
        return response(baseCurrency, validRates());
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

    private Map<String, Double> ratesWithoutKrw() {
        Map<String, Double> rates = validRates();
        rates.remove("KRW");
        return rates;
    }
}
