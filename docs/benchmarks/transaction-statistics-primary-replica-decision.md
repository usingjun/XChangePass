# 거래 통계 조회 Primary/Replica 후보 판단

## 1. 목적

이 문서는 거래 통계 조회 방식 검증 결과를 바탕으로 PostgreSQL Primary/Read Replica 분리 가능성을 판단하기 위한 문서다.

구현 문서가 아니며, 운영 반영 결과도 아니다. 현재 판단은 local benchmark, Testcontainers, k6 측정 결과를 기반으로 한 후보 판단이다.

목표는 무엇을 Primary에 남겨야 하는지와 무엇을 Read Replica 후보로 볼 수 있는지 구분하는 것이다. 전체 검증 흐름은 아래 문서에 정리되어 있다.

- [거래 통계 조회 방식 검증 전체 요약](./transaction-statistics-validation-summary.md)

## 2. 현재 검증 상태

현재까지 거래 통계 조회 방식 비교는 아래 단계까지 진행됐다.

* `GROUP_BY`, `MATERIALIZED_VIEW`, `SUMMARY` 구현
* 세 방식 결과 동일성 검증
* Java benchmark 100k
* Java benchmark 1M smoke
* `EXPLAIN (ANALYZE, BUFFERS)`
* `pg_stat_user_tables`
* `pg_stat_statements`
* k6 통계 API 단독 read
* k6 송금 API 단독 baseline
* k6 송금 + 통계 API 혼합 부하 1회
* k6 송금 + 통계 API 혼합 부하 반복 측정
* 전체 검증 흐름 요약 문서 작성

이 흐름은 Primary/Replica를 구현한 결과가 아니라, PostgreSQL 내부 통계 조회 최적화 방식과 read/write 분리 후보를 판단하기 위한 근거 수집이다.

## 3. Primary/Replica 판단 기준

Primary에 남겨야 하는 기준은 아래와 같다.

* 최신성이 강하게 필요하다.
* 정합성이 강하게 필요하다.
* write transaction과 직접 연결된다.
* 중복 처리 방지 판단에 사용된다.
* 장애 복구 판단에 사용된다.
* 원장/잔액의 authoritative source다.
* stale read를 허용할 수 없다.

Read Replica 후보로 볼 수 있는 기준은 아래와 같다.

* 조회 전용이다.
* 지연을 허용할 수 있다.
* 최신 상태 판단에 사용하지 않는다.
* 파생 데이터다.
* 과거 조회다.
* 월별/기간별 집계다.
* 사용자 화면 표시용 통계다.
* stale read가 서비스 정합성을 깨지 않는다.

거래 통계 조회는 최신 잔액이나 recovery 판단에 사용하지 않는 파생 조회이므로, 지연 허용 가능한 Read Replica 후보로 볼 수 있다.

## 4. Primary에 남겨야 하는 경로

아래 경로는 Primary 유지 대상이다.

* 송금 처리
* 잔액 변경
* 원장 저장
* `Idempotency-Key` 판단
* recovery 판단
* reconciliation 판단
* 최신 거래 상태 판단
* 최신 잔액 판단
* fraud 판단과 write path 차단

송금/잔액/원장/recovery/reconciliation/idempotency/latest state 판단은 stale read를 허용하기 어렵기 때문에 Primary write model 기준으로 유지한다.

특히 XChangePass에서 기준 데이터는 거래 원장이다. 지갑 잔액 스냅샷은 빠른 조회용 데이터지만, 최종 정합성 판단은 원장과 Primary write model 기준으로 다뤄야 한다.

## 5. Read Replica 후보로 볼 수 있는 경로

아래 경로는 Read Replica 후보로 볼 수 있다.

* 거래 통계 조회
* 과거 거래내역 조회
* 월별 통계 조회
* 기간별 집계 조회
* 관리자/분석성 조회
* 최신 상태 판단에 사용하지 않는 파생 read model

거래 통계 조회는 최신 잔액이나 recovery 판단에 사용하지 않는 파생 조회이므로, 지연 허용 가능한 Read Replica 후보로 볼 수 있다.

다만 Replica lag가 사용자 경험과 `dataAsOf` 표현에 어떤 영향을 주는지는 별도로 설계해야 한다.

## 6. 거래 통계 조회 방식별 판단

### 6.1 GROUP_BY

`GROUP_BY`는 원본 거래 테이블을 조회 시점에 직접 `UNION ALL + GROUP BY`로 집계한다.

최신성은 좋고 구현이 단순하다. 하지만 heavy user 장기 조회에서 원본 row 접근과 aggregate 비용이 커진다. Java benchmark, EXPLAIN, pg_stat_statements, k6 read 결과에서 이 특성이 반복적으로 확인됐다.

