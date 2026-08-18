# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

XChangePass ("XCP") is a Spring Boot backend for a simulated foreign-exchange service: real-time exchange rate sync, multi-currency wallets, wallet-to-wallet transfers, card issuance/payment, and transaction monitoring/recovery.

**This is a learning/verification project, not a production service.** When writing docs, comments, or commit messages, don't phrase things as real operational incidents or real user traffic (e.g. avoid "장애 발생 시 운영 대응", "실제 트래픽 대응"). Prefer phrasing like "동시성 테스트를 통해 검증", "장애 상황을 가정한 복구 구조 설계" — see `docs/agent/project-context.md` for the exact wording conventions.

The core concern of this codebase is **correctness of financial data**, not feature velocity: transaction consistency, wallet balance integrity, ledger consistency, concurrency control, idempotency, failure recovery, fail-closed fraud detection, and large-scale transaction-history query performance. Treat these as the review bar for any change that touches wallet/transfer/fraud/recovery code.

## Commands

```bash
./gradlew build                 # full build
./gradlew test                  # run all tests (JUnit 5 via useJUnitPlatform)
./gradlew test --tests "bumblebee.xchangepass.domain.wallet.transfer.WalletTransferIdempotencyServiceTest"   # single test class
./gradlew test --tests "*WalletTransferIdempotencyServiceTest.duplicateKeyReturnsExistingResult"             # single test method
./gradlew bootRun                # run the app locally
```

Many tests in `domain/wallet/**` and `domain/transaction/statistics/**` use Testcontainers (Postgres, Redis via `config/RedisTestBase`) and require Docker running locally. Several are explicitly named `*BenchmarkTest` / `*ExplainAnalyzeTest` / `*ChaosBenchmarkTest` — these are perf/chaos experiments (see `docs/benchmarks/`), not part of a required fast test loop; don't assume they need to pass for a normal change.

There is no lint/format task wired into Gradle — match existing style by hand.

## Working conventions (from AGENTS.md / docs/agent)

This repo has an existing agent-rules doc set at `docs/agent/` (`project-context.md`, `xchangepass-rules.md`, `prompt-cache-rules.md`, `task-template.md`) written for Codex but equally applicable here. Highlights:

- Read existing code/structure before changing it; make the smallest change that solves the problem. Don't mix in unrelated refactoring or architecture changes that weren't requested.
- Follow existing package structure, naming, and code style.
- If you change business logic, add or update tests for it.
- Don't introduce technologies not already present in `build.gradle`.
- High-risk areas that require reading existing flow *before* touching: Transfer, Wallet, Balance, Ledger, transaction status, Idempotency, Fraud Detection, Redis, Circuit Breaker, Recovery batch, unified transaction-history query, schema/Flyway migrations, external API integration.
- Default verification command is `./gradlew test`; scope to related tests when a full run isn't warranted.

## Architecture

Package root: `bumblebee.xchangepass`. Standard layered structure per domain: `controller` / `service` / `repository` / `entity` / `dto`. Cross-cutting stuff (security, error handling, AOP, schedulers, config) lives under `global/`.

Domains under `domain/`: `card`, `cardTransaction`, `exchangeRate`, `exchangeTransaction`, `monitoring`, `refresh` (JWT refresh tokens), `transaction` (unified transaction-history read model + statistics), `user`, `wallet`.

### Wallet domain (the highest-risk area)

`domain/wallet/` is split into sub-modules, each with its own service layer:

- `wallet/` — wallet CRUD, charge/withdraw, scheduled transfers.
- `balance/` — wallet balance entity/service (the fast-read snapshot).
- `transfer/` — wallet-to-wallet transfer: `WalletTransferLifecycleService` (state machine), `WalletTransferOperationsService`, `WalletTransferIdempotencyService` + `WalletTransferRequestHasher` (Idempotency-Key handling), `WalletTransferValidationService`, `WalletTransferFailureClassifier`/`WalletTransferFailureService`, `WalletTransferReservation(Writer)`. Sub-package `transfer/recovery/` holds the stale-transfer recovery batch (`WalletTransferRecoveryService`, `WalletTransferRecoveryCaseService`, a scheduler, and an "operational exception" case entity for ambiguous transfers that must NOT be auto-retried).
- `fraud/` — Redis+Lua-based fraud/anomaly detection (`FraudRuleEvaluator`, `FraudPolicyProperties`, Lua script at `src/main/resources/lua/fraud_check.lua`), wrapped in a Resilience4j circuit breaker (`fraudRedis` instance, configured in `src/main/resources/config/application.properties`).
- `reconciliation/` — wallet-balance-vs-ledger reconciliation (snapshot vs. source-of-truth check).

