# Transaction History Step 3 Benchmark

## Reproduction

```bash
docker run --rm -d --name xcp-benchmark-postgres \
  -e POSTGRES_USER=benchmark \
  -e POSTGRES_PASSWORD=benchmark \
  -e POSTGRES_DB=xcp_benchmark \
  -p 55432:5432 postgres:16

TOTAL_RECORDS=1000000 \
PAGE_SIZE=50 \
SOURCE_LIMIT=51 \
WARMUP=50 \
RUNS=300 \
PG_URL=jdbc:postgresql://127.0.0.1:55432/xcp_benchmark \
PG_USER=benchmark \
PG_PASSWORD=benchmark \
bash tools/perf/run-transaction-read-benchmark.sh
```

The run used PostgreSQL 16, 1,000,000 deterministic synthetic rows distributed across 100 users, a 50-row response page, one extra row per source, 50 warm-up executions, and 300 measured executions.

## Primary result

| Query | Average | p95 | Min | Max | Throughput |
| --- | ---: | ---: | ---: | ---: | ---: |
| Baseline union then global sort | 4.607ms | 6.242ms | 3.637ms | 7.679ms | 217.1 req/s |
| Per-source Top-N then merge | 0.228ms | 0.265ms | 0.184ms | 0.649ms | 4,394.6 req/s |
| Four-source sent/received/card/exchange Top-N | 0.239ms | 0.279ms | 0.203ms | 0.487ms | 4,181.3 req/s |

- Average improvement: `20.25x`
- p95 improvement: `23.55x`
- The portfolio should use the reproducible `about 20x` claim for this environment, not the older `about 35x` claim.

## Plan evidence

Baseline `EXPLAIN (ANALYZE, BUFFERS)` read about 10,001 user rows from the three source tables and performed a top-N sort after the append. The measured plan execution was 6.861ms.

The optimized query performed ordered index scans capped at 50 rows per source before appending 150 rows. The measured plan execution was 0.176ms.

The production four-source shape read at most 51 sent wallet, 51 received wallet, 51 card, and 51 completed exchange rows before the final 50-row merge. Its measured plan execution was 0.129ms.

## Index decisions

- Keep the user/time/id and counterparty/time/id B-tree indexes.
- Do not add broad currency or amount indexes. They did not improve the combined workload consistently.
- Do not add GIN trigram for the current user-scoped merchant lookup; average latency regressed from 0.146ms to 3.050ms.
- BRIN improved broad settlement scans but not user Top-N, so defer it until a settlement API exists.
- The completed-exchange partial index improved this isolated workload, but remains deferred because the approved Step 3 production DDL keeps the general completed-time index and the project has no managed migration framework yet.
