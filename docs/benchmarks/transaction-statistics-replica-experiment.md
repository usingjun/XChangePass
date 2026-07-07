# 거래 통계 조회 Read Replica 시험 적용 결과

## 1. 목적

이 문서는 XChangePass 거래 통계 조회를 대상으로 PostgreSQL Primary + Read Replica 구조를 로컬 검증 환경에서 시험 적용하기 위한 변경 사항과 실행 방법을 정리한다.

운영용 Primary/Replica 도입이 아니며, 운영 반영 결과도 아니다. 목적은 `MATERIALIZED_VIEW` 기반 통계 조회를 Read Replica 후보로 분리했을 때, 송금 write path가 Primary에서 더 안정적으로 유지되는지 전후 비교할 수 있는 구조를 준비하는 것이다.

비교 기준은 아래와 같다.

* Before: Single PostgreSQL DB + `MATERIALIZED_VIEW`
* After: PostgreSQL Primary + Read Replica + `MATERIALIZED_VIEW`

## 2. 왜 MATERIALIZED_VIEW 기준으로 실험하는지

기존 검증에서 `MATERIALIZED_VIEW`는 조회 안정성, refresh 비용, maintenance 부담의 균형 면에서 가장 무난한 후보로 정리됐다.

`SUMMARY`는 조회 성능은 안정적이지만 refresh 반복 시 dead tuple과 vacuum/analyze 관리가 필요하다. 따라서 이번 Replica 실험에서는 메인 후보가 아니라 보조 후보로 둔다.

`GROUP_BY`는 원본 거래 테이블을 직접 읽고 집계하므로 baseline/검증용 경로로 남긴다. 이번 Replica 전후 비교의 메인 대상은 아니다.

## 3. 비교군 A: Single DB + MV

Single DB 조건에서는 송금 API와 `MATERIALIZED_VIEW` 기반 통계 API가 같은 PostgreSQL DB를 사용한다.

실행 예시는 아래와 같다.

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/xchangepass \
SPRING_DATASOURCE_USERNAME=postgres \
SPRING_DATASOURCE_PASSWORD=postgres \
TRANSACTION_STATISTICS_REPLICA_ENABLED=false \
./gradlew bootRun
```

k6는 `MODE=MATERIALIZED_VIEW`로 실행한다.

```bash
SCENARIO_LABEL=single-db-mv \
BASE_URL=http://localhost:8080 \
MODE=MATERIALIZED_VIEW \
TRANSFER_RATE=10 \
STATISTICS_RATE=10 \
DURATION=30s \
tools/perf/run-transaction-transfer-statistics-replica-car.sh
```

## 4. 비교군 B: Primary + Replica + MV

Primary + Replica 조건에서는 송금 API와 기존 write path가 Primary DB를 사용하고, `mode=MATERIALIZED_VIEW` 통계 조회만 Read Replica DB를 사용한다.

로컬 검증용 PostgreSQL Primary/Replica는 별도 compose 파일로 준비한다.

```bash
docker compose -f infra/docker-compose.transaction-statistics-replica.yml up -d
```

애플리케이션 실행 예시는 아래와 같다.

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/xchangepass \
SPRING_DATASOURCE_USERNAME=postgres \
SPRING_DATASOURCE_PASSWORD=postgres \
TRANSACTION_STATISTICS_REPLICA_ENABLED=true \
TRANSACTION_STATISTICS_REPLICA_URL=jdbc:postgresql://localhost:15433/xchangepass \
TRANSACTION_STATISTICS_REPLICA_USERNAME=postgres \
TRANSACTION_STATISTICS_REPLICA_PASSWORD=postgres \
./gradlew bootRun
```

k6는 같은 rate/duration 조건으로 실행한다.

```bash
SCENARIO_LABEL=primary-replica-mv \
BASE_URL=http://localhost:8080 \
MODE=MATERIALIZED_VIEW \
TRANSFER_RATE=10 \
STATISTICS_RATE=10 \
DURATION=30s \
tools/perf/run-transaction-transfer-statistics-replica-car.sh
```

위 비밀번호는 로컬 검증용 compose 기본값 예시다. 운영 secret/env가 아니며, 운영 설정에 반영하지 않는다.

## 5. Primary 유지 대상

아래 경로는 반드시 Primary를 사용한다.

* 송금 처리
* 잔액 변경
* 원장 저장
* `Idempotency-Key` 판단
* recovery 판단
* reconciliation 판단
* 최신 거래 상태 판단
* 최신 잔액 판단
* fraud 판단과 write path 차단
* 정합성이 강하게 필요한 write path

송금/잔액/원장/recovery/reconciliation/idempotency/latest state 판단은 stale read를 허용할 수 없으므로 Primary write model 기준으로 유지한다.

## 6. Replica routing 대상

이번 시험 적용에서 Replica routing 대상은 아래 하나로 제한한다.

* `GET /api/v1/transactions/statistics/monthly?mode=MATERIALIZED_VIEW`

`mode=GROUP_BY`와 `mode=SUMMARY`는 기존 primary `JdbcTemplate` 경로를 유지한다.

`@Transactional(readOnly = true)` 전체를 Read Replica로 보내지 않는다. readOnly transaction 전체를 Replica로 보내면 latest state 판단, idempotency 확인, recovery 판단 같은 stale read 금지 경로가 잘못 Replica로 갈 수 있기 때문이다.

구현은 `transaction.statistics.replica.enabled=true`일 때만 replica 전용 `JdbcTemplate`을 생성하고, `MATERIALIZED_VIEW` 조회 메서드에서만 이 `JdbcTemplate`을 사용한다. replica 설정이 비활성화되어 있으면 기존 primary `JdbcTemplate`으로 fallback한다.

## 7. k6 constant-arrival-rate 설계

기존 혼합 부하 스크립트는 같은 VU loop 안에서 송금과 통계를 순차 실행했다. 이 구조에서는 통계 API 응답 시간이 느려질 때 송금 요청 발생 간격도 함께 달라질 수 있으므로 mode별 송금 latency를 직접 비교하기 어렵다.

이번 스크립트는 송금 scenario와 통계 scenario를 분리하고 각각 `constant-arrival-rate`로 요청 발생률을 고정한다.

파일:

```text
k6-scripts/transaction-transfer-statistics-replica-car.js
tools/perf/run-transaction-transfer-statistics-replica-car.sh
```

수집 metric:

* `transfer_duration`
* `statistics_duration`
* `transfer_error_rate`
* `statistics_error_rate`

송금 성공 기준:

* HTTP 200
* response body에 `COMPLETED` 포함
* 요청마다 unique `Idempotency-Key`

통계 성공 기준:

* HTTP 200
* `mode=MATERIALIZED_VIEW`

주요 환경 변수:

* `BASE_URL`
* `MODE`
* `TRANSFER_RATE`
* `STATISTICS_RATE`
* `DURATION`
* `TRANSFER_VUS`
* `STATISTICS_VUS`
* `TRANSFER_MAX_VUS`
* `STATISTICS_MAX_VUS`
* `TRANSFER_AUTH_ENV`
* `STATS_AUTH_ENV`

auth env와 token은 로컬 산출물이며 커밋하지 않는다.

## 8. 실행 조건과 유효성 기준

이번 실행은 로컬 개발 장비 기준 A/B 실험이다. 운영 환경 결과가 아니며, 운영 DB 설정이나 운영 env는 변경하지 않았다.

성능 비교 결과로 채택하는 기준은 아래와 같다.

* A/B 모두 `mode=MATERIALIZED_VIEW`
* A/B 모두 송금 error 0
* A/B 모두 통계 error 0
* 송금 scenario와 통계 scenario는 각각 `constant-arrival-rate`로 분리
* A/B 실행 전 DB fixture를 새로 생성
* Primary + Replica 조건에서 Primary/Replica MV row count 동일
* Primary + Replica 조건에서 WAL byte lag 0 또는 사유 명확

송금 실패가 1건이라도 있는 run은 송금 write path 안정성 비교 근거로 사용하지 않고, 실패 원인 분석 대상으로 분리한다.

## 9. 이전 실행 실패 원인 분석

2026-07-06 최초 실행과 2026-07-07 재실행에서 `Primary + Replica + MV` 조건에 송금 실패 1건이 있었다. 이 run은 아래 이유로 A/B 성능 비교 결과에서 제외한다.

| 구분 | 결과 |
| --- | --- |
| 실패 조건 | Primary + Replica + MV |
| 실패 수 | transfer failed 1건 |
| statistics failed | 0건 |
| Primary/Replica MV rows | 83,510 / 83,510 |
| WAL byte lag | 0 |

직전 실패 run의 DB 상태를 확인한 결과는 아래와 같다.

```text
status   | failure_stage   | failure_code            | count
---------|-----------------|-------------------------|------
COMPLETED|                 |                         | 299
FAILED   | FRAUD_VALIDATION| SUSPICIOUS_TRANSACTION  | 1
```

상태 이벤트도 같은 원인을 가리켰다.

```text
event_type           | current_status | failure_stage   | error_code             | count
---------------------|----------------|-----------------|------------------------|------
FRAUD_CHECK_BLOCKED  | VALIDATING     | FRAUD_VALIDATION| SUSPICIOUS_TRANSACTION | 1
FAILED               | FAILED         | FRAUD_VALIDATION| SUSPICIOUS_TRANSACTION | 1
```

따라서 이전 실패는 Replica routing이 송금 경로에 잘못 적용된 문제가 아니라, 송금 검증 단계의 Fraud 차단으로 분류한다. raw k6 결과에는 실패 response body가 남아 있지 않아 `frequency`, `repeated amount`, `night time` 중 세부 fraud rule까지는 확정하지 않는다.

이번 재실행에서는 소스 코드, Lua, 기본 운영 설정을 바꾸지 않았다. 로컬 실행 인자로만 fraud 야간 시간을 현재 실행 시간과 겹치지 않게 조정하고, 송금 rate를 낮춰 같은 사용자에게 짧은 시간에 반복 요청이 몰릴 가능성을 줄였다.

## 10. 유효 run 재실행 조건

공통 조건은 아래와 같다.

| 항목 | 값 |
| --- | --- |
| 실행일 | 2026-07-07 |
| PostgreSQL | `postgres:15` |
| Primary port | `15432` |
| Replica port | `15433` |
| Spring Boot port | `18080` |
| 통계 seed | 100,000 rows, 1,000 users, 12 months |
| 송금 seed | 2,000 sender/receiver pairs |
| 통계 mode | `MATERIALIZED_VIEW` |
| 송금 rate | 5/s |
| 통계 rate | 10/s |
| duration | 30s |
| transfer VUs | 20, max 50 |
| statistics VUs | 20, max 50 |
| fraud 야간 정책 | `--fraud.policy.night-start=12:00 --fraud.policy.night-end=12:01` |

