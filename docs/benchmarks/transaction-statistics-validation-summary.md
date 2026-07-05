# 거래 통계 조회 방식 검증 전체 요약

## 1. 목적

XChangePass 거래 통계 조회 방식을 `GROUP_BY`, `MATERIALIZED_VIEW`, `SUMMARY` 세 방식으로 비교했다.

이번 검증은 단순 평균 응답 시간만 본 것이 아니라 Java benchmark, `EXPLAIN (ANALYZE, BUFFERS)`, `pg_stat_user_tables`, `pg_stat_statements`, k6 단독/혼합 부하를 단계적으로 확인한 흐름이다.

목적은 통계 조회 방식별 조회 비용, refresh 비용, maintenance 비용, 송금 API와의 혼합 부하 영향을 분리해서 판단하는 것이다.

이 검증은 local/benchmark 환경 결과이며 운영 결과가 아니다. 또한 PostgreSQL Primary/Replica를 구현한 결과가 아니라, 후속 Primary/Replica 후보 판단을 위한 근거 수집 단계다.

## 2. 검증 대상 방식

### GROUP_BY

`GROUP_BY`는 원본 거래 테이블을 조회 시점에 직접 `UNION ALL + GROUP BY`로 집계한다.

최신성은 좋지만 원본 row 접근과 aggregate 비용이 있다. 특히 heavy user, 긴 기간 조회에서는 원본 거래 row를 많이 읽고 집계해야 하므로 비용이 커질 수 있다.

### MATERIALIZED_VIEW

`MATERIALIZED_VIEW`는 PostgreSQL Materialized View에 집계 결과를 저장하고 조회 시에는 `mv_transaction_monthly_statistics`를 읽는다.

조회는 index scan 기반으로 빠르다. 대신 refresh 비용과 freshness 관리가 필요하다. `REFRESH MATERIALIZED VIEW CONCURRENTLY`는 읽기 차단을 줄일 수 있지만 일반 refresh보다 비용이 더 크게 측정됐다.

### SUMMARY

`SUMMARY`는 애플리케이션이 관리하는 `transaction_monthly_summary` table에 집계 결과를 저장한다.

조회는 index scan 기반으로 빠르고, `dataAsOf`로 freshness를 표현할 수 있다. 다만 현재 refresh가 delete/insert 방식이면 dead tuple, vacuum, index write 등 maintenance 비용이 발생한다.

## 3. 기능 구현 및 동일성 검증

세 방식은 동일한 거래 통계 응답을 제공하도록 구현했다.

동일성 비교에서는 아래 값을 핵심 비교 대상으로 삼았다.

* `userId`
* `bucketMonth`
* `sourceType`
* `transactionType`
* `currency`
* `direction`
* `amountSum`
* `transactionCount`

`dataAsOf`는 방식별 의미가 다르므로 동일성 비교 핵심 값에서는 분리했다. `GROUP_BY`는 조회 시점의 원본 테이블을 직접 집계하고, `MATERIALIZED_VIEW`는 마지막 refresh 시점까지의 materialized 결과를 조회하며, `SUMMARY`는 summary row의 `data_as_of` 기준으로 freshness를 표현한다.

통계 데이터는 조회용 파생 데이터로 분리했다. 최신 잔액, 최신 원장, recovery, reconciliation, idempotency, latest state 판단에는 사용하지 않는다. 최신 상태 판단은 기존 primary write model 기준을 유지한다.

## 4. Java benchmark 결과 요약

### 100k 결과

| Scenario | GROUP_BY avg | MATERIALIZED_VIEW avg | SUMMARY avg |
| --- | ---: | ---: | ---: |
| HEAVY_USER_12M | 16.041ms | 1.033ms | 4.721ms |
| HEAVY_USER_3M | 3.190ms | 0.457ms | 0.987ms |
| REGULAR_USERS_12M | 0.518ms | 0.458ms | 1.375ms |

Refresh:

| Refresh | avg |
| --- | ---: |
| MV_REFRESH | 126.178ms |
| MV_REFRESH_CONCURRENTLY | 571.556ms |
| SUMMARY_REFRESH | 1303.154ms |

### 1M smoke 결과

| Scenario | GROUP_BY avg | MATERIALIZED_VIEW avg | SUMMARY avg |
| --- | ---: | ---: | ---: |
| HEAVY_USER_12M | 97.284ms | 0.860ms | 2.170ms |
| HEAVY_USER_3M | 35.918ms | 0.527ms | 0.742ms |
| REGULAR_USERS_12M | 0.589ms | 0.424ms | 1.739ms |

Refresh:

