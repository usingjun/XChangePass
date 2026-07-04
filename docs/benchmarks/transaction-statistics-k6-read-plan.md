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

## 8. XChangePass 서버 재확인 결과

2026-07-05 재확인 결과, `localhost:8080`은 여전히 XChangePass 서버가 아니었다.

확인 명령:

```bash
curl -i http://localhost:8080
curl -i http://localhost:8080/actuator/health
lsof -i :8080
```

확인 결과:

* `localhost:8080` 응답 header의 `server` 값은 `uvicorn`이었다.
* `/`와 `/actuator/health` 모두 `HTTP 404`와 `{"detail":"Not Found"}`를 반환했다.
* `lsof -i :8080` 기준 8080 포트는 `BlueStack` 프로세스가 점유하고 있었다.
* 따라서 8080 기준 k6 결과는 XChangePass 통계 API 결과로 볼 수 없다.

Docker compose 기준으로는 Spring Boot app이 container 내부 `8080`을 host `8081`에 매핑한다.

확인한 설정:

```yaml
ports:
  - "8081:8080"
```

다만 `localhost:8081`에도 현재 응답하는 XChangePass 서버는 없었다.

따라서 다음 k6 read test는 아래 중 하나로 실행해야 한다.

* `BlueStack`이 점유 중인 8080을 건드리지 않고 XChangePass를 다른 포트로 실행한 뒤 `BASE_URL`을 해당 포트로 지정
* Docker compose로 XChangePass app을 실행한 뒤 `BASE_URL=http://localhost:8081` 사용
* 기존 실행 중인 XChangePass 서버가 있다면 그 실제 host/port를 `BASE_URL`로 지정

프로세스는 임의로 종료하지 않았다.

BlueStack 종료 후 다시 확인한 결과, `localhost:8080`은 더 이상 다른 프로세스가 점유하지 않는 상태가 됐다.

재확인 명령:

```bash
curl -i http://localhost:8080
curl -i http://localhost:8080/actuator/health
lsof -i :8080
```

재확인 결과:

* `curl`은 connection 실패로 종료됐다.
* `lsof -i :8080`은 점유 프로세스를 반환하지 않았다.
* 즉 BlueStack 충돌은 해소됐지만, XChangePass Spring Boot 서버도 아직 `localhost:8080`에서 실행 중이 아니다.
* `localhost:8081` 역시 응답하는 서버가 없었다.

따라서 IntelliJ에서 XChangePass를 실행한 뒤 `localhost:8080`에 Spring Boot 서버가 실제로 떠 있는지 먼저 확인해야 한다.

확인 후보:

```bash
curl -i http://localhost:8080/actuator/health
curl -i "http://localhost:8080/api/v1/transactions/statistics/monthly?userId=1&fromMonth=2026-01&toMonth=2026-12&mode=GROUP_BY"
```

첫 번째 명령에서 Spring Boot 응답이 오고, 두 번째 명령에서 인증 실패 또는 정상 API 응답이 와야 k6 재실행 단계로 넘어갈 수 있다.

IntelliJ로 XChangePass를 실행한 뒤 다시 확인한 결과, `localhost:8080`은 XChangePass Spring Boot 서버로 확인됐다.

재확인 결과:

* `lsof -i :8080`에서 `java` 프로세스가 `*:http-alt`를 listen하고 있었다.
* `curl -i http://localhost:8080`은 XChangePass Spring Security의 `401` JSON을 반환했다.
* `curl -i http://localhost:8080/actuator/health`도 동일하게 인증 실패 응답을 반환했다.
* 이전처럼 `uvicorn` 또는 `BlueStack` 응답은 나오지 않았다.

응답 예시:

```json
{"errorCode":"E0002","errorMsg":"인증되지 않은 사용자입니다."}
```

따라서 서버 포트 문제는 해결됐지만, k6 read test를 진행하려면 인증 가능한 사용자가 필요하다.

이번 재시도에서 인증 준비도 확인했다.

* `/api/v1/user/signup`으로 임시 사용자를 생성하려 했으나 `ENC006` 오류로 실패했다.
* 오류 메시지는 `AES 키 암호화에 실패했습니다.`였다.
* 기존 k6 스크립트에서 사용하던 `testuser_1@gmail.com` 로그인도 시도했으나 `U001` 오류로 실패했다.
* 오류 메시지는 `존재 하지 않는 회원입니다.`였다.

따라서 이번 실행에서는 통계 API 인증을 통과하지 못했고, `GROUP_BY`, `MATERIALIZED_VIEW`, `SUMMARY` mode별 유효 k6 결과를 수집하지 않았다.

