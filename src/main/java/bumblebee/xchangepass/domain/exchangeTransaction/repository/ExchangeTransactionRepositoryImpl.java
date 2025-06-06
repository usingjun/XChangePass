package bumblebee.xchangepass.domain.exchangeTransaction.repository;

import bumblebee.xchangepass.domain.exchangeTransaction.entitiy.QExchangeTransaction;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionSearchCondition;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class ExchangeTransactionRepositoryImpl implements ExchangeTransactionRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private final QExchangeTransaction et = QExchangeTransaction.exchangeTransaction;

    @Override
    public List<TransactionResponse> search(Long userId, TransactionSearchCondition cond, int size) {
        if (cond.transactionType() != null && cond.transactionType() != TransactionType.EXCHANGE) {
            return List.of();
        }

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(et.user.userId.eq(userId));

        if (cond.startDate() != null) {
            builder.and(et.exchangeDate.goe(cond.startDate()));
        }
        if (cond.endDate() != null) {
            builder.and(et.exchangeDate.loe(cond.endDate()));
        }
        if (cond.cursor() != null) {
            builder.and(et.exchangeDate.lt(cond.cursor()));
        }

        return queryFactory.selectFrom(et)
                .where(builder)
                .orderBy(et.exchangeDate.desc())
                .limit(size)
                .fetch()
                .stream()
                .map(tx -> new TransactionResponse(
                        tx.getUser().getUserId(),
                        tx.getExchangeDate(),
                        "EXCHANGE",
                        new TransactionResponse.TransactionDataDto(
                                null,
                                tx.getReceivedAmount(),
                                null,
                                null,
                                tx.getFromCurrency(),
                                tx.getToCurrency(),
                                tx.getExchangeRate()
                        )
                ))
                .toList();
    }
}
