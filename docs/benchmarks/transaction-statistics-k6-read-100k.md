# 거래 통계 k6 읽기 부하 테스트 결과 - 100,000 rows

## 목적

XChangePass 거래 통계 조회 API에서 `GROUP_BY`, `MATERIALIZED_VIEW`, `SUMMARY` 세 조회 방식을 HTTP 경로 기준으로 비교했다.

이 테스트는 Java benchmark runner 결과를 대체하지 않는다. 실제 애플리케이션 서버, Spring Security 필터, Controller/Service 호출, JSON 응답 직렬화까지 포함한 읽기 API 단독 부하 확인용이다.

## 실행 조건

| 항목 | 값 |
| --- | --- |
| 데이터 규모 | 100,000 rows |
| 사용자 수 | 1,000명 |
| 월 범위 | 2026-01 ~ 2026-12 |
| k6 VUs | 10 |
| 실행 시간 | 30s |
| 대상 API | `GET /api/v1/transactions/statistics/monthly` |
| 조회 모드 | `GROUP_BY`, `MATERIALIZED_VIEW`, `SUMMARY` |
| 인증 방식 | benchmark seed가 생성한 `accessToken` 쿠키 |
| 원시 결과 위치 | `build/perf/k6/` |

로컬 환경에서 BlueStack이 IPv4 `127.0.0.1:8080`을 점유하고 있어, XChangePass 서버는 IPv6 loopback인 `http://[::1]:8080`으로 호출했다.

## 데이터 준비

`tools/perf/run-transaction-statistics-k6-seed.sh`로 benchmark 전용 데이터를 준비했다.

- `users`에 benchmark 전용 사용자 생성
- `wallet_transaction`, `card_transaction`, `exchange_transaction`에 100,000 rows 분산 적재
- `mv_transaction_monthly_statistics` refresh
- `transaction_monthly_summary` refresh
- 관련 테이블 `ANALYZE`
- k6 실행용 auth env 생성

auth env와 원시 실행 결과는 로컬 산출물이므로 커밋하지 않는다.

## 실행 명령

```bash
set -a
. build/perf/k6/transaction-statistics-auth.env
set +a
BASE_URL='http://[::1]:8080' K6_VUS=10 K6_DURATION=30s \
  tools/perf/run-transaction-statistics-k6-read.sh
```

## 조회 성능 결과

| Mode | avg | p90 | p95 | p99 | max | requests | error rate |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| GROUP_BY | 12.19ms | 39.25ms | 42.05ms | 47.21ms | 56.69ms | 2,660 | 0.00% |
| MATERIALIZED_VIEW | 7.89ms | 9.09ms | 9.43ms | 9.99ms | 21.68ms | 2,770 | 0.00% |
| SUMMARY | 8.10ms | 9.37ms | 9.70ms | 10.72ms | 13.44ms | 2,760 | 0.00% |

## 해석

`MATERIALIZED_VIEW`와 `SUMMARY`는 HTTP 경로에서도 p95/p99가 10ms 안팎으로 안정적이었다.

`GROUP_BY`는 평균 응답 시간은 12.19ms로 크게 나쁘지 않았지만, p95 42.05ms, p99 47.21ms로 tail latency가 더 크게 나타났다. 원본 거래 테이블을 직접 `UNION ALL + GROUP BY`로 집계하기 때문에 사용자/월 범위와 데이터 분포에 따라 응답 시간이 더 흔들릴 수 있다.

이번 결과는 통계 API 단독 읽기 부하 기준이다. 송금 API와 동시에 실행한 혼합 부하 결과가 아니므로, 송금 p95/p99 영향이나 DB connection 경합 여부는 아직 판단하지 않았다.

## Primary/Replica 판단

이 결과만으로 Primary/Replica 분리를 결정하지 않는다.

다만 통계 조회처럼 최신 잔액, 최신 원장, recovery, reconciliation 판단에 사용하지 않는 파생 조회는 후속 단계에서 Replica 후보가 될 수 있다. 반대로 송금 직후 잔액 조회, 최신 원장 조회, 잔액 정합성 검증, 복구 판단, 멱등성/상태 변경 흐름은 Primary 유지 대상이다.

후속 판단에는 k6 혼합 부하, `EXPLAIN (ANALYZE, BUFFERS)`, `pg_stat_statements`, DB CPU/IO, connection 사용량, lock wait 확인이 필요하다.

## 변경하지 않은 것

- 기존 통계 SQL 변경 없음
- 기존 통계 서비스/비즈니스 로직 변경 없음
- 송금/지갑/잔액/원장/recovery/reconciliation 흐름 변경 없음
- Primary/Replica 구현 없음
- PostgreSQL 운영 설정 변경 없음
- `pg_stat_statements` 활성화 없음
- k6 원시 결과와 auth env 커밋 없음

## 다음 단계

1. k6 송금 API 단독 기준선을 측정한다.
2. 송금 API와 통계 API를 함께 실행하는 혼합 부하를 측정한다.
3. `GROUP_BY`, `MATERIALIZED_VIEW`, `SUMMARY`별 DB connection, lock wait, p95/p99 영향을 비교한다.
4. 필요하면 benchmark 전용 환경에서 `pg_stat_statements`를 활성화해 쿼리별 누적 비용을 확인한다.
