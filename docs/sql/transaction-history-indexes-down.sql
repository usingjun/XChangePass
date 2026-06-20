-- Run each DROP INDEX CONCURRENTLY statement outside a transaction.
DROP INDEX CONCURRENTLY IF EXISTS idx_exchange_tx_user_completed;
DROP INDEX CONCURRENTLY IF EXISTS idx_card_tx_user_time;
DROP INDEX CONCURRENTLY IF EXISTS idx_wallet_tx_counterparty_time;
DROP INDEX CONCURRENTLY IF EXISTS idx_wallet_tx_user_time;
