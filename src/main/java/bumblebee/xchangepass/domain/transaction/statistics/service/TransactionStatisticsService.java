package bumblebee.xchangepass.domain.transaction.statistics.service;

import bumblebee.xchangepass.domain.transaction.statistics.dto.TransactionMonthlyStatisticsResponse;
import bumblebee.xchangepass.domain.transaction.statistics.dto.TransactionStatisticsMode;
import bumblebee.xchangepass.domain.transaction.statistics.dto.TransactionStatisticsRow;
import bumblebee.xchangepass.domain.transaction.statistics.repository.TransactionStatisticsQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionStatisticsService {

    private final TransactionStatisticsQueryRepository queryRepository;

    @Transactional(readOnly = true)
    public List<TransactionMonthlyStatisticsResponse> findMonthlyStatistics(
            Long userId, YearMonth fromMonth, YearMonth toMonth
    ) {
        return findMonthlyStatistics(userId, fromMonth, toMonth, TransactionStatisticsMode.GROUP_BY);
    }

    @Transactional(readOnly = true)
    public List<TransactionMonthlyStatisticsResponse> findMonthlyStatistics(
            Long userId, YearMonth fromMonth, YearMonth toMonth, TransactionStatisticsMode mode
    ) {
        validate(userId, fromMonth, toMonth);
        TransactionStatisticsMode queryMode = mode == null ? TransactionStatisticsMode.GROUP_BY : mode;
        LocalDateTime dataAsOf = LocalDateTime.now();
        return findRows(userId, fromMonth, toMonth, queryMode).stream()
                .map(row -> row.toResponse(dataAsOf))
                .toList();
    }

    public void refreshMaterializedView() {
        queryRepository.refreshMaterializedView();
    }

    public void refreshMaterializedViewConcurrently() {
        queryRepository.refreshMaterializedViewConcurrently();
    }

    @Transactional
    public void refreshMonthlySummary(YearMonth fromMonth, YearMonth toMonth) {
        validateMonthRange(fromMonth, toMonth);
        queryRepository.refreshMonthlySummary(fromMonth, toMonth);
    }

    private List<TransactionStatisticsRow> findRows(
            Long userId, YearMonth fromMonth, YearMonth toMonth, TransactionStatisticsMode mode
    ) {
        return switch (mode) {
            case GROUP_BY -> queryRepository.findMonthlyStatistics(userId, fromMonth, toMonth);
            case MATERIALIZED_VIEW -> queryRepository.findMonthlyStatisticsFromMaterializedView(
                    userId, fromMonth, toMonth
            );
            case SUMMARY -> queryRepository.findMonthlyStatisticsFromSummary(userId, fromMonth, toMonth);
        };
    }

    private void validate(Long userId, YearMonth fromMonth, YearMonth toMonth) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        validateMonthRange(fromMonth, toMonth);
    }

    private void validateMonthRange(YearMonth fromMonth, YearMonth toMonth) {
        if (fromMonth == null) {
            throw new IllegalArgumentException("fromMonth is required");
        }
        if (toMonth == null) {
            throw new IllegalArgumentException("toMonth is required");
        }
        if (fromMonth.isAfter(toMonth)) {
            throw new IllegalArgumentException("fromMonth must be before or equal to toMonth");
        }
    }
}