따라서 `GROUP_BY`를 통계 API 기본 경로로 두기에는 부담이 있다. 다만 baseline, 소량 조회, 관리자/검증용 경로로는 남길 수 있다. 항상 부적합하다고 단정하지 않는다.

Primary/Replica 관점에서는 원본 write model을 직접 읽는 방식이므로, 통계 조회 부하가 커질수록 Primary에 남기는 부담이 커질 수 있다. Read Replica 후보로 보려면 최신성 허용 범위와 lag 표현 정책이 필요하다.

### 6.2 MATERIALIZED_VIEW

`MATERIALIZED_VIEW`는 이미 집계된 materialized result를 index scan으로 조회한다.

조회 성능과 tail latency가 안정적이었다. Java benchmark와 EXPLAIN에서 조회 비용이 낮게 나왔고, k6 통계 API 단독 read 및 반복 혼합 측정에서도 통계 read p95가 10ms 이하로 유지됐다.

refresh 비용과 freshness 정책은 필요하다. `REFRESH MATERIALIZED VIEW CONCURRENTLY`는 읽기 차단을 줄일 수 있지만 일반 refresh보다 비용이 크다.

현재 결과 기준으로는 조회 안정성, refresh 비용, maintenance 부담의 균형 면에서 가장 무난한 후보로 보인다. 다만 항상 최적이라고 쓰지 않는다.

Primary/Replica 관점에서는 통계 read model로 분리하기 좋은 구조다. Replica에 두더라도 최신 상태 판단에는 쓰지 않고, 응답에는 refresh 기준 시각 또는 `dataAsOf` 성격의 정보를 함께 노출하는 방향을 검토해야 한다.

### 6.3 SUMMARY

`SUMMARY`는 애플리케이션이 관리하는 summary table을 조회한다.

조회는 안정적이고 `dataAsOf` 같은 freshness 표현이 가능하다. `MATERIALIZED_VIEW`와 마찬가지로 이미 집계된 row를 index scan으로 읽는 구조라 통계 read 자체는 가볍게 동작했다.

하지만 refresh 반복 시 delete/insert로 dead tuple이 누적됐다. 100k table stats에서 최초 refresh 후 `n_live_tup`은 83,510, 반복 refresh 후 `n_dead_tup`은 417,550으로 증가했고, `VACUUM ANALYZE` 후 0으로 정리됐다.

Summary를 선택하려면 refresh 주기, vacuum, analyze, `dataAsOf` 정책, write cost를 함께 설계해야 한다. 조회 성능만 보고 선택하면 안 된다.

Primary/Replica 관점에서는 통계 read model 후보가 될 수 있지만, refresh가 Primary에 write 부하를 줄 수 있다. 따라서 Summary read 분리와 Summary refresh/maintenance 경로는 별도로 판단해야 한다.

## 7. 검증 결과 기반 판단 근거

### 7.1 Java benchmark

100k heavy user 12개월 조회에서 평균 지연은 `GROUP_BY` 16.041ms, `MATERIALIZED_VIEW` 1.033ms, `SUMMARY` 4.721ms였다.

1M smoke heavy user 12개월 조회에서는 `GROUP_BY` 97.284ms, `MATERIALIZED_VIEW` 0.860ms, `SUMMARY` 2.170ms였다.

이 결과는 heavy user 장기 조회에서 `GROUP_BY`가 데이터 증가 영향을 크게 받는다는 근거다. 반대로 `MATERIALIZED_VIEW`와 `SUMMARY`는 이미 집계된 read model을 조회하므로 상대적으로 안정적이었다.

refresh 기준으로는 100k에서 `MV_REFRESH` 126.178ms, `MV_REFRESH_CONCURRENTLY` 571.556ms, `SUMMARY_REFRESH` 1303.154ms였다. 1M smoke에서는 각각 1038.364ms, 2853.261ms, 11828.420ms였다. 따라서 조회 비용과 refresh 비용은 분리해서 판단해야 한다.

### 7.2 EXPLAIN ANALYZE

`EXPLAIN (ANALYZE, BUFFERS)` 결과에서 `GROUP_BY` heavy 12개월은 원본 테이블 bitmap scan과 hash aggregate를 사용했고 execution time은 6.824ms였다.

`MATERIALIZED_VIEW` heavy 12개월은 index scan으로 0.054ms, `SUMMARY` heavy 12개월은 index scan으로 0.062ms였다.

Java benchmark와 EXPLAIN 결과에서 `GROUP_BY`는 원본 row 접근과 aggregate 비용이 확인됐다. 반면 `MATERIALIZED_VIEW`와 `SUMMARY`는 이미 집계된 row를 index scan으로 조회했다. 이는 통계 조회를 원본 write model에서 직접 수행하기보다 파생 read model로 분리할 근거가 된다.