송금 rate는 최초 후보였던 10/s에서 5/s로 낮췄다. 목적은 송금 API를 빠르게 보이게 만드는 것이 아니라, 실험 목적과 무관한 Fraud 차단을 피하고 `transfer error 0`, `statistics error 0` 조건을 먼저 만족시키는 것이다. A/B는 같은 rate와 같은 fixture 규모로 실행했다.

## 11. 유효 실행 결과

### 11.1 A: Single DB + MV

앱 설정:

```bash
TRANSACTION_STATISTICS_REPLICA_ENABLED=false
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/xchangepass
```

결과:

| Metric | avg | p95 | p99 | min | max | error |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| transfer_duration | 31.872ms | 41.588ms | 47.877ms | 16.902ms | 229.705ms | 0.00% |
| statistics_duration | 10.843ms | 15.205ms | 17.636ms | 5.336ms | 107.098ms | 0.00% |

요청 수:

| 항목 | 결과 |
| --- | ---: |
| total http requests | 452 |
| transfer requests | 151 |
| statistics requests | 301 |
| transfer failed | 0 |
| statistics failed | 0 |

### 11.2 B: Primary + Replica + MV

앱 설정:

```bash
TRANSACTION_STATISTICS_REPLICA_ENABLED=true
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/xchangepass
TRANSACTION_STATISTICS_REPLICA_URL=jdbc:postgresql://localhost:15433/xchangepass
```

실행 전 확인:

| 항목 | 결과 |
| --- | --- |
| Primary MV rows | 83,510 |
| Replica MV rows | 83,510 |
| replication state | streaming |
| sync_state | async |
| WAL byte lag before run | 0 |

결과:

| Metric | avg | p95 | p99 | min | max | error |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| transfer_duration | 30.399ms | 35.294ms | 50.767ms | 18.603ms | 251.514ms | 0.00% |
| statistics_duration | 29.016ms | 34.041ms | 36.879ms | 17.744ms | 132.453ms | 0.00% |

요청 수:

| 항목 | 결과 |
| --- | ---: |
| total http requests | 451 |
| transfer requests | 151 |
| statistics requests | 300 |
| transfer failed | 0 |
| statistics failed | 0 |

실행 후 확인:

| 항목 | 결과 |
| --- | --- |
| wallet_transfer_request status | `COMPLETED` 151건 |
| Primary MV rows | 83,510 |
| Replica MV rows | 83,510 |
| replication state | streaming |
| sync_state | async |
| WAL byte lag after run | 0 |

### 11.3 A/B 비교

아래 표는 송금 실패가 없는 유효 run만 사용한다.

| Scenario | Transfer avg | Transfer p95 | Transfer p99 | Transfer error | Statistics avg | Statistics p95 | Statistics p99 | Statistics error |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Single DB + MV | 31.872ms | 41.588ms | 47.877ms | 0.00% | 10.843ms | 15.205ms | 17.636ms | 0.00% |
| Primary + Replica + MV | 30.399ms | 35.294ms | 50.767ms | 0.00% | 29.016ms | 34.041ms | 36.879ms | 0.00% |

요청 수:

| Scenario | Transfer requests | Statistics requests |
| --- | ---: | ---: |
| Single DB + MV | 151 | 301 |
| Primary + Replica + MV | 151 | 300 |

이번 유효 run에서는 Replica 조건의 transfer avg/p95가 Single DB보다 낮고 p99는 높게 나왔다. 차이가 작고 30초 단일 실행이므로, Replica가 송금 응답을 개선한다고 단정하지 않는다.

반대로 통계 API는 Replica 조건에서 더 느렸다. 이 결과는 로컬 Docker Primary/Replica 구성, 별도 datasource connection pool, replica 컨테이너 상태, 캐시 warm-up 차이의 영향을 받을 수 있다. 이번 결과만으로 Replica 통계 조회가 항상 느리다고 단정하지 않는다.

## 12. Replica 통계 조회 지연 확인

Replica 조건에서 통계 API가 더 느리게 나온 원인을 단정하지 않기 위해 Primary/Replica의 MV 상태와 query plan을 간단히 확인했다.

MV index는 Primary와 Replica 모두 동일하게 존재했다.

```text
ux_mv_transaction_monthly_statistics
(user_id, bucket_month, source_type, transaction_type, currency, direction)
```

`pg_stat_user_tables` 기준으로 Primary는 `n_live_tup=83510`, `last_analyze` 값이 있었지만, Replica에서는 `n_live_tup=0`, `last_analyze=NULL`로 관측됐다. 이는 로컬 Replica의 planner 통계 관측이 Primary와 다르게 보인다는 점을 의미한다.

다만 같은 heavy user 12개월 MV 조회를 `EXPLAIN (ANALYZE, BUFFERS)`로 확인하면 Primary와 Replica 모두 index scan을 사용했다.

| DB | Plan | Rows | Buffers | Execution Time |
| --- | --- | ---: | --- | ---: |
| Primary | Index Scan | 397 | shared hit=11 read=6 | 0.125ms |
| Replica | Index Scan | 397 | shared hit=17 | 0.120ms |

따라서 이번 k6에서 Replica 통계 API latency가 더 높게 나온 현상을 SQL 실행 계획 자체 때문이라고 단정하기는 어렵다. 로컬 Docker 컨테이너 리소스, replica datasource connection pool, 네트워크 경로, 캐시 warm-up, 반복 실행 편차를 추가로 확인해야 한다.

## 13. 해석

이번 실행은 운영용 Read Replica 도입 결과가 아니라 로컬 검증용 시험 적용 결과다.

송금 write path는 Primary에 유지하고, 지연 허용 가능한 `MATERIALIZED_VIEW` 기반 거래 통계 조회만 Replica 후보로 분리했다. `mode=GROUP_BY`, `mode=SUMMARY`는 이번 Replica A/B 비교의 메인 대상이 아니다.

이번 유효 run에서 확인한 것은 아래 수준이다.

* `mode=MATERIALIZED_VIEW` 통계 조회만 Replica로 보내는 구조는 로컬에서 동작했다.
* 송금 write path는 Primary에 유지됐다.
* 송금 scenario와 통계 scenario를 분리한 `constant-arrival-rate` 조건에서 A/B 모두 error 0으로 실행됐다.
* Primary + Replica 조건에서 MV row count는 Primary/Replica 모두 83,510으로 같았다.
* Primary + Replica 조건에서 WAL byte lag는 실행 전후 0이었다.
* Replica 조건의 통계 API latency는 Single DB보다 높게 관측됐다.

따라서 이번 결과만으로 Primary/Replica 도입을 확정하지 않는다. 반복 실행, 더 긴 duration, 더 높은 rate, DB CPU/IO/lock/connection 지표, `pg_stat_activity`, `pg_stat_statements`가 필요하다.

## 14. 한계

* 운영 환경 결과가 아니다.
* Primary/Replica 운영 반영이 아니다.
* 30초 단일 유효 실행 결과다.
* 송금 rate를 5/s로 낮춘 조건이다.
* DB CPU/IO, lock wait, connection pool, `pg_stat_activity`, `pg_stat_statements`는 함께 수집하지 않았다.
* Replica lag는 WAL byte lag 중심으로만 확인했다.
* `dataAsOf` 정책은 후속 검토가 필요하다.
* Summary refresh와 송금 API 동시 실행 영향은 별도 시나리오가 필요하다.
* Replica 통계 API가 더 느리게 나온 원인은 아직 확정하지 않았다.
* 로컬 Docker Primary/Replica 구성의 성능 특성이 운영 환경과 다를 수 있다.

실험 준비 과정에서 이미 보완된 사항:

* k6 `Idempotency-Key`가 UUID 형식이 아니어서 송금 API가 400을 반환하던 문제를 UUID v4 형태로 수정했다.
* 송금 pair 선택이 앞쪽 사용자에게 몰려 Fraud Detection을 건드리던 문제를 랜덤 분산으로 수정했다.
* Fraud frequency 조건을 피하기 위해 송금 seed pair를 400개에서 2,000개로 늘려 실행했다.
* 이번 유효 run에서는 송금 rate를 10/s에서 5/s로 낮췄다.
* 로컬 compose 이미지는 사용 가능한 `postgres:15` 기준으로 맞췄고, replica base backup을 위해 replication user와 `pg_hba.conf` 설정을 초기화 스크립트로 준비했다.

## 15. 후속 과제

* 같은 조건으로 A/B를 3회 이상 반복 실행
* duration을 30s에서 3m 이상으로 늘려 재측정
* 송금 rate와 통계 rate를 단계적으로 높여 임계점 확인
* Primary/Replica DB connection active/idle 관측
* Primary lock wait 관측
* Replica wait event 관측
* `pg_stat_activity.wait_event` 수집
* `pg_stat_statements` 누적 변화 수집
* CPU/IO 관측
* Replica lag를 `pg_stat_replication`, `pg_last_xact_replay_timestamp()`, `dataAsOf` 기준으로 함께 측정
* MV refresh 주기와 `dataAsOf` 응답 정책 검토
* Replica 통계 latency가 Single DB보다 높게 나온 원인 추가 확인

## 16. 포트폴리오 반영 가능 문장

아래 문장은 운영 결과가 아니라 개발 및 검증 결과 기준으로 사용할 수 있다.

* 거래 통계 조회 검증 결과를 바탕으로 `MATERIALIZED_VIEW` 기반 통계 조회만 Read Replica 후보로 분리하는 로컬 시험 구조를 구성하고 A/B 실험을 수행했다.
* 송금/잔액/원장/idempotency/recovery/reconciliation/latest state 판단은 Primary write model에 유지하고, 지연 허용 가능한 통계 read model만 Replica 후보로 제한했다.
* k6 혼합 부하를 `constant-arrival-rate` 기반 송금 scenario와 통계 scenario로 분리해, 통계 요청 지연이 송금 요청 발생률을 흔드는 기존 loop 방식의 한계를 보완했다.
* 로컬 100k 통계 데이터, 2,000개 송금 pair 조건에서 Single DB + MV와 Primary/Replica + MV를 비교하고, 송금 latency와 통계 latency를 분리 측정했다.
* 송금 실패가 있는 run은 성능 비교 근거에서 제외하고, 실패 원인을 `FRAUD_VALIDATION / SUSPICIOUS_TRANSACTION`으로 분리 분석했다.

## 17. 피해야 할 표현

아래 표현은 피한다.

* 운영 환경에서 Replica를 도입했다.
* Read Replica 도입 완료
* 운영 조회 부하 분리 완료
* 송금 API 영향 없음 확정
* Replica 필수
* MV가 항상 최적
* Replica로 송금 API 영향이 사라졌다.
* 운영 조회 부하를 분리했다.
* Replica를 쓰면 통계 조회가 항상 빨라진다.

## 18. Replica 통계 지연 추가 확인

이전 유효 run에서는 `Primary + Replica + MV` 조건의 통계 API latency가 `Single DB + MV`보다 높게 관측됐다.

