package bumblebee.xchangepass.domain.wallet.transaction.repository;

import bumblebee.xchangepass.domain.transaction.repository.WalletTransactionRow;
import bumblebee.xchangepass.domain.transaction.repository.WalletTransactionRowDto;
import bumblebee.xchangepass.domain.wallet.transaction.entity.QWalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
public class WalletTransactionRepositoryImpl implements WalletTransactionRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<WalletTransactionRow> findRecentSentByUser(Long userId, WalletTransactionType walletType,
                                                           BigDecimal minAmount, BigDecimal maxAmount, String currency,
                                                           LocalDateTime startDate, LocalDateTime endDate,
                                                           LocalDateTime cursor, boolean includeCursorTime,
                                                           Long cursorTransactionId, Pageable pageable) {
        QWalletTransaction transaction = QWalletTransaction.walletTransaction;
        BooleanBuilder where = baseWhere(transaction, walletType, startDate, endDate);
        where.and(transaction.user.userId.eq(userId));
        applyAmount(where, transaction.amount, minAmount, maxAmount);
        if (currency != null) {
            where.and(transaction.fromCurrency.eq(currency));
        }
        applyCursor(where, transaction, cursor, includeCursorTime, cursorTransactionId);
        return fetch(transaction, where, pageable);
    }

    @Override
    public List<WalletTransactionRow> findRecentReceivedByUser(Long userId, WalletTransactionType walletType,
                                                               BigDecimal minAmount, BigDecimal maxAmount, String currency,
                                                               LocalDateTime startDate, LocalDateTime endDate,
                                                               LocalDateTime cursor, boolean includeCursorTime,
                                                               Long cursorTransactionId, Pageable pageable) {
        QWalletTransaction transaction = QWalletTransaction.walletTransaction;
        BooleanBuilder where = baseWhere(transaction, walletType, startDate, endDate);
        where.and(transaction.counterpartyUser.userId.eq(userId));
        NumberExpression<BigDecimal> receivedAmount = transaction.receivedAmount.coalesce(transaction.amount);
        applyAmount(where, receivedAmount, minAmount, maxAmount);
        if (currency != null) {
            where.and(transaction.toCurrency.eq(currency));
        }
        applyCursor(where, transaction, cursor, includeCursorTime, cursorTransactionId);
        return fetch(transaction, where, pageable);
    }

    private BooleanBuilder baseWhere(QWalletTransaction transaction, WalletTransactionType walletType,
                                     LocalDateTime startDate, LocalDateTime endDate) {
        BooleanBuilder where = new BooleanBuilder();
        if (walletType != null) {
            where.and(transaction.transactionType.eq(walletType));
        }
        if (startDate != null) {
            where.and(transaction.transactionTime.goe(startDate));
        }
        if (endDate != null) {
            where.and(transaction.transactionTime.loe(endDate));
        }
        return where;
    }

    private void applyAmount(BooleanBuilder where, NumberExpression<BigDecimal> amount,
                             BigDecimal minAmount, BigDecimal maxAmount) {
        if (minAmount != null) {
            where.and(amount.goe(minAmount));
        }
        if (maxAmount != null) {
            where.and(amount.loe(maxAmount));
        }
    }

    private List<WalletTransactionRow> fetch(QWalletTransaction transaction, BooleanBuilder where, Pageable pageable) {
        return queryFactory
                .select(Projections.constructor(
                        WalletTransactionRowDto.class,
                        transaction.transactionId,
                        transaction.user.userId,
                        transaction.counterpartyUser.userId,
                        transaction.amount,
                        transaction.receivedAmount,
                        transaction.fromCurrency,
                        transaction.toCurrency,
                        transaction.transactionType,
                        transaction.transactionTime
                ))
                .from(transaction)
                .where(where)
                .orderBy(transaction.transactionTime.desc(), transaction.transactionId.desc())
                .limit(pageable.getPageSize())
                .fetch()
                .stream()
                .map(WalletTransactionRow.class::cast)
                .toList();
    }

    private void applyCursor(BooleanBuilder where, QWalletTransaction transaction, LocalDateTime cursor,
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
