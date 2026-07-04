# 거래 통계 pg_stat_statements 결과 - 100k

## 1. 목적

이 문서는 XChangePass 거래 통계 조회 방식 3가지에 대해 PostgreSQL `pg_stat_statements`로 수집한 100,000건 기준 SQL 실행 통계를 기록한다.

비교 대상은 아래 세 가지다.

* `GROUP_BY`: 원본 거래 테이블을 직접 `UNION ALL + GROUP BY`로 집계
* `MATERIALIZED_VIEW`: `mv_transaction_monthly_statistics` 조회
* `SUMMARY`: `transaction_monthly_summary` 조회

추가로 refresh 비용은 아래 항목을 관측했다.

* Materialized View 일반 refresh
* Materialized View concurrent refresh
* Summary Table refresh

`pg_stat_statements`는 거래 통계 API를 만들어주는 기능이 아니라 PostgreSQL이 실행한 SQL의 호출 수, 실행 시간, block 접근량, temp block 사용량을 집계하는 관측 도구다.

이번 수집은 benchmark 전용 Testcontainers PostgreSQL에서만 `shared_preload_libraries=pg_stat_statements`를 활성화했다. 운영 PostgreSQL 설정, 로컬 공통 설정, 배포 설정은 변경하지 않았다.

## 2. 실행 조건

* 실행 방식: JUnit + Testcontainers PostgreSQL
* 수집 도구: `pg_stat_statements`
* 적용 범위: benchmark 전용 PostgreSQL container
* 데이터 건수: 100,000건
* 사용자 수: 1,000명
* 월 범위: 12개월
* 반복 횟수: 20회
* random seed: `20260704`
* 결과 CSV: `build/perf/pg-stat-statements/transaction-statistics-pg-stat-statements.csv`

실행 명령:

```bash
RUN_TRANSACTION_STATISTICS_PG_STAT_STATEMENTS=true \
TRANSACTION_STATISTICS_BENCHMARK_ROWS=100000 \
TRANSACTION_STATISTICS_BENCHMARK_USERS=1000 \
TRANSACTION_STATISTICS_BENCHMARK_MONTHS=12 \
TRANSACTION_STATISTICS_PGSTAT_ITERATIONS=20 \
./gradlew test --tests "*TransactionStatisticsPgStatStatementsTest"
```

## 3. 데이터셋

benchmark data factory가 생성한 데이터셋은 아래와 같다.

* 전체 거래 row 수: 100,000건
* 사용자 수: 1,000명
* 월 범위: 2026-01 ~ 2026-12
* Wallet row 수: 50,057건
* Card row 수: 34,969건
* Exchange row 수: 14,974건
* heavy user 포함
* regular user 포함

## 4. 수집 방식

각 시나리오는 실행 전에 `pg_stat_statements_reset()`으로 누적 통계를 초기화한 뒤 같은 작업을 20회 반복했다.

그 다음 `pg_stat_statements`에서 현재 database의 statement를 `total_exec_time` 기준으로 정렬해 상위 statement를 CSV로 남겼다.

조회 시나리오는 각 모드별 repository 조회 메서드를 그대로 호출했다.

refresh 시나리오는 아래 메서드를 호출했다.

* `refreshMaterializedView()`
* `refreshMaterializedViewConcurrently()`
* `refreshMonthlySummary(fromMonth, toMonth)`

## 5. 조회 SQL 관측 결과

아래 표는 각 시나리오의 상위 statement 기준 결과다.

