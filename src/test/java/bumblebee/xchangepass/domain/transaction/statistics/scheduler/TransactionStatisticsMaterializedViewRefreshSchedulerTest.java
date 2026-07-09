package bumblebee.xchangepass.domain.transaction.statistics.scheduler;

import bumblebee.xchangepass.domain.transaction.statistics.service.TransactionStatisticsService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class TransactionStatisticsMaterializedViewRefreshSchedulerTest {

    private final TransactionStatisticsService statisticsService = mock(TransactionStatisticsService.class);
    private final TransactionStatisticsMaterializedViewRefreshScheduler scheduler =
            new TransactionStatisticsMaterializedViewRefreshScheduler(statisticsService);

    @Test
    void requestsScheduledMaterializedViewRefresh() {
        scheduler.refresh();

        verify(statisticsService).refreshMaterializedViewForScheduledRun();
        verifyNoMoreInteractions(statisticsService);
    }

    @Test
    void allowsNextRefreshAfterFailure() {
        doThrow(new IllegalStateException("refresh failed"))
                .doNothing()
                .when(statisticsService)
                .refreshMaterializedViewForScheduledRun();

        assertThatThrownBy(scheduler::refresh)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("refresh failed");

        scheduler.refresh();

        verify(statisticsService, org.mockito.Mockito.times(2)).refreshMaterializedViewForScheduledRun();
    }
}
