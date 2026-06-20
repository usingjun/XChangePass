package bumblebee.xchangepass.domain.transaction.dto.cond;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import bumblebee.xchangepass.domain.transaction.entity.TransactionDirection;
import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

@Schema(description = "거래내역 조회 요청 객체")
public record TransactionSearchCondition(

        @Schema(description = "거래 종류", example = "Wallet")
        TransactionType transactionType,

        @Schema(description = "카드 거래 종류", example = "Payment")
        CardTransactionType cardTransactionType,

        @Schema(description = "카드 판매자명 검색어", example = "STARBUCKS")
        String merchantName,

        @Schema(description = "최소 거래 금액", example = "10000")
        BigDecimal minAmount,

        @Schema(description = "최대 거래 금액", example = "50000")
        BigDecimal maxAmount,

        @Schema(description = "거래 통화", example = "USD")
        String currency,

        @Schema(description = "지갑 거래 방향", example = "SENT")
        TransactionDirection direction,

        @Schema(description = "지갑 거래 종류", example = "TRANSFER")
        WalletTransactionType walletTransactionType,

        @Schema(description = "조회 시작 일자", example = "2025-03-26T00:00:00")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime startDate,

        @Schema(description = "조회 종료 일자", example = "2025-03-26T00:00:00")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime endDate,

        @Schema(description = "서버가 반환한 다음 페이지 커서")
        String cursor
) {
    public TransactionSearchCondition {
        merchantName = merchantName == null || merchantName.isBlank() ? null : merchantName.trim();
        currency = currency == null || currency.isBlank() ? null : currency.trim().toUpperCase(Locale.ROOT);
        direction = direction == null ? TransactionDirection.ALL : direction;
    }
}
