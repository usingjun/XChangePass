package bumblebee.xchangepass.domain.transaction.mapper;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import bumblebee.xchangepass.domain.transaction.dto.response.CardTransactionDto;
import bumblebee.xchangepass.domain.transaction.dto.response.ExchangeTransactionDto;
import bumblebee.xchangepass.domain.transaction.dto.response.TransactionDataDto;
import bumblebee.xchangepass.domain.transaction.dto.response.WalletTransactionDto;
import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;

import java.math.BigDecimal;
import java.util.Map;

public class TransactionMetadataMapper {
    public static TransactionDataDto mapToDto(Map<String, Object> meta) {
        TransactionType type = castToTransactionType(meta.get("type"));
        if(type==null) throw new NullPointerException();
        return switch (type){
            case CARD -> new CardTransactionDto(
                    (String) meta.get("merchant"),
                    castToBigDecimal(meta.get("amount")),
                    castToBigDecimal(meta.get("balanceAfter")),
                    castToTransactionType(meta.get("type")),
                    castToCardType(meta.get("cardType"))
            );
            case WALLET -> new WalletTransactionDto(
                    (Long) meta.get("receiver"),
                    castToBigDecimal(meta.get("amount")),
                    castToTransactionType(meta.get("type")),
                    castToWalletType(meta.get("walletType"))
                    );
            case EXCHANGE -> new ExchangeTransactionDto(
                    castToBigDecimal(meta.get("amount")),
                    castToBigDecimal(meta.get("afterAmount")),
                    castToBigDecimal(meta.get("rate")),
                    castToTransactionType(meta.get("type"))
            );
        };
    }

    private static TransactionType castToTransactionType(Object o) {
        if(o instanceof TransactionType type) return type;
        if(o instanceof String s) return TransactionType.valueOf(s);
        return null;
    }

    private static CardTransactionType castToCardType(Object o) {
        if(o instanceof CardTransactionType type) return type;
        if(o instanceof String s) return CardTransactionType.valueOf(s);
        return null;
    }

    private static WalletTransactionType castToWalletType(Object o) {
        if(o instanceof WalletTransactionType type) return type;
        if(o instanceof String s) return WalletTransactionType.valueOf(s);
        return null;
    }

    private static BigDecimal castToBigDecimal(Object o) {
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (o instanceof String s) return new BigDecimal(s);
        return null;
    }
}
