# 송금 API + 거래 통계 API k6 혼합 부하 반복 측정 계획

## 1. 목적

송금 API + 거래 통계 API 혼합 부하를 동일 조건으로 반복 실행해 p95/p99 경향이 단일 실행의 우연인지 확인하려고 했다.

이번 시도에서는 반복 측정 wrapper와 실행 절차를 준비했지만, 송금 smoke가 이상거래 야간 정책에 의해 차단되어 유효한 반복 측정 결과는 수집하지 않았다.

## 2. 준비한 반복 실행 구조

반복 실행 wrapper:

```bash
tools/perf/run-transaction-transfer-statistics-k6-mixed-repeat.sh
```

역할:

- 기존 `tools/perf/run-transaction-transfer-statistics-k6-mixed.sh` 반복 호출
- 기본 `K6_REPEAT=3`
- 각 repeat마다 `GROUP_BY`, `MATERIALIZED_VIEW`, `SUMMARY` 순서로 실행
- 각 repeat 결과를 `build/perf/k6/mixed-repeat/run-{n}/` 하위에 복사
- 반복 결과 CSV 생성

결과 CSV:

```text
build/perf/k6/mixed-repeat/transaction-transfer-statistics-k6-mixed-repeat-summary.csv
```

CSV 컬럼:

- `run`
- `mode`
- `transfer_avg_ms`
- `transfer_p90_ms`
- `transfer_p95_ms`
- `transfer_p99_ms`
- `transfer_max_ms`
- `transfer_requests`
- `transfer_error_rate`
- `statistics_avg_ms`
- `statistics_p90_ms`
- `statistics_p95_ms`
- `statistics_p99_ms`
- `statistics_max_ms`
- `statistics_requests`
- `statistics_error_rate`

## 3. 시도한 절차

### 서버 확인

`http://localhost:8080/actuator/health` 호출 결과, XChangePass Spring Security 응답을 확인했다.

```text
HTTP/1.1 401
{"errorCode":"E0002","errorMsg":"인증되지 않은 사용자입니다."}
```

서버는 실행 중이었다.

### 통계 auth env 갱신

기존 통계 seed 데이터는 유지하고, 만료된 토큰만 갱신했다.

- 기존 `stats-k6-*` 사용자 조회
- `/login` 호출
- `build/perf/k6/transaction-statistics-auth.env` 갱신

토큰 값은 출력하거나 문서화하지 않았다.

### 송금 seed 재실행

송금 반복 측정 중 fraud frequency limit 누적을 줄이기 위해 fresh sender/receiver 400쌍을 준비하려 했다.

처음에는 기존 benchmark seed의 전화번호 생성 폭이 좁아 이전 seed 데이터와 unique 제약 충돌이 발생했다.

```text
ERROR: duplicate key value violates unique constraint "unique_user_phonenumber"
```

원인은 `TransactionTransferK6Seed`의 전화번호가 `010-7100-4001`처럼 반복 실행 간 충돌하기 쉬운 구조였기 때문이다. 운영 로직이 아니라 benchmark seed 식별자 생성 문제이므로, 전화번호 생성 범위만 넓히는 최소 수정이 필요하다.

수정 후 sender/receiver 400쌍 생성은 성공했다.

### 통계 API smoke

아래 세 모드는 모두 HTTP 200을 반환했다.

- `GROUP_BY`
- `MATERIALIZED_VIEW`
- `SUMMARY`

### 송금 API smoke

송금 API smoke는 실패했다.

```text
HTTP/1.1 403
{"code":"T006","message":"이상 거래가 감지되어 거래가 차단되었습니다."}
```

## 4. 실패 원인

실패 원인은 데이터 부족, 인증 실패, 멱등성 충돌, 잔액 부족이 아니다.

현재 실행 시간이 KST 02:57 부근이었고, XChangePass의 이상거래 탐지 정책은 기본값 기준 KST 02:30~03:30 야간 거래를 위험 사유로 판단한다.

관련 정책:

- `FraudPolicyProperties.nightStart = 02:30`
- `FraudPolicyProperties.nightEnd = 03:30`
- `FraudRuleEvaluator`가 현재 시간이 야간 구간이면 fraud command에 night flag를 전달
- 결과가 clear가 아니면 `FraudDetectionService`가 `SUSPICIOUS_TRANSACTION`으로 송금을 차단

따라서 이 시간대에는 송금 API 단독 smoke 자체가 차단되며, 혼합 부하 반복 측정 결과도 유효하지 않다.

## 5. 다음 실행 명령

KST 03:30 이후 또는 야간 정책 구간 밖에서 아래 순서로 재실행한다.

```bash
mkdir -p build/perf/k6

# 통계 auth env 갱신이 필요한 경우 먼저 로그인 또는 기존 통계 seed를 통해 auth env를 갱신한다.

TRANSACTION_TRANSFER_K6_PAIRS=400 \
  tools/perf/run-transaction-transfer-k6-seed.sh

BASE_URL=http://localhost:8080 \
TRANSFER_AUTH_ENV=build/perf/k6/transaction-transfer-auth.env \
STATS_AUTH_ENV=build/perf/k6/transaction-statistics-auth.env \
K6_VUS=10 \
K6_DURATION=30s \
K6_SLEEP=1 \
K6_REPEAT=3 \
tools/perf/run-transaction-transfer-statistics-k6-mixed-repeat.sh
```

IPv4 8080 포트가 다른 프로세스에 잡히면 `BASE_URL=http://[::1]:8080`을 사용한다.

## 6. 필요한 env

송금 auth env:

```text
build/perf/k6/transaction-transfer-auth.env
```

필수 값:

- `AUTH_TOKENS`
- `SENDER_USER_IDS`
- `RECEIVER_USER_IDS`
- `RECEIVER_NAMES`
- `RECEIVER_PHONE_NUMBERS`
- `TRANSFER_AMOUNT`

통계 auth env:

```text
build/perf/k6/transaction-statistics-auth.env
```

필수 값:

- `ACCESS_TOKEN_COOKIE` 또는 `AUTH_TOKEN`
- `USER_IDS` 또는 `TEST_USER_ID`
- `FROM_MONTH`
- `TO_MONTH`

## 7. 아직 Primary/Replica 판단 문서로 갈 수 없는 이유

이번 단계의 목표는 혼합 부하 반복 측정을 통해 단일 실행 결과의 우연성을 줄이는 것이다.

하지만 송금 API smoke가 이상거래 야간 정책에 의해 차단되어 반복 측정 결과를 확보하지 못했다. 따라서 이번 결과만으로는 기존 단일 혼합 부하 결과가 재현된다고 판단할 수 없다.

Primary/Replica 후보 판단 문서는 반복 측정이 유효하게 완료된 뒤 작성해야 한다.

## 8. 변경하지 않은 것

- 송금/잔액/원장/recovery/reconciliation 비즈니스 로직 수정 없음
- 통계 SQL 수정 없음
- 통계 서비스 수정 없음
- Primary/Replica 구현 없음
- PostgreSQL 운영 설정 변경 없음
- fraud 정책 완화 없음
- raw k6 결과 커밋 없음
- auth env/token 커밋 없음
