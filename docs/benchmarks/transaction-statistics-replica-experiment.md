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

## 8. 실행 조건

이번 실행은 로컬 개발 장비 기준 A/B 실험이다. 운영 환경 결과가 아니며, 운영 DB 설정이나 운영 env는 변경하지 않았다.

공통 조건은 아래와 같다.

* 실행일: 2026-07-06
* PostgreSQL: `postgres:15`
* Primary port: `15432`
* Replica port: `15433`
* Spring Boot port: `18080`
* 통계 seed: 100,000 rows, 1,000 users, 12 months
* 송금 seed: 2,000 sender/receiver pairs
* 통계 mode: `MATERIALIZED_VIEW`
* 송금 rate: 10/s
* 통계 rate: 10/s
* duration: 30s
* transfer VUs: 20, max 50
* statistics VUs: 20, max 50

실행 준비 명령 예시는 아래와 같다.

```bash
docker compose -f infra/docker-compose.transaction-statistics-replica.yml up -d
tools/perf/run-transaction-statistics-k6-seed.sh
TRANSACTION_TRANSFER_K6_PAIRS=2000 tools/perf/run-transaction-transfer-k6-seed.sh
```

## 9. 실행 결과

실제 A/B k6 실행을 완료했다.

### 9.1 A: Single DB + MV

앱 설정:

```bash
TRANSACTION_STATISTICS_REPLICA_ENABLED=false
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/xchangepass
```

결과:

| Metric | avg | p95 | p99 | min | max | error |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| transfer_duration | 27.617ms | 33.084ms | 40.551ms | 14.476ms | 218.450ms | 0.00% |
| statistics_duration | 10.232ms | 12.385ms | 13.728ms | 4.804ms | 112.183ms | 0.00% |

요청 수:

| 항목 | 결과 |
| --- | ---: |
| total http requests | 601 |
| transfer requests | 301 |
| statistics requests | 300 |
| transfer failed | 0 |
| statistics failed | 0 |

### 9.2 B: Primary + Replica + MV

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
| transfer_duration | 26.655ms | 31.730ms | 37.072ms | 14.521ms | 200.242ms | 0.33% |
| statistics_duration | 26.794ms | 30.963ms | 33.744ms | 15.793ms | 113.851ms | 0.00% |

요청 수:

| 항목 | 결과 |
| --- | ---: |
| total http requests | 602 |
| transfer requests | 301 |
| statistics requests | 301 |
| transfer failed | 1 |
| statistics failed | 0 |

실행 후 확인:

| 항목 | 결과 |
| --- | --- |
| replication state | streaming |
| sync_state | async |
| WAL byte lag after run | 0 |
| replica replay delay after run | 약 3분 22초 |

`replay_delay`는 마지막 replay timestamp와 현재 시각의 차이다. WAL byte lag가 0이므로, 실행 종료 시점에 미적용 WAL이 남아 있다는 의미로 해석하지 않는다. 쓰기가 한동안 없으면 이 값은 시간이 지날수록 커질 수 있다.

### 9.3 A/B 비교

| Metric | Single DB + MV | Primary + Replica + MV | 차이 |
| --- | ---: | ---: | ---: |
| transfer avg | 27.617ms | 26.655ms | -0.962ms |
| transfer p95 | 33.084ms | 31.730ms | -1.354ms |
| transfer p99 | 40.551ms | 37.072ms | -3.479ms |
| statistics avg | 10.232ms | 26.794ms | +16.562ms |
| statistics p95 | 12.385ms | 30.963ms | +18.578ms |
| statistics p99 | 13.728ms | 33.744ms | +20.016ms |

이번 로컬 실행에서는 송금 지연시간이 Replica 조건에서 소폭 낮게 나왔다. 다만 차이가 작고 실행 횟수가 1회이므로, Replica가 송금 응답을 개선한다고 단정하지 않는다.

반대로 통계 API는 Replica 조건에서 더 느렸다. 이 결과는 로컬 Docker Primary/Replica 구성, 별도 connection pool, replica 컨테이너 상태, 캐시 warm-up 차이의 영향을 받을 수 있다. 이번 결과만으로 Replica 통계 조회가 항상 느리다고 단정하지 않는다.