| 시나리오 | 모드 | calls | mean_exec_time | total_exec_time | rows | shared_hit | shared_read | temp_read | temp_written |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `HEAVY_USER_12M` | `GROUP_BY_QUERY` | 20 | 12.584ms | 251.680ms | 7,940 | 376,451 | 0 | 0 | 0 |
| `HEAVY_USER_12M` | `MATERIALIZED_VIEW_QUERY` | 20 | 0.350ms | 7.007ms | 7,940 | 6,514 | 6 | 0 | 0 |
| `HEAVY_USER_12M` | `SUMMARY_QUERY` | 20 | 0.326ms | 6.524ms | 7,940 | 7,412 | 0 | 0 | 0 |
| `HEAVY_USER_3M` | `GROUP_BY_QUERY` | 20 | 3.279ms | 65.584ms | 2,020 | 113,340 | 0 | 0 | 0 |
| `HEAVY_USER_3M` | `MATERIALIZED_VIEW_QUERY` | 20 | 0.102ms | 2.046ms | 2,020 | 1,900 | 0 | 0 | 0 |
| `HEAVY_USER_3M` | `SUMMARY_QUERY` | 20 | 0.064ms | 1.272ms | 2,020 | 2,080 | 0 | 0 | 0 |
| `REGULAR_USER_12M` | `GROUP_BY_QUERY` | 20 | 0.131ms | 2.623ms | 1,740 | 3,300 | 0 | 0 | 0 |
| `REGULAR_USER_12M` | `MATERIALIZED_VIEW_QUERY` | 20 | 0.071ms | 1.427ms | 1,740 | 1,680 | 0 | 0 | 0 |
| `REGULAR_USER_12M` | `SUMMARY_QUERY` | 20 | 0.048ms | 0.967ms | 1,740 | 1,800 | 0 | 0 | 0 |

## 6. Refresh SQL 관측 결과

| 시나리오 | statement | calls | mean_exec_time | total_exec_time | rows | shared_hit | shared_read | temp_read | temp_written |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `MV_REFRESH` | `REFRESH MATERIALIZED VIEW` | 20 | 134.946ms | 2,698.916ms | 1,670,200 | 123,585 | 0 | 31,844 | 62,071 |
| `MV_REFRESH_CONCURRENTLY` | `REFRESH MATERIALIZED VIEW CONCURRENTLY` | 20 | 533.507ms | 10,670.138ms | 1,670,200 | 96,624 | 7 | 131,499 | 162,736 |
| `SUMMARY_REFRESH` | `INSERT SELECT` | 17 | 534.116ms | 9,079.967ms | 1,419,670 | 22,745,311 | 7,528 | 16,321 | 22,204 |
| `SUMMARY_REFRESH` | `DELETE` | 20 | 39.527ms | 790.533ms | 1,670,200 | 1,771,531 | 166,996 | 0 | 0 |

`SUMMARY_REFRESH`는 delete/insert 방식이므로 `pg_stat_statements`에서도 `DELETE`와 `INSERT SELECT`가 별도 statement로 관측된다.

이번 실행에서 `SUMMARY_REFRESH`의 `INSERT SELECT`는 동일한 normalized query가 17회와 3회로 나뉘어 기록됐다. 이는 PostgreSQL의 query normalization, plan/stat entry 관리, statement 내부 형태 차이의 영향을 받을 수 있다. 따라서 Summary refresh 총비용은 단일 row 하나만 보지 않고 `INSERT SELECT`와 `DELETE`를 함께 해석해야 한다.

## 7. 해석

heavy user 12개월 조회에서 `GROUP_BY_QUERY`는 평균 12.584ms, `MATERIALIZED_VIEW_QUERY`는 평균 0.350ms, `SUMMARY_QUERY`는 평균 0.326ms로 관측됐다.

같은 시나리오에서 shared block hit는 `GROUP_BY_QUERY`가 376,451, `MATERIALIZED_VIEW_QUERY`가 6,514, `SUMMARY_QUERY`가 7,412였다. 이는 원본 거래 테이블을 직접 집계하는 `GROUP_BY`가 파생 테이블/View 조회보다 훨씬 많은 buffer 접근을 발생시킨다는 것을 보여준다.

heavy user 3개월 조회에서도 `GROUP_BY_QUERY`의 shared block hit는 113,340으로, `MATERIALIZED_VIEW_QUERY` 1,900 및 `SUMMARY_QUERY` 2,080보다 컸다.

regular user 12개월 조회에서는 세 방식 모두 절대 실행 시간이 작았다. 다만 이 경우에도 `SUMMARY_QUERY`와 `MATERIALIZED_VIEW_QUERY`가 `GROUP_BY_QUERY`보다 낮은 평균 실행 시간을 보였다.

refresh 쪽에서는 `MV_REFRESH_CONCURRENTLY`가 일반 `MV_REFRESH`보다 더 큰 평균 실행 시간과 temp block 사용량을 보였다. `SUMMARY_REFRESH`는 `INSERT SELECT`와 `DELETE`를 함께 고려해야 하며, 특히 `INSERT SELECT`에서 shared block hit와 shared block read가 크게 관측됐다.