```text
Single DB + MV statistics avg/p95/p99: 10.843ms / 15.205ms / 17.636ms
Primary + Replica + MV statistics avg/p95/p99: 29.016ms / 34.041ms / 36.879ms
```

이번 추가 확인의 목적은 Replica를 더 좋게 보이게 만드는 것이 아니라, 이 차이가 반복되는지와 DB 내부 지표상 설명 가능한 차이가 있는지를 확인하는 것이다.

확인 항목은 아래와 같다.

* Primary/Replica MV row count
* Primary/Replica MV unique index
* Primary/Replica `EXPLAIN (ANALYZE, BUFFERS)`
* Primary/Replica `pg_stat_user_tables`
* Primary/Replica `pg_stat_activity` wait event
* `pg_stat_replication` WAL byte lag
* 간단한 Docker container CPU/memory snapshot

### 18.1 MV row count와 lag

반복 측정의 `Primary + Replica + MV` 조건 3회 모두 MV row count와 WAL byte lag는 아래처럼 같았다.

| Run | Primary MV rows | Replica MV rows | WAL byte lag |
| ---: | ---: | ---: | ---: |
| 1 | 83,510 | 83,510 | 0 |
| 2 | 83,510 | 83,510 | 0 |
| 3 | 83,510 | 83,510 | 0 |

### 18.2 MV index와 query plan

Primary와 Replica 모두 아래 unique index를 가지고 있었다.

```text
ux_mv_transaction_monthly_statistics
(user_id, bucket_month, source_type, transaction_type, currency, direction)
```

heavy user 12개월 조회 기준 `EXPLAIN (ANALYZE, BUFFERS)` 결과도 Primary와 Replica 모두 index scan이었다.

| Run | DB | Plan | Rows | Buffers | Planning Time | Execution Time |
| ---: | --- | --- | ---: | --- | ---: | ---: |
| 1 | Primary | Index Scan | 397 | shared hit=17 | 0.147ms | 0.071ms |
| 1 | Replica | Index Scan | 397 | shared hit=17 | 0.160ms | 0.077ms |
| 2 | Primary | Index Scan | 397 | shared hit=17 | 0.160ms | 0.072ms |
| 2 | Replica | Index Scan | 397 | shared hit=17 | 0.134ms | 0.069ms |
| 3 | Primary | Index Scan | 397 | shared hit=17 | 0.144ms | 0.083ms |
| 3 | Replica | Index Scan | 397 | shared hit=17 | 0.154ms | 0.069ms |

이 결과만 보면 Replica 통계 API가 더 느릴 이유를 SQL plan 자체에서 찾기는 어렵다.

### 18.3 table stats와 wait event

`pg_stat_user_tables`에서는 이전과 동일하게 Primary와 Replica의 MV 통계 관측값이 다르게 보였다.

| DB | n_live_tup | n_dead_tup | last_analyze | last_autoanalyze |
| --- | ---: | ---: | --- | --- |
| Primary | 83,510 | 0 | 있음 | 있음 |
| Replica | 0 | 0 | 없음 | 없음 |

다만 Replica의 `n_live_tup=0`, `last_analyze=NULL` 상태에서도 `EXPLAIN`은 Primary와 같은 index scan을 사용했고, 실행 시간도 비슷했다. 따라서 이번 반복 측정에서는 이 관측값 차이가 HTTP 통계 latency 차이로 직접 이어진다고 단정하지 않는다.

측정 후 `pg_stat_activity` wait event는 제한적으로만 관측됐다.

| DB | wait event 요약 |
| --- | --- |
| Primary | `ClientRead` 2건, wait 없음 1건 |
| Replica | wait 없음 1건 |

Docker snapshot도 낮은 CPU/memory 사용량만 보였다.

| Container | CPU range | Memory |
| --- | ---: | ---: |
| Primary | 0.04% ~ 0.07% | 약 94MiB |
| Replica | 0.15% ~ 0.21% | 약 89MiB |

`pg_stat_statements`는 이번 반복 실행에서 별도로 활성화하지 않았다. 운영 PostgreSQL 설정을 바꾸지 않는 원칙 때문에 local benchmark 환경에서 별도 준비가 필요하다.

## 19. A/B 반복 측정 결과

반복 측정은 이전 유효 run과 같은 기본 조건을 사용했다.

| 항목 | 값 |
| --- | --- |
| MODE | `MATERIALIZED_VIEW` |
| TRANSFER_RATE | 5/s |
| STATISTICS_RATE | 10/s |
| DURATION | 30s |
| 통계 seed | 100,000 rows / 1,000 users / 12 months |
| 송금 seed | 2,000 pairs |
| warm-up | 각 run 본 측정 전 통계 API 10회 |
| fraud 정책 | 런타임 인자로 야간 창을 `12:00~12:01`로 조정 |
| 실행 순서 | A1 -> B1 -> B2 -> A2 -> A3 -> B3 |

A/B 각 run 전에 DB volume을 초기화하고 같은 seed 규모를 다시 적재했다.

### 19.1 run별 결과

| Run | Scenario | Transfer avg | Transfer p95 | Transfer p99 | Transfer error | Statistics avg | Statistics p95 | Statistics p99 | Statistics error |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | Single DB + MV | 30.922ms | 39.360ms | 47.588ms | 0 | 10.259ms | 12.921ms | 16.141ms | 0 |
| 1 | Primary + Replica + MV | 27.364ms | 34.302ms | 37.960ms | 0 | 9.164ms | 11.101ms | 15.858ms | 0 |
| 2 | Single DB + MV | 26.396ms | 32.469ms | 38.091ms | 0 | 9.066ms | 11.417ms | 13.591ms | 0 |
| 2 | Primary + Replica + MV | 27.009ms | 34.186ms | 39.186ms | 0 | 8.929ms | 11.657ms | 13.928ms | 0 |
| 3 | Single DB + MV | 25.917ms | 33.470ms | 35.863ms | 0 | 8.929ms | 11.408ms | 12.501ms | 0 |
| 3 | Primary + Replica + MV | 25.398ms | 32.668ms | 37.783ms | 0 | 8.636ms | 11.529ms | 12.585ms | 0 |

요청 수:

| Scenario | Transfer requests total | Statistics requests total | Transfer requests avg | Statistics requests avg |
| --- | ---: | ---: | ---: | ---: |
| Single DB + MV | 452 | 901 | 150.667 | 300.333 |
| Primary + Replica + MV | 451 | 903 | 150.333 | 301.000 |

DB 유효성:

| Scenario | Transfer failed total | Statistics failed total | MV row count | WAL byte lag |
| --- | ---: | ---: | --- | --- |
| Single DB + MV | 0 | 0 | Primary 83,510 | 해당 없음 |
| Primary + Replica + MV | 0 | 0 | Primary/Replica 83,510 / 83,510 | 0 |

### 19.2 평균 집계

| Scenario | Transfer avg | Transfer p95 | Transfer p99 | Statistics avg | Statistics p95 | Statistics p99 | Error |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Single DB + MV | 27.745ms | 35.100ms | 40.514ms | 9.418ms | 11.915ms | 14.078ms | 0 |
| Primary + Replica + MV | 26.590ms | 33.719ms | 38.309ms | 8.910ms | 11.429ms | 14.124ms | 0 |

편차:

| Scenario | Transfer avg stdev | Statistics avg stdev |
| --- | ---: | ---: |
| Single DB + MV | 2.255ms | 0.597ms |
| Primary + Replica + MV | 0.856ms | 0.216ms |

## 20. 반복 측정 해석

반복 측정에서는 이전 단일 유효 run에서 보였던 `Primary + Replica + MV` 통계 API 지연 증가가 재현되지 않았다.

이번 반복 측정은 각 run 본 측정 전에 통계 API warm-up 10회를 넣었다. 따라서 이전 단일 run에서 Replica 통계 API가 더 느리게 나온 현상은 Replica SQL plan 자체보다는 datasource connection pool 초기화, 캐시 warm-up, 로컬 Docker 상태, 실행 순서, 짧은 30초 단일 측정 편차의 영향을 받았을 가능성이 있다.

다만 이번 반복 측정에서도 Read Replica가 성능을 개선한다고 단정하지 않는다.

* 30초 단기 local run이다.
* 송금 rate는 5/s로 낮춘 조건이다.
* HTTP/k6 기준이며 DB CPU/IO를 정밀하게 수집하지 않았다.
* `pg_stat_statements`를 함께 수집하지 않았다.
* 운영 환경 결과가 아니다.
* 통계 API warm-up을 넣은 조건이므로 cold start 성능과는 다르다.

이번 결과에서 말할 수 있는 범위는 아래 정도다.

* MV 기반 통계 조회만 Replica로 보내는 구조는 반복 run에서도 동작했다.
* 송금 write path는 Primary에 유지됐고, 3회 반복 모두 송금 실패는 없었다.
* Primary/Replica MV row count는 동일했고 WAL byte lag는 0이었다.
* warm-up 이후 30초 반복 측정에서는 Single DB와 Primary/Replica의 통계 API latency가 비슷한 범위로 수렴했다.
* SQL plan은 Primary/Replica 모두 index scan으로 유사했다.

## 21. 현재 기준 Replica 적용 판단

현재 기준으로는 Read Replica 적용을 확정하지 않는다.

거래 통계 조회는 최신 잔액, 최신 원장, recovery, reconciliation, idempotency 판단에 쓰지 않는 파생 read model이므로 Replica 후보가 될 수 있다. 하지만 이번 local 반복 측정만으로 Replica가 통계 API latency를 확실히 개선하거나 송금 write path 성능을 확실히 개선한다고 말하기는 어렵다.

현재 문서/포트폴리오 표현은 아래 수준이 적절하다.

```text
거래 통계 조회 검증 결과를 바탕으로 MATERIALIZED_VIEW 기반 통계 조회만 Read Replica 후보로 분리하는 로컬 시험 구조를 구성하고, Single DB 대비 A/B 성능을 비교했다.
```

피해야 할 표현은 그대로 유지한다.

* Replica로 성능을 개선했다.
* Replica로 송금 API 안정성을 높였다.
* Read Replica 도입을 완료했다.
* 운영 조회 부하를 분리했다.

후속 검증은 아래가 필요하다.

* 3분 이상 duration 반복 측정
* 더 높은 송금/통계 rate 단계 측정
* `pg_stat_statements` 포함 benchmark 환경 준비
* `pg_stat_activity`를 실행 중 주기적으로 sampling
* DB CPU/IO와 connection pool metric 수집
* cold start와 warm-up 이후 성능 분리
* Replica lag와 `dataAsOf` 응답 정책 정리

## 22. 고부하 A/B 재검증과 DB 내부 지표 수집

이 섹션은 30초 반복 측정 이후, 같은 `MATERIALIZED_VIEW` 통계 기준으로 3분 고부하 A/B를 재실행하고 DB 내부 지표를 함께 수집한 결과다.

