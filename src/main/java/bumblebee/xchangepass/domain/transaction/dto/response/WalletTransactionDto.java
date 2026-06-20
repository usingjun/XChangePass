package bumblebee.xchangepass.domain.transaction.dto.response;

import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import bumblebee.xchangepass.domain.transaction.entity.TransactionDirection;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@JsonTypeName("wallet")
public record WalletTransactionDto (
        Long counterpartyUserId,
        BigDecimal amount,
        TransactionDirection direction,
        TransactionType transactionType,
        WalletTransactionType walletType
) implements TransactionDataDto {
    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("counterpartyUserId", counterpartyUserId);
        metadata.put("amount", amount);
        metadata.put("direction", direction);
        metadata.put("type", transactionType);
        metadata.put("walletType", walletType);
        return metadata;
    }

}
