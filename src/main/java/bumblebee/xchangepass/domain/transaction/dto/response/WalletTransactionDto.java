package bumblebee.xchangepass.domain.transaction.dto.response;

import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.math.BigDecimal;
import java.util.Map;

@JsonTypeName("wallet")
public record WalletTransactionDto (
        Long receiver,
        BigDecimal amount,
        TransactionType transactionType,
        WalletTransactionType walletType
) implements TransactionDataDto {
    @Override
    public Map<String, Object> toMap() {
        return Map.of(
                "receiver",receiver,
                "amount", amount,
                "type", transactionType,
                "walletType", walletType
        );
    }

}
