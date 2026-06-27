# Transaction Monitoring Aggregation Comparison - 2026-06-27

## Dataset
- Events: 1000000
- Range: 2026-06-26T00:00 ~ 2026-06-27T00:00
- Bucket: 1 hour
- Index: BTREE (occurred_at, id)

## Result
| Aggregation mode | Source | Summary | Diff | Summary rows | Elapsed ms | Events/sec |
|---|---:|---:|---:|---:|---:|---:|
| JAVA_GROUPING | 1000000 | 1000000 | 0 | 336 | 2866 | 348918.35 |
| DB_GROUP_BY | 1000000 | 1000000 | 0 | 336 | 566 | 1766784.45 |

## Decision
- Adopt `DB_GROUP_BY` as the default hourly transaction monitoring summary path.
- Keep `JAVA_GROUPING` as a benchmark and fallback path for correctness comparison.
- With 1,000,000 events and `BTREE (occurred_at, id)`, `DB_GROUP_BY` was about 5.06x faster than Java grouping.

## Files
- CSV: `docs/benchmarks/transaction-monitoring-aggregation-comparison-2026-06-27.csv`
