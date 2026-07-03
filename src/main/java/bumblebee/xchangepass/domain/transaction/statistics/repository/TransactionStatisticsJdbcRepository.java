package bumblebee.xchangepass.domain.transaction.statistics.repository;

import bumblebee.xchangepass.domain.transaction.statistics.dto.TransactionStatisticsDirection;
import bumblebee.xchangepass.domain.transaction.statistics.dto.TransactionStatisticsRow;
import bumblebee.xchangepass.domain.transaction.statistics.dto.TransactionStatisticsSourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TransactionStatisticsJdbcRepository implements TransactionStatisticsQueryRepository {

    private static final String MONTHLY_STATISTICS_SQL = """
            select
                user_id,
                bucket_month,
                source_type,
                transaction_type,
                currency,
                direction,
                sum(amount) as amount_sum,
                count(*) as transaction_count
            from (
                select
                    user_id,
                    date_trunc('month', transaction_time) as bucket_month,
                    'WALLET' as source_type,
                    transaction_type as transaction_type,
                    coalesce(from_currency, to_currency) as currency,
                    amount as amount,
                    case
                        when transaction_type = 'DEPOSIT' then 'INCOMING'
                        when transaction_type = 'WITHDRAWAL' then 'OUTGOING'
                    end as direction
                from wallet_transaction
                where user_id = ?
                  and transaction_time >= ?
                  and transaction_time < ?
                  and transaction_type in ('DEPOSIT', 'WITHDRAWAL')

                union all

                select
                    user_id,
                    date_trunc('month', transaction_time) as bucket_month,
                    'WALLET' as source_type,
                    transaction_type as transaction_type,
                    from_currency as currency,
                    amount as amount,
                    'OUTGOING' as direction
                from wallet_transaction
                where user_id = ?
                  and transaction_time >= ?
                  and transaction_time < ?
                  and transaction_type = 'TRANSFER'

                union all

                select
                    counterparty_user_id as user_id,
                    date_trunc('month', transaction_time) as bucket_month,
                    'WALLET' as source_type,
                    transaction_type as transaction_type,
                    to_currency as currency,
                    received_amount as amount,
                    'INCOMING' as direction
                from wallet_transaction
                where counterparty_user_id = ?
                  and transaction_time >= ?
                  and transaction_time < ?
                  and transaction_type = 'TRANSFER'

                union all

                select
                    user_id,
                    date_trunc('month', transaction_time) as bucket_month,
                    'CARD' as source_type,
                    transaction_type as transaction_type,
                    approved_currency as currency,
                    approved_amount as amount,
                    case
                        when transaction_type = 'PAYMENT' then 'OUTGOING'
                        when transaction_type in ('REFUND', 'DEPOSIT') then 'INCOMING'
                    end as direction
                from card_transaction
                where user_id = ?
                  and transaction_time >= ?
                  and transaction_time < ?
                  and transaction_type in ('PAYMENT', 'REFUND', 'DEPOSIT')

                union all

                select
                    user_id,
                    date_trunc('month', completed_at) as bucket_month,
                    'EXCHANGE' as source_type,
                    status as transaction_type,
                    from_currency as currency,
                    amount as amount,
                    'OUTGOING' as direction
                from exchange_transaction
                where user_id = ?
                  and completed_at >= ?
                  and completed_at < ?
                  and status = 'COMPLETED'
                  and completed_at is not null

                union all

                select
                    user_id,
                    date_trunc('month', completed_at) as bucket_month,
                    'EXCHANGE' as source_type,
                    status as transaction_type,
                    to_currency as currency,
                    received_amount as amount,
                    'INCOMING' as direction
                from exchange_transaction
                where user_id = ?
                  and completed_at >= ?
                  and completed_at < ?
                  and status = 'COMPLETED'
                  and completed_at is not null
            ) statistics_source
            where currency is not null
              and amount is not null
              and direction is not null
            group by user_id, bucket_month, source_type, transaction_type, currency, direction
            order by bucket_month, source_type, transaction_type, currency, direction
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<TransactionStatisticsRow> findMonthlyStatistics(Long userId, YearMonth fromMonth, YearMonth toMonth) {
        LocalDateTime from = fromMonth.atDay(1).atStartOfDay();
        LocalDateTime toExclusive = toMonth.plusMonths(1).atDay(1).atStartOfDay();
        Timestamp fromTimestamp = Timestamp.valueOf(from);
        Timestamp toTimestamp = Timestamp.valueOf(toExclusive);

        return jdbcTemplate.query(
                MONTHLY_STATISTICS_SQL,
                (rs, rowNum) -> new TransactionStatisticsRow(
                        rs.getLong("user_id"),
                        YearMonth.from(rs.getTimestamp("bucket_month").toLocalDateTime()),
                        TransactionStatisticsSourceType.valueOf(rs.getString("source_type")),
                        rs.getString("transaction_type"),
                        rs.getString("currency"),
                        TransactionStatisticsDirection.valueOf(rs.getString("direction")),
                        rs.getBigDecimal("amount_sum"),
                        rs.getLong("transaction_count")
                ),
                userId, fromTimestamp, toTimestamp,
                userId, fromTimestamp, toTimestamp,
                userId, fromTimestamp, toTimestamp,
                userId, fromTimestamp, toTimestamp,
                userId, fromTimestamp, toTimestamp,
                userId, fromTimestamp, toTimestamp
        );
    }
}
