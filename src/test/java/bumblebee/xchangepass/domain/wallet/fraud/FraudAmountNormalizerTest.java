package bumblebee.xchangepass.domain.wallet.fraud;

import bumblebee.xchangepass.domain.exchangeRate.service.ExchangeService;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudAmountNormalizer;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudPolicyProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FraudAmountNormalizerTest {

    private final ExchangeService exchangeService = mock(ExchangeService.class);
    private final FraudPolicyProperties properties = new FraudPolicyProperties();
    private final FraudAmountNormalizer normalizer = new FraudAmountNormalizer(exchangeService, properties);

    @Test
    void baseCurrencyDoesNotRequestExchangeRate() {
        BigDecimal result = normalizer.normalize(new BigDecimal("10000"), Currency.getInstance("KRW"));

        assertThat(result).isEqualByComparingTo("10000");
        verifyNoInteractions(exchangeService);
    }

    @Test
    void foreignCurrencyIsConvertedToConfiguredBaseCurrency() {
        Currency usd = Currency.getInstance("USD");
        Currency krw = Currency.getInstance("KRW");
        when(exchangeService.getExchangeMoney(usd, krw, new BigDecimal("10")))
                .thenReturn(new BigDecimal("14000"));

        BigDecimal result = normalizer.normalize(new BigDecimal("10"), usd);

        assertThat(result).isEqualByComparingTo("14000");
    }
}
