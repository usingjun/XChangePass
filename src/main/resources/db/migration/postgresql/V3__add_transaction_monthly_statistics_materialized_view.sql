DO $$
BEGIN
    IF to_regclass('public.wallet_transaction') IS NOT NULL
        AND to_regclass('public.card_transaction') IS NOT NULL
        AND to_regclass('public.exchange_transaction') IS NOT NULL THEN
        EXECUTE $mv$
            CREATE MATERIALIZED VIEW IF NOT EXISTS mv_transaction_monthly_statistics AS
            SELECT
                user_id,
                bucket_month,
                source_type,
                transaction_type,
                currency,
                direction,
                SUM(amount) AS amount_sum,
                COUNT(*) AS transaction_count
            FROM (
                SELECT
                    user_id,
                    date_trunc('month', transaction_time) AS bucket_month,
                    'WALLET' AS source_type,
                    transaction_type AS transaction_type,
                    coalesce(from_currency, to_currency) AS currency,
                    amount AS amount,
                    CASE
                        WHEN transaction_type = 'DEPOSIT' THEN 'INCOMING'
                        WHEN transaction_type = 'WITHDRAWAL' THEN 'OUTGOING'
                    END AS direction
                FROM wallet_transaction
                WHERE transaction_type IN ('DEPOSIT', 'WITHDRAWAL')

                UNION ALL

                SELECT
                    user_id,
                    date_trunc('month', transaction_time) AS bucket_month,
                    'WALLET' AS source_type,
                    transaction_type AS transaction_type,
                    from_currency AS currency,
                    amount AS amount,
                    'OUTGOING' AS direction
                FROM wallet_transaction
                WHERE transaction_type = 'TRANSFER'

                UNION ALL

                SELECT
                    counterparty_user_id AS user_id,
                    date_trunc('month', transaction_time) AS bucket_month,
                    'WALLET' AS source_type,
                    transaction_type AS transaction_type,
                    to_currency AS currency,
                    received_amount AS amount,
                    'INCOMING' AS direction
                FROM wallet_transaction
                WHERE transaction_type = 'TRANSFER'

                UNION ALL

                SELECT
                    user_id,
                    date_trunc('month', transaction_time) AS bucket_month,
                    'CARD' AS source_type,
                    transaction_type AS transaction_type,
                    approved_currency AS currency,
                    approved_amount AS amount,
                    CASE
                        WHEN transaction_type = 'PAYMENT' THEN 'OUTGOING'
                        WHEN transaction_type IN ('REFUND', 'DEPOSIT') THEN 'INCOMING'
                    END AS direction
                FROM card_transaction
                WHERE transaction_type IN ('PAYMENT', 'REFUND', 'DEPOSIT')

                UNION ALL

                SELECT
                    user_id,
                    date_trunc('month', completed_at) AS bucket_month,
                    'EXCHANGE' AS source_type,
                    status AS transaction_type,
                    from_currency AS currency,
                    amount AS amount,
                    'OUTGOING' AS direction
                FROM exchange_transaction
                WHERE status = 'COMPLETED'
                  AND completed_at IS NOT NULL

                UNION ALL

                SELECT
                    user_id,
                    date_trunc('month', completed_at) AS bucket_month,
                    'EXCHANGE' AS source_type,
                    status AS transaction_type,
                    to_currency AS currency,
                    received_amount AS amount,
                    'INCOMING' AS direction
                FROM exchange_transaction
                WHERE status = 'COMPLETED'
                  AND completed_at IS NOT NULL
            ) statistics_source
            WHERE user_id IS NOT NULL
              AND currency IS NOT NULL
              AND amount IS NOT NULL
              AND direction IS NOT NULL
            GROUP BY user_id, bucket_month, source_type, transaction_type, currency, direction
            WITH NO DATA
        $mv$;

        EXECUTE $idx$
            CREATE UNIQUE INDEX IF NOT EXISTS ux_mv_transaction_monthly_statistics
            ON mv_transaction_monthly_statistics (
                user_id,
                bucket_month,
                source_type,
                transaction_type,
                currency,
                direction
            )
        $idx$;
    END IF;
END $$;
