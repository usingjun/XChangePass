package bumblebee.xchangepass.domain.cardTransaction.repository;

import bumblebee.xchangepass.domain.cardTransaction.entity.QCardTransaction;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionSearchCondition;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CardTransactionRepositoryImpl implements CardTransactionRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QCardTransaction ct = QCardTransaction.cardTransaction;

    /**
     * ✅ 사용자 거래내역 무한 스크롤 조회 (최신순)
     */
    @Override
    public List<TransactionResponse> search(Long userId, TransactionSearchCondition cond, int size) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(ct.user.userId.eq(userId));

        if (cond.cardTransactionType() != null) {
            builder.and(ct.cardTransactionType.eq(cond.cardTransactionType()));
        }
        if (cond.startDate() != null) {
            builder.and(ct.transactionTime.goe(cond.startDate()));
        }
        if (cond.endDate() != null) {
            builder.and(ct.transactionTime.loe(cond.endDate()));
        }
        if (cond.cursor() != null) {
            builder.and(ct.transactionTime.lt(cond.cursor()));
        }

        return queryFactory
                .selectFrom(ct)
                .where(builder)
                .orderBy(ct.transactionTime.desc())
                .limit(size)
                .fetch()
                .stream()
                .map(tx -> new TransactionResponse(
                        tx.getUser().getUserId(),
                        tx.getTransactionTime(),
                        "CARD",
                        new TransactionResponse.TransactionDataDto(
                                tx.getMerchantName(),
                                tx.getApprovedAmount(),
                                tx.getApprovedCurrency().getCurrencyCode(),
                                tx.getBalanceAfter(),
                                null,
                                null,
                                null
                        )
                )).toList();
    }
}
