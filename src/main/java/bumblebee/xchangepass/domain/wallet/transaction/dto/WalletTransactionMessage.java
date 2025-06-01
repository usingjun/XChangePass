package bumblebee.xchangepass.domain.wallet.transaction.dto;

import java.math.BigDecimal;

//내부 지갑 트랜잭션 메시지
public record WalletTransactionMessage(
        Long senderId,
        Long receiverId,
        BigDecimal amount,
        String fromCurrency,
        String toCurrency,
        String transactionType
) {}