package bumblebee.xchangepass.domain.wallet.transaction.repository.search;

import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionSearchCondition;
import bumblebee.xchangepass.domain.transaction.rdbmsV.entity.TransactionType;
import bumblebee.xchangepass.domain.user.entity.QUser;
import bumblebee.xchangepass.domain.wallet.transaction.entity.QWalletTransaction;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class WalletTransactionRepositoryCustomImpl implements WalletTransactionRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private final QWalletTransaction tx = QWalletTransaction.walletTransaction;
    private final QUser senderUser = new QUser("senderUser");
    private final QUser receiverUser = new QUser("receiverUser");


    @Override
    public List<TransactionResponse> search(Long userId, TransactionSearchCondition cond, int size) {
        if (cond.transactionType() != null && cond.transactionType() != TransactionType.WALLET) {
            return List.of();
        }

        BooleanBuilder builder = new BooleanBuilder();
        if (userId != null) {
            builder.and(
                    tx.sender.userId.eq(userId)
                            .or(tx.receiver.userId.eq(userId))
            );
        }
        if (cond.walletTransactionType() != null) {
            builder.and(tx.transactionType.eq(cond.walletTransactionType()));
        }
        if (cond.startDate() != null) {
            builder.and(tx.updatedAt.goe(cond.startDate()));
        }
        if (cond.endDate() != null) {
            builder.and(tx.updatedAt.loe(cond.endDate()));
        }
        if (cond.cursor() != null) {
            builder.and(tx.updatedAt.lt(cond.cursor()));
        }


        return queryFactory
                .selectFrom(tx)
                .join(tx.sender, senderUser).fetchJoin()
                .join(tx.receiver, receiverUser).fetchJoin()
                .where(builder)
                .orderBy(tx.updatedAt.desc())
                .limit(size)
                .fetch()
                .stream()
                .map(t -> new TransactionResponse(
                        t.getSender().getUserId(),
                        t.getUpdatedAt(),
                        "WALLET",
                        new TransactionResponse.TransactionDataDto(
                                t.getReceiver().getUserNickname().getValue(),
                                t.getAmount(),
                                null,
                                null,
                                t.getFromCurrency().getCurrencyCode(),
                                t.getToCurrency().getCurrencyCode(),
                                null
                        )
                )).toList();
    }
}
