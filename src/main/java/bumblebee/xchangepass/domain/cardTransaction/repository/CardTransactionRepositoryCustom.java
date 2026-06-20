package bumblebee.xchangepass.domain.cardTransaction.repository;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import bumblebee.xchangepass.domain.transaction.repository.CardTransactionRow;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface CardTransactionRepositoryCustom {
    List<CardTransactionRow> findRecentForUser(
            Long userId,
            CardTransactionType cardType,
            String merchantName,
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
