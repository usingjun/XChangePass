# Transaction Monitoring Index Comparison - 2026-06-27

## Dataset
- Events: 1000000
- Range: 2026-06-26T00:00 ~ 2026-06-27T00:00
- Bucket: 1 hour
- Mode: Java grouping baseline with PostgreSQL index candidates

## Result
| Index case | Source | Summary | Diff | Elapsed ms | Events/sec |
|---|---:|---:|---:|---:|---:|
| NO_EXTRA_INDEX | 1000000 | 1000000 | 0 | 2822 | 354358.61 |
| BRIN | 1000000 | 1000000 | 0 | 2537 | 394166.34 |
| BTREE | 1000000 | 1000000 | 0 | 2319 | 431220.35 |
| BRIN_AND_BTREE | 1000000 | 1000000 | 0 | 2377 | 420698.36 |

## Decision
- Adopt `BTREE (occurred_at, id)` as the default production index for hourly transaction status event summaries.
- This benchmark measures the current Java grouping summary cycle across 24 hourly buckets, and `BTREE` showed the lowest total elapsed time.
- Keep `BRIN (occurred_at)` as a follow-up candidate for very large append-only event tables where index size and broad time-range scans become more important.

## Explain Summary
- `NO_EXTRA_INDEX`: ->  Sort  (cost=21893.48..21937.16 rows=17472 width=158) (actual time=3.074..3.422 rows=13889 loops=3) / Sort Key: occurred_at, id / Sort Method: quicksort  Memory: 3204kB / Worker 0:  Sort Method: quicksort  Memory: 2160kB / Worker 1:  Sort Method: quicksort  Memory: 2168kB / ->  Parallel Bitmap Heap Scan on transaction_status_event  (cost=1114.25..20662.34 rows=17472 width=158) (actual time=0.441..2.323 rows=13889 loops=3) / ->  Bitmap Index Scan on idx_tx_status_event_time_failure  (cost=0.00..1103.76 rows=41934 width=0) (actual time=1.233..1.234 rows=41667 loops=1) / Planning Time: 0.111 ms / Execution Time: 9.621 ms
- `BRIN`: ->  Sort  (cost=20767.49..20809.18 rows=16677 width=158) (actual time=1.945..2.268 rows=13889 loops=3) / Sort Key: occurred_at, id / Sort Method: quicksort  Memory: 4062kB / Worker 0:  Sort Method: quicksort  Memory: 1772kB / Worker 1:  Sort Method: quicksort  Memory: 1697kB / ->  Parallel Bitmap Heap Scan on transaction_status_event  (cost=22.23..19597.97 rows=16677 width=158) (actual time=0.088..1.078 rows=13889 loops=3) / ->  Bitmap Index Scan on idx_tx_status_event_occurred_at_brin  (cost=0.00..12.22 rows=46358 width=0) (actual time=0.057..0.057 rows=8960 loops=1) / Planning Time: 0.110 ms / Execution Time: 8.350 ms
- `BTREE`: ->  Sort  (cost=21898.33..21942.71 rows=17752 width=158) (actual time=1.760..2.086 rows=13889 loops=3) / Sort Key: occurred_at, id / Sort Method: quicksort  Memory: 3550kB / Worker 0:  Sort Method: quicksort  Memory: 1998kB / Worker 1:  Sort Method: quicksort  Memory: 1983kB / ->  Parallel Bitmap Heap Scan on transaction_status_event  (cost=1093.14..20645.42 rows=17752 width=158) (actual time=0.330..1.093 rows=13889 loops=3) / ->  Bitmap Index Scan on idx_tx_status_event_occurred_at_id  (cost=0.00..1082.48 rows=42606 width=0) (actual time=0.922..0.923 rows=41667 loops=1) / Planning Time: 0.123 ms / Execution Time: 8.342 ms
- `BRIN_AND_BTREE`: ->  Sort  (cost=20858.74..20903.35 rows=17844 width=158) (actual time=1.957..2.287 rows=13889 loops=3) / Sort Key: occurred_at, id / Sort Method: quicksort  Memory: 3951kB / Worker 0:  Sort Method: quicksort  Memory: 1805kB / Worker 1:  Sort Method: quicksort  Memory: 1774kB / ->  Parallel Bitmap Heap Scan on transaction_status_event  (cost=22.93..19598.67 rows=17844 width=158) (actual time=0.082..1.284 rows=13889 loops=3) / ->  Bitmap Index Scan on idx_tx_status_event_occurred_at_brin  (cost=0.00..12.22 rows=46358 width=0) (actual time=0.050..0.050 rows=8960 loops=1) / Planning Time: 0.106 ms / Execution Time: 7.944 ms

## Files
- CSV: `docs/benchmarks/transaction-monitoring-index-comparison-2026-06-27.csv`
