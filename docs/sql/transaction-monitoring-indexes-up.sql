-- PostgreSQL production index for transaction monitoring hourly summaries.
-- Run outside a transaction because CREATE INDEX CONCURRENTLY cannot run inside one.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_tx_status_event_occurred_at_id
ON transaction_status_event (occurred_at, id);

-- Follow-up candidate for very large append-only tables:
-- CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_tx_status_event_occurred_at_brin
-- ON transaction_status_event
-- USING brin (occurred_at);
