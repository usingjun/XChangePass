package bumblebee.xchangepass.domain.ExchangeTransaction.dto.response;


import bumblebee.xchangepass.domain.ExchangeTransaction.entitiy.ExchangeTransaction;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record ExchangeResponseDTO (
     Long transactionId,   // 거래 ID
     Long userId,   // 사용자 ID
     String fromCurrency,  // 환전 전 통화
     String toCurrency,    // 환전 후 통화
     BigDecimal exchangeRate,  // 적용된 환율
     BigDecimal amount,    // 환전할 금액
     BigDecimal receivedAmount, // 환전 후 받는 금액
     String status,        // 거래 상태
     LocalDateTime createdAt // 거래 생성 시간
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
