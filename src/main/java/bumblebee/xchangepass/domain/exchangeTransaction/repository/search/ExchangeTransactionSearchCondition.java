package bumblebee.xchangepass.domain.exchangeTransaction.repository.search;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Schema(description = "환전 거래내역 조회 요청 객체")
public record ExchangeTransactionSearchCondition(
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
