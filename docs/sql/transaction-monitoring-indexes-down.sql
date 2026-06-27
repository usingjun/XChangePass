-- Drop transaction monitoring production index.
-- Run outside a transaction because DROP INDEX CONCURRENTLY cannot run inside one.

DROP INDEX CONCURRENTLY IF EXISTS idx_tx_status_event_occurred_at_id;