목적은 Replica를 더 좋게 보이게 만드는 것이 아니라, 통계 조회를 Replica로 분리했을 때 Primary write path 관련 SQL과 DB 지표가 어떻게 달라지는지 확인하는 것이다.

이번 실행에서도 송금, 잔액 변경, 원장 저장, idempotency, recovery, reconciliation 흐름은 수정하지 않았다. 통계 조회 경로는 `mode=MATERIALIZED_VIEW`만 비교했다.

### 22.1 실행 조건

| 항목 | 값 |
| --- | --- |
| 실행일 | 2026-07-07 |
| 데이터셋 | 통계 seed 100,000 rows / 1,000 users / 12 months |
| 송금 seed | 5,000 sender/receiver pairs |
| 통계 mode | `MATERIALIZED_VIEW` |
| duration | 3m |
| 반복 | 조건별 3회 |
| 실행 순서 | A1 -> B1 -> B2 -> A2 -> A3 -> B3 |
| A | Single DB + MV |
| B | Primary + Replica + MV |
| 송금 rate | 5/s |
| 통계 rate | 30/s, 50/s |
| fraud 야간 정책 | `12:00~12:01`로 로컬 실행 인자 조정 |
| warm-up | 각 run 전 통계 API 10회 |
| raw 산출물 | `build/perf/replica-highload-ab-rerun-main` |

5,000 pair 조건에서는 auth/token 환경 변수가 너무 길어질 수 있어, k6 실행 시 auth env 파일 경로를 전달하고 k6 스크립트가 파일을 읽도록 했다. 이는 부하 입력 전달 방식만 바꾼 것이며, 통계 SQL이나 송금 비즈니스 로직 변경이 아니다.

### 22.2 수집한 DB 내부 지표

이번 고부하 runner는 각 run 동안 5초 간격으로 아래 지표를 수집했다.

| 지표 | 저장 파일 |
| --- | --- |
| Primary `pg_stat_activity` state | `activity-state-primary.csv` |
| Primary `pg_stat_activity` wait event | `activity-wait-primary.csv` |
| Replica `pg_stat_activity` state | `activity-state-replica.csv` |
| Replica `pg_stat_activity` wait event | `activity-wait-replica.csv` |
| Primary replication byte lag | `replication-primary.csv` |
| Replica replay delay | `replay-delay-replica.csv` |
| `pg_stat_statements` Primary | `pg-stat-statements-primary.csv` |
| `pg_stat_statements` Replica | `pg-stat-statements-replica.csv` |
| Docker CPU/memory snapshot | `docker-stats.csv` |
| MV row count | `primary-mv-count.txt`, `replica-mv-count.txt` |
| 실행 후 WAL byte lag | `wal-byte-lag-after.txt` |

`pg_stat_statements`는 이번 로컬 compose 환경에서만 활성화했다. 운영 PostgreSQL 설정을 변경한 것이 아니다.

### 22.3 run별 k6 결과

아래 표는 raw summary 기준이다. 송금 실패가 1건이라도 있는 run은 송금 latency 해석에서 별도 표시한다.

| 조건 | Run | Scenario | Transfer avg | Transfer p95 | Transfer p99 | Transfer error | Statistics avg | Statistics p95 | Statistics p99 | Statistics error |
| --- | ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| t5/s30 | 1 | Single DB + MV | 25.244ms | 31.864ms | 37.317ms | 0 | 7.742ms | 10.503ms | 12.020ms | 0 |
| t5/s30 | 1 | Primary + Replica + MV | 17.389ms | 22.747ms | 26.363ms | 0 | 16.738ms | 19.748ms | 21.207ms | 0 |
| t5/s30 | 2 | Primary + Replica + MV | 15.794ms | 18.509ms | 24.938ms | 1/900 | 16.472ms | 19.538ms | 20.648ms | 0 |
| t5/s30 | 2 | Single DB + MV | 27.050ms | 34.230ms | 37.157ms | 0 | 8.082ms | 10.546ms | 11.943ms | 0 |
| t5/s30 | 3 | Single DB + MV | 25.981ms | 31.967ms | 38.371ms | 0 | 7.841ms | 10.534ms | 11.595ms | 0 |
| t5/s30 | 3 | Primary + Replica + MV | 15.948ms | 19.011ms | 25.008ms | 0 | 16.848ms | 20.064ms | 21.297ms | 0 |
| t5/s50 | 1 | Single DB + MV | 24.456ms | 30.021ms | 33.915ms | 0 | 6.762ms | 9.495ms | 10.794ms | 0 |
| t5/s50 | 1 | Primary + Replica + MV | 13.440ms | 17.496ms | 22.212ms | 0 | 13.094ms | 15.104ms | 16.942ms | 0 |
| t5/s50 | 2 | Primary + Replica + MV | 13.502ms | 17.954ms | 22.048ms | 0 | 13.244ms | 15.217ms | 16.903ms | 0 |
| t5/s50 | 2 | Single DB + MV | 24.373ms | 29.254ms | 33.863ms | 1/900 | 6.648ms | 9.452ms | 10.562ms | 0 |
| t5/s50 | 3 | Single DB + MV | 24.637ms | 30.175ms | 33.824ms | 0 | 6.672ms | 9.399ms | 10.616ms | 0 |
| t5/s50 | 3 | Primary + Replica + MV | 14.035ms | 18.739ms | 21.655ms | 0 | 13.315ms | 15.442ms | 17.211ms | 0 |

통계 API 실패는 모든 run에서 0건이었다.

송금 실패가 있었던 run은 아래 2개다.

| 조건 | Scenario | 실패 |
| --- | --- | ---: |
| t5/s30 | Primary + Replica + MV run 2 | 1/900 |
| t5/s50 | Single DB + MV run 2 | 1/900 |

애플리케이션 로그에서 서버 예외는 확인되지 않았고, k6 기준으로는 송금 HTTP 200/`COMPLETED` 체크 실패 1건으로 집계됐다. 실패 1건이 섞인 run은 송금 응답속도 비교 근거로 강하게 사용하지 않는다.

### 22.4 실패 0건 run만 사용한 보조 집계

아래 표는 송금 실패와 통계 실패가 모두 0건인 run만 평균낸 값이다. run 수가 2~3회로 작으므로 방향성 참고용이다.

| 조건 | Scenario | 유효 run | Transfer avg | Transfer p95 | Transfer p99 | Statistics avg | Statistics p95 | Statistics p99 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| t5/s30 | Single DB + MV | 3 | 26.092ms | 32.687ms | 37.615ms | 7.888ms | 10.528ms | 11.853ms |
| t5/s30 | Primary + Replica + MV | 2 | 16.668ms | 20.879ms | 25.686ms | 16.793ms | 19.906ms | 21.252ms |
| t5/s50 | Single DB + MV | 2 | 24.547ms | 30.098ms | 33.870ms | 6.717ms | 9.447ms | 10.705ms |
| t5/s50 | Primary + Replica + MV | 3 | 13.659ms | 18.063ms | 21.972ms | 13.218ms | 15.254ms | 17.019ms |

이 결과만으로 Replica가 송금 응답속도를 개선한다고 단정하지 않는다. 로컬 Docker에서 Single DB 조건은 통계 MV 조회와 송금 write query가 Primary 하나에 같이 쌓이고, Replica 조건은 통계 MV 조회가 replica 컨테이너로 이동한다. 따라서 Primary 내부 SQL 실행 시간은 낮아졌지만, HTTP latency는 로컬 컨테이너 배치, connection pool, 캐시 상태, 실행 순서의 영향을 함께 받는다.

### 22.5 `pg_stat_statements` 요약

`statistics_mv`는 Single DB 조건에서는 Primary에서, Primary + Replica 조건에서는 Replica에서 주로 관측됐다.

| 조건 | Scenario | DB | Query group | Calls | Total exec | Mean exec |
| --- | --- | --- | --- | ---: | ---: | ---: |
| t5/s30 | Single DB + MV | Primary | `statistics_mv` | 5,401 | 764.935ms | 0.142ms |
| t5/s30 | Primary + Replica + MV | Replica | `statistics_mv` | 5,401 | 492.941ms | 0.091ms |
| t5/s50 | Single DB + MV | Primary | `statistics_mv` | 9,001 | 1,297.669ms | 0.144ms |
| t5/s50 | Primary + Replica + MV | Replica | `statistics_mv` | 9,001 | 681.999ms | 0.076ms |

Primary write path 관련 query group 평균은 Replica 조건에서 낮게 관측됐다.

| 조건 | Scenario | DB | Query group | Calls | Total exec | Mean exec |
| --- | --- | --- | --- | ---: | ---: | ---: |
| t5/s30 | Single DB + MV | Primary | `balance` | 3,601 | 1,481.252ms | 0.411ms |
| t5/s30 | Primary + Replica + MV | Primary | `balance` | 3,599 | 862.467ms | 0.240ms |
| t5/s30 | Single DB + MV | Primary | `wallet_transfer_request` | 8,109 | 383.461ms | 0.047ms |
| t5/s30 | Primary + Replica + MV | Primary | `wallet_transfer_request` | 8,105 | 249.074ms | 0.031ms |
| t5/s50 | Single DB + MV | Primary | `balance` | 3,601 | 1,474.144ms | 0.410ms |
| t5/s50 | Primary + Replica + MV | Primary | `balance` | 3,603 | 729.097ms | 0.202ms |
| t5/s50 | Single DB + MV | Primary | `wallet_transfer_request` | 8,111 | 378.902ms | 0.047ms |
| t5/s50 | Primary + Replica + MV | Primary | `wallet_transfer_request` | 8,112 | 167.429ms | 0.021ms |

이 수치는 DB 내부 SQL 실행 시간 기준이다. 사용자가 보는 송금 HTTP 응답속도와 1:1로 같지 않으므로, 문서나 포트폴리오에서는 “Primary의 관련 SQL 누적 실행 시간이 낮아졌다” 정도로 표현해야 한다.

### 22.6 replication lag, wait event, CPU

Primary + Replica 조건에서 실행 후 WAL byte lag는 모든 run에서 0이었다.

| 조건 | Max byte lag 평균 | Run별 max byte lag | 실행 후 byte lag |
| --- | ---: | --- | --- |
| t5/s30 | 621.3 bytes | 1,864 / 0 / 0 | 0 / 0 / 0 |
| t5/s50 | 0 bytes | 0 / 0 / 0 | 0 / 0 / 0 |

관측된 wait event는 대부분 `ClientRead` 또는 wait 없음이었다. 이 샘플만으로 lock wait 병목은 확인되지 않았다.

| 조건 | Scenario | 주요 wait event |
| --- | --- | --- |
| t5/s30 | Single DB + MV | Primary `ClientRead`, wait 없음 |
| t5/s30 | Primary + Replica + MV | Primary `ClientRead`, Replica wait 없음/`ClientRead` |
| t5/s50 | Single DB + MV | Primary `ClientRead`, wait 없음 |
| t5/s50 | Primary + Replica + MV | Primary `ClientRead`, Replica wait 없음/`ClientRead` |

