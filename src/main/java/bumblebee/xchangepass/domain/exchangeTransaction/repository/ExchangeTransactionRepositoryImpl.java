package bumblebee.xchangepass.domain.exchangeTransaction.repository;

import bumblebee.xchangepass.domain.exchangeTransaction.entitiy.ExchangeTransactionStatus;
import bumblebee.xchangepass.domain.exchangeTransaction.entitiy.QExchangeTransaction;
import bumblebee.xchangepass.domain.transaction.repository.ExchangeTransactionRow;
import bumblebee.xchangepass.domain.transaction.repository.ExchangeTransactionRowDto;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
public class ExchangeTransactionRepositoryImpl implements ExchangeTransactionRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ExchangeTransactionRow> findRecentForUser(Long userId, BigDecimal minAmount, BigDecimal maxAmount,
                                                          String currency, LocalDateTime startDate,
                                                          LocalDateTime endDate, LocalDateTime cursor,
                                                          boolean includeCursorTime, Long cursorTransactionId,
                                                          Pageable pageable) {
        QExchangeTransaction transaction = QExchangeTransaction.exchangeTransaction;
        BooleanBuilder where = new BooleanBuilder();
        where.and(transaction.user.userId.eq(userId));
        where.and(transaction.status.eq(ExchangeTransactionStatus.COMPLETED));

        if (minAmount != null) {
            where.and(transaction.amount.goe(minAmount));
        }
        if (maxAmount != null) {
            where.and(transaction.amount.loe(maxAmount));
        }
        if (currency != null) {
            where.and(transaction.fromCurrency.eq(currency).or(transaction.toCurrency.eq(currency)));
        }
        if (startDate != null) {
            where.and(transaction.completedAt.goe(startDate));
        }
        if (endDate != null) {
            where.and(transaction.completedAt.loe(endDate));
        }
        applyCursor(where, transaction, cursor, includeCursorTime, cursorTransactionId);

        return queryFactory
                .select(Projections.constructor(
                        ExchangeTransactionRowDto.class,
                        transaction.transactionId,
                        transaction.user.userId,
                        transaction.fromCurrency,
                        transaction.toCurrency,
                        transaction.amount,
                        transaction.receivedAmount,
                        transaction.exchangeRate,
                        transaction.completedAt
                ))
                .from(transaction)
                .where(where)
                .orderBy(transaction.completedAt.desc(), transaction.transactionId.desc())
                .limit(pageable.getPageSize())
                .fetch()
                .stream()
                .map(ExchangeTransactionRow.class::cast)
                .toList();
    }

    private void applyCursor(BooleanBuilder where, QExchangeTransaction transaction, LocalDateTime cursor,
                             boolean includeCursorTime, Long cursorTransactionId) {
        if (cursor == null) {
            return;
        }
        BooleanBuilder cursorWhere = new BooleanBuilder();
        cursorWhere.or(transaction.completedAt.lt(cursor));
        if (includeCursorTime) {
            BooleanBuilder sameTime = new BooleanBuilder();
            sameTime.and(transaction.completedAt.eq(cursor));
            if (cursorTransactionId != null) {
                sameTime.and(transaction.transactionId.lt(cursorTransactionId));
            }
            cursorWhere.or(sameTime);
        }
        where.and(cursorWhere);
    }
}
