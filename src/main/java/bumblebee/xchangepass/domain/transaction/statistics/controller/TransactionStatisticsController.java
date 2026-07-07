package bumblebee.xchangepass.domain.transaction.statistics.controller;

import bumblebee.xchangepass.domain.transaction.statistics.dto.TransactionMonthlyStatisticsResponse;
import bumblebee.xchangepass.domain.transaction.statistics.dto.TransactionStatisticsMode;
import bumblebee.xchangepass.domain.transaction.statistics.metrics.TransactionStatisticsTimingRecorder;
import bumblebee.xchangepass.domain.transaction.statistics.service.TransactionStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class TransactionStatisticsController {

    private final TransactionStatisticsService statisticsService;

    @Operation(summary = "사용자별 월별 거래 통계 조회", description = "조회 모드에 따라 원본 거래 GROUP BY 또는 Materialized View로 월별 거래 금액과 건수를 조회합니다.")
    @GetMapping("/api/v1/transactions/statistics/monthly")
    @ResponseStatus(HttpStatus.OK)
    public List<TransactionMonthlyStatisticsResponse> monthlyStatistics(
            @RequestParam Long userId,
            @RequestParam String fromMonth,
            @RequestParam String toMonth,
            @RequestParam(defaultValue = "GROUP_BY") TransactionStatisticsMode mode
    ) {
        return TransactionStatisticsTimingRecorder.configured().recordRequest(
                mode,
                () -> statisticsService.findMonthlyStatistics(
                        userId,
                        YearMonth.parse(fromMonth),
                        YearMonth.parse(toMonth),
                        mode
                )
        );
    }
}
