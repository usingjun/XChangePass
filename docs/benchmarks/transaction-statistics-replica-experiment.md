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
