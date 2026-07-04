# 거래 통계 Table Stats 결과 - 100k

## 1. 목적

이 문서는 Summary refresh의 delete/insert 방식이 PostgreSQL table statistics와 maintenance 관점에서 어떤 영향을 주는지 확인한 결과를 정리한다.

확인 대상은 `pg_stat_user_tables`이며, 특히 `transaction_monthly_summary`의 아래 지표 변화를 중심으로 본다.

* `n_tup_ins`
* `n_tup_del`
* `n_live_tup`
* `n_dead_tup`
* `vacuum_count`
* `analyze_count`

이 결과는 benchmark/Testcontainers 기준 관측이다. 운영 결과가 아니며, Primary/Replica 판단도 아직 보류한다.

## 2. 실행 조건

* 데이터 건수: 100,000건
* 사용자 수: 1,000명
* 월 범위: 12개월
* Summary refresh 반복 횟수: 5회
* 실행 방식: JUnit + Testcontainers PostgreSQL
* 수집 도구: `pg_stat_user_tables`
* 결과 CSV 위치: `build/perf/table-stats/transaction-statistics-table-stats.csv`

실행 명령:

```bash
RUN_TRANSACTION_STATISTICS_TABLE_STATS=true \
TRANSACTION_STATISTICS_BENCHMARK_ROWS=100000 \
TRANSACTION_STATISTICS_BENCHMARK_USERS=1000 \
TRANSACTION_STATISTICS_BENCHMARK_MONTHS=12 \
TRANSACTION_STATISTICS_TABLE_STATS_REFRESH_REPEAT=5 \
./gradlew test --tests "*TransactionStatisticsTableStatsTest"
```

## 3. 수집 Snapshot

| Snapshot | 의미 |
| --- | --- |
| `A_AFTER_DATA_LOAD` | benchmark data factory로 원본 거래 데이터 생성 직후, Summary refresh 전 |
| `B_AFTER_ANALYZE` | `wallet_transaction`, `card_transaction`, `exchange_transaction`, `transaction_monthly_summary`에 수동 `ANALYZE` 실행 후 |
| `C_AFTER_FIRST_SUMMARY_REFRESH` | `refreshMonthlySummary(fromMonth, toMonth)` 최초 실행 후 |
| `D_AFTER_REPEATED_SUMMARY_REFRESH` | 같은 월 범위에 대해 Summary refresh 5회 추가 반복 후 |
| `E_AFTER_VACUUM_ANALYZE` | benchmark 전용 Testcontainers 환경에서 `VACUUM ANALYZE transaction_monthly_summary` 실행 후 |

## 4. 주요 결과

### transaction_monthly_summary

| Snapshot | relname | n_tup_ins | n_tup_del | n_live_tup | n_dead_tup | analyze_count | vacuum_count | notes |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `A_AFTER_DATA_LOAD` | `transaction_monthly_summary` | 0 | 0 | 0 | 0 | 0 | 0 | 원본 데이터 적재 직후, Summary refresh 전 |
| `B_AFTER_ANALYZE` | `transaction_monthly_summary` | 0 | 0 | 0 | 0 | 1 | 0 | 수동 `ANALYZE` 실행 |
| `C_AFTER_FIRST_SUMMARY_REFRESH` | `transaction_monthly_summary` | 83,510 | 0 | 83,510 | 0 | 1 | 0 | Summary 최초 refresh 완료 |
| `D_AFTER_REPEATED_SUMMARY_REFRESH` | `transaction_monthly_summary` | 501,060 | 417,550 | 83,510 | 417,550 | 1 | 0 | Summary refresh 5회 추가 반복 |
| `E_AFTER_VACUUM_ANALYZE` | `transaction_monthly_summary` | 501,060 | 417,550 | 83,510 | 0 | 2 | 1 | `VACUUM ANALYZE` 완료 |

### 원본 거래 테이블 보조 관측

| Snapshot | relname | seq_scan | seq_tup_read | n_tup_ins | n_tup_del | n_live_tup | n_dead_tup | analyze_count |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `A_AFTER_DATA_LOAD` | `wallet_transaction` | 6 | 0 | 50,057 | 0 | 50,057 | 0 | 0 |
| `A_AFTER_DATA_LOAD` | `card_transaction` | 4 | 0 | 34,969 | 0 | 34,969 | 0 | 0 |
| `A_AFTER_DATA_LOAD` | `exchange_transaction` | 4 | 0 | 14,974 | 0 | 14,974 | 0 | 0 |
| `B_AFTER_ANALYZE` | `wallet_transaction` | 6 | 0 | 50,057 | 0 | 50,057 | 0 | 1 |
| `B_AFTER_ANALYZE` | `card_transaction` | 4 | 0 | 34,969 | 0 | 34,969 | 0 | 1 |
| `B_AFTER_ANALYZE` | `exchange_transaction` | 4 | 0 | 14,974 | 0 | 14,974 | 0 | 1 |
| `D_AFTER_REPEATED_SUMMARY_REFRESH` | `wallet_transaction` | 24 | 901,026 | 50,057 | 0 | 50,057 | 0 | 1 |
| `D_AFTER_REPEATED_SUMMARY_REFRESH` | `card_transaction` | 10 | 209,814 | 34,969 | 0 | 34,969 | 0 | 1 |
| `D_AFTER_REPEATED_SUMMARY_REFRESH` | `exchange_transaction` | 16 | 179,688 | 14,974 | 0 | 14,974 | 0 | 1 |
| `E_AFTER_VACUUM_ANALYZE` | `wallet_transaction` | 24 | 901,026 | 50,057 | 0 | 50,057 | 0 | 1 |
| `E_AFTER_VACUUM_ANALYZE` | `card_transaction` | 10 | 209,814 | 34,969 | 0 | 34,969 | 0 | 1 |
| `E_AFTER_VACUUM_ANALYZE` | `exchange_transaction` | 16 | 179,688 | 14,974 | 0 | 14,974 | 0 | 1 |

