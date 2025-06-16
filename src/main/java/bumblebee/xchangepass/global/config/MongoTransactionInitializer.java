package bumblebee.xchangepass.global.config;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import bumblebee.xchangepass.domain.transaction.mongoV.entity.TransactionDocument;
import bumblebee.xchangepass.domain.transaction.rdbmsV.entity.TransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MongoTransactionInitializer {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void init() {
//        if (mongoTemplate.collectionExists("transactions") &&
//            mongoTemplate.findAll(TransactionDocument.class).size() > 0) {
//            return; // 이미 데이터 있으면 스킵
//        }

        // CARD 거래
        Map<String, Object> cardMeta = new HashMap<>();
        cardMeta.put("merchant", "Starbucks");
        cardMeta.put("amount", new BigDecimal("4500"));
        cardMeta.put("balanceAfter", new BigDecimal("99500"));
        cardMeta.put("cardType", CardTransactionType.DEPOSIT);  // 필수!

        mongoTemplate.insert(new TransactionDocument(
                58L,
                TransactionType.CARD,
                Currency.getInstance("KRW"),
                Currency.getInstance("USD"),
                LocalDateTime.now().minusDays(2),
                cardMeta
        ));

        // WALLET 거래
        Map<String, Object> walletMeta = new HashMap<>();
        walletMeta.put("receiver", "user456");
        walletMeta.put("amount", new BigDecimal("30000"));
        walletMeta.put("walletType", WalletTransactionType.TRANSFER);  // 필수!

        mongoTemplate.insert(new TransactionDocument(
                58L,
                TransactionType.WALLET,
                Currency.getInstance("KRW"),
                Currency.getInstance("KRW"),
                LocalDateTime.now().minusDays(1),
                walletMeta
        ));

        // EXCHANGE 거래
        Map<String, Object> exchangeMeta = new HashMap<>();
        exchangeMeta.put("amount", new BigDecimal("100"));
        exchangeMeta.put("afterAmount", new BigDecimal("132050"));  // 환전 후 금액
        exchangeMeta.put("rate", new BigDecimal("1320.50"));

        // EXCHANGE 거래 (시간 명시)
        mongoTemplate.insert(new TransactionDocument(
                58L,
                TransactionType.EXCHANGE,
                Currency.getInstance("KRW"),
                Currency.getInstance("USD"),
                LocalDateTime.now().minusDays(2),
                exchangeMeta
        ));

        System.out.println("[MongoDB] 테스트용 거래 데이터 삽입 완료");
    }
}
