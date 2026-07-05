# 송금 API + 거래 통계 API 혼합 부하 반복 측정 결과

## 목적

이 문서는 XChangePass 거래 통계 조회 방식 비교 과정에서 송금 API와 통계 API를 동시에 실행했을 때의 반복 측정 결과를 정리한다.

측정 대상 통계 조회 방식은 다음 3가지다.

* `GROUP_BY`: 원본 거래 테이블 직접 `UNION ALL + GROUP BY`
* `MATERIALIZED_VIEW`: `mv_transaction_monthly_statistics` 조회
* `SUMMARY`: `transaction_monthly_summary` 조회

이 결과는 로컬 Java/Spring Boot 애플리케이션과 k6를 이용한 개발 및 검증용 측정이다. 운영 환경 결과로 해석하지 않는다.

## 실행 조건

| 항목 | 값 |
| --- | --- |
| 실행일 | 2026-07-06 |
| 애플리케이션 | 로컬 Spring Boot |
| 대상 URL | `http://localhost:18080` |
| 통계 기준 데이터 | 100,000 rows |
| k6 VUs | 10 |
| duration | 30s |
| sleep | 1s |
| repeat | 3 |
| 반복별 송금 seed | 매 반복마다 400 sender/receiver pair 재생성 |
| 통계 mode | `GROUP_BY`, `MATERIALIZED_VIEW`, `SUMMARY` |

Fraud 야간 정책은 측정 시각이 기본 야간 차단 구간에 걸려 있어, 별도 로컬 벤치마크 서버를 `18080` 포트로 실행하면서 런타임 인자로만 시간대를 우회했다.

```bash
./gradlew bootRun --args='--server.port=18080 --fraud.policy.night-start=04:00 --fraud.policy.night-end=04:01'
```

이 처리는 벤치마크 실행용 런타임 설정이며, Fraud 관련 소스 코드와 기본 설정 파일은 수정하지 않았다.

## 실행 명령

```bash
BASE_URL=http://localhost:18080 \
TRANSFER_AUTH_ENV=build/perf/k6/transaction-transfer-auth.env \
STATS_AUTH_ENV=build/perf/k6/transaction-statistics-auth.env \
K6_VUS=10 \
K6_DURATION=30s \
K6_SLEEP=1 \
K6_REPEAT=3 \
tools/perf/run-transaction-transfer-statistics-k6-mixed-repeat.sh
```

결과 요약 CSV는 아래 경로에 생성되었다.

```text
build/perf/k6/mixed-repeat/transaction-transfer-statistics-k6-mixed-repeat-summary.csv
```

원시 k6 결과와 인증 파일은 커밋 대상에서 제외한다.

## 반복 측정 원자료 요약

| Run | Mode | Transfer avg | Transfer p95 | Transfer p99 | Transfer err | Statistics avg | Statistics p95 | Statistics p99 | Statistics err |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | GROUP_BY | 15.871ms | 37.563ms | 63.927ms | 0.00% | 10.623ms | 37.320ms | 38.878ms | 0.00% |
| 1 | MATERIALIZED_VIEW | 18.975ms | 24.221ms | 25.776ms | 0.00% | 6.800ms | 7.930ms | 11.367ms | 0.00% |
| 1 | SUMMARY | 18.935ms | 24.303ms | 24.859ms | 0.00% | 7.265ms | 8.698ms | 9.253ms | 0.00% |
| 2 | GROUP_BY | 11.948ms | 18.355ms | 19.394ms | 0.00% | 10.747ms | 40.556ms | 41.303ms | 0.00% |
| 2 | MATERIALIZED_VIEW | 19.081ms | 22.853ms | 24.917ms | 0.00% | 6.902ms | 7.939ms | 8.186ms | 0.00% |
| 2 | SUMMARY | 18.625ms | 21.482ms | 22.184ms | 0.00% | 6.971ms | 8.391ms | 8.998ms | 0.00% |
| 3 | GROUP_BY | 13.085ms | 20.079ms | 22.163ms | 0.00% | 10.818ms | 40.228ms | 41.847ms | 0.00% |
| 3 | MATERIALIZED_VIEW | 18.917ms | 23.596ms | 24.286ms | 0.00% | 6.859ms | 7.992ms | 8.502ms | 0.00% |
| 3 | SUMMARY | 18.841ms | 23.011ms | 23.460ms | 0.00% | 7.078ms | 8.475ms | 9.069ms | 0.00% |

