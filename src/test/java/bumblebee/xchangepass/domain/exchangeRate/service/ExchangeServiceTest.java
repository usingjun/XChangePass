package bumblebee.xchangepass.domain.exchangeRate.service;

import bumblebee.xchangepass.domain.exchangeRate.dto.response.ExchangeRateResponse;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRate;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateTemp;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRateTempRepository;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import com.sun.management.OperatingSystemMXBean;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


@SpringBootTest
class ExchangeServiceTest {

    @Autowired
    private ExchangeService service;

    @Autowired
    private ExchangeRateTransactionService exchangeRateTransactionService;

    @Autowired
    private ExchangeRepository exchangeRepository;

    @Autowired
    private ExchangeRateTempRepository rateTempRepository;

    @Autowired
    private ExchangeRateTempRepository exchangeRateTempRepository;

    @Autowired
    CacheManager cacheManager;

    @AfterEach
    void clearCache() {
        Objects.requireNonNull(cacheManager.getCache("exchangeRates")).clear();
    }
    @Test
    @DisplayName("데이터 베이스 저장 확인")
    @Transactional
    public void Test1() {
        Map<String, Double> map = new HashMap<>();
        String baseCurrency = "USD";

        map.put("AED", 1.0);
        map.put("EUR", 2.0);

        ExchangeRateResponse exchangeRateResponse = new ExchangeRateResponse(baseCurrency, map);
        service.saveRatesToTempDB(baseCurrency, exchangeRateResponse);

        List<ExchangeRateTemp> all = rateTempRepository.findAll();

        Map<String, Double> exchangeRates = all.get(0).getExchangeRates();

        Assertions.assertTrue(exchangeRates.containsKey("AED"));
        Assertions.assertTrue(exchangeRates.containsKey("EUR"));
    }


    @Test
    @DisplayName("환율 조회하기")
    public void Test3(){
        String baseCurrency = "USD";

        ExchangeRateResponse exchangeRateResponse = service.fetchExchangeRates(baseCurrency);

        assertEquals("USD",exchangeRateResponse.baseCurrency());
        assertEquals(163,exchangeRateResponse.conversionRates().size());
    }

    @Test
    @DisplayName("존재 하지 않는 환율 조회하기")
    public void Test4(){
        String baseCurrency = "QASD";
                assertThrows(ErrorCode.EXCHANGE_RATE_NOT_FOUND.commonException().getClass(),
                        () -> service.fetchExchangeRates(baseCurrency));
    }

    @Test
    @DisplayName("없는 나라 환율 찾기")
    public void Test6(){
        String baseCurrency = "USD";
        String tageCurrency = "KRWW";

        assertThrows(ErrorCode.EXCHANGE_RATE_FOR_COUNTRY.commonException().getClass(),
                () -> service.getExchangeRateForCountry(baseCurrency, tageCurrency));
    }
    @Test
    @DisplayName("나라 개수 확인")
    public void Test7() throws InterruptedException {

        service.fetchAndSaveAllExchangeRates().join();
        List<ExchangeRate> all = exchangeRepository.findAll();
        assertEquals(163, all.get(0).getExchangeRates().size());

        long startTime = System.currentTimeMillis();
        service.fetchAndSaveAllExchangeRates().join(); // 비동기 작업이 끝날 때까지 기다림
        long endTime = System.currentTimeMillis();
        System.out.println("fetchAndSaveAllExchangeRates total execution time: " + (endTime - startTime) + "ms");
    }

    @Test
    @DisplayName("나라 개수 확인 및 실행 시간 및 CPU 사용량 측정")
    public void Test8() throws InterruptedException {
        // 실행 시간 측정 시작
        long startTime = System.currentTimeMillis();

        // CPU 사용량 측정 (시작 전)
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        Thread.sleep(500); // 샘플링 시간 확보
        double cpuBefore = osBean.getProcessCpuLoad() * 100; // JVM 프로세스 CPU 사용률 (%)
        double systemCpuBefore = osBean.getCpuLoad() * 100; // 시스템 전체 CPU 사용률 (%)

        // fetchAndSaveAllExchangeRates 실행 및 완료 대기
        service.fetchAndSaveAllExchangeRates().join();

        // 실행 시간 측정 종료
        long endTime = System.currentTimeMillis();

        // CPU 사용량 측정 (작업 완료 후)
        Thread.sleep(500); // 처리 후 CPU 사용이 반영되도록 잠깐 대기
        double cpuAfter = osBean.getProcessCpuLoad() * 100;
        double systemCpuAfter = osBean.getCpuLoad() * 100;

        // 실행 시간 출력
        System.out.println("fetchAndSaveAllExchangeRates total execution time: " + (endTime - startTime) + "ms");

        // CPU 사용량 출력
        System.out.println("JVM CPU Usage Before: " + String.format("%.2f", cpuBefore) + "%");
        System.out.println("JVM CPU Usage After: " + String.format("%.2f", cpuAfter) + "%");
        System.out.println("System CPU Usage Before: " + String.format("%.2f", systemCpuBefore) + "%");
        System.out.println("System CPU Usage After: " + String.format("%.2f", systemCpuAfter) + "%");

        // DB에서 저장된 환율 정보 확인
        List<ExchangeRate> all = exchangeRepository.findAll();
        int cpuCores = Runtime.getRuntime().availableProcessors();
        System.out.println("Available CPU Cores: " + cpuCores);

    }

