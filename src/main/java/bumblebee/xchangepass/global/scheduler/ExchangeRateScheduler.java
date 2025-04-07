package bumblebee.xchangepass.global.scheduler;

import bumblebee.xchangepass.domain.exchangeRate.service.ExchangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExchangeRateScheduler {
    private final ExchangeService exchangeService;

    @Scheduled(cron = "0 0 0 * * ?") // 매일 자정 실행
    public void scheduledFetchAndSaveAllExchangeRates() {
        exchangeService.fetchAndSaveAllExchangeRates();
    }
}