주의할 점은 `MV definition SELECT` 79.530ms가 `REFRESH MATERIALIZED VIEW` 자체를 EXPLAIN한 값이 아니라는 것이다. MV refresh 전체 비용은 Java benchmark의 `MV_REFRESH`와 함께 봐야 한다.

### 7.3 pg_stat_user_tables

`pg_stat_user_tables`에서는 Summary refresh 반복에 따른 maintenance 비용을 확인했다.

`transaction_monthly_summary`는 최초 refresh 후 `n_tup_ins` 83,510, `n_dead_tup` 0이었다. 반복 refresh 후에는 `n_tup_ins` 501,060, `n_tup_del` 417,550, `n_dead_tup` 417,550으로 증가했다. `VACUUM ANALYZE` 후 `n_dead_tup`은 0으로 정리됐다.

Summary는 조회 성능은 안정적이지만 refresh 반복에 따른 dead tuple 누적이 확인됐다. 따라서 Summary를 선택하려면 Replica 분리 여부와 별개로 refresh/maintenance 정책을 함께 설계해야 한다.

### 7.4 pg_stat_statements

`pg_stat_statements`에서 heavy user 12개월 조회 평균은 `GROUP_BY` 12.584ms, `MATERIALIZED_VIEW` 0.350ms, `SUMMARY` 0.326ms였다.

같은 시나리오의 shared hit은 `GROUP_BY` 376,451, `MATERIALIZED_VIEW` 6,514, `SUMMARY` 7,412였다.

이 결과는 `GROUP_BY`가 반복 실행에서 block 접근이 크고, `MATERIALIZED_VIEW`와 `SUMMARY` 조회는 낮은 statement 비용을 보인다는 근거다.

refresh에서는 `MV_REFRESH` 134.946ms, `MV_REFRESH_CONCURRENTLY` 533.507ms, `SUMMARY_REFRESH INSERT SELECT` 534.116ms, `SUMMARY_REFRESH DELETE` 39.527ms가 관측됐다. Summary refresh는 `DELETE`와 `INSERT SELECT`가 별도 비용으로 나타나므로 read 성능만 보고 판단하면 안 된다.

### 7.5 k6 통계 API 단독 read

k6 통계 API 단독 read에서 `GROUP_BY`는 avg 12.19ms, p95 42.05ms, p99 47.21ms였다.

`MATERIALIZED_VIEW`는 avg 7.89ms, p95 9.43ms, p99 9.99ms였고, `SUMMARY`는 avg 8.10ms, p95 9.70ms, p99 10.72ms였다.

HTTP API 경로에서도 `GROUP_BY`는 p95/p99가 높게 관측됐다. 반면 `MATERIALIZED_VIEW`와 `SUMMARY`는 안정적인 tail latency를 보였다. 이는 사용자 화면 표시용 통계 read를 파생 read model로 분리할 근거가 된다.

### 7.6 k6 송금 baseline

송금 API 단독 baseline은 avg 16.50ms, p95 21.49ms, p99 29.20ms, 요청 수 300, error 0.00%였다.

이 값은 혼합 부하를 해석하기 위한 기준선이다. 송금 API는 unique `Idempotency-Key`, HTTP 200, 응답 상태 `COMPLETED`를 성공 기준으로 측정했다.

이 baseline은 Primary/Replica 후보 판단에서 write path가 어떤 기준선으로 동작했는지 보여주는 참고값이다.

### 7.7 k6 송금 + 통계 혼합 부하

1회 혼합 부하에서 통계 API p95는 `GROUP_BY` 38.38ms, `MATERIALIZED_VIEW` 10.09ms, `SUMMARY` 9.92ms였다. 반복 혼합 측정에서도 통계 API p95는 `GROUP_BY` 39.368ms, `MATERIALIZED_VIEW` 7.954ms, `SUMMARY` 8.521ms였다.

k6 통계 API 단독 read와 혼합 부하 반복 측정에서도 `GROUP_BY`의 통계 API p95/p99가 높게 관측됐다. 반면 `MATERIALIZED_VIEW`와 `SUMMARY`는 안정적인 tail latency를 보였다.

반복 혼합 측정에서 송금 API 지표도 수집했지만, 현재 mixed script는 송금 요청과 통계 요청이 같은 실행 흐름 안에서 발생하므로 mode별 송금 latency를 직접 비교하는 근거로 사용하지 않는다.

