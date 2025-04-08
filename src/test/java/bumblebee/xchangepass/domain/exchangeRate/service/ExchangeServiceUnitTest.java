package bumblebee.xchangepass.domain.exchangeRate.service;

import bumblebee.xchangepass.domain.exchangeRate.dto.response.ExchangeRateResponse;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRate;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRateTempRepository;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRepository;
import bumblebee.xchangepass.domain.exchangeRate.util.ExchangeRateLockManager;
import bumblebee.xchangepass.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExchangeServiceUnitTest {

    @InjectMocks
    private ExchangeService exchangeService;

    @Mock
    private ExchangeRepository exchangeRepository;

    @Mock
    private ExchangeRateTransactionService exchangeTransactionService;

    @Mock
    private ExchangeRateTempRepository exchangeRateTempRepository;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ExchangeRateLockManager lockManager;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        exchangeService = new ExchangeService(
                Executors.newSingleThreadExecutor(),
                exchangeRepository,
                exchangeRateTempRepository,
                exchangeTransactionService,
                applicationContext,
                lockManager,
                cacheManager,
                restTemplate
        );
    }

    @Test
    @DisplayName("기존 환율이 존재하면 정상 응답을 반환한다")
    void test1() {
        // given
        String baseCurrency = "USD";
        String targetCurrency = "KRW";
        Map<String, Double> mockRates = Map.of(targetCurrency, 1350.0);

        ExchangeRate mockRate = ExchangeRate.builder()
                .baseCurrency(baseCurrency)
                .rate(mockRates)
                .build();

        when(exchangeRepository.findByBaseCurrencyAndKey(baseCurrency, targetCurrency))
                .thenReturn(List.of(mockRate));

        // when
        ExchangeRateResponse result = exchangeService.getExchangeRateForCountry(baseCurrency, targetCurrency);

        // then
        assertNotNull(result);
        assertEquals(baseCurrency, result.baseCurrency());
        assertEquals(1350.0, result.conversionRates().get(targetCurrency));
    }

    @Test
    @DisplayName("환율 정보가 없으면 예외를 던진다")
    void test2() {
        // given
        String baseCurrency = "USD";
        String targetCurrency = "ABC";

        // when
        when(exchangeRepository.findByBaseCurrencyAndKey(baseCurrency, targetCurrency))
                .thenReturn(List.of());

        // then
        assertThrows(
                ErrorCode.EXCHANGE_RATE_FOR_COUNTRY.commonException().getClass(),
                () -> exchangeService.getExchangeRateForCountry(baseCurrency, targetCurrency)
        );
    }

    @Test
    @DisplayName("fetchExchangeRates는 RestTemplate을 통해 환율 정보를 정상적으로 반환한다")
    void test3() {
        // given
        String baseCurrency = "USD";
        String apiKey = "dummy-key";
        ReflectionTestUtils.setField(exchangeService, "authkey", apiKey);

        RestTemplate restTemplateMock = mock(RestTemplate.class);
        ExchangeRateResponse dummyResponse = new ExchangeRateResponse(baseCurrency, Map.of("KRW", 1350.0));

        when(restTemplateMock.getForObject(anyString(), eq(ExchangeRateResponse.class)))
                .thenReturn(dummyResponse);

        ReflectionTestUtils.setField(exchangeService, "restTemplate", restTemplateMock);

        // when
        ExchangeRateResponse result = exchangeService.fetchExchangeRates(baseCurrency);

        // then
        assertNotNull(result); 
        assertEquals("USD", result.baseCurrency());
        assertEquals(1350.0, result.conversionRates().get("KRW"));
    }


}
