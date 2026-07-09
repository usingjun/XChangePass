package bumblebee.xchangepass.domain.transaction.statistics.scheduler;

import bumblebee.xchangepass.domain.transaction.statistics.service.TransactionStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "transaction.statistics.materialized-view.refresh.enabled",
        havingValue = "true"
)
public class TransactionStatisticsMaterializedViewRefreshScheduler {

    private final TransactionStatisticsService statisticsService;
    private final AtomicBoolean refreshing = new AtomicBoolean();

    @Scheduled(
            fixedDelayString = "${transaction.statistics.materialized-view.refresh.fixed-delay:300000}",
            initialDelayString = "${transaction.statistics.materialized-view.refresh.initial-delay:60000}"
    )
    public void refresh() {
        if (!refreshing.compareAndSet(false, true)) {
            log.warn("Skipping transaction statistics materialized view refresh because it is already running");
            return;
        }

        long startedAt = System.nanoTime();
        try {
            statisticsService.refreshMaterializedViewForScheduledRun();
            log.info(
                    "Transaction statistics materialized view refresh completed in {} ms",
                    (System.nanoTime() - startedAt) / 1_000_000
            );
        } catch (RuntimeException exception) {
            log.error("Transaction statistics materialized view refresh failed", exception);
            throw exception;
        } finally {
            refreshing.set(false);
        }
    }
}