## 10. 해석

이번 실행은 운영용 Read Replica 도입 결과가 아니라 로컬 검증용 시험 적용 결과다.

송금 write path는 Primary에 유지하고, 지연 허용 가능한 `MATERIALIZED_VIEW` 기반 거래 통계 조회만 Replica 후보로 분리했다.

`constant-arrival-rate` 기반으로 송금 요청과 통계 요청 발생률을 분리 고정했기 때문에, 통계 요청이 느려져도 송금 요청 발생률 자체가 같이 줄어드는 구조는 피했다.

이번 결과에서 확인한 것은 아래 수준이다.

* `mode=MATERIALIZED_VIEW` 통계 조회를 Replica로 보내는 구조는 로컬에서 동작했다.
* 송금 write path는 Primary에 유지됐다.
* 통계 요청과 송금 요청을 분리한 k6 시나리오에서 A/B 모두 실행 가능했다.
* 이번 30초 단일 실행에서는 Replica 조건이 송금 p95/p99를 악화시키지 않았다.
* 통계 조회 latency는 Replica 조건에서 더 높게 측정됐다.

따라서 이번 결과만으로 Primary/Replica 도입을 확정하지 않는다. 반복 실행, DB 내부 관측, 더 긴 duration, 더 높은 rate, CPU/IO/lock/connection 지표가 필요하다.

## 11. 한계

* 운영 환경 결과가 아니다.
* Primary/Replica 운영 반영이 아니다.
* 30초 단일 실행 결과다.
* 통계/송금 rate가 각각 10/s인 제한된 부하다.
* DB CPU/IO, lock wait, connection pool, `pg_stat_activity`, `pg_stat_statements`는 함께 수집하지 않았다.
* Replica lag는 WAL byte lag 중심으로만 간단히 확인했다.
* `dataAsOf` 정책은 후속 검토가 필요하다.
* Summary refresh와 송금 API 동시 실행 영향은 별도 시나리오가 필요하다.
* B 조건에서 송금 실패 1건이 발생했다. error rate는 threshold 미만이지만, 원인은 별도 debug가 필요하다.
* 로컬 Docker Primary/Replica 구성의 성능 특성이 운영 환경과 다를 수 있다.

실행 중 보완한 사항:

* k6 `Idempotency-Key`가 UUID 형식이 아니어서 송금 API가 400을 반환하던 문제를 UUID v4 형태로 수정했다.
* 송금 pair 선택이 앞쪽 사용자에게 몰려 Fraud Detection을 건드리던 문제를 랜덤 분산으로 수정했다.
* Fraud frequency 조건을 피하기 위해 송금 seed pair를 400개에서 2,000개로 늘려 실행했다.
* 로컬 compose 이미지는 사용 가능한 `postgres:15` 기준으로 맞췄고, replica base backup을 위해 replication user와 `pg_hba.conf` 설정을 초기화 스크립트로 준비했다.

## 12. 후속 과제

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
* B 조건 송금 실패 1건의 원인 확인
* Replica 통계 latency가 Single DB보다 높게 나온 원인 확인

## 13. 포트폴리오 반영 가능 문장

아래 문장은 운영 결과가 아니라 개발 및 검증 결과 기준으로 사용할 수 있다.

* 거래 통계 조회 검증 결과를 바탕으로 `MATERIALIZED_VIEW` 기반 통계 조회만 Read Replica 후보로 분리하는 로컬 시험 구조를 구성하고 A/B 실험을 수행했다.
* 송금/잔액/원장/idempotency/recovery/reconciliation/latest state 판단은 Primary write model에 유지하고, 지연 허용 가능한 통계 read model만 Replica 후보로 제한했다.
* k6 혼합 부하를 `constant-arrival-rate` 기반 송금 scenario와 통계 scenario로 분리해, 통계 요청 지연이 송금 요청 발생률을 흔드는 기존 loop 방식의 한계를 보완했다.
* 로컬 100k 통계 데이터, 2,000개 송금 pair 조건에서 Single DB + MV와 Primary/Replica + MV를 비교하고, 송금 latency와 통계 latency를 분리 측정했다.

## 14. 피해야 할 표현

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