다음 재실행 전 추가로 필요한 준비:

1. XChangePass 서버가 사용하는 DB에 로그인 가능한 테스트 사용자 준비
2. accessToken cookie 또는 JWT 확보
3. 통계 조회 대상 userId 확정
4. 거래 통계 데이터 준비
5. MV/Summary refresh 완료

인증 토큰이 준비되면 아래처럼 실행한다.

```bash
BASE_URL=http://localhost:8080 \
ACCESS_TOKEN_COOKIE={accessTokenCookieValue} \
USER_IDS={targetUserIds} \
K6_VUS=10 \
K6_DURATION=30s \
tools/perf/run-transaction-statistics-k6-read.sh
```

## 9. API 및 데이터 준비 확인

통계 API mapping은 현재 k6 스크립트와 일치한다.

확인한 endpoint:

```http
GET /api/v1/transactions/statistics/monthly?userId={userId}&fromMonth={fromMonth}&toMonth={toMonth}&mode={mode}
```

확인한 mode enum:

* `GROUP_BY`
* `MATERIALIZED_VIEW`
* `SUMMARY`

현재 `SecurityConfig` 기준으로 이 endpoint는 `permitAll` 대상이 아니므로 인증이 필요하다. k6 스크립트는 `AUTH_TOKEN`, `ACCESS_TOKEN`, `JWT_TOKEN`, `ACCESS_TOKEN_COOKIE`, `LOGIN_EMAIL`, `LOGIN_PASSWORD` 기반 인증을 지원한다.

데이터 준비 측면에서는 100,000건 benchmark 데이터 생성기가 `src/test` 하위 테스트 전용 코드에 있다. 이 factory는 JUnit/Testcontainers benchmark 용도로 사용되고 있으며, 현재 확인한 범위에서는 실행 중인 HTTP 서버 DB에 같은 데이터를 직접 주입하는 운영/로컬 실행 스크립트는 없다.

따라서 유효한 100k HTTP k6 결과를 만들려면 아래 준비가 먼저 필요하다.

1. XChangePass 서버가 바라보는 PostgreSQL에 통계용 거래 데이터 준비
2. heavy user와 regular user id 확인
3. `mv_transaction_monthly_statistics` refresh 완료
4. `transaction_monthly_summary` refresh 완료
5. 통계 API 접근 가능한 인증 토큰 준비
6. 실제 XChangePass 서버의 `BASE_URL` 확정

## 10. 기존 결과와 연결

각 관측의 역할은 아래처럼 분리한다.

* Java benchmark: repository/service 호출 기준 latency
* `EXPLAIN (ANALYZE, BUFFERS)`: PostgreSQL 단일 SQL 실행 계획과 buffer 사용량
* `pg_stat_user_tables`: Summary refresh 이후 table maintenance 관측
* `pg_stat_statements`: 반복 실행된 SQL statement 단위 누적 통계
* k6 read test: HTTP, 인증, controller, JSON 직렬화까지 포함한 API 경로 기준 latency

k6 read test 결과가 준비되면 `GROUP_BY`, `MATERIALIZED_VIEW`, `SUMMARY`의 HTTP 기준 avg, p95, p99, throughput, error rate를 비교한다.

## 11. Primary/Replica 판단

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

## 12. 다음 실행 절차

Docker compose 기반으로 실행한다면 후보 절차는 아래와 같다.

```bash
docker compose -f infra/docker-compose.yml up -d postgres redis app
```

그 다음 XChangePass 서버 응답을 확인한다.

```bash
curl -i http://localhost:8081/actuator/health
```

통계 API가 인증을 통과하고 데이터가 준비된 뒤 아래처럼 실행한다.

```bash
BASE_URL=http://localhost:8081 \
AUTH_TOKEN={accessToken} \
USER_IDS={heavyUserId},{regularUserId} \
K6_VUS=10 \
K6_DURATION=30s \
tools/perf/run-transaction-statistics-k6-read.sh
```

raw 결과는 `build/perf/k6/`에 생성되며 커밋하지 않는다.

## 13. 후속 작업

1. 테스트용 서버와 인증 토큰을 준비한다.
2. `GROUP_BY`, `MATERIALIZED_VIEW`, `SUMMARY` mode별 k6 read test를 실행한다.
3. mode별 avg, p95, p99, throughput, error rate를 문서화한다.
4. 송금 API 단독 k6 baseline을 준비한다.
5. 송금 API + 통계 API 혼합 부하 테스트를 준비한다.
6. `pg_stat_activity`의 `wait_event`, active connection, query duration을 함께 관측한다.
