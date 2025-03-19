package bumblebee.xchangepass.domain.exchangeRate.service;

import bumblebee.xchangepass.domain.exchangeRate.dto.response.ExchangeRateResponse;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRate;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateTemp;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRateTempRepository;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;


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

//    @Autowired
//    private JdbcTemplate jdbcTemplate;
//
//    @Autowired
//    private EntityManager entityManager;
//
//
//
////    @Mock
////    private ExchangeRepository exchangeRepository;
////
////    @InjectMocks
////    private ExchangeService service;
//
//    @Test
//    @DisplayName("데이터 베이스 저장 확인")
//    @Transactional
//    public void Test1() {
//        Map<String, Double> map = new HashMap<>();
//        String baseCurrency = "USD";
//
//        map.put("AED", 1.0);
//        map.put("EUR", 2.0);
//
//        ExchangeRateResponse exchangeRateResponse = new ExchangeRateResponse(baseCurrency, map);
//        service.saveRatesToTempDB(baseCurrency, exchangeRateResponse);
//
//        List<ExchangeRateTemp> all = rateTempRepository.findAll();
//
//        Map<String, Double> exchangeRates = all.get(0).getExchangeRates();
//
//        assertTrue(exchangeRates.containsKey("AED"));
//        assertTrue(exchangeRates.containsKey("EUR"));
//    }
//
//
//    @Test
//    @DisplayName("환율 조회하기")
//    public void Test3(){
//        String baseCurrency = "USD";
//
//        ExchangeRateResponse exchangeRateResponse = service.fetchExchangeRates(baseCurrency);
//
//        assertEquals("USD",exchangeRateResponse.baseCurrency());
//        assertEquals(162,exchangeRateResponse.conversionRates().size());
//    }
//
//    @Test
//    @DisplayName("존재 하지 않는 환율 조회하기")
//    public void Test4(){
//        String baseCurrency = "QASD";
//                assertThrows(ErrorCode.EXCHANGE_RATE_NOT_FOUND.commonException().getClass(),
//                        () -> service.fetchExchangeRates(baseCurrency));
//    }
//
//    @Test
//    @DisplayName("없는 나라 환율 찾기")
//    public void Test6(){
//        String baseCurrency = "USD";
//        String tageCurrency = "KRWW";
//
//        assertThrows(ErrorCode.EXCHANGE_RATE_FOR_COUNTRY.commonException().getClass(),
//                () -> service.getExchangeRateForCountry(baseCurrency, tageCurrency));
//    }
//    @Test
//    @DisplayName("나라 개수 확인")
//    public void Test7() throws InterruptedException {
//
//        CompletableFuture.runAsync( () -> service.fetchAndSaveAllExchangeRates()).join();
//        List<ExchangeRate> all = exchangeRepository.findAll();
//        assertEquals(162, all.get(0).getExchangeRates().size());
//    }


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
            service.fetchAndSaveAllExchangeRates();
        ExchangeRateResponse response = service.getExchangeRateAll("USD");
        assertThat(response).isNotNull();
        assertThat(response.conversionRates().get("KRW")).isEqualTo(1400.0);


        exchangeRateTransactionService.swapExchangeRateTables();


        ExchangeRateResponse updatedResponse = service.getExchangeRateAll("USD");
        assertThat(updatedResponse).isNotNull();
        assertThat(updatedResponse.conversionRates().get("KRW")).isEqualTo(1450.3019);

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
        assertThat(initialResponse.conversionRates().get("KRW")).isEqualTo(1450.3019);

    }
}