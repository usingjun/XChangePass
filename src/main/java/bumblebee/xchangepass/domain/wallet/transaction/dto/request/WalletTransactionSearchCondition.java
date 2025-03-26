package bumblebee.xchangepass.domain.wallet.transaction.dto.request;

import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionStatus;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public record WalletTransactionSearchCondition(
        WalletTransactionType transactionType,

        WalletTransactionStatus status,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime startDate,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime endDate
) {
}
