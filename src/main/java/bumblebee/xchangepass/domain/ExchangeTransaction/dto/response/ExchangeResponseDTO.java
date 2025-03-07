package bumblebee.xchangepass.domain.ExchangeTransaction.dto.response;


import bumblebee.xchangepass.domain.ExchangeTransaction.entitiy.ExchangeTransaction;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record ExchangeResponseDTO (
     Long transactionId,
     Long userId,
     String fromCurrency,
     String toCurrency,
     BigDecimal exchangeRate,
     BigDecimal amount,
     BigDecimal receivedAmount,
     String status,
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
