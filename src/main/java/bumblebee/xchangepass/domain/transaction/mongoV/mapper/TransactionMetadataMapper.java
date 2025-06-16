package bumblebee.xchangepass.domain.transaction.mongoV.mapper;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import bumblebee.xchangepass.domain.transaction.mongoV.dto.CardTransactionDto;
import bumblebee.xchangepass.domain.transaction.mongoV.dto.ExchangeTransactionDto;
import bumblebee.xchangepass.domain.transaction.mongoV.dto.TransactionDataDto;
import bumblebee.xchangepass.domain.transaction.mongoV.dto.WalletTransactionDto;
import bumblebee.xchangepass.domain.transaction.rdbmsV.entity.TransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;

import java.math.BigDecimal;
import java.util.Map;

public class TransactionMetadataMapper {
    public static TransactionDataDto mapToDto(TransactionType type, Map<String, Object> meta) {
        return switch (type){
            case CARD -> new CardTransactionDto(
                    (String) meta.get("merchant"),
                    castToBigDecimal(meta.get("amount")),
                    castToBigDecimal(meta.get("balanceAfter")),
                    (CardTransactionType) meta.get("cardType")
            );
            case WALLET -> new WalletTransactionDto(
                    (String) meta.get("receiver"),
                    castToBigDecimal(meta.get("amount")),
                    (WalletTransactionType) meta.get("walletType")
                    );
            case EXCHANGE -> new ExchangeTransactionDto(
                    castToBigDecimal(meta.get("amount")),
                    castToBigDecimal(meta.get("afterAmount")),
                    castToBigDecimal(meta.get("rate"))
            );
        };
    }


    private static BigDecimal castToBigDecimal(Object o) {
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (o instanceof String s) return new BigDecimal(s);
        return null;
    }
}