송금 latency 영향 판단에는 송금 scenario와 통계 scenario를 분리하고, 송금 요청을 `constant-arrival-rate`로 고정한 별도 실험이 필요하다.

## 8. 현재 기준 권장 방향

송금/잔액/원장/recovery/reconciliation/idempotency/latest state 판단은 Primary에 남긴다.

통계 조회와 과거 조회는 Read Replica 후보로 본다.

통계 API 기본 read 경로는 `GROUP_BY`보다 `MATERIALIZED_VIEW` 또는 `SUMMARY` 같은 파생 read model이 적합하다.

현재 benchmark 기준으로는 `MATERIALIZED_VIEW`가 조회 안정성, refresh 비용, maintenance 부담의 균형 면에서 가장 무난한 후보로 보인다.

`SUMMARY`는 조회는 안정적이지만 refresh/maintenance 설계가 필요하다.

`GROUP_BY`는 baseline, 소량 조회, 관리자/검증용 경로로 남길 수 있다.

Read Replica는 아직 구현 대상이 아니라 후속 설계 후보다. 다음 단계에서는 Replica lag, `dataAsOf`, refresh 주기, 송금 API와의 실제 경합 지표를 함께 검토해야 한다.

## 9. 아직 단정하면 안 되는 것

아래 내용은 아직 단정하면 안 된다.

* Primary/Replica 도입 완료
* Read Replica 필수
* 운영 환경 성능 개선
* 실제 사용자 트래픽 개선
* 송금 API 영향 없음 확정
* MV가 항상 최적
* Summary가 항상 최적
* GROUP_BY가 항상 부적합
* Summary refresh 비용 해결 완료
* Fraud 정책 완화
* 통계 조회 결과로 최신 잔액 판단
* 통계 summary로 recovery 판단
* Replica에서 idempotency 판단
* Replica에서 최신 상태 판단

현재 문서의 결론은 후보 판단과 후속 설계 방향이다.

## 10. 후속 검증 과제

후속 검증 과제는 아래와 같다.

* 송금 scenario와 통계 scenario를 분리한 k6 `constant-arrival-rate` 혼합 부하 재설계
* 송금 API arrival rate 고정
* 통계 API arrival rate 고정
* DB connection active/idle 관측
* lock wait 관측
* `pg_stat_activity.wait_event` 관측
* `pg_stat_statements` 누적 변화 관측
* CPU/IO 관측
* Summary refresh와 송금 API 동시 실행 시 영향 검증
* Replica lag가 통계 freshness와 사용자 경험에 미치는 영향 검토
* MV refresh 주기와 `dataAsOf` 노출 정책 검토
* Summary refresh 주기, vacuum, analyze 정책 검토

이 과제가 끝나야 Read Replica 분리 필요성을 더 강하게 판단할 수 있다.

## 11. 포트폴리오 반영 가능 문장

아래 문장은 운영 결과가 아니라 개발 및 검증 결과 기준으로 사용할 수 있다.

* 거래 통계 조회를 GROUP_BY, Materialized View, Summary Table 세 방식으로 구현하고, Java benchmark, EXPLAIN, pg_stat_statements, k6 단독/혼합 부하를 통해 조회 비용과 혼합 부하 시 통계 API tail latency를 검증했다.
* 원본 테이블 직접 GROUP_BY는 heavy user 장기 조회와 HTTP tail latency에서 비용이 커지는 경향을 보였고, Materialized View와 Summary Table 조회는 index 기반 파생 데이터 조회로 안정적인 p95/p99를 보였다.
* Summary Table은 조회 성능은 안정적이었지만, refresh 반복 시 dead tuple과 vacuum 관리 대상이 되는 점을 pg_stat_user_tables로 확인했다.
* 통계 조회는 최신 잔액/원장/recovery 판단과 분리된 파생 조회로 설계하고, Primary/Read Replica 분리 여부를 송금 API 혼합 부하와 DB 관측 지표를 기준으로 판단하도록 했다.

송금 API 영향도 비교라고 강하게 쓰지 않는다. 송금 API 영향 없음 확정이라고 쓰지 않으며, 운영 환경에서 개선했다고 표현하지 않는다.

## 12. 피해야 할 표현

아래 표현은 피한다.

* 운영 환경에서 성능 개선
* 실제 사용자 트래픽 개선
* Primary/Replica 도입 완료
* Read Replica 필수
* 송금 API 영향 없음 확정
* MV가 항상 최적
* Summary가 항상 최적
* GROUP_BY는 항상 부적합
* Summary refresh 비용 해결 완료
* Fraud 정책 완화
* 통계 조회로 최신 잔액 판단
* 통계 summary로 recovery 판단
* Replica에서 idempotency 판단
* Replica에서 최신 상태 판단
