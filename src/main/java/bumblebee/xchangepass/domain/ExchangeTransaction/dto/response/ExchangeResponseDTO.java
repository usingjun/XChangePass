package bumblebee.xchangepass.domain.ExchangeTransaction.dto.response;


import bumblebee.xchangepass.domain.ExchangeTransaction.entitiy.ExchangeTransaction;
import bumblebee.xchangepass.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record ExchangeResponseDTO (
        @Schema(description = "거래 ID", example = "123456789")
        Long transactionId,

        @Schema(description = "사용자 ID", example = "1001")
        User userId,

        @Schema(description = "환전할 통화", example = "USD")
        String fromCurrency,

        @Schema(description = "환전 받을 통화", example = "KRW")
        String toCurrency,

        @Schema(description = "환율", example = "1300.50")
        BigDecimal exchangeRate,

        @Schema(description = "환전할 금액", example = "100")
        BigDecimal amount,

        @Schema(description = "실제 수령 금액", example = "130050")
        BigDecimal receivedAmount,

        @Schema(description = "거래 상태 (예: PENDING, COMPLETED, FAILED)", example = "COMPLETED")
        String status,

        @Schema(description = "거래 생성 시간", example = "2024-03-16T10:15:30")
        LocalDateTime createdAt
){
    public static ExchangeResponseDTO toEntity(ExchangeTransaction exchangeTransaction){
        return ExchangeResponseDTO.builder()
                .transactionId(exchangeTransaction.getExchangeTransactionId())
                .userId(exchangeTransaction.getUserId())
                .fromCurrency(exchangeTransaction.getFromCurrency())
                .toCurrency(exchangeTransaction.getToCurrency())
                .exchangeRate(exchangeTransaction.getExchangeRate())
                .amount(exchangeTransaction.getAmount())
                .receivedAmount(exchangeTransaction.getReceivedAmount())
                .status(exchangeTransaction.getStatus().name())
                .build();
    }
}