Docker CPU snapshot은 로컬 컨테이너 기준이다.

| 조건 | Scenario | Primary CPU avg/max | Replica CPU avg/max |
| --- | --- | ---: | ---: |
| t5/s30 | Single DB + MV | 5.77% / 14.70% | 1.50% / 3.88% |
| t5/s30 | Primary + Replica + MV | 2.76% / 4.92% | 15.19% / 18.11% |
| t5/s50 | Single DB + MV | 7.20% / 17.26% | 1.13% / 3.66% |
| t5/s50 | Primary + Replica + MV | 2.49% / 5.41% | 18.07% / 19.98% |

Replica 조건에서는 통계 조회 CPU가 replica 컨테이너 쪽으로 이동한 것으로 해석할 수 있다. 다만 운영 DB CPU/IO 결과가 아니므로 운영 효과로 일반화하지 않는다.

### 22.7 고부하 재검증 해석

이번 고부하 재실행에서 확인한 것은 아래 범위다.

* 3분 고부하 조건에서도 `MATERIALIZED_VIEW` 통계 조회만 Replica로 보내는 구조는 동작했다.
* 통계 API 실패는 모든 run에서 0건이었다.
* 송금 실패는 12개 run 중 2개 run에서 각 1건씩 있었다. 이 run은 송금 응답속도 비교에서 주의해서 해석한다.
* `pg_stat_statements` 기준으로 MV 통계 조회는 Single DB에서는 Primary, Replica 조건에서는 Replica에서 관측됐다.
* Replica 조건에서는 Primary의 `statistics_mv` query group이 사라지고, `balance`, `wallet_transfer_request` 등 write path 관련 query group의 누적 실행 시간이 낮게 관측됐다.
* Primary + Replica 조건의 실행 후 WAL byte lag는 0이었다.
* 샘플링된 wait event에서 lock wait 병목은 확인되지 않았다.
* Replica 조건의 통계 HTTP latency는 Single DB보다 높게 나왔다. DB 내부 mean exec time은 낮았으므로, HTTP latency 차이는 DB 실행 계획만으로 설명하지 않는다.

따라서 현재 기준 판단은 그대로 보류다.

```text
Read Replica는 거래 통계 MV 조회를 Primary write path에서 분리하는 후보로는 유효하지만,
이번 로컬 고부하 결과만으로 도입을 확정하거나 성능 개선을 단정하지 않는다.
```

다음 검증은 아래 순서가 적절하다.

* 송금 실패 0건 조건을 더 안정적으로 만들기 위한 fixture/rate 검증
* t5/s30, t5/s50을 실패 0건으로 추가 반복
* t5/s100 또는 t10/s50은 별도 고부하 단계로 분리
* connection pool metric과 애플리케이션 HTTP 처리 시간 분해
* Replica lag와 `dataAsOf` 정책 문서화
* 필요 시 k6 결과에 실패 응답 status/body sample을 별도 로컬 raw로 남기는 디버그 모드 보강

## 23. Fraud 차단 회피 조건 재측정

이 섹션은 22장의 고부하 A/B에서 일부 run에 송금 실패가 1건씩 섞인 문제를 줄이기 위해, Fraud 소스 코드나 Lua를 수정하지 않고 로컬 벤치마크 입력 조건만 조정한 뒤 다시 실행한 결과다.

목적은 송금 API를 더 빠르게 보이게 하는 것이 아니라, Replica A/B 실험 목적과 무관한 Fraud 차단을 제거하고 `transfer error 0`, `statistics error 0` 조건에서 비교 가능한 run을 확보하는 것이다.

### 23.1 최근 실패 원인 분류

이전 고부하 run에서 송금 실패가 있었던 조건은 아래와 같다.

| Condition | Scenario | Run | Transfer failed | Statistics failed | 채택 여부 |
| --- | --- | ---: | ---: | ---: | --- |
| 3m / T5 / S30 | Primary + Replica + MV | 2 | 1/900 | 0 | 제외 |
| 3m / T5 / S50 | Single DB + MV | 2 | 1/900 | 0 | 제외 |

이전 실패 run은 run 종료 후 DB volume을 정리하는 실험 구조라, 해당 두 run의 최종 DB에서 `wallet_transfer_request`, `transaction_status_event`를 다시 조회할 수는 없었다. 다만 앞선 실패 분석에서는 DB 상태가 `FRAUD_VALIDATION / SUSPICIOUS_TRANSACTION`으로 확인됐고, 이번 실패도 서버 예외 없이 k6의 송금 HTTP 200/`COMPLETED` 체크 실패 1건으로만 관측됐다.

따라서 이번 문서에서는 최근 실패를 Replica routing 문제나 통계 SQL 문제로 보지 않고, 로컬 벤치마크 입력 분산이 충분하지 않아 Fraud 차단을 건드렸을 가능성이 높은 run으로 분리한다. 원인을 단정하기보다, 성능 평균에는 포함하지 않고 별도 제외 대상으로 다룬다.

### 23.2 조정한 실행 조건

아래 조정은 모두 로컬 벤치마크 조건에만 적용했다.

| 항목 | 조정 |
| --- | --- |
| Fraud 소스 코드 | 수정 없음 |
| Fraud Lua script | 수정 없음 |
| 기본 운영 설정 | 수정 없음 |
| 야간 차단 회피 | Spring Boot runtime args로 `12:00~12:01` 사용 |
| 송금 pair 수 | 5,000 -> 10,000 |
| sender/receiver 선택 | 무작위 선택 -> `SCENARIO_LABEL + VU + iteration` 기반 순환 분산 |
| 송금 rate | 5/s 유지 |
| 통계 rate | 30/s, 50/s, 100/s |
| duration | 3m |
| A/B 조건 | 같은 seed 규모, 같은 rate, 같은 duration, 같은 k6 script |

10,000 pair seed를 만들 때 `users.user_name` 길이 제한 때문에 기존 `s10000`, `r10000` 형태가 컬럼 길이를 초과했다. 이 문제는 도메인 스키마를 바꾸지 않고, benchmark fixture의 사용자 이름만 base36 기반 5자 이하 값으로 생성하도록 조정했다.

### 23.3 유효 run 기준

이번 재측정에서 성능 평균에 포함한 기준은 아래와 같다.

* `mode=MATERIALIZED_VIEW`
* `transfer error 0`
* `statistics error 0`
* A/B 같은 `TRANSFER_RATE`
* A/B 같은 `STATISTICS_RATE`
* A/B 같은 `DURATION`
* A/B 같은 seed 규모
* A/B 같은 fraud runtime args
* A/B 같은 k6 script
* Primary + Replica 조건에서 Primary/Replica MV row count 동일
* Primary + Replica 조건에서 실행 후 WAL byte lag 0

이번 재측정에서는 `s30`, `s50`, `s100` 총 18개 run 모두 위 기준을 만족했다.

### 23.4 유효 run 결과

| Condition | Scenario | Runs | Transfer avg | Transfer p95 | Transfer p99 | Statistics avg | Statistics p95 | Statistics p99 | Error |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 3m / T5 / S30 | Single DB + MV | 3 | 27.950ms | 34.450ms | 38.200ms | 7.917ms | 10.506ms | 11.831ms | 0 |
| 3m / T5 / S30 | Primary + Replica + MV | 3 | 17.267ms | 22.548ms | 28.756ms | 16.642ms | 19.975ms | 21.347ms | 0 |
| 3m / T5 / S50 | Single DB + MV | 3 | 24.591ms | 30.998ms | 35.004ms | 6.546ms | 9.012ms | 10.234ms | 0 |
| 3m / T5 / S50 | Primary + Replica + MV | 3 | 14.621ms | 17.647ms | 24.145ms | 13.512ms | 15.276ms | 17.088ms | 0 |
| 3m / T5 / S100 | Single DB + MV | 3 | 21.654ms | 27.506ms | 31.154ms | 4.737ms | 6.600ms | 7.709ms | 0 |
| 3m / T5 / S100 | Primary + Replica + MV | 3 | 13.412ms | 17.287ms | 22.695ms | 10.917ms | 12.827ms | 15.346ms | 0 |

요청 수는 조건별로 아래와 같았다.

| Condition | Scenario | Transfer requests | Statistics requests |
| --- | --- | ---: | ---: |
| 3m / T5 / S30 | Single DB + MV | 2,702 | 16,202 |
| 3m / T5 / S30 | Primary + Replica + MV | 2,702 | 16,202 |
| 3m / T5 / S50 | Single DB + MV | 2,702 | 27,002 |
| 3m / T5 / S50 | Primary + Replica + MV | 2,701 | 27,002 |
| 3m / T5 / S100 | Single DB + MV | 2,701 | 54,003 |
| 3m / T5 / S100 | Primary + Replica + MV | 2,702 | 54,001 |

이번 결과에서도 Replica가 송금 HTTP 응답속도를 개선한다고 단정하지 않는다. 다만 실패 0건 조건에서 비교 가능한 A/B 데이터가 확보됐고, DB 내부 지표상 통계 MV 조회가 Primary에서 Replica로 이동한 것은 확인됐다.

### 23.5 제외한 run

이번 재측정에서 제외한 run은 없다.

| Condition | Scenario | Run | Transfer failed | Failure stage | Failure code | 제외 사유 |
| --- | --- | ---: | ---: | --- | --- | --- |
| - | - | - | 0 | - | - | 제외 없음 |

### 23.6 DB 내부 지표

`pg_stat_statements` 기준으로 `statistics_mv` query group은 Single DB 조건에서는 Primary, Primary + Replica 조건에서는 Replica에서 관측됐다.

| Condition | Scenario | DB | Query group | Calls | Total exec | Mean exec |
| --- | --- | --- | --- | ---: | ---: | ---: |
| 3m / T5 / S30 | Single DB + MV | Primary | `statistics_mv` | 5,401 | 746.566ms | 0.138ms |
| 3m / T5 / S30 | Primary + Replica + MV | Replica | `statistics_mv` | 5,401 | 501.762ms | 0.093ms |
| 3m / T5 / S50 | Single DB + MV | Primary | `statistics_mv` | 9,001 | 1,151.388ms | 0.128ms |
| 3m / T5 / S50 | Primary + Replica + MV | Replica | `statistics_mv` | 9,001 | 667.754ms | 0.074ms |
| 3m / T5 / S100 | Single DB + MV | Primary | `statistics_mv` | 18,001 | 2,270.620ms | 0.126ms |
| 3m / T5 / S100 | Primary + Replica + MV | Replica | `statistics_mv` | 18,000 | 1,122.782ms | 0.062ms |

Primary write path 관련 query group도 Replica 조건에서 누적 실행 시간이 낮게 관측됐다.

