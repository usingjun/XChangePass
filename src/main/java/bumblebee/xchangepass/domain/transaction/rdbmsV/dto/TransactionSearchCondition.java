package bumblebee.xchangepass.domain.transaction.rdbmsV.dto;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import bumblebee.xchangepass.domain.transaction.rdbmsV.entity.TransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Schema(description = "거래내역 조회 요청 객체")
public record TransactionSearchCondition(

        @Schema(description = "거래 종류", example = "Wallet")
        TransactionType transactionType,

        @Schema(description = "카드 거래 종류", example = "Payment")
        CardTransactionType cardTransactionType,

        @Schema(description = "지갑 거래 종류", example = "TRANSFER")
        WalletTransactionType walletTransactionType,

        @Schema(description = "조회 시작 일자", example = "2025-03-26T00:00:00")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime startDate,

        @Schema(description = "조회 종료 일자", example = "2025-03-26T00:00:00")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime endDate,

        @Schema(description = "조회 커서", example = "2025-03-26T00:00:00")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime cursor
) {
}