**The system of record is the transaction ledger, not the wallet balance snapshot.** The balance row exists for fast reads; whenever balance and ledger can diverge, treat the ledger as authoritative and consider reconciliation implications. Key invariants (see `docs/agent/xchangepass-rules.md` for the full, authoritative list):

- Debit sender, credit receiver, and ledger write happen in one funds transaction; a successful transfer produces exactly one ledger entry; a failed transfer must not partially apply to either wallet.
- When locking two wallets, always acquire locks in ascending wallet-ID order (deadlock avoidance) using PostgreSQL `pg_advisory_xact_lock` (transaction-scoped, auto-released on commit/rollback — not session-scoped, not released manually in `finally`).
- Idempotency-Key: same user + same key must not double-transfer; same key + different request body is a conflict (compare via request-body hash — `WalletTransferRequestHasher`); in-flight duplicates return current status, completed duplicates return the stored result, without re-running balance changes, ledger writes, or fraud checks.
- Transfer status is a multi-step state machine (`REQUESTED → VALIDATING → PROCESSING → COMPLETED/FAILED`), not a boolean; a completed transfer must never be forced back to failed.
- Recovery batch: only transfers that are clearly pre-funds-movement (`REQUESTED`/`VALIDATING` with no ledger entry) may be auto-failed. `PROCESSING`, or anything where ledger existence is ambiguous, must be routed to an operational-exception case instead of retried automatically. The batch must be idempotent/safe under concurrent runs and must not misclassify a recently-completed transfer as stale.
- Fraud detection is fail-closed: if Redis or the fraud pipeline is unstable, block the transfer rather than allow it; no balance/ledger writes may happen while fraud status is unknown; when the circuit breaker is OPEN, don't hammer Redis — wait for HALF_OPEN validation before resuming.
- Large transaction-history queries should prefer keyset pagination over offset pagination, use indexes that lead with the user-scoping predicate, and use time+id as a stable sort key; when merging results across multiple transaction tables, fetch bounded candidates from each table first and merge rather than scanning everything.

### Transaction-history / statistics

`domain/transaction/` holds the unified transaction-history read path and `domain/transaction/statistics/` holds aggregation/materialized-view work (see `V3__add_transaction_monthly_statistics_materialized_view.sql`, `V4__add_transaction_monthly_summary.sql`, and `global/config/TransactionStatisticsReplicaJdbcConfig` for the optional read-replica JDBC datasource). Extensive perf investigation for this path lives in `docs/benchmarks/*.md` and `docs/*.md` (e.g. `refactoring-roadmap.md`, `financial-transaction-hardening.md`) — check there before re-deriving index/pagination decisions from scratch.

### Persistence & migrations

- PostgreSQL (production/dev via Docker), H2 (in-memory, test convenience). Flyway migrations live in `src/main/resources/db/migration/postgresql/`; `spring.flyway.baseline-on-migrate=true`.
- QueryDSL is used for dynamic queries (annotation-processor generated `Q` types — run a Gradle build to regenerate after entity changes).

### Security & external integration

- Spring Security + JWT (`io.jsonwebtoken`), refresh tokens live in `domain/refresh/`. Card number/CVC and other sensitive fields are double-encrypted; AWS KMS (`global/config/KmsConfig.java`) is used for key management.
- Monitoring/ops: Micrometer + Prometheus, Grafana dashboards (`grafana/`, `infra/`), Loki log shipping (`logstash-logback-encoder` / `loki-logback-appender`).
- Resilience4j is used for circuit-breaking Redis calls in the fraud pipeline — tune via `resilience4j.circuitbreaker.instances.fraudRedis.*` / `resilience4j.retry.instances.fraudRedis.*` in `src/main/resources/config/application.properties`, not by editing thresholds in code.

## Testing conventions

- `RedisTestBase` (in `src/test/java/bumblebee/xchangepass/config/`) spins up a Testcontainers Redis and wires `spring.data.redis.host/port` dynamically — extend it for tests that need real Redis/Lua behavior instead of mocking Redis.
- For wallet/transfer/fraud/recovery changes, mirror the existing test shape rather than just adding a happy-path test — e.g. concurrent-transfer balance-drift tests, idempotency conflict tests (same key/different body), recovery-batch misclassification tests, and Redis-outage/circuit-breaker tests already exist for these areas and are the pattern to extend.
