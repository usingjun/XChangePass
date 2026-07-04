# 송금 API + 거래 통계 API k6 혼합 부하 결과 - 100,000 rows

## 1. 목적

송금 API와 거래 통계 API를 동시에 호출해 통계 조회 방식이 송금 API p95/p99에 미치는 영향을 확인했다.

이번 결과는 local/benchmark 환경에서 수집한 검증 결과이며 운영 결과가 아니다. Primary/Replica를 구현한 결과도 아니고, Primary/Replica 필요성 판단을 위한 근거 수집 단계다.

## 2. 실행 조건

| 항목 | 값 |
| --- | --- |
| BASE_URL | `http://localhost:8080` |
| VUs | 10 |
| Duration | 30s |
| Sleep | 1s |
| 송금 요청 | `PUT /api/v1/wallet/transfer` |
| 통계 요청 | `GET /api/v1/transactions/statistics/monthly` |
| 통계 모드 | `GROUP_BY`, `MATERIALIZED_VIEW`, `SUMMARY` |
| 인증 방식 | `accessToken` 쿠키 |
| 송금 seed | sender/receiver 400쌍, KRW 잔액 준비 |
| 통계 seed | 100,000 rows, 1,000 users, 2026-01 ~ 2026-12 |
| MV/Summary | 기존 materialized view refresh 및 summary refresh 완료 데이터 사용 |
| raw 결과 위치 | `build/perf/k6/transaction-transfer-statistics-mixed-*.json`, `.txt` |

각 k6 iteration에서 송금 요청 1회와 통계 조회 1회를 같은 test 안에서 실행했다. 전체 `http_req_duration`만 보지 않기 위해 `transfer_duration`, `statistics_duration`, `transfer_error_rate`, `statistics_error_rate` custom metric을 따로 기록했다.

## 3. 기준선

### 송금 API 단독 baseline

| Scenario | avg | p90 | p95 | p99 | max | requests | error |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| TRANSFER_BASELINE | 16.50ms | 19.61ms | 21.49ms | 29.20ms | 29.30ms | 300 | 0.00% |

### 통계 API 단독 read

| Mode | avg | p95 | p99 | error |
| --- | ---: | ---: | ---: | ---: |
| GROUP_BY | 12.19ms | 42.05ms | 47.21ms | 0.00% |
| MATERIALIZED_VIEW | 7.89ms | 9.43ms | 9.99ms | 0.00% |
| SUMMARY | 8.10ms | 9.70ms | 10.72ms | 0.00% |

통계 API 단독 read는 `K6_SLEEP=0.1`, 이번 혼합 부하는 `K6_SLEEP=1` 조건이다. 따라서 통계 API 단독 결과와 혼합 결과는 절대 처리량 기준이 다르며, 주요 비교 대상은 송금 API 단독 baseline 대비 혼합 부하의 송금 p95/p99 변화다.

## 4. 혼합 부하 결과

### 송금 API 지표

| Mixed Scenario | Transfer avg | Transfer p90 | Transfer p95 | Transfer p99 | Transfer max | Transfer requests | Transfer error |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| TRANSFER_WITH_GROUP_BY_STATS | 22.46ms | 30.86ms | 34.69ms | 44.75ms | 52.07ms | 298 | 0.00% |
| TRANSFER_WITH_MATERIALIZED_VIEW_STATS | 28.80ms | 32.59ms | 33.28ms | 38.61ms | 39.03ms | 290 | 0.00% |
| TRANSFER_WITH_SUMMARY_STATS | 30.28ms | 34.67ms | 37.68ms | 39.28ms | 39.96ms | 290 | 0.34% |

### 통계 API 지표

| Mixed Scenario | Statistics avg | Statistics p90 | Statistics p95 | Statistics p99 | Statistics max | Statistics requests | Statistics error |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| TRANSFER_WITH_GROUP_BY_STATS | 10.64ms | 34.51ms | 38.38ms | 42.55ms | 70.02ms | 298 | 0.00% |
| TRANSFER_WITH_MATERIALIZED_VIEW_STATS | 7.20ms | 9.73ms | 10.09ms | 10.94ms | 11.30ms | 290 | 0.00% |
| TRANSFER_WITH_SUMMARY_STATS | 8.01ms | 9.69ms | 9.92ms | 10.66ms | 11.47ms | 290 | 0.00% |

### 송금 baseline 대비 변화

| Mixed Scenario | Baseline p95 | Mixed Transfer p95 | p95 delta | Baseline p99 | Mixed Transfer p99 | p99 delta |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| TRANSFER_WITH_GROUP_BY_STATS | 21.49ms | 34.69ms | +61.42% | 29.20ms | 44.75ms | +53.25% |
| TRANSFER_WITH_MATERIALIZED_VIEW_STATS | 21.49ms | 33.28ms | +54.86% | 29.20ms | 38.61ms | +32.23% |
| TRANSFER_WITH_SUMMARY_STATS | 21.49ms | 37.68ms | +75.34% | 29.20ms | 39.28ms | +34.52% |

