# 거래 통계 EXPLAIN ANALYZE 결과 - 100k

## 1. 목적

이 문서는 100,000건 데이터셋에서 수집한 `EXPLAIN (ANALYZE, BUFFERS)` 결과를 기준으로 XChangePass 거래 통계 조회 방식의 실행 계획과 buffer 관점을 정리한다.

분석 목적은 아래와 같다.

* Java benchmark에서 확인된 `GROUP_BY`, `MATERIALIZED_VIEW`, `SUMMARY` 성능 차이를 SQL 실행 계획과 buffer 관점에서 해석한다.
* `GROUP_BY`가 heavy user 장기 조회에서 데이터 증가 영향을 받는 이유를 확인한다.
* `MATERIALIZED_VIEW`와 `SUMMARY` 조회가 index scan 기반으로 가볍게 동작하는지 확인한다.
* `SUMMARY_REFRESH` 비용이 큰 이유를 Summary refresh `DELETE` / `INSERT SELECT` plan으로 확인한다.
* 이번 결과는 PostgreSQL 실행 계획 분석이며, HTTP/k6/송금 API 혼합 부하 결과가 아니다.
* Primary/Replica 판단은 아직 보류한다.

## 2. 실행 조건

* 데이터 건수: 100,000건
* 사용자 수: 1,000명
* 월 범위: 12개월
* 실행 방식: JUnit + Testcontainers PostgreSQL
* 수집 방식: `EXPLAIN (ANALYZE, BUFFERS)`
* 결과 생성 위치: `build/perf/explain`

실행 명령:

```bash
RUN_TRANSACTION_STATISTICS_EXPLAIN=true \
TRANSACTION_STATISTICS_BENCHMARK_ROWS=100000 \
TRANSACTION_STATISTICS_BENCHMARK_USERS=1000 \
TRANSACTION_STATISTICS_BENCHMARK_MONTHS=12 \
./gradlew test --tests "*TransactionStatisticsExplainAnalyzeTest"
```

## 3. 생성된 EXPLAIN 파일

| 파일 | 대상 |
| --- | --- |
| `group-by-heavy-user-12m.txt` | heavy user 12개월 `GROUP_BY` 조회 |
| `group-by-heavy-user-3m.txt` | heavy user 3개월 `GROUP_BY` 조회 |
| `group-by-regular-user-12m.txt` | regular user 12개월 `GROUP_BY` 조회 |
| `materialized-view-heavy-user-12m.txt` | heavy user 12개월 Materialized View 조회 |
| `materialized-view-heavy-user-3m.txt` | heavy user 3개월 Materialized View 조회 |
| `materialized-view-regular-user-12m.txt` | regular user 12개월 Materialized View 조회 |
| `summary-heavy-user-12m.txt` | heavy user 12개월 Summary 조회 |
| `summary-heavy-user-3m.txt` | heavy user 3개월 Summary 조회 |
| `summary-regular-user-12m.txt` | regular user 12개월 Summary 조회 |
| `materialized-view-definition-select.txt` | Materialized View 정의 SELECT |
| `summary-refresh-delete.txt` | Summary refresh DELETE |
| `summary-refresh-insert-select.txt` | Summary refresh INSERT SELECT |

## 4. 주요 결과 요약

아래 수치는 `build/perf/explain` 하위 파일에서 확인한 최신 실행 결과다.

| 대상 | 실행 계획 핵심 | Execution Time |
| --- | --- | ---: |
| `GROUP_BY` heavy 12m | 원본 테이블 bitmap scan + hash aggregate | 6.824ms |
| `GROUP_BY` heavy 3m | 원본 테이블 bitmap scan + group/hash aggregate | 4.053ms |
| `GROUP_BY` regular 12m | 원본 테이블 bitmap scan + group aggregate | 0.254ms |
| `MATERIALIZED_VIEW` heavy 12m | `ux_mv_transaction_monthly_statistics` index scan | 0.054ms |
| `MATERIALIZED_VIEW` heavy 3m | `ux_mv_transaction_monthly_statistics` index scan | 0.027ms |
| `MATERIALIZED_VIEW` regular 12m | `ux_mv_transaction_monthly_statistics` index scan | 0.020ms |
| `SUMMARY` heavy 12m | `ux_transaction_monthly_summary_key` index scan | 0.062ms |
| `SUMMARY` heavy 3m | `ux_transaction_monthly_summary_key` index scan | 0.027ms |
| `SUMMARY` regular 12m | `ux_transaction_monthly_summary_key` index scan | 0.022ms |
| MV definition SELECT | 원본 테이블 seq scan + hash aggregate, temp 사용 | 79.530ms |
| SUMMARY refresh DELETE | summary seq scan | 15.433ms |
| SUMMARY refresh INSERT SELECT | 원본 seq scan + hash aggregate + insert, temp/write 발생 | 610.406ms |

## 5. GROUP_BY 해석

`GROUP_BY`는 조회 시마다 `wallet_transaction`, `card_transaction`, `exchange_transaction` 원본 테이블을 읽고 `UNION ALL` 후 집계한다.

