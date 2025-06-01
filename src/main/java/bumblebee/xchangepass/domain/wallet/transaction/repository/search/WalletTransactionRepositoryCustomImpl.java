package bumblebee.xchangepass.domain.wallet.transaction.repository.search;

import bumblebee.xchangepass.domain.wallet.transaction.dto.request.WalletTransactionSearchCondition;
import bumblebee.xchangepass.domain.wallet.transaction.entity.QWalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

@RequiredArgsConstructor
public class WalletTransactionRepositoryCustomImpl implements WalletTransactionRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<WalletTransaction> search(Long userId, WalletTransactionSearchCondition cond, Pageable pageable) {
        QWalletTransaction tx = QWalletTransaction.walletTransaction;

        BooleanBuilder builder = new BooleanBuilder();
        if (userId != null) {
            builder.and(
                    tx.sender.userId.eq(userId)
                            .or(tx.receiver.userId.eq(userId))
            );
        }
        if (cond.transactionType() != null) {
            builder.and(tx.transactionType.eq(cond.transactionType()));
        }
        if (cond.status() != null) {
            builder.and(tx.status.eq(cond.status()));
        }
        if (cond.startDate() != null) {
            builder.and(tx.updatedAt.goe(cond.startDate()));
        }
        if (cond.endDate() != null) {
            builder.and(tx.updatedAt.loe(cond.endDate()));
        }

        List<WalletTransaction> results = queryFactory
                .selectFrom(tx)
                .where(builder)
                .orderBy(tx.updatedAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return PageableExecutionUtils.getPage(results, pageable,
                () -> queryFactory.selectFrom(tx).where(builder).fetchCount());
    }
}