| Condition | Scenario | DB | Query group | Calls | Total exec | Mean exec |
| --- | --- | --- | --- | ---: | ---: | ---: |
| 3m / T5 / S30 | Single DB + MV | Primary | `balance` | 3,603 | 2,695.106ms | 0.748ms |
| 3m / T5 / S30 | Primary + Replica + MV | Primary | `balance` | 3,603 | 1,609.345ms | 0.447ms |
| 3m / T5 / S50 | Single DB + MV | Primary | `balance` | 3,603 | 2,423.232ms | 0.672ms |
| 3m / T5 / S50 | Primary + Replica + MV | Primary | `balance` | 3,601 | 1,359.050ms | 0.377ms |
| 3m / T5 / S100 | Single DB + MV | Primary | `balance` | 3,601 | 2,296.215ms | 0.638ms |
| 3m / T5 / S100 | Primary + Replica + MV | Primary | `balance` | 3,603 | 1,233.886ms | 0.343ms |
| 3m / T5 / S30 | Single DB + MV | Primary | `wallet_transfer_request` | 8,112 | 385.687ms | 0.047ms |
| 3m / T5 / S30 | Primary + Replica + MV | Primary | `wallet_transfer_request` | 8,112 | 240.456ms | 0.030ms |
| 3m / T5 / S50 | Single DB + MV | Primary | `wallet_transfer_request` | 8,112 | 344.553ms | 0.043ms |
| 3m / T5 / S50 | Primary + Replica + MV | Primary | `wallet_transfer_request` | 8,109 | 187.315ms | 0.023ms |
| 3m / T5 / S100 | Single DB + MV | Primary | `wallet_transfer_request` | 8,109 | 331.311ms | 0.041ms |
| 3m / T5 / S100 | Primary + Replica + MV | Primary | `wallet_transfer_request` | 8,112 | 157.307ms | 0.019ms |

Replication lag는 실행 중 순간 byte lag가 작게 관측됐지만, 모든 Primary + Replica run의 실행 후 byte lag는 0이었다.

| Condition | Run별 max byte lag | 실행 후 byte lag |
| --- | --- | --- |
| 3m / T5 / S30 | 808 / 0 / 1,664 bytes | 0 / 0 / 0 |
| 3m / T5 / S50 | 0 / 264 / 240 bytes | 0 / 0 / 0 |
| 3m / T5 / S100 | 416 / 0 / 264 bytes | 0 / 0 / 0 |

Docker CPU snapshot은 아래처럼 관측됐다.

| Condition | Scenario | Primary CPU avg/max | Replica CPU avg/max |
| --- | --- | ---: | ---: |
| 3m / T5 / S30 | Single DB + MV | 6.27% / 8.99% | 1.61% / 3.86% |
| 3m / T5 / S30 | Primary + Replica + MV | 3.65% / 17.22% | 14.86% / 17.59% |
| 3m / T5 / S50 | Single DB + MV | 7.19% / 10.32% | 1.07% / 3.65% |
| 3m / T5 / S50 | Primary + Replica + MV | 2.68% / 4.56% | 18.40% / 21.72% |
| 3m / T5 / S100 | Single DB + MV | 8.75% / 26.71% | 0.97% / 3.60% |
| 3m / T5 / S100 | Primary + Replica + MV | 3.09% / 19.88% | 29.82% / 35.48% |

wait event는 대부분 `ClientRead` 또는 wait 없음으로 관측됐다. 일부 run에서 `WALSync`가 1회 관측됐지만, 이번 샘플에서 lock wait 병목은 확인되지 않았다.

### 23.7 해석

이번 재측정에서는 10,000 pair와 결정적 pair 분산을 적용한 뒤 `s30`, `s50`, `s100` 총 18개 run 모두 `transfer error 0`, `statistics error 0`을 만족했다.

Fraud 소스 코드, Lua, 기본 운영 설정은 수정하지 않았다. 변경한 것은 로컬 벤치마크 입력 분산과 fixture 생성 방식이다.

`pg_stat_statements` 기준으로 Single DB 조건에서는 통계 MV 조회가 Primary에 쌓이고, Primary + Replica 조건에서는 통계 MV 조회가 Replica에 쌓였다. Replica 조건에서 Primary의 일부 write path 관련 query group 누적 실행 시간이 낮게 관측됐다.

다만 이 결과는 로컬 Docker 기반 고부하 검증 결과다. 운영 환경 결과가 아니며, Replica가 송금 HTTP 응답속도를 개선한다고 단정하지 않는다. HTTP latency는 DB 실행 시간 외에도 애플리케이션 스레드, connection pool, 로컬 컨테이너 리소스, 캐시 상태, 실행 순서의 영향을 받는다.

현재 기준 판단은 아래와 같다.

```text
MATERIALIZED_VIEW 기반 통계 조회는 Read Replica 후보로 유지할 수 있다.
이번 재측정은 통계 read 부하가 Replica 쪽으로 이동하는 구조를 확인한 결과다.
하지만 운영 도입이나 성능 개선 확정으로 표현하지 않는다.
```

후속 검증은 connection pool metric, 애플리케이션 처리 시간 분해, Replica lag와 `dataAsOf` 정책, 더 긴 duration 반복 측정 순서가 적절하다.

## 24. 고부하 심화 검증과 HTTP latency 원인 분석

### 24.1 목적

이번 심화 검증의 목적은 통계 API HTTP latency를 빠르게 보이게 만드는 것이 아니다.

핵심 질문은 아래다.

```text
통계 read 부하가 더 커졌을 때,
Single DB에서는 Primary write path와 통계 read가 같은 DB 자원을 경쟁하고,
Primary + Replica 구조에서는 통계 read 부하가 Replica 쪽으로 격리되는가?
```

보조 질문은 아래다.

```text
Replica DB의 statistics_mv SQL mean exec는 낮게 관측되는데,
왜 통계 API HTTP latency는 Replica 조건에서 더 높게 관측되는가?
```

이번 작업에서는 아래를 추가했다.

* `S200`, `S300`, `T10/S100`, `T10/S200` 조건 실행
* k6 HTTP timing 분해 metric 추가
  * `waiting`
  * `blocked`
  * `receiving`
* high-load runner의 Hikari Actuator metric 수집 추가
* `RUN_DEEP`, `RUN_T10_S200` 실행 옵션 추가

Java controller/service/repository 단위 Micrometer timer는 이번 결과 근거에 포함하지 않는다. 로컬 sanity에서 custom timer가 Actuator metric으로 안정적으로 노출되지 않았기 때문에, 이번 문서에는 실제 수집에 성공한 k6 HTTP timing, Hikari 기본 metric, `pg_stat_statements`, `pg_stat_activity`, Docker CPU, WAL lag만 근거로 사용한다.

### 24.2 실행 조건

공통 조건은 아래와 같다.

| 항목 | 값 |
| --- | --- |
| 실행일 | 2026-07-07 |
| 통계 seed | 100,000 rows / 1,000 users / 12 months |
| 송금 seed | 10,000 sender/receiver pairs |
| 통계 mode | `MATERIALIZED_VIEW` |
| duration | 3m |
| A/B 실행 순서 | A1 -> B1 -> B2 -> A2 -> A3 -> B3 |
| fraud 야간 정책 | `--fraud.policy.night-start=12:00 --fraud.policy.night-end=12:01` |
| pair 선택 | `SCENARIO_LABEL + VU + iteration` 기반 결정적 분산 |

추가 조건은 아래다.

| Condition | Transfer rate | Statistics rate | Runs |
| --- | ---: | ---: | ---: |
| `high-t5-s200` | 5/s | 200/s | A/B 각 3회 |
| `high-t5-s300` | 5/s | 300/s | A/B 각 3회 |
| `high-t10-s100` | 10/s | 100/s | A/B 각 3회 |
| `high-t10-s200` | 10/s | 200/s | A/B 각 3회 |

### 24.3 유효 run 기준

성능 평균에는 아래 기준을 만족한 run만 포함했다.

* `mode=MATERIALIZED_VIEW`
* `transfer error 0`
* `statistics error 0`
* k6 `dropped_iterations = 0`
* A/B 같은 `TRANSFER_RATE`
* A/B 같은 `STATISTICS_RATE`
* A/B 같은 `DURATION`
* A/B 같은 seed 규모
* Primary + Replica 조건에서 실행 전 Primary/Replica MV row count 동일
* 실행 후 WAL byte lag 0

`high-t10-s200 / Primary + Replica + MV / run 3`은 아래 이유로 평균에서 제외했다.

| Condition | Scenario | Run | Transfer error | Statistics error | Dropped iterations | 제외 사유 |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| 3m / T10 / S200 | Primary + Replica + MV | 3 | 0 | 0 | 16,914 | k6 VU 부족으로 요청 발생률 유지 실패 |

나머지 23개 run은 `transfer error 0`, `statistics error 0`이었다. 모든 run의 실행 후 WAL byte lag는 0이었다.

### 24.4 k6 결과 요약

아래 표는 유효 run만 평균에 포함한 값이다.

| Condition | Scenario | Runs | Transfer avg | Transfer p95 | Transfer p99 | Statistics avg | Statistics p95 | Statistics p99 | Error |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 3m / T5 / S200 | Single DB + MV | 3 | 18.328ms | 23.721ms | 27.565ms | 3.485ms | 4.746ms | 5.551ms | 0 |
| 3m / T5 / S200 | Primary + Replica + MV | 3 | 13.919ms | 17.652ms | 23.297ms | 10.863ms | 12.239ms | 13.955ms | 0 |
| 3m / T5 / S300 | Single DB + MV | 3 | 15.320ms | 19.529ms | 22.679ms | 2.627ms | 3.529ms | 4.116ms | 0 |
| 3m / T5 / S300 | Primary + Replica + MV | 3 | 14.335ms | 18.826ms | 24.156ms | 10.321ms | 11.630ms | 13.427ms | 0 |
| 3m / T10 / S100 | Single DB + MV | 3 | 20.659ms | 25.079ms | 29.019ms | 4.574ms | 6.232ms | 7.216ms | 0 |
| 3m / T10 / S100 | Primary + Replica + MV | 3 | 13.089ms | 16.879ms | 21.358ms | 11.323ms | 12.548ms | 14.473ms | 0 |
| 3m / T10 / S200 | Single DB + MV | 3 | 16.855ms | 21.484ms | 24.407ms | 3.341ms | 4.464ms | 5.303ms | 0 |
| 3m / T10 / S200 | Primary + Replica + MV | 2 | 14.144ms | 19.197ms | 23.838ms | 11.099ms | 13.395ms | 16.177ms | 0 |

이 표만 보고 Replica가 송금 HTTP latency를 개선한다고 단정하지 않는다. 같은 로컬 장비에서 반복 실행한 A/B 결과이며, HTTP latency는 DB 외부 요인의 영향을 함께 받는다.

### 24.5 HTTP timing 분해

k6 `duration` 중 대부분은 `waiting`으로 관측됐다. `blocked`는 모든 조건에서 매우 낮았다.

