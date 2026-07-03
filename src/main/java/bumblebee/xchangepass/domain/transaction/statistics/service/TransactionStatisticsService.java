package bumblebee.xchangepass.domain.transaction.statistics.service;

import bumblebee.xchangepass.domain.transaction.statistics.dto.TransactionMonthlyStatisticsResponse;
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
        validate(userId, fromMonth, toMonth);
        LocalDateTime dataAsOf = LocalDateTime.now();
        return queryRepository.findMonthlyStatistics(userId, fromMonth, toMonth).stream()
                .map(row -> row.toResponse(dataAsOf))
                .toList();
    }

    private void validate(Long userId, YearMonth fromMonth, YearMonth toMonth) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
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