## 8. 기존 결과와 연결

Java benchmark 100k 결과에서는 heavy user 12개월 기준 평균 지연이 아래처럼 측정됐다.

* `GROUP_BY`: 16.041ms
* `MATERIALIZED_VIEW`: 1.033ms
* `SUMMARY`: 4.721ms

이번 `pg_stat_statements` 결과도 같은 방향을 보였다. 원본 테이블 직접 `GROUP_BY`는 조회 시 많은 block 접근과 더 큰 SQL 실행 시간을 보였고, `MATERIALIZED_VIEW`와 `SUMMARY` 조회는 상대적으로 가볍게 동작했다.

EXPLAIN 결과는 개별 SQL 실행 계획과 buffer 사용량을 보여주고, `pg_stat_statements`는 반복 실행 동안 누적된 statement 단위 통계를 보여준다. 두 결과를 함께 보면 `GROUP_BY`의 병목이 원본 거래 테이블 직접 집계에 있고, `MATERIALIZED_VIEW`와 `SUMMARY`는 조회 시 이미 집계된 결과를 읽기 때문에 가볍다는 판단을 보강할 수 있다.

Table Stats 결과에서는 Summary refresh 반복 시 `transaction_monthly_summary`의 insert/delete와 dead tuple 증가가 관측됐다. 이번 `pg_stat_statements` 결과는 그 refresh 과정에서 실제 `DELETE`와 `INSERT SELECT`가 별도 비용으로 잡힌다는 점을 추가로 보여준다.

## 9. Primary/Replica 판단

이번 결과만으로 Primary/Replica 분리를 결정하지 않는다.

이번 수집은 PostgreSQL statement 단위 실행 통계이며, HTTP/k6 결과가 아니다. 송금 API p95/p99에 통계 조회가 어떤 영향을 주는지는 아직 측정하지 않았다.

Replica 후보로 볼 수 있는 것은 지연을 허용할 수 있는 통계 조회나 과거 거래 조회다.

Primary에 유지해야 하는 것은 아래 흐름이다.

* 송금 처리
* 잔액 변경
* 원장 저장
* 최신 잔액 조회
* 최신 원장/상태 조회
* Recovery 판단
* Reconciliation 판단
* Idempotency 판단

Primary/Replica 필요성은 통계 API 단독 부하와 송금 API + 통계 API 혼합 부하를 측정한 뒤 판단한다.

## 10. 포트폴리오 반영 가능 문장

아래 표현은 운영 결과가 아니라 개발 및 검증 과정의 결과로 조심스럽게 사용할 수 있다.

* PostgreSQL `pg_stat_statements`를 benchmark 전용 Testcontainers 환경에서 활성화해 거래 통계 조회 방식별 SQL 실행 시간과 block 접근량을 수집했다.
* 100,000건 기준 heavy user 12개월 조회에서 원본 테이블 직접 `GROUP_BY`는 Materialized View 및 Summary Table 조회보다 더 많은 shared block 접근을 보였다.
* `EXPLAIN (ANALYZE, BUFFERS)`, Java benchmark, `pg_stat_user_tables`, `pg_stat_statements`를 함께 사용해 조회 비용과 refresh/maintenance 비용을 분리해서 검증했다.
* 통계 데이터는 최신 잔액, 원장, recovery, reconciliation 판단과 분리된 조회용 파생 데이터로 다루고, Primary/Replica 분리는 혼합 부하 검증 전까지 보류했다.

피해야 할 표현:

* 운영 환경에서 개선
* 실제 사용자 트래픽 개선
* Primary/Replica 도입 완료
* 송금 API 영향 없음 확정
* `pg_stat_statements`로 통계 API를 구현
* Materialized View 또는 Summary Table이 항상 최적

## 11. 후속 작업

1. `pg_stat_statements` 결과와 EXPLAIN 결과를 함께 비교해 병목 SQL을 좁힌다.
2. k6로 통계 API 단독 부하 테스트를 준비한다.
3. k6로 송금 API + 통계 API 혼합 부하 테스트를 준비한다.
4. 혼합 부하에서 송금 API p95/p99, DB connection, lock wait, CPU/IO를 함께 관측한다.
5. 그 결과를 기준으로 Primary/Replica 분리 필요성을 판단한다.