| Condition | Scenario | Transfer waiting avg | Transfer waiting p95 | Statistics waiting avg | Statistics waiting p95 | Statistics blocked avg |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 3m / T5 / S200 | Single DB + MV | 18.247ms | 23.650ms | 3.313ms | 4.384ms | 0.004ms |
| 3m / T5 / S200 | Primary + Replica + MV | 13.840ms | 17.573ms | 10.709ms | 12.014ms | 0.004ms |
| 3m / T5 / S300 | Single DB + MV | 15.259ms | 19.474ms | 2.490ms | 3.180ms | 0.003ms |
| 3m / T5 / S300 | Primary + Replica + MV | 14.266ms | 18.751ms | 10.182ms | 11.420ms | 0.003ms |
| 3m / T10 / S100 | Single DB + MV | 20.541ms | 24.963ms | 4.356ms | 5.822ms | 0.006ms |
| 3m / T10 / S100 | Primary + Replica + MV | 13.012ms | 16.797ms | 11.159ms | 12.300ms | 0.005ms |
| 3m / T10 / S200 | Single DB + MV | 16.773ms | 21.404ms | 3.177ms | 4.141ms | 0.004ms |
| 3m / T10 / S200 | Primary + Replica + MV | 14.066ms | 19.077ms | 10.941ms | 13.177ms | 0.004ms |

Replica 조건의 통계 API HTTP latency 증가는 `blocked`가 아니라 `waiting`에 거의 그대로 반영됐다. 따라서 k6 클라이언트 큐잉보다는 서버 처리 구간, datasource routing, replica 연결 방식, 로컬 Docker network/connection 생성 비용 가능성이 더 크다.

현재 Replica `JdbcTemplate`은 Hikari가 아니라 `DriverManagerDataSource` 기반이다. 따라서 Replica 조건에서는 통계 SQL 자체가 빨라도, Replica 조회마다 connection 생성 비용이 HTTP latency에 섞일 수 있다. 이 가설은 다음 단계에서 Replica datasource를 Hikari로 바꾼 별도 A/B로 확인해야 한다. 이번 문서에서는 원인 후보로만 남긴다.

### 24.6 Primary write path query group

`pg_stat_statements` 기준으로 `statistics_mv`는 Single DB 조건에서는 Primary에, Primary + Replica 조건에서는 Replica에 쌓였다.

| Condition | Scenario | DB | `statistics_mv` calls | Total exec | Mean exec |
| --- | --- | --- | ---: | ---: | ---: |
| 3m / T5 / S200 | Single DB + MV | Primary | 36,001 | 3,962.634ms | 0.110ms |
| 3m / T5 / S200 | Primary + Replica + MV | Replica | 36,000 | 2,629.375ms | 0.073ms |
| 3m / T5 / S300 | Single DB + MV | Primary | 54,001 | 5,173.712ms | 0.096ms |
| 3m / T5 / S300 | Primary + Replica + MV | Replica | 54,001 | 2,839.910ms | 0.053ms |
| 3m / T10 / S100 | Single DB + MV | Primary | 18,000 | 2,200.328ms | 0.122ms |
| 3m / T10 / S100 | Primary + Replica + MV | Replica | 18,001 | 1,160.828ms | 0.064ms |
| 3m / T10 / S200 | Single DB + MV | Primary | 36,001 | 3,587.783ms | 0.100ms |
| 3m / T10 / S200 | Primary + Replica + MV | Replica | 36,001 | 2,536.899ms | 0.071ms |

Primary write path 관련 query group은 Replica 조건에서 낮게 관측됐다.

| Condition | Scenario | Query group | Calls | Total exec | Mean exec |
| --- | --- | --- | ---: | ---: | ---: |
| 3m / T5 / S200 | Single DB + MV | `balance` | 3,603 | 1,810.063ms | 0.503ms |
| 3m / T5 / S200 | Primary + Replica + MV | `balance` | 3,604 | 1,221.423ms | 0.339ms |
| 3m / T5 / S300 | Single DB + MV | `balance` | 3,604 | 1,564.210ms | 0.434ms |
| 3m / T5 / S300 | Primary + Replica + MV | `balance` | 3,604 | 1,245.497ms | 0.345ms |
| 3m / T10 / S100 | Single DB + MV | `balance` | 7,204 | 4,360.887ms | 0.605ms |
| 3m / T10 / S100 | Primary + Replica + MV | `balance` | 7,203 | 2,455.466ms | 0.341ms |
| 3m / T10 / S200 | Single DB + MV | `balance` | 7,204 | 3,374.519ms | 0.469ms |
| 3m / T10 / S200 | Primary + Replica + MV | `balance` | 7,204 | 2,511.619ms | 0.348ms |
| 3m / T5 / S200 | Single DB + MV | `wallet_transfer_request` | 8,112 | 305.500ms | 0.038ms |
| 3m / T5 / S200 | Primary + Replica + MV | `wallet_transfer_request` | 8,115 | 172.581ms | 0.021ms |
| 3m / T5 / S300 | Single DB + MV | `wallet_transfer_request` | 8,115 | 267.067ms | 0.033ms |
| 3m / T5 / S300 | Primary + Replica + MV | `wallet_transfer_request` | 8,115 | 165.616ms | 0.020ms |
| 3m / T10 / S100 | Single DB + MV | `wallet_transfer_request` | 16,215 | 638.012ms | 0.039ms |
| 3m / T10 / S100 | Primary + Replica + MV | `wallet_transfer_request` | 16,212 | 310.299ms | 0.019ms |
| 3m / T10 / S200 | Single DB + MV | `wallet_transfer_request` | 16,215 | 566.619ms | 0.035ms |
| 3m / T10 / S200 | Primary + Replica + MV | `wallet_transfer_request` | 16,215 | 351.108ms | 0.021ms |

이 결과는 통계 read 부하가 Primary에서 Replica로 이동하면 Primary write path와 같은 DB에서 경쟁하는 SQL 누적 비용이 줄어드는 경향을 보여준다. 다만 로컬 Docker 실험이므로 운영 효과로 단정하지 않는다.

### 24.7 CPU / wait / lag

Docker CPU snapshot에서도 Replica 조건에서는 통계 read 부하가 Replica 컨테이너 쪽으로 이동했다.

| Condition | Scenario | Primary CPU avg/max | Replica CPU avg/max |
| --- | --- | ---: | ---: |
| 3m / T5 / S200 | Single DB + MV | 11.68% / 27.27% | 1.09% / 3.22% |
| 3m / T5 / S200 | Primary + Replica + MV | 3.73% / 5.94% | 56.42% / 66.96% |
| 3m / T5 / S300 | Single DB + MV | 12.90% / 26.10% | 0.89% / 2.64% |
| 3m / T5 / S300 | Primary + Replica + MV | 4.83% / 20.76% | 76.54% / 83.76% |
| 3m / T10 / S100 | Single DB + MV | 11.49% / 26.33% | 1.68% / 4.21% |
| 3m / T10 / S100 | Primary + Replica + MV | 4.65% / 29.36% | 32.08% / 48.14% |
| 3m / T10 / S200 | Single DB + MV | 13.17% / 50.36% | 1.43% / 3.28% |
| 3m / T10 / S200 | Primary + Replica + MV | 5.65% / 7.91% | 59.28% / 69.93% |

wait event는 대부분 `ClientRead` 또는 wait 없음으로 관측됐다. 이번 샘플에서는 lock wait 병목을 확인하지 못했다.

모든 run의 실행 후 WAL byte lag는 0이었다. 실행 전 catchup에서도 Primary/Replica MV row count는 동일했다.

### 24.8 Hikari / datasource 지표

Actuator metric에서 확인된 Hikari pool은 `HikariPool-1` 하나였다.

| Condition | Scenario | Hikari active avg/max | Hikari pending max | Hikari acquire max |
| --- | --- | ---: | ---: | ---: |
| 3m / T5 / S200 | Single DB + MV | 0.528 / 2 | 0 | 0.006s |
| 3m / T5 / S200 | Primary + Replica + MV | 2.114 / 4 | 0 | 0.022s |
| 3m / T5 / S300 | Single DB + MV | 0.569 / 2 | 0 | 0.047s |
| 3m / T5 / S300 | Primary + Replica + MV | 2.928 / 4 | 0 | 0.022s |
| 3m / T10 / S100 | Single DB + MV | 0.403 / 2 | 0 | 0.039s |
| 3m / T10 / S100 | Primary + Replica + MV | 1.188 / 3 | 0 | 0.005s |
| 3m / T10 / S200 | Single DB + MV | 0.653 / 2 | 0 | 0.011s |
| 3m / T10 / S200 | Primary + Replica + MV | 2.174 / 4 | 0 | 0.011s |

현재 Replica datasource는 `DriverManagerDataSource`라서 Replica 전용 Hikari pool metric은 없다. 따라서 이번 Hikari 표는 Primary datasource pool 기준으로 해석해야 한다.

이번 측정에서 Primary Hikari pending은 0이었다. 따라서 송금/통계 HTTP latency 차이를 Primary Hikari pending으로 설명하기는 어렵다.

Replica 조건에서 통계 API HTTP latency가 높게 관측된 원인 후보는 아래다.

* Replica `JdbcTemplate`의 `DriverManagerDataSource` 기반 연결 생성 비용
* 로컬 Docker network 경로
* Primary/Replica 컨테이너 CPU 경합
* Spring datasource routing과 JDBC connection 준비 비용

다음 단계에서 이 원인을 분리하려면 Replica datasource를 Hikari로 구성한 로컬 A/B가 필요하다. 이 변경은 실험 조건 자체를 바꾸므로 별도 작업으로 분리한다.

### 24.9 현재 기준 해석

이번 심화 검증에서 확인한 것은 아래다.

* 통계 MV 조회는 Single DB 조건에서 Primary에 쌓였다.
* 통계 MV 조회는 Primary + Replica 조건에서 Replica에 쌓였다.
* Replica 조건에서 Primary의 `balance`, `wallet_transfer_request` query group 누적 실행 시간이 낮게 관측됐다.
* Replica 조건에서 Primary CPU 평균은 낮고 Replica CPU 평균은 높게 관측됐다.
* 실행 후 WAL byte lag는 0이었다.
* 통계 API HTTP latency는 Replica 조건에서 더 높게 관측됐다.
* 그 차이는 DB SQL mean exec보다는 HTTP `waiting` 구간에 반영됐다.
* 현재 Replica datasource는 Hikari pool이 아니므로 connection 생성/라우팅 비용이 원인 후보로 남는다.

따라서 현재 판단은 아래와 같다.

```text
MATERIALIZED_VIEW 기반 통계 조회를 Read Replica 후보로 제한 routing하는 방향은 계속 검토할 수 있다.
고부하 통계 read 조건에서 Primary write path 관련 DB 지표가 낮아지는 경향은 확인됐다.
다만 Replica가 통계 API HTTP latency 또는 송금 HTTP latency를 개선한다고 단정하지 않는다.
```

### 24.10 다음 검증

다음 검증은 아래 순서가 적절하다.