heavy user 12개월 조회에서는 여러 source를 읽은 뒤 `Finalize HashAggregate`를 수행했다. 원본 row 접근은 `Bitmap Heap Scan` 중심으로 나타났고, 대표적으로 wallet 입출금 6,121 rows, wallet 송신 transfer 4,086 rows, card 6,903 rows, exchange outgoing/incoming 각각 2,441 rows를 읽었다. 결과적으로 execution time은 6.824ms였다.

heavy user 3개월 조회는 같은 구조지만 기간 조건이 좁아 읽는 row 수가 줄었다. wallet 입출금 1,078 rows, wallet 송신 transfer 714 rows, card 1,197 rows, exchange outgoing/incoming 각각 421 rows를 읽었고 execution time은 4.053ms였다.

regular user 12개월 조회는 거래량 자체가 적어 `GROUP_BY`도 낮게 나왔다. wallet/card/exchange 원본 테이블을 읽고 group aggregate를 수행했지만 execution time은 0.254ms였다.

Bitmap scan은 index를 활용해 대상 row 위치를 찾은 뒤 실제 테이블 row를 읽는 방식이다. 즉 `GROUP_BY`는 index를 타더라도 원본 row 접근, `UNION ALL`, `GROUP BY`, `sum/count` 비용이 남는다. 이 특성 때문에 heavy user 또는 긴 기간 조회에서는 데이터 증가 영향을 직접 받는다.

## 6. MATERIALIZED_VIEW 해석

Materialized View 조회는 `mv_transaction_monthly_statistics`에 이미 집계된 row를 조회한다.

세 조회 모두 `ux_mv_transaction_monthly_statistics` index scan을 사용했다.

* heavy user 12개월: 397 rows, execution time 0.054ms
* heavy user 3개월: 101 rows, execution time 0.027ms
* regular user 12개월: 87 rows, execution time 0.020ms

조회 plan에서는 원본 거래 테이블을 다시 읽지 않는다. heavy user와 regular user 모두 execution time이 낮게 나온 이유는 집계된 row만 index로 조회하기 때문이다. 이는 Java benchmark에서 Materialized View 조회가 안정적으로 나온 결과와 일치한다.

## 7. SUMMARY 해석

Summary 조회는 `transaction_monthly_summary`에 이미 집계된 row를 조회한다.

세 조회 모두 `ux_transaction_monthly_summary_key` index scan을 사용했다.

* heavy user 12개월: 397 rows, execution time 0.062ms
* heavy user 3개월: 101 rows, execution time 0.027ms
* regular user 12개월: 87 rows, execution time 0.022ms

조회 plan만 보면 Summary도 매우 가볍게 동작한다. Java benchmark에서 일부 regular user scenario에서 Summary가 Materialized View보다 느리게 나온 것은 SQL plan 자체보다는 JDBC/DTO 매핑, 반복 횟수, 캐시 상태, 결과 row 수, runner 환경 영향일 수 있다. 따라서 Summary 조회 SQL 자체가 비효율적이라고 단정하지 않는다.

## 8. MV definition SELECT 해석

`materialized-view-definition-select.txt`는 `REFRESH MATERIALIZED VIEW` 명령 자체를 직접 `EXPLAIN`한 결과가 아니다.

이 파일은 Materialized View를 구성하는 원본 집계 SELECT를 따로 `EXPLAIN (ANALYZE, BUFFERS)`한 결과다. 따라서 이 수치는 MV refresh 전체 비용이 아니라, MV refresh에서 핵심이 되는 원본 집계 SELECT 부분의 참고 실행 계획이다.

MV definition SELECT는 원본 거래 테이블 seq scan과 hash aggregate를 수행했다.

* `Finalize HashAggregate` 결과 row 수: 83,510 rows
* 원본 table buffer: `shared hit=2695`
* temp 사용: `temp read=1539 written=3015`
* execution time: 79.530ms

원본 scan은 wallet/card/exchange source를 넓게 읽었다.

* wallet 입출금: 29,945 rows
* card: 34,969 rows
* wallet sender transfer: 20,112 rows
* wallet receiver transfer: 20,112 rows
* exchange outgoing: 11,988 rows
* exchange incoming: 11,988 rows

이 결과는 Materialized View 조회와 달리 refresh 계열 작업은 원본 테이블 전체 또는 넓은 범위를 다시 읽고 집계해야 한다는 점을 보여준다.

실제 MV refresh 전체 시간은 Java benchmark의 `MV_REFRESH` 수치를 함께 봐야 한다. 100k benchmark 기준 `MV_REFRESH avg 126.178ms`와 MV definition SELECT `79.530ms`는 서로 다른 측정 범위다. MV definition SELECT는 집계 SELECT 부분이고, `MV_REFRESH`는 `REFRESH MATERIALIZED VIEW` 명령 전체 비용이다.

## 9. SUMMARY refresh 해석

### DELETE

Summary refresh는 대상 월 범위의 기존 summary row를 삭제한다.

DELETE plan에서는 `transaction_monthly_summary`에 대한 seq scan이 나왔다.