## 5. 해석

Summary 최초 refresh 후 `transaction_monthly_summary`에는 83,510건의 summary row가 insert됐다. 이 시점에서 `n_tup_ins`는 83,510으로 증가했고, `n_live_tup`도 83,510으로 잡혔다.

같은 월 범위에 대해 Summary refresh를 5회 추가 반복한 뒤에는 `n_tup_ins`가 501,060, `n_tup_del`이 417,550으로 증가했다. `n_live_tup`는 83,510으로 유지됐고, `n_dead_tup`는 417,550까지 증가했다.

이는 현재 Summary refresh가 대상 범위의 기존 row를 삭제한 뒤 다시 insert하는 delete/insert 방식이기 때문이다. 조회 시에는 Summary Table이 index scan 기반으로 가볍게 동작하지만, refresh 반복 시에는 table write와 dead tuple이 누적될 수 있다.

`VACUUM ANALYZE transaction_monthly_summary` 실행 후에는 `vacuum_count`가 1, `analyze_count`가 2로 증가했고 `n_dead_tup`는 0으로 내려갔다. 이 결과는 반복 refresh 이후 Summary Table이 vacuum/autovacuum 및 analyze 관측 대상이 된다는 점을 보여준다.

이번 실행에서는 `n_dead_tup`가 명확히 증가했다. 다만 다른 환경에서 `n_dead_tup`가 예상보다 작거나 0으로 보일 수 있다. PostgreSQL 통계 갱신 타이밍, autovacuum, analyze, 트랜잭션 종료 시점, 테스트 환경 특성에 따라 `pg_stat_user_tables` 반영 시점이 달라질 수 있기 때문이다.

결론적으로 이 결과는 Summary Table이 “조회는 빠르지만 refresh/maintenance 비용이 있다”는 판단을 보완한다.

## 6. EXPLAIN / Benchmark 결과와 연결

이전 `EXPLAIN (ANALYZE, BUFFERS)` 결과에서 Summary refresh `INSERT SELECT`는 원본 seq scan + hash aggregate + insert + temp/write를 사용했다. 100k 기준 `summary-refresh-insert-select`의 execution time은 610.406ms였고, Java benchmark의 `SUMMARY_REFRESH` 평균은 1303.154ms였다.

이번 table stats 결과는 이 refresh가 실제로 `transaction_monthly_summary`에 insert/delete를 발생시키고, 반복 실행 시 dead tuple과 vacuum 관측 대상이 된다는 점을 보조 근거로 보여준다.

따라서 Summary Table은 조회 최적화 후보지만 refresh 주기와 maintenance 비용을 함께 설계해야 한다.

## 7. Primary/Replica 판단

이번 결과만으로 Primary/Replica 분리를 결정하지 않는다.

현재 결과는 Summary refresh의 PostgreSQL maintenance 관측이다. Primary/Replica 판단에는 송금 API + 통계 API 혼합 부하 검증이 필요하다.

Replica 후보:

* 통계 조회
* Summary 조회
* 지연을 허용할 수 있는 과거 거래 조회

Primary 유지 대상:

* 최신성이 필요한 잔액 조회
* 송금 처리
* 원장 저장
* Recovery 판단
* Reconciliation 판단
* Idempotency 판단
* 최신 상태 판단

## 8. 포트폴리오 반영 가능 문장

아래 표현은 운영 결과가 아니라 개발 및 검증 과정의 결과로 조심스럽게 사용할 수 있다.

* Summary Table은 조회 시 index scan 기반으로 낮은 SQL 실행 비용을 보였지만, refresh 과정에서는 delete/insert로 인한 table write와 maintenance 비용이 발생할 수 있음을 `pg_stat_user_tables` 관측 대상으로 확인했다.
* PostgreSQL의 `EXPLAIN (ANALYZE, BUFFERS)`와 `pg_stat_user_tables`를 함께 사용해 조회 성능뿐 아니라 refresh 이후 dead tuple/vacuum 관점까지 검증했다.
* 통계 조회는 최신 잔액/원장/recovery 판단과 분리된 파생 데이터로 설계하고, Primary/Replica 분리는 혼합 부하 검증 결과를 기준으로 판단하도록 했다.

피해야 할 표현:

* 운영 환경에서 개선
* 실제 사용자 트래픽 개선
* Primary/Replica 도입 완료
* autovacuum 문제 해결 완료
* Summary refresh 비용 완전 해결
* 송금 API 영향 없음 확정
