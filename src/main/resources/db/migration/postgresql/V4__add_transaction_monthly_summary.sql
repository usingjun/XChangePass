CREATE TABLE IF NOT EXISTS transaction_monthly_summary (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    bucket_month DATE NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    amount_sum NUMERIC(19, 4) NOT NULL,
    transaction_count BIGINT NOT NULL,
    data_as_of TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_transaction_monthly_summary_key
ON transaction_monthly_summary (
    user_id,
    bucket_month,
    source_type,
    transaction_type,
    currency,
    direction
);

CREATE INDEX IF NOT EXISTS ix_transaction_monthly_summary_user_month
ON transaction_monthly_summary(user_id, bucket_month);

CREATE INDEX IF NOT EXISTS ix_transaction_monthly_summary_month_source_type
ON transaction_monthly_summary(bucket_month, source_type, transaction_type);

CREATE INDEX IF NOT EXISTS ix_transaction_monthly_summary_currency_month
ON transaction_monthly_summary(currency, bucket_month);
