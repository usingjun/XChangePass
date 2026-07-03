package bumblebee.xchangepass.domain.transaction.statistics.repository;

import bumblebee.xchangepass.domain.transaction.statistics.dto.TransactionStatisticsDirection;
import bumblebee.xchangepass.domain.transaction.statistics.dto.TransactionStatisticsRow;
import bumblebee.xchangepass.domain.transaction.statistics.dto.TransactionStatisticsSourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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

    private static final String MONTHLY_STATISTICS_MATERIALIZED_VIEW_SQL = """
            select
                user_id,
                bucket_month,
                source_type,
                transaction_type,
                currency,
                direction,
                amount_sum,
                transaction_count
            from mv_transaction_monthly_statistics
            where user_id = ?
              and bucket_month >= ?
              and bucket_month < ?
            order by bucket_month, source_type, transaction_type, currency, direction
            """;

    private static final String MONTHLY_STATISTICS_SUMMARY_SQL = """
            select
                user_id,
                bucket_month,
                source_type,
                transaction_type,
                currency,
                direction,
                amount_sum,
                transaction_count,
                data_as_of
            from transaction_monthly_summary
            where user_id = ?
              and bucket_month >= ?
              and bucket_month < ?
            order by bucket_month, source_type, transaction_type, currency, direction
            """;

    private static final String DELETE_MONTHLY_SUMMARY_SQL = """
            delete from transaction_monthly_summary
            where bucket_month >= ?
              and bucket_month < ?
            """;

    private static final String INSERT_MONTHLY_SUMMARY_SQL = """
            insert into transaction_monthly_summary (
                user_id,
                bucket_month,
                source_type,
                transaction_type,
                currency,
                direction,
                amount_sum,
                transaction_count,
                data_as_of,
                created_at,
                updated_at
            )
            select
                user_id,
                bucket_month::date,
                source_type,
                transaction_type,
                currency,
                direction,
                sum(amount) as amount_sum,
                count(*) as transaction_count,
                ? as data_as_of,
                ? as created_at,
                ? as updated_at
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
                where transaction_time >= ?
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
                where transaction_time >= ?
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
                where transaction_time >= ?
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
                where transaction_time >= ?
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
                where completed_at >= ?
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
                where completed_at >= ?
                  and completed_at < ?
                  and status = 'COMPLETED'
                  and completed_at is not null
            ) statistics_source
            where user_id is not null
              and currency is not null
              and amount is not null
              and direction is not null
            group by user_id, bucket_month, source_type, transaction_type, currency, direction
            """;

    private static final RowMapper<TransactionStatisticsRow> ROW_MAPPER = (rs, rowNum) -> new TransactionStatisticsRow(
            rs.getLong("user_id"),
            YearMonth.from(rs.getTimestamp("bucket_month").toLocalDateTime()),
            TransactionStatisticsSourceType.valueOf(rs.getString("source_type")),
            rs.getString("transaction_type"),
            rs.getString("currency"),
            TransactionStatisticsDirection.valueOf(rs.getString("direction")),
            rs.getBigDecimal("amount_sum"),
            rs.getLong("transaction_count")
    );

    private static final RowMapper<TransactionStatisticsRow> SUMMARY_ROW_MAPPER = (rs, rowNum) -> new TransactionStatisticsRow(
            rs.getLong("user_id"),
            YearMonth.from(rs.getTimestamp("bucket_month").toLocalDateTime()),
            TransactionStatisticsSourceType.valueOf(rs.getString("source_type")),
            rs.getString("transaction_type"),
            rs.getString("currency"),
            TransactionStatisticsDirection.valueOf(rs.getString("direction")),
            rs.getBigDecimal("amount_sum"),
            rs.getLong("transaction_count"),
            rs.getTimestamp("data_as_of").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<TransactionStatisticsRow> findMonthlyStatistics(Long userId, YearMonth fromMonth, YearMonth toMonth) {
        Timestamp fromTimestamp = toStartTimestamp(fromMonth);
        Timestamp toTimestamp = toExclusiveEndTimestamp(toMonth);

        return jdbcTemplate.query(
                MONTHLY_STATISTICS_SQL,
                ROW_MAPPER,
                userId, fromTimestamp, toTimestamp,
                userId, fromTimestamp, toTimestamp,
                userId, fromTimestamp, toTimestamp,
                userId, fromTimestamp, toTimestamp,
                userId, fromTimestamp, toTimestamp,
                userId, fromTimestamp, toTimestamp
        );
    }

    @Override
    public List<TransactionStatisticsRow> findMonthlyStatisticsFromMaterializedView(
            Long userId, YearMonth fromMonth, YearMonth toMonth
    ) {
        return jdbcTemplate.query(
                MONTHLY_STATISTICS_MATERIALIZED_VIEW_SQL,
                ROW_MAPPER,
                userId,
                toStartTimestamp(fromMonth),
                toExclusiveEndTimestamp(toMonth)
        );
    }

    @Override
    public List<TransactionStatisticsRow> findMonthlyStatisticsFromSummary(
            Long userId, YearMonth fromMonth, YearMonth toMonth
    ) {
        return jdbcTemplate.query(
                MONTHLY_STATISTICS_SUMMARY_SQL,
                SUMMARY_ROW_MAPPER,
                userId,
                toStartTimestamp(fromMonth),
                toExclusiveEndTimestamp(toMonth)
        );
    }

    @Override
    public void refreshMaterializedView() {
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW mv_transaction_monthly_statistics");
    }

    @Override
    public void refreshMaterializedViewConcurrently() {
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_transaction_monthly_statistics");
    }

    @Override
    public void refreshMonthlySummary(YearMonth fromMonth, YearMonth toMonth) {
        Timestamp fromTimestamp = toStartTimestamp(fromMonth);
        Timestamp toTimestamp = toExclusiveEndTimestamp(toMonth);
        Timestamp dataAsOf = Timestamp.valueOf(LocalDateTime.now());

        jdbcTemplate.update(DELETE_MONTHLY_SUMMARY_SQL, fromTimestamp, toTimestamp);
        jdbcTemplate.update(
                INSERT_MONTHLY_SUMMARY_SQL,
                dataAsOf, dataAsOf, dataAsOf,
                fromTimestamp, toTimestamp,
                fromTimestamp, toTimestamp,
                fromTimestamp, toTimestamp,
                fromTimestamp, toTimestamp,
                fromTimestamp, toTimestamp,
                fromTimestamp, toTimestamp
        );
    }

    private Timestamp toStartTimestamp(YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        return Timestamp.valueOf(start);
    }

    private Timestamp toExclusiveEndTimestamp(YearMonth month) {
        LocalDateTime endExclusive = month.plusMonths(1).atDay(1).atStartOfDay();
        return Timestamp.valueOf(endExclusive);
    }
}
