package bumblebee.xchangepass.domain.exchangeTransaction.repository;

import bumblebee.xchangepass.domain.transaction.repository.ExchangeTransactionRow;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface ExchangeTransactionRepositoryCustom {
    List<ExchangeTransactionRow> findRecentForUser(
            Long userId,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String currency,
            LocalDateTime startDate,
            LocalDateTime endDate,
            LocalDateTime cursor,
            boolean includeCursorTime,
            Long cursorTransactionId,
            Pageable pageable
    );
}