| Refresh | avg |
| --- | ---: |
| MV_REFRESH | 1038.364ms |
| MV_REFRESH_CONCURRENTLY | 2853.261ms |
| SUMMARY_REFRESH | 11828.420ms |

Java benchmark에서는 `GROUP_BY` heavy user 장기 조회가 데이터 증가 영향을 크게 받았다. 100k에서 1M smoke로 늘리자 heavy user 12개월 평균 지연은 16.041ms에서 97.284ms로 증가했다.

`MATERIALIZED_VIEW`와 `SUMMARY` 조회는 상대적으로 안정적이었다. 다만 Summary refresh는 데이터 증가에 따라 큰 비용이 발생했다. 1M 결과는 smoke 성격이므로 p95/p99 안정성은 제한적으로 해석해야 한다.

## 5. EXPLAIN ANALYZE 결과 요약

| 대상 | 실행 계획 핵심 | Execution Time |
| --- | --- | ---: |
| GROUP_BY heavy 12m | 원본 테이블 bitmap scan + hash aggregate | 6.824ms |
| GROUP_BY heavy 3m | 원본 테이블 bitmap scan + group/hash aggregate | 4.053ms |
| GROUP_BY regular 12m | 원본 테이블 bitmap scan + group aggregate | 0.254ms |
| MATERIALIZED_VIEW heavy 12m | index scan | 0.054ms |
| MATERIALIZED_VIEW heavy 3m | index scan | 0.027ms |
| MATERIALIZED_VIEW regular 12m | index scan | 0.020ms |
| SUMMARY heavy 12m | index scan | 0.062ms |
| SUMMARY heavy 3m | index scan | 0.027ms |
| SUMMARY regular 12m | index scan | 0.022ms |
| MV definition SELECT | 원본 seq scan + hash aggregate + temp | 79.530ms |
| SUMMARY refresh DELETE | summary seq scan | 15.433ms |
| SUMMARY refresh INSERT SELECT | 원본 seq scan + hash aggregate + insert + temp/write | 610.406ms |

`GROUP_BY`는 원본 row 접근과 aggregate 비용이 존재했다. 반면 `MATERIALIZED_VIEW`와 `SUMMARY` 조회는 이미 집계된 row를 index scan으로 조회했다.

`MV definition SELECT`는 `REFRESH MATERIALIZED VIEW` 자체를 `EXPLAIN`한 것이 아니다. Materialized View를 구성하는 원본 집계 SELECT의 참고 실행 계획이다. 실제 MV refresh 전체 비용은 Java benchmark의 `MV_REFRESH` 수치와 함께 봐야 한다.

Summary refresh는 `INSERT SELECT`에서 원본 scan, hash aggregate, insert, temp/write 비용이 크게 나타났다.

## 6. pg_stat_user_tables 결과 요약

| Snapshot | n_tup_ins | n_tup_del | n_live_tup | n_dead_tup | analyze_count | vacuum_count |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 최초 refresh 후 | 83,510 | 0 | 83,510 | 0 | 1 | 0 |
| 반복 refresh 후 | 501,060 | 417,550 | 83,510 | 417,550 | 1 | 0 |
| VACUUM ANALYZE 후 | 501,060 | 417,550 | 83,510 | 0 | 2 | 1 |

Summary refresh를 반복하면 delete/insert로 dead tuple이 누적됐다. `VACUUM ANALYZE` 후에는 dead tuple이 정리됐다.

따라서 Summary는 조회는 빠르지만 refresh와 maintenance 비용을 함께 설계해야 한다. refresh 주기, dead tuple, vacuum, analyze 관측 없이 조회 성능만 보고 선택하면 안 된다.

## 7. pg_stat_statements 결과 요약

조회 결과:

| Scenario | GROUP_BY mean | MV mean | SUMMARY mean |
| --- | ---: | ---: | ---: |
| HEAVY_USER_12M | 12.584ms | 0.350ms | 0.326ms |
| HEAVY_USER_3M | 3.279ms | 0.102ms | 0.064ms |
| REGULAR_USER_12M | 0.131ms | 0.071ms | 0.048ms |

HEAVY_USER_12M shared hit:

| Mode | shared hit |
| --- | ---: |
| GROUP_BY | 376,451 |
| MATERIALIZED_VIEW | 6,514 |
| SUMMARY | 7,412 |

Refresh:

| Refresh | mean |
| --- | ---: |
| MV_REFRESH | 134.946ms |
| MV_REFRESH_CONCURRENTLY | 533.507ms |
| SUMMARY_REFRESH INSERT SELECT | 534.116ms |
| SUMMARY_REFRESH DELETE | 39.527ms |

`GROUP_BY`는 반복 실행에서 block 접근이 컸다. `MATERIALIZED_VIEW`와 `SUMMARY` 조회는 낮은 statement 비용으로 관측됐다.