## 반복 평균 집계

| Mode | Transfer avg 평균 | Transfer avg 범위 | Transfer p95 평균 | Transfer p99 평균 | Statistics avg 평균 | Statistics avg 범위 | Statistics p95 평균 | Statistics p99 평균 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| GROUP_BY | 13.635ms | 11.948~15.871ms | 25.332ms | 35.161ms | 10.729ms | 10.623~10.818ms | 39.368ms | 40.676ms |
| MATERIALIZED_VIEW | 18.991ms | 18.917~19.081ms | 23.557ms | 24.993ms | 6.854ms | 6.800~6.902ms | 7.954ms | 9.352ms |
| SUMMARY | 18.801ms | 18.625~18.935ms | 22.932ms | 23.501ms | 7.105ms | 6.971~7.265ms | 8.521ms | 9.106ms |

모든 반복에서 송금 API와 통계 API의 k6 에러율은 0.00%였다.

## 해석

`GROUP_BY`는 통계 조회 평균 응답 시간 자체는 약 10.7ms 수준이었지만, p95와 p99가 각각 약 39.4ms, 40.7ms로 튀었다. 원본 거래 테이블을 직접 집계하기 때문에 혼합 부하에서 꼬리 지연이 상대적으로 크게 나타난다.

`MATERIALIZED_VIEW`와 `SUMMARY`는 통계 조회 p95가 각각 약 8.0ms, 8.5ms로 안정적이었다. 100,000 rows 기준에서는 파생 조회 구조가 원본 테이블 직접 집계보다 통계 API 꼬리 지연을 낮추는 효과가 확인된다.

송금 API는 이번 반복 측정에서 세 mode 모두 에러율 0.00%였다. 다만 mode별 송금 p95/p99 차이는 이 로컬 k6 측정만으로 통계 조회 방식이 송금 성능에 직접 영향을 줬다고 단정하기 어렵다. 송금 API 영향 판단에는 더 긴 duration, 더 높은 VUs, DB 관측 지표, lock wait, connection 사용량 확인이 필요하다.

이 결과만으로 Primary/Replica 도입 여부를 판단하지 않는다. 현재 단계에서는 PostgreSQL 내부 최적화 방식별 조회 지연 차이를 확인한 결과로만 사용한다.

## 기존 단일 혼합 측정과 비교

이전 단일 혼합 측정에서는 `SUMMARY` mode 송금 API에 0.34% 에러가 있었지만, 이번 3회 반복 측정에서는 모든 mode에서 송금 API와 통계 API 에러율이 0.00%였다.

통계 조회 기준으로는 `MATERIALIZED_VIEW`와 `SUMMARY`가 반복 측정에서도 10ms 이하 p95를 유지했고, `GROUP_BY`는 p95가 30~40ms대까지 상승하는 패턴이 반복되었다.

## 한계

* 이 결과는 Java benchmark runner가 아니라 k6 기반 로컬 HTTP 측정이다.
* 운영 환경 결과가 아니다.
* 송금 API p95/p99에 대한 최종 판단 자료가 아니다.
* `pg_stat_statements`, `EXPLAIN (ANALYZE, BUFFERS)`, DB CPU/IO, lock wait, connection pool 지표는 함께 측정하지 않았다.
* Fraud 야간 정책은 런타임 인자로만 우회했으며, 기본 Fraud Fail-Closed 정책을 변경하지 않았다.

## 다음 단계

1. 같은 혼합 부하에서 `EXPLAIN (ANALYZE, BUFFERS)` 또는 `pg_stat_statements`로 통계 쿼리의 buffer 사용량과 실행 시간을 확인한다.
2. k6 VUs와 duration을 늘려 송금 API p95/p99가 통계 mode별로 의미 있게 달라지는지 확인한다.
3. PostgreSQL connection 사용량, lock wait, active query duration을 함께 수집한다.
4. Summary refresh와 Materialized View refresh가 송금 API와 동시에 실행될 때의 영향을 별도 측정한다.
