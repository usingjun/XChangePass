-- Run each CREATE INDEX CONCURRENTLY statement outside a transaction.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_tx_user_time
    ON wallet_transaction (user_id, transaction_time DESC, transaction_id DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_tx_counterparty_time
    ON wallet_transaction (counterparty_user_id, transaction_time DESC, transaction_id DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_card_tx_user_time
    ON card_transaction (user_id, transaction_time DESC, transaction_id DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_exchange_tx_user_completed
    ON exchange_transaction (user_id, completed_at DESC, transaction_id DESC);
