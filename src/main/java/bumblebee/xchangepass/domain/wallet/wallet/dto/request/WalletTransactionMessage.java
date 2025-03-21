package bumblebee.xchangepass.domain.wallet.wallet.dto.request;

import java.math.BigDecimal;

public record WalletTransactionMessage(
        Long myWalletId,
        Long counterWalletId,
        BigDecimal amount,
        String fromCurrency,
        String toCurrency,
        String transactionType
) {}