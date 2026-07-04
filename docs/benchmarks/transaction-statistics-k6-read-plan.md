# 거래 통계 k6 Read 부하 테스트 계획

## 1. 목적

이 문서는 XChangePass 거래 통계 API를 실제 HTTP 경로로 호출해 `GROUP_BY`, `MATERIALIZED_VIEW`, `SUMMARY` mode별 latency, p95, p99, throughput, error rate를 비교하기 위한 k6 read 부하 테스트 준비 내용을 정리한다.

이번 단계는 통계 API 단독 부하 테스트다. 송금 API와 통계 API를 함께 실행하는 혼합 부하는 아직 수행하지 않는다.

이 결과는 Java benchmark, `EXPLAIN (ANALYZE, BUFFERS)`, `pg_stat_user_tables`, `pg_stat_statements` 관측을 HTTP API 관점에서 보완하기 위한 것이다. Primary/Replica 판단은 아직 보류한다.

## 2. 실행 대상

대상 API:

```http
GET /api/v1/transactions/statistics/monthly?userId={userId}&fromMonth={fromMonth}&toMonth={toMonth}&mode={mode}
```

mode:

* `GROUP_BY`
* `MATERIALIZED_VIEW`
* `SUMMARY`

기본 월 범위:

* `FROM_MONTH=2026-01`
* `TO_MONTH=2026-12`

기본 사용자:

* `USER_IDS=1`

여러 사용자를 섞어서 호출하려면 아래처럼 쉼표로 전달한다.

```bash
USER_IDS=1,2,3,4,5
```

## 3. 인증 필요 여부

현재 `SecurityConfig` 기준으로 통계 API는 `permitAll` 목록에 없다. 따라서 실제 HTTP 호출에서는 인증이 필요하다.

확인한 기준:

* `/login`, `/api/v1/user/signup`, `/token-refresh` 등은 인증 없이 허용된다.
* `/api/v1/transactions/statistics/monthly`는 별도 허용 경로가 아니므로 `anyRequest().authenticated()`에 포함된다.
* `TransactionStatisticsController`는 `userId` query parameter를 받지만, HTTP 접근 자체는 Spring Security 인증 필터를 통과해야 한다.

k6 스크립트는 아래 인증 방식을 지원한다.

* `AUTH_TOKEN`, `ACCESS_TOKEN`, `JWT_TOKEN` 중 하나를 전달하면 `Authorization: Bearer ...` header 사용
* `ACCESS_TOKEN_COOKIE`를 전달하면 `Cookie: accessToken=...` 사용
* `LOGIN_EMAIL`, `LOGIN_PASSWORD`를 전달하면 setup 단계에서 `/login` 호출 후 token 사용

운영/보안 설정은 변경하지 않는다.

## 4. 추가 파일

추가한 k6 스크립트:

```text
k6-scripts/transaction-statistics-read.js
```

역할:

* `BASE_URL` 환경변수 지원
* `MODE` 환경변수 지원
* `FROM_MONTH`, `TO_MONTH` 환경변수 지원
* `USER_IDS` 환경변수 지원
* `K6_VUS`, `K6_DURATION`, `K6_SLEEP` 환경변수 지원
* 응답 status `200` check
* `statistics_read_error_rate` custom metric 기록
* `summaryTrendStats`에 `avg`, `min`, `med`, `p90`, `p95`, `p99`, `max` 포함

추가한 실행 스크립트:

```text
tools/perf/run-transaction-statistics-k6-read.sh
```

역할:

* `GROUP_BY`, `MATERIALIZED_VIEW`, `SUMMARY`를 순서대로 실행
* 결과 raw 파일을 `build/perf/k6/` 하위에 저장
* k6 미설치 시 안내 메시지 출력
* 서버가 먼저 실행되어 있어야 한다는 점 안내

## 5. 실행 방법

서버가 먼저 실행되어 있어야 한다.

직접 실행 예시:

```bash
BASE_URL=http://localhost:8080 \
AUTH_TOKEN={accessToken} \
USER_IDS=1,2,3 \
MODE=GROUP_BY \
k6 run k6-scripts/transaction-statistics-read.js
```

로그인 계정으로 실행하는 예시:

```bash
BASE_URL=http://localhost:8080 \
LOGIN_EMAIL=testuser@example.com \
LOGIN_PASSWORD=Test1234! \
USER_IDS=1,2,3 \
MODE=SUMMARY \
k6 run k6-scripts/transaction-statistics-read.js
```

세 mode를 순서대로 실행하는 예시:

```bash
BASE_URL=http://localhost:8080 \
AUTH_TOKEN={accessToken} \
USER_IDS=1,2,3 \
K6_VUS=10 \
K6_DURATION=30s \
tools/perf/run-transaction-statistics-k6-read.sh
```

결과 raw 파일:

```text
build/perf/k6/transaction-statistics-read-group-by.json
build/perf/k6/transaction-statistics-read-group-by.txt
build/perf/k6/transaction-statistics-read-materialized-view.json
build/perf/k6/transaction-statistics-read-materialized-view.txt
build/perf/k6/transaction-statistics-read-summary.json
build/perf/k6/transaction-statistics-read-summary.txt
```

위 raw 결과 파일은 커밋하지 않는다.

## 6. 실행 제한 및 준비사항

이번 k6 read 테스트를 의미 있게 실행하려면 아래 조건이 필요하다.

* 애플리케이션 서버가 `BASE_URL`에서 실행 중이어야 한다.
* 통계 API 접근이 가능한 JWT 또는 accessToken cookie가 필요하다.
* `USER_IDS`에 지정한 사용자와 거래 통계 데이터가 DB에 준비되어 있어야 한다.
* `MATERIALIZED_VIEW`와 `SUMMARY` mode를 비교하려면 Materialized View refresh와 Summary refresh가 선행되어 있어야 한다.
* `k6` CLI가 로컬에 설치되어 있어야 한다.

서버 미실행, 인증 토큰 없음, 데이터 미준비 상태에서는 `401`, `404`, 빈 결과, connection refused 등이 발생할 수 있다.

## 7. 이번 실행 확인 결과

이번 작업에서는 k6 CLI 설치 여부와 스크립트 실행 가능 여부를 확인했다.

확인 결과:

* k6 CLI는 설치되어 있었다.
* `tools/perf/run-transaction-statistics-k6-read.sh`는 `GROUP_BY`, `MATERIALIZED_VIEW`, `SUMMARY` mode를 순서대로 실행하고 raw 결과 파일을 생성했다.
* 다만 현재 `http://localhost:8080`은 XChangePass 애플리케이션이 아니라 `uvicorn` 서버가 응답하고 있었다.
* 통계 API endpoint는 `404 Not Found`로 응답했으므로, 유효한 mode별 latency 비교 결과는 수집하지 못했다.

확인 명령:

```bash
BASE_URL=http://localhost:8080 \
K6_VUS=1 \
K6_DURATION=3s \
tools/perf/run-transaction-statistics-k6-read.sh
```

생성된 raw 결과 파일:

```text
build/perf/k6/transaction-statistics-read-group-by.json
build/perf/k6/transaction-statistics-read-group-by.txt
build/perf/k6/transaction-statistics-read-materialized-view.json
build/perf/k6/transaction-statistics-read-materialized-view.txt
build/perf/k6/transaction-statistics-read-summary.json
build/perf/k6/transaction-statistics-read-summary.txt
```

위 raw 결과 파일은 실행 실패 원인 확인용이며 커밋하지 않는다.

실제 mode별 성능 비교를 위해서는 XChangePass 서버를 `BASE_URL`에서 실행하고, 인증 토큰과 통계 데이터 및 refresh 상태를 준비한 뒤 다시 실행해야 한다.

## 8. 기존 결과와 연결

각 관측의 역할은 아래처럼 분리한다.

* Java benchmark: repository/service 호출 기준 latency
* `EXPLAIN (ANALYZE, BUFFERS)`: PostgreSQL 단일 SQL 실행 계획과 buffer 사용량
* `pg_stat_user_tables`: Summary refresh 이후 table maintenance 관측
* `pg_stat_statements`: 반복 실행된 SQL statement 단위 누적 통계
* k6 read test: HTTP, 인증, controller, JSON 직렬화까지 포함한 API 경로 기준 latency

k6 read test 결과가 준비되면 `GROUP_BY`, `MATERIALIZED_VIEW`, `SUMMARY`의 HTTP 기준 avg, p95, p99, throughput, error rate를 비교한다.

## 9. Primary/Replica 판단

이번 k6 read test만으로 Primary/Replica를 결정하지 않는다.

Replica 후보가 될 수 있는 것은 지연을 허용할 수 있는 통계 조회와 과거 조회다.

Primary 유지 대상은 아래 흐름이다.

* 송금 처리
* 잔액 변경
* 원장 저장
* 최신 잔액 조회
* 최신 원장/상태 조회
* Recovery 판단
* Reconciliation 판단
* Idempotency 판단

Primary/Replica 필요성은 통계 API 단독 부하 이후, 송금 API + 통계 API 혼합 부하에서 송금 API p95/p99 영향을 확인한 뒤 판단한다.

## 10. 후속 작업

1. 테스트용 서버와 인증 토큰을 준비한다.
2. `GROUP_BY`, `MATERIALIZED_VIEW`, `SUMMARY` mode별 k6 read test를 실행한다.
3. mode별 avg, p95, p99, throughput, error rate를 문서화한다.
4. 송금 API 단독 k6 baseline을 준비한다.
5. 송금 API + 통계 API 혼합 부하 테스트를 준비한다.
6. `pg_stat_activity`의 `wait_event`, active connection, query duration을 함께 관측한다.
