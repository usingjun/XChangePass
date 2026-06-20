package bumblebee.xchangepass.domain.wallet.transaction.repository;

import bumblebee.xchangepass.domain.transaction.repository.WalletTransactionRow;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface WalletTransactionRepositoryCustom {
    List<WalletTransactionRow> findRecentSentByUser(
            Long userId,
            WalletTransactionType walletType,
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

    List<WalletTransactionRow> findRecentReceivedByUser(
            Long userId,
            WalletTransactionType walletType,
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
