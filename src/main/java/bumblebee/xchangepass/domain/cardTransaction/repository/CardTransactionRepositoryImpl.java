package bumblebee.xchangepass.domain.cardTransaction.repository;

import bumblebee.xchangepass.domain.cardTransaction.dto.response.CardTransactionSummaryResponse;
import bumblebee.xchangepass.domain.cardTransaction.entity.QCardTransaction;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CardTransactionRepositoryImpl implements CardTransactionRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QCardTransaction cardTransaction = QCardTransaction.cardTransaction;

    /**
     * ✅ 사용자 거래내역 무한 스크롤 조회 (최신순)
     */
    @Override
    public List<CardTransactionSummaryResponse> getUserTransactions(Long userId, Long lastTransactionId, int size) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(cardTransaction.user.userId.eq(userId));

        if (lastTransactionId != null) {
            builder.and(cardTransaction.transactionId.lt(lastTransactionId));
        }

        return queryFactory
                .select(com.querydsl.core.types.Projections.constructor(
                        CardTransactionSummaryResponse.class,
                        cardTransaction.transactionId,
                        cardTransaction.merchantName,
                        cardTransaction.approvedAmount,
                        cardTransaction.approvedCurrency,
                        cardTransaction.transactionTime,
                        cardTransaction.transactionType
                ))
                .from(cardTransaction)
                .where(builder)
                .orderBy(cardTransaction.transactionId.desc())
                .limit(size)
                .fetch();
    }
}