Summary refresh는 `DELETE`와 `INSERT SELECT`가 별도 비용으로 관측됐다. 이는 Summary Table이 단순 read 최적화만이 아니라 refresh/maintenance 설계 대상이라는 점을 다시 보여준다.

## 8. k6 통계 API 단독 read 결과 요약

| Mode | avg | p95 | p99 | requests | error |
| --- | ---: | ---: | ---: | ---: | ---: |
| GROUP_BY | 12.19ms | 42.05ms | 47.21ms | 2,660 | 0.00% |
| MATERIALIZED_VIEW | 7.89ms | 9.43ms | 9.99ms | 2,770 | 0.00% |
| SUMMARY | 8.10ms | 9.70ms | 10.72ms | 2,760 | 0.00% |

HTTP API 경로에서도 `GROUP_BY`는 p95/p99가 높았다. `MATERIALIZED_VIEW`와 `SUMMARY`는 통계 read 자체가 안정적이었다.

이 결과는 통계 API 단독 읽기 부하 기준이다. 송금 API와 동시에 실행한 혼합 부하 결과가 아니므로 송금 p95/p99 영향이나 DB connection 경합 여부는 이 결과만으로 판단하지 않는다.

## 9. k6 송금 API 단독 baseline

| Scenario | avg | p95 | p99 | requests | error |
| --- | ---: | ---: | ---: | ---: | ---: |
| TRANSFER_BASELINE | 16.50ms | 21.49ms | 29.20ms | 300 | 0.00% |

송금 API 단독 기준선은 혼합 부하에서 송금 API 영향도를 비교하기 위한 기준값이다.

송금 API는 unique `Idempotency-Key`, HTTP 200, 응답 상태 `COMPLETED`를 성공 기준으로 측정했다.

## 10. k6 송금 + 통계 API 혼합 부하 1회 결과

### 송금 API 지표

| Mixed Scenario | Transfer avg | Transfer p95 | Transfer p99 | Transfer error |
| --- | ---: | ---: | ---: | ---: |
| GROUP_BY | 22.46ms | 34.69ms | 44.75ms | 0.00% |
| MATERIALIZED_VIEW | 28.80ms | 33.28ms | 38.61ms | 0.00% |
| SUMMARY | 30.28ms | 37.68ms | 39.28ms | 0.34% |

### 통계 API 지표

| Mixed Scenario | Statistics avg | Statistics p95 | Statistics p99 | Statistics error |
| --- | ---: | ---: | ---: | ---: |
| GROUP_BY | 10.64ms | 38.38ms | 42.55ms | 0.00% |
| MATERIALIZED_VIEW | 7.20ms | 10.09ms | 10.94ms | 0.00% |
| SUMMARY | 8.01ms | 9.92ms | 10.66ms | 0.00% |

1회 혼합 부하에서는 세 모드 모두 송금 API tail latency가 baseline보다 증가했다. `GROUP_BY`는 통계 API 자체 p95/p99가 가장 높았다.

`SUMMARY`의 송금 error 0.34%는 1회 측정에서 관측된 값이므로 반복 측정에서 재현 여부 확인이 필요했다.

## 11. k6 송금 + 통계 API 혼합 부하 반복 결과

반복 평균 집계:

| Mode | Transfer avg | Transfer p95 | Transfer p99 | Statistics avg | Statistics p95 | Statistics p99 | Error |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| GROUP_BY | 13.635ms | 25.332ms | 35.161ms | 10.729ms | 39.368ms | 40.676ms | 0.00% |
| MATERIALIZED_VIEW | 18.991ms | 23.557ms | 24.993ms | 6.854ms | 7.954ms | 9.352ms | 0.00% |
| SUMMARY | 18.801ms | 22.932ms | 23.501ms | 7.105ms | 8.521ms | 9.106ms | 0.00% |

반복 측정에서도 `GROUP_BY` 통계 API p95/p99가 높았다. `MATERIALIZED_VIEW`와 `SUMMARY` 통계 read는 p95 10ms 이하로 안정적이었다.

이전 단일 실행의 Summary 송금 error 0.34%는 반복 측정에서 재현되지 않았다.

주의할 점은 반복 혼합 스크립트가 송금 요청과 통계 요청을 같은 k6 실행 안에서 함께 발생시키는 구조라는 것이다. 통계 API 응답 시간이 mode마다 다르면 k6 요청 루프의 진행 속도와 요청 발생 타이밍도 달라질 수 있다. 따라서 반복 측정의 송금 API avg/p95/p99는 mode별 송금 latency 비교 근거로 확정하면 안 된다.