1. Replica datasource를 Hikari로 구성한 로컬 A/B
2. Replica Hikari pool name/tag 분리
3. 통계 API service/repository 단위 timing metric 재시도
4. `T10/S200` 조건의 VU 상향 재측정
5. 더 긴 duration 기준의 반복 측정
6. `dataAsOf`와 Replica lag 허용 정책 정리

## 25. HTTP latency 계층별 원인 분석 지표 보강

### 25.1 작업 목적

이전 고부하 A/B에서는 `pg_stat_statements` 기준 `statistics_mv` SQL 평균 실행 시간이 Replica에서 더 낮거나 비슷했지만, k6 기준 통계 API HTTP latency는 Replica 조건에서 더 높게 관측됐다.

이번 보강의 목적은 이 차이가 DB SQL 실행 시간 때문인지, 애플리케이션 API 계층, repository/JDBC 호출, DTO mapping, 또는 로컬 Replica datasource 연결 방식 때문인지 분리해서 보기 위한 지표를 추가하는 것이다.

이번 변경은 성능 관측용이며 아래 흐름은 변경하지 않았다.

* 송금/지갑/잔액/원장/recovery/reconciliation 비즈니스 로직
* 통계 SQL 의미
* Primary/Replica routing 대상
* Replica datasource의 Hikari 전환
* PostgreSQL 설정

### 25.2 추가한 지표

`RUN_TRANSACTION_STATISTICS_TIMING_ENABLED=true` 또는 runner 내부 timing 옵션이 켜진 경우에만 통계 API 계층별 timing CSV를 생성한다.

생성 위치는 각 run 디렉터리의 `api-timing.csv`다.

```text
build/perf/replica-latency-breakdown-t5-s200/{run}/api-timing.csv
```

CSV 컬럼은 아래와 같다.

| Column | 의미 |
| --- | --- |
| `recorded_at` | 계측 row 기록 시각 |
| `request_id` | 통계 API 요청 단위 식별자 |
| `metric` | 계측 구간 |
| `mode` | 통계 조회 모드 |
| `datasource_target` | repository가 사용한 datasource 대상 |
| `duration_ms` | 구간 소요 시간 |
| `row_count` | repository 결과 row 수 |

계측 metric은 아래다.

| Metric | 의미 |
| --- | --- |
| `statistics.api.total` | Controller 진입부터 응답 객체 반환까지 |
| `statistics.service.total` | Service 전체 처리 |
| `statistics.service.query` | Repository 조회 호출 |
| `statistics.repository.jdbc` | JdbcTemplate query 호출 |
| `statistics.repository.total` | Repository 조회 + row count 기록 |
| `statistics.mapping` | `TransactionStatisticsRow` -> response DTO 변환 |

### 25.3 실행 조건

이번 재측정은 계층별 timing이 정상 기록되고, `primary-replica-mv` 조건에서 `datasource_target=replica`가 찍히는 것을 확인한 뒤 실행했다.

| 항목 | 값 |
| --- | --- |
| 조건 | `latency-t5-s200` |
| Duration | 3m |
| Transfer rate | 5/s |
| Statistics rate | 200/s |
| 반복 | Single DB 3회, Primary + Replica 3회 |
| 통계 mode | `MATERIALIZED_VIEW` |
| 통계 seed | 100,000 rows / 1,000 users / 12 months |
| 송금 seed | 10,000 pairs |
| 결과 위치 | `build/perf/replica-latency-breakdown-t5-s200` |

모든 run에서 k6 error rate는 0이었고 dropped iteration도 0이었다.

### 25.4 k6 / HTTP timing 결과

| Scenario | Transfer avg | Transfer p95 | Transfer p99 | Statistics avg | Statistics p95 | Statistics p99 | Statistics waiting avg | Statistics blocked avg |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Single DB + MV | 18.011ms | 23.669ms | 27.963ms | 3.483ms | 4.912ms | 6.355ms | 3.313ms | 0.004ms |
| Primary + Replica + MV | 12.297ms | 15.755ms | 24.516ms | 10.036ms | 11.238ms | 13.163ms | 9.902ms | 0.003ms |

이번 run에서는 Replica 조건에서 송금 HTTP latency가 낮게 관측됐다. 다만 이 값은 “Replica를 쓰면 송금 API가 항상 빨라진다”는 의미가 아니다. 같은 로컬 장비에서 Docker 컨테이너 CPU 배치, JVM 상태, run 순서의 영향을 받는 A/B 결과이며, 송금 응답 속도 개선 여부는 별도 반복 조건으로 판단해야 한다.

통계 API의 차이는 `blocked`가 아니라 `waiting`에 거의 그대로 반영됐다. 따라서 k6 클라이언트 큐잉보다는 서버 처리, JDBC 호출, connection 준비, Docker network 구간 쪽을 봐야 한다.

### 25.5 API 계층 timing 결과

| Scenario | Metric | Count | Avg | P95 | P99 | Min | Max |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Single DB + MV | `statistics.api.total` | 108,033 | 1.364ms | 2.189ms | 2.675ms | 0.552ms | 38.231ms |
| Single DB + MV | `statistics.service.query` | 108,033 | 0.963ms | 1.691ms | 2.115ms | 0.389ms | 37.268ms |
| Single DB + MV | `statistics.repository.jdbc` | 108,033 | 0.837ms | 1.553ms | 1.914ms | 0.310ms | 20.257ms |
| Single DB + MV | `statistics.mapping` | 108,033 | 0.006ms | 0.013ms | 0.016ms | 0.001ms | 1.569ms |
| Primary + Replica + MV | `statistics.api.total` | 108,031 | 8.371ms | 9.117ms | 10.906ms | 7.686ms | 35.734ms |
| Primary + Replica + MV | `statistics.service.query` | 108,031 | 8.250ms | 8.966ms | 10.697ms | 7.578ms | 35.323ms |
| Primary + Replica + MV | `statistics.repository.jdbc` | 108,031 | 8.115ms | 8.774ms | 10.316ms | 7.457ms | 23.432ms |
| Primary + Replica + MV | `statistics.mapping` | 108,031 | 0.006ms | 0.011ms | 0.013ms | 0.002ms | 3.401ms |

계층별 timing 기준으로 통계 API 증가분은 대부분 `statistics.repository.jdbc`에 있다.

DTO mapping은 양쪽 모두 평균 0.006ms 수준이라 원인이 아니다.

`datasource_target`은 아래처럼 기록됐다.

| Scenario | `datasource_target` | Repository query count |
| --- | --- | ---: |
| Single DB + MV | `primary` | 108,033 |
| Primary + Replica + MV | `replica` | 108,031 |

따라서 이번 결과는 “Replica 조건에서 통계 API HTTP latency가 높은 이유가 Java DTO mapping이 아니라 Replica JDBC 호출 구간에 있다”는 쪽으로 좁혀준다.

### 25.6 pg_stat_statements 비교

반대로 PostgreSQL 내부의 `statistics_mv` 실행 시간은 Replica 조건이 더 낮았다.

| Scenario | DB | Calls avg | Mean exec avg | Total exec avg |
| --- | --- | ---: | ---: | ---: |
| Single DB + MV | Primary | 36,001 | 0.121ms | 4,364.6ms |
| Primary + Replica + MV | Replica | 36,000.3 | 0.067ms | 2,395.8ms |

Primary write path 관련 query group도 Replica 조건에서 낮게 관측됐다.

| Query group | Single DB mean avg | Primary + Replica mean avg |
| --- | ---: | ---: |
| `balance` | 0.536ms | 0.328ms |
| `wallet_transfer_request` | 0.040ms | 0.018ms |
| `wallet_transaction` | 0.079ms | 0.048ms |

즉 DB 내부 SQL 실행 시간만 보면 Replica로 통계 read를 분리하는 효과는 있다. 그러나 HTTP 통계 API latency는 이 DB 실행 시간과 반대로 증가했다.

### 25.7 CPU / wait / lag

Docker CPU snapshot은 통계 read 부하가 Replica 컨테이너로 이동했음을 보여준다.

| Scenario | Primary CPU avg/max | Replica CPU avg/max |
| --- | ---: | ---: |
| Single DB + MV | 10.82% / 13.08% | 1.06% / 3.32% |
| Primary + Replica + MV | 3.51% / 19.69% | 48.22% / 65.04% |

실행 후 WAL byte lag는 대부분 0이었다. 단일 DB 3회차의 종료 시점 샘플에서 4,648 bytes가 한 번 관측됐지만, 이 run은 Replica 조회 조건이 아니며 실행 중 catchup과 MV row count는 맞춰진 상태에서 시작했다.

Hikari pending은 모든 run에서 0이었다. 현재 Replica datasource는 Hikari가 아니라 `DriverManagerDataSource` 기반이므로 Replica 전용 Hikari 지표는 없다.

### 25.8 해석

이번 계층별 timing으로 원인 후보가 더 좁혀졌다.

* PostgreSQL 내부 `statistics_mv` SQL 실행은 Replica에서 더 빠르다.
* Primary의 write path query group 평균 실행 시간도 Replica 조건에서 낮아진다.
* 그러나 통계 API HTTP latency는 Replica 조건에서 더 높다.
* 증가분은 거의 `statistics.repository.jdbc`에 있다.
* DTO mapping, controller/service 순수 처리 시간은 원인이 아니다.
* k6 `blocked`가 아니라 `waiting`에 반영되므로 클라이언트 큐잉보다는 서버/JDBC/네트워크 대기 구간이다.

현재 가장 유력한 원인 후보는 Replica datasource가 `DriverManagerDataSource`라서 조회마다 connection 준비 비용이 HTTP latency에 포함되는 것이다. Single DB 조건은 Primary Hikari pool을 쓰고, Replica 조건은 별도 pool이 없는 DriverManager 기반이라 비교 조건이 완전히 같지 않다.

따라서 현재 결론은 아래다.

```text
Read Replica는 Primary DB의 통계 read 부하를 분리하는 효과가 있다.
하지만 현재 구현 상태에서는 Replica 통계 API HTTP latency가 더 높게 관측된다.
그 원인은 DB SQL 실행 시간이 아니라 Replica JDBC 호출/연결 계층일 가능성이 높다.
```

이 결과만으로 “Replica 도입” 또는 “Replica 불필요”를 최종 판단하지 않는다.

### 25.9 다음 단계

다음 검증은 아래 순서가 적절하다.

1. Replica datasource를 Hikari로 구성한 A/B 실험
2. Replica Hikari pool name/tag를 분리해 Actuator metric에서 Primary/Replica pool을 따로 확인
3. 동일한 T5/S200 조건으로 API timing 재측정
4. 통계 API 단독 부하와 송금 + 통계 혼합 부하를 분리해 재측정
5. `EXPLAIN (ANALYZE, BUFFERS)`와 `pg_stat_statements`를 같은 run id 기준으로 연결
6. Replica lag 허용 범위와 `dataAsOf` 응답 정책 정리