    @Test
    @DisplayName("최적 스레드 수 계산 테스트 - 외부 API 호출 기준")
    void 스레드수계산테스트() {
        int cores = Runtime.getRuntime().availableProcessors();

        // 1. 응답 시간 측정 (전체 fetch → 외부 API 포함)
        long responseStart = System.currentTimeMillis();
        service.fetchAndSaveExchangeRate("USD"); // 단일 외부 API 호출
        long responseEnd = System.currentTimeMillis();
        long responseTime = responseEnd - responseStart;

        // 2. CPU 처리 시간 측정 (외부 API 제외한 내부 처리)
        Map<String, Double> rates = new HashMap<>();
        rates.put("KRW", 1300.00);

        ExchangeRate dummyRate = ExchangeRate.builder()
                .baseCurrency("USD")
                .rate(rates)
                .build();
        long cpuStart = System.nanoTime();
        exchangeRepository.save(dummyRate); // 저장 로직만 측정
        long cpuEnd = System.nanoTime();
        long cpuTime = (cpuEnd - cpuStart) / 1_000_000; // ms 단위

        // 3. 최적 스레드 수 계산
        int optimalThreads = (int)((double) responseTime / cpuTime * cores);

        // 4. 출력
        System.out.println("CPU 코어 수: " + cores);
        System.out.println("단일 외부 API 응답 시간: " + responseTime + "ms");
        System.out.println("내부 처리(CPU) 시간: " + cpuTime + "ms");
        System.out.println("계산된 최적 스레드 수: " + optimalThreads + "개");
    }
    @Test
    @DisplayName("비동기 업데이트시 기존 데이터를 가져오므로 사용자 블로킹 발생 안함")
    void testFetchExchangeRatesWhileUpdating() throws ExecutionException, InterruptedException {

        ExchangeRate initialExchangeRate = ExchangeRate.builder()
                .baseCurrency("USD")
                .rate(Map.of("KRW", 1400.0))
                .build();

        ExchangeRateResponse build = ExchangeRateResponse.builder()
                .baseCurrency(initialExchangeRate.getBaseCurrency())
                .conversionRates(initialExchangeRate.getExchangeRates())
                .build();

        Map<String, Double> rates = build.conversionRates();
        ExchangeRateTemp exchangeRateTemp = ExchangeRateTemp.builder()
                .baseCurrency(build.baseCurrency())
                .rate(rates)
                .build();
        exchangeRateTempRepository.save(exchangeRateTemp);

        exchangeRateTransactionService.swapExchangeRateTables();
        CompletableFuture<Void> voidCompletableFuture = service.fetchAndSaveAllExchangeRates();
        ExchangeRateResponse response = service.getExchangeRateAll("USD");
        assertThat(response).isNotNull();
        assertThat(response.conversionRates().get("KRW")).isEqualTo(1400.0);


        voidCompletableFuture.get();


        ExchangeRateResponse updatedResponse = service.getExchangeRateAll("USD");
        assertThat(updatedResponse).isNotNull();
        assertThat(updatedResponse.conversionRates().get("KRW")).isEqualTo(1472.8846);

    }
    @Test
    @DisplayName("동기식 업데이트 할시 기존 데이터 못가져오는 경우")
    void testFetchExchangeRatesWhileUpdating2() throws ExecutionException, InterruptedException {


        ExchangeRate initialExchangeRate = ExchangeRate.builder()
                .baseCurrency("USD")
                .rate(Map.of("KRW", 1400.0))
                .build();

        ExchangeRateResponse build = ExchangeRateResponse.builder()
                .baseCurrency(initialExchangeRate.getBaseCurrency())
                .conversionRates(initialExchangeRate.getExchangeRates())
                .build();

        Map<String, Double> rates = build.conversionRates();
        ExchangeRateTemp exchangeRateTemp = ExchangeRateTemp.builder()
                .baseCurrency(build.baseCurrency())
                .rate(rates)
                .build();
        exchangeRateTempRepository.save(exchangeRateTemp);


        exchangeRateTransactionService.swapExchangeRateTables();

        service.fetchAndSaveAllExchangeRates().get();


        ExchangeRateResponse initialResponse = service.getExchangeRateAll("USD");
        assertThat(initialResponse).isNotNull();
        assertThat(initialResponse.conversionRates().get("KRW")).isEqualTo(1472.8846);

    }
}