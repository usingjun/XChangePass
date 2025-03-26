package bumblebee.xchangepass.domain.wallet.transaction.dto.request;

import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionStatus;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Schema(description = "거래내역 조회 요청 객체")
public record WalletTransactionSearchCondition(

        @Schema(description = "거래 종류", example = "TRANSFER")
        WalletTransactionType transactionType,

        @Schema(description = "거래 완료 상황", example = "PENDING")
        WalletTransactionStatus status,

        @Schema(description = "조회 시작 일자", example = "2025-03-26T00:00:00")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime startDate,

        @Schema(description = "조회 종료 일자", example = "2025-03-26T00:00:00")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime endDate
) {
}