* 삭제 대상 row 수: 83,510 rows
* buffer: `shared hit=84732`
* execution time: 15.433ms

DELETE 자체는 이번 실행에서 15.433ms 수준으로, 전체 refresh 비용의 핵심은 `INSERT SELECT` 쪽이다. 다만 반복 refresh 시 delete는 dead tuple을 만들 수 있다.

### INSERT SELECT

Summary refresh `INSERT SELECT`는 원본 거래 테이블을 읽고 `GROUP BY` 후 summary table에 insert한다.

실행 계획에서는 원본 seq scan, hash aggregate, insert, temp/write가 함께 나타났다.

* `HashAggregate` 결과 row 수: 83,510 rows
* 원본 scan buffer: `shared hit=2695`
* 전체 insert 경로 buffer: `shared hit=1165789 dirtied=2373 written=2373`
* temp 사용: `temp read=887 written=1619`
* execution time: 610.406ms

이 작업은 단순 SELECT가 아니라 table write와 index 갱신을 포함한다. 따라서 MV definition SELECT보다 비용이 훨씬 크게 나오는 것이 자연스럽다.

100k 기준 Java benchmark의 `SUMMARY_REFRESH avg 1303.154ms`와 EXPLAIN 기준 `summary-refresh-insert-select 610.406ms`는 서로 다른 측정 범위다. Summary refresh 전체 benchmark 수치는 DELETE + INSERT SELECT + transaction/JDBC/test overhead 등을 포함한다.

Summary refresh는 delete/insert 방식이므로 `n_dead_tup`, autovacuum, autoanalyze 관측이 필요하다.

## 10. Java benchmark와 EXPLAIN 수치 차이

EXPLAIN은 DB 내부 SQL 실행 계획과 실행 시간을 중심으로 본다. 반면 Java benchmark는 Java 호출, JDBC 왕복, `ResultSet` 처리, DTO 매핑, 반복 측정, Testcontainers 환경, JVM 영향까지 포함한다.

따라서 Java benchmark 숫자가 EXPLAIN execution time보다 큰 것은 자연스럽다. 두 결과는 서로 대체 관계가 아니라 보완 관계다.

100k benchmark에서 heavy user 12개월 기준 평균은 아래와 같았다.

* `GROUP_BY_QUERY`: 16.041ms
* `MATERIALIZED_VIEW_QUERY`: 1.033ms
* `SUMMARY_QUERY`: 4.721ms

같은 조건의 EXPLAIN execution time은 각각 6.824ms, 0.054ms, 0.062ms였다. Java benchmark는 애플리케이션 호출 경로를 포함하고, EXPLAIN은 DB SQL 실행 계획을 더 직접적으로 보여준다.

## 11. 결론

조회 성능 관점에서는 Materialized View와 Summary가 원본 `GROUP_BY`보다 가볍다는 실행 계획을 확인했다.

`GROUP_BY`는 원본 테이블 접근과 aggregate 비용 때문에 heavy user/긴 기간 조회에서 비용이 커진다. Materialized View와 Summary 조회는 index scan으로 이미 집계된 row를 조회하므로 SQL 실행 비용이 낮다.

Summary refresh는 delete/insert, hash aggregate, temp/write, index write 비용 때문에 별도 관리가 필요하다. 특히 반복 refresh 구조에서는 dead tuple/autovacuum 관측이 필요하다.

아직 `pg_stat_statements`, `pg_stat_activity`, `pg_stat_user_tables`, k6 혼합 부하 검증은 하지 않았다. Primary/Replica 판단은 아직 보류한다.

## 12. 포트폴리오 반영 가능 문장

아래 표현은 운영 결과가 아니라 개발 및 검증 과정의 결과로 조심스럽게 사용할 수 있다.

* PostgreSQL 기반 거래 통계 조회를 `GROUP_BY`, Materialized View, Summary Table 세 방식으로 구현하고, Java benchmark와 `EXPLAIN (ANALYZE, BUFFERS)`를 통해 조회 비용과 refresh 비용을 비교했다.
* EXPLAIN 결과, `GROUP_BY`는 원본 거래 테이블 scan 및 aggregate 비용이 발생하는 반면, Materialized View와 Summary Table 조회는 index scan 기반으로 이미 집계된 row를 조회해 낮은 SQL 실행 비용을 보였다.
* Summary Table은 조회 비용은 낮았지만, delete/insert refresh 과정에서 원본 scan, hash aggregate, temp/write 및 index 갱신 비용이 발생해 refresh 비용과 autovacuum 관측 필요성을 확인했다.
* Primary/Replica 분리는 바로 도입하지 않고, 통계 조회와 송금 API 혼합 부하 검증 결과를 기준으로 판단하도록 검증 흐름을 설계했다.

피해야 할 표현:

* 운영 환경에서 개선
* 실제 사용자 트래픽 개선
* Primary/Replica 도입 완료
* Materialized View가 항상 최적
* Summary가 항상 최적
* 송금 API 영향 없음 확정
* MV refresh를 EXPLAIN으로 직접 분석 완료
* Summary refresh 비용을 완전히 해결