송금 API 응답속도를 mode별로 정확히 비교하려면 송금 scenario와 통계 scenario를 분리하고, 송금 요청은 `constant-arrival-rate`로 고정한 별도 실험이 필요하다.

반복 측정은 Fraud 야간 정책을 소스 수정 없이 런타임 인자로만 회피했으며, Fraud 기본 정책은 변경하지 않았다.

## 12. 종합 해석

`GROUP_BY`는 구현이 단순하고 최신성이 높지만, heavy user 장기 조회와 HTTP/k6 tail latency에서 불리했다.

`MATERIALIZED_VIEW`는 조회 성능과 tail latency가 안정적이며, 현재 결과 기준 가장 균형 잡힌 후보로 보인다. refresh 비용은 존재하지만 Summary refresh보다 낮게 측정됐고, 조회 경로는 index scan 기반으로 가볍게 동작했다.

`SUMMARY`는 조회 read는 안정적이지만 refresh/maintenance 비용이 크다. Summary를 선택하려면 refresh 주기, dead tuple, vacuum, `dataAsOf` 정책을 함께 설계해야 한다.

통계 조회와 과거 조회는 최신 상태 판단과 분리된 파생 조회로 다루는 것이 타당하다. 송금, 잔액 변경, 원장 저장, recovery, reconciliation, idempotency, latest state 판단은 Primary 유지 대상이다.

통계 조회와 지연을 허용할 수 있는 과거 조회는 Read Replica 후보로 볼 수 있다. 다만 Primary/Replica는 아직 구현하지 않았고, 운영 결과도 아니므로 “후보” 또는 “판단 근거”로만 표현한다.

## 13. 현재 기준 권장 방향

통계 API 기본 read 경로는 원본 `GROUP_BY`보다 `MATERIALIZED_VIEW` 또는 `SUMMARY` 같은 파생 조회 구조가 적합하다.

현재 benchmark 결과만 보면 `MATERIALIZED_VIEW`가 조회 안정성, refresh 비용, maintenance 부담의 균형 면에서 가장 무난한 후보로 보인다.

`SUMMARY`는 조회 성능은 좋지만 refresh/maintenance 비용 때문에 별도 관리 전략이 필요하다.

`GROUP_BY`는 baseline 또는 소량/관리자/검증용 경로로 남길 수 있으나 heavy user 장기 조회의 기본 경로로 두기는 부담이 있다.

Read Replica는 통계 조회/과거 조회 분리 후보로 볼 수 있다. Primary에는 송금 write와 최신 상태 판단을 유지한다.

## 14. 한계

* local benchmark 환경 결과다.
* 운영 트래픽 결과가 아니다.
* k6 VUs와 duration이 제한적이다.
* 반복 측정은 3회 기준이다.
* 1M은 smoke 성격이다.
* DB CPU/IO, lock wait, connection pool 관측은 아직 제한적이다.
* Summary refresh와 송금 API 동시 실행은 별도 시나리오가 필요하다.
* 반복 혼합 측정의 송금 latency는 mode별 직접 비교 근거로 쓰기 어렵다.
* 송금 latency 영향 판단에는 `constant-arrival-rate` 기반 혼합 실험이 필요하다.
* Primary/Replica는 아직 구현하지 않았다.

## 15. 포트폴리오 반영 가능 문장

아래 문장은 운영 결과가 아니라 개발 및 검증 결과 기준으로 사용할 수 있다.

* 거래 통계 조회를 GROUP_BY, Materialized View, Summary Table 세 방식으로 구현하고, Java benchmark, EXPLAIN, pg_stat_statements, k6 단독/혼합 부하를 통해 조회 비용과 송금 API 영향도를 비교했다.
* 원본 테이블 직접 GROUP_BY는 heavy user 장기 조회와 HTTP tail latency에서 비용이 커지는 경향을 보였고, Materialized View와 Summary Table 조회는 index 기반 파생 데이터 조회로 안정적인 p95/p99를 보였다.
* Summary Table은 조회 성능은 안정적이었지만, refresh 반복 시 dead tuple과 vacuum 관리 대상이 되는 점을 pg_stat_user_tables로 확인했다.
* 통계 조회는 최신 잔액/원장/recovery 판단과 분리된 파생 조회로 설계하고, Read Replica 분리 여부는 송금 API 혼합 부하 결과를 기준으로 판단하도록 했다.

피해야 할 표현:

* 운영 환경에서 개선
* 실제 사용자 트래픽 개선
* Primary/Replica 도입 완료
* Read Replica 필수
* 송금 API 영향 없음 확정
* MV가 항상 최적
* Summary가 항상 최적
* GROUP_BY는 항상 부적합
* Summary refresh 비용 해결 완료
* Fraud 정책 완화