## 5. 해석

혼합 부하에서는 세 모드 모두 송금 API p95/p99가 단독 baseline보다 증가했다. 이는 같은 로컬 애플리케이션/DB에 송금 write와 통계 read를 동시에 넣은 결과다.

`GROUP_BY` 혼합에서는 송금 p99가 44.75ms로 가장 높게 나타났다. 원본 거래 테이블을 직접 `UNION ALL + GROUP BY`로 집계하는 쿼리 특성상, 기존 Java benchmark와 EXPLAIN에서 확인한 원본 scan/aggregate 비용과 같은 방향의 결과다.

`MATERIALIZED_VIEW`와 `SUMMARY`의 통계 조회 자체는 p95/p99가 10ms 안팎으로 안정적이었다. 다만 송금 p95는 두 모드에서도 baseline보다 증가했다. 특히 `SUMMARY` 혼합에서는 송금 p95가 가장 높고 송금 실패 1건이 있었다. 이 한 건은 전체 290건 중 0.34%이며 threshold는 통과했지만, 후속 혼합 부하 반복 측정에서 재현 여부를 확인할 필요가 있다.

이번 혼합 부하는 Summary refresh가 아니라 Summary read와 송금 API를 함께 실행한 결과다. Summary refresh 비용은 별도 maintenance 비용으로 구분해서 봐야 한다.

## 6. 기존 PostgreSQL 관측 결과와 연결

기존 Java benchmark에서는 `GROUP_BY` heavy user 장기 조회가 데이터 증가 영향을 더 크게 받았다.

EXPLAIN 수집 결과에서도 `GROUP_BY`는 원본 거래 테이블 scan과 aggregate가 필요하고, `MATERIALIZED_VIEW`와 `SUMMARY` 조회는 집계된 파생 데이터를 읽는 구조다.

pg_stat_statements 관점에서도 `GROUP_BY`는 shared block 접근량이 커질 수 있고, MV/Summary 조회는 상대적으로 낮은 SQL 비용을 기대할 수 있다. 다만 Summary Table은 refresh 시 delete/insert 및 dead tuple 관리 비용이 별도로 발생하므로 read 성능만 보고 최종 채택하면 안 된다.

## 7. Primary/Replica 판단

이번 결과는 Primary/Replica 구현 결과가 아니다.

다만 통계 조회가 송금 API와 동시에 실행될 때 송금 p95/p99가 상승하는 흐름은 확인됐다. 특히 원본 테이블 직접 `GROUP_BY`는 통계 조회 방식 중 송금 p99와 통계 p99가 가장 높게 나타났으므로, 통계 API의 기본 경로로 두기보다는 MV/Summary 같은 파생 조회 구조를 우선 검토하는 것이 타당하다.

통계 조회나 과거 조회는 후속 단계에서 Replica 후보가 될 수 있다. 하지만 아래 흐름은 Primary 유지 대상이다.

- 송금 처리
- 잔액 변경
- 원장 저장
- recovery 판단
- reconciliation 판단
- idempotency 판단
- 최신 상태 판단

현재 단계에서는 “Primary + Read Replica가 유효한 후보”라고 표현할 수는 있지만, “도입 완료”나 “필수”라고 판단하지 않는다. 최종 판단에는 반복 측정, DB CPU/IO, connection 사용량, lock wait, pg_stat_statements 결과 연결이 필요하다.

## 8. 포트폴리오 반영 가능 문장

송금 API 단독 k6 baseline을 먼저 확보한 뒤, 통계 조회 방식별 혼합 부하를 비교해 통계 조회가 송금 API p95/p99에 미치는 영향을 검증했다.

GROUP_BY, Materialized View, Summary Table을 Java benchmark, EXPLAIN, pg_stat_statements, k6 단독/혼합 부하로 비교해 조회 성능과 송금 API 영향도를 분리해 확인했다.

통계 조회는 최신 잔액/원장/recovery 판단과 분리된 파생 데이터로 설계하고, Read Replica 분리 여부는 혼합 부하 결과를 기준으로 판단하도록 했다.

## 9. 다음 단계

1. 혼합 부하를 동일 조건으로 2~3회 반복해 p95/p99 분산을 확인한다.
2. 혼합 부하 중 pg_stat_activity로 wait event와 connection 사용량을 확인한다.
3. pg_stat_statements 결과와 k6 결과를 연결해 쿼리별 DB 비용을 비교한다.
4. Summary refresh와 송금 API를 동시에 실행하는 maintenance 혼합 부하는 별도 시나리오로 분리한다.
5. 반복 결과를 바탕으로 Primary/Replica 후보 판단 문서를 작성한다.
