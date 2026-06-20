package bumblebee.xchangepass.domain.cardTransaction.repository;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType;
import bumblebee.xchangepass.domain.cardTransaction.entity.QCardTransaction;
import bumblebee.xchangepass.domain.transaction.repository.CardTransactionRow;
import bumblebee.xchangepass.domain.transaction.repository.CardTransactionRowDto;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
public class CardTransactionRepositoryImpl implements CardTransactionRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<CardTransactionRow> findRecentForUser(Long userId, CardTransactionType cardType, String merchantName,
                                                      BigDecimal minAmount, BigDecimal maxAmount, String currency,
                                                      LocalDateTime startDate, LocalDateTime endDate,
                                                      LocalDateTime cursor, boolean includeCursorTime,
                                                      Long cursorTransactionId, Pageable pageable) {
        QCardTransaction transaction = QCardTransaction.cardTransaction;
        BooleanBuilder where = new BooleanBuilder();
        where.and(transaction.user.userId.eq(userId));

        if (cardType != null) {
            where.and(transaction.transactionType.eq(cardType));
        }
        if (merchantName != null) {
            where.and(transaction.merchantName.containsIgnoreCase(merchantName));
        }
        if (minAmount != null) {
            where.and(transaction.approvedAmount.goe(minAmount));
        }
        if (maxAmount != null) {
            where.and(transaction.approvedAmount.loe(maxAmount));
        }
        if (currency != null) {
            where.and(transaction.approvedCurrency.eq(currency));
        }
        if (startDate != null) {
            where.and(transaction.transactionTime.goe(startDate));
        }
        if (endDate != null) {
            where.and(transaction.transactionTime.loe(endDate));
        }
        applyCursor(where, transaction, cursor, includeCursorTime, cursorTransactionId);

        return queryFactory
                .select(Projections.constructor(
                        CardTransactionRowDto.class,
                        transaction.transactionId,
                        transaction.user.userId,
                        transaction.merchantName,
                        transaction.approvedAmount,
                        transaction.approvedCurrency,
                        transaction.balanceAfter,
                        transaction.transactionType,
                        transaction.transactionTime
                ))
                .from(transaction)
                .where(where)
                .orderBy(transaction.transactionTime.desc(), transaction.transactionId.desc())
                .limit(pageable.getPageSize())
                .fetch()
                .stream()
                .map(CardTransactionRow.class::cast)
                .toList();
    }

    private void applyCursor(BooleanBuilder where, QCardTransaction transaction, LocalDateTime cursor,
                             boolean includeCursorTime, Long cursorTransactionId) {
        if (cursor == null) {
            return;
        }
        BooleanBuilder cursorWhere = new BooleanBuilder();
        cursorWhere.or(transaction.transactionTime.lt(cursor));
        if (includeCursorTime) {
            BooleanBuilder sameTime = new BooleanBuilder();
            sameTime.and(transaction.transactionTime.eq(cursor));
            if (cursorTransactionId != null) {
                sameTime.and(transaction.transactionId.lt(cursorTransactionId));
            }
            cursorWhere.or(sameTime);
        }
        where.and(cursorWhere);
    }
}
