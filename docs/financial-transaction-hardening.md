# XChangePass Financial Transaction Hardening Specification

## 1. Purpose

XChangePass를 금융권 포트폴리오의 첫 프로젝트로 제시할 수 있도록 거래 정합성, 재시도 안전성,
조회 성능, 이상 거래 차단, 운영 추적성을 실제 구현과 자동화 테스트로 강화한다.

포트폴리오에는 이 문서에 정의된 검증을 통과한 사실만 사용한다.

## 2. Delivery Process

각 단계는 다음 순서로 진행한다.

1. 상세 명세를 이 문서에 작성한다.
2. 명세를 기준으로 현재 코드와 테스트의 충족 여부를 확인한다.
3. 구현 범위, API·DB 변경, 예외 정책, 테스트 목록을 사용자에게 제시하고 승인을 받는다.
4. 승인된 범위만 구현한다.
5. 자동화 테스트와 `git diff --check`를 실행한다.
6. 검증 결과와 실제 변경 파일을 이 문서에 기록한다.

명세 승인 전에는 운영 코드, 테스트 코드, 설정 파일을 수정하지 않는다. 사용자가 요청하기 전에는
커밋하지 않는다.

## 3. Shared Principles

- PostgreSQL의 잔액과 거래 원장을 금융 거래의 Source of Truth로 사용한다.
- Redis는 이상 거래 사전 판단에 사용하며 거래 성공 여부의 원장으로 사용하지 않는다.
- Resilience4j는 Redis 장애를 격리하지만 금융 정합성을 직접 보장하지 않는다.
- 금융 정합성은 PostgreSQL 트랜잭션, Advisory Lock, 거래 원장 저장, Fail-Closed 정책이 담당한다.
- 멱등성, 상태 전이, 원장 기록은 서버 인스턴스가 여러 대여도 동일하게 동작하도록 DB 제약을 기준으로 한다.
- CircuitBreaker 상태는 서버 인스턴스별로 독립적이라는 한계를 명시한다.
- 기존 사용자 변경사항을 보존하고 각 단계와 관계없는 파일은 수정하지 않는다.

## 4. Execution Order and Approval

| Step | Topic | Specification | Current-state check | Approval | Implementation | Verification |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 지갑 거래 동시성 제어 | Approved | Checked | Approved 2026-06-19 | Completed | Completed 2026-06-20 |
| 2 | 거래 요청 멱등성 | Approved | Checked | Approved 2026-06-20 | Completed | Completed 2026-06-20 |
| 3 | 거래내역 통합조회 최적화 | Approved | Checked | Approved 2026-06-20 | Completed | Completed 2026-06-20 |
| 4 | Redis 이상 거래 장애 대응 | Approved | Checked | Approved 2026-06-20 | Completed | Completed 2026-06-20 |
| 5 | 거래 상태 및 실패 추적 | Approved | Rechecked 2026-06-21 | Approved 2026-06-21 | Completed | Completed (targeted); full suite has existing KMS failures |

## 5. Step 1 - Transaction-scoped Wallet Concurrency Control

### 5.1 Failure scenarios

- 여러 요청이 동일 잔액을 동시에 읽고 갱신해 갱신 유실이 발생한다.
- 양방향 송금이 서로 다른 순서로 두 지갑을 잠가 데드락이 발생한다.
- 잔액 변경만 커밋되고 거래 원장 저장은 실패한다.
- 애플리케이션이 DB 커밋 전에 락을 해제해 다른 요청이 진입한다.

### 5.2 Required design

- PostgreSQL `pg_advisory_xact_lock`으로 지갑 ID 단위의 트랜잭션 락을 획득한다.
- 송금 대상 지갑 두 개는 지갑 ID 오름차순으로 락을 획득한다.
- 잔액의 존재 여부와 금액은 락 획득 후 다시 확인한다.
- 잔액 차감·증가와 RDB 거래 원장 저장은 하나의 Spring 트랜잭션에 참여한다.
- 락은 커밋 또는 롤백 시 PostgreSQL이 자동 해제한다.
- 지갑 단위 락은 한 지갑의 서로 다른 통화 거래도 직렬화한다는 성능 특성을 문서화한다.
- 다른 용도의 Advisory Lock과 키 공간이 충돌하지 않는지 확인한다.

### 5.3 Why Advisory Lock instead of a row lock

`pg_advisory_xact_lock`은 애플리케이션 메모리 락이 아니라 PostgreSQL이 제공하는 트랜잭션 범위 DB 락이다.
비교 대상은 `SELECT ... FOR UPDATE` 같은 행 단위 비관적 락이다.

Advisory Lock을 선택하는 이유:

- 신규 통화 충전 시 아직 `wallet_balance` 행이 존재하지 않아 해당 잔액 행을 잠글 수 없다.
- 송금은 출금 통화와 입금 통화가 다를 수 있지만 동시성 제어 대상은 개별 잔액 행보다 지갑이라는
  논리 자원이다.
- 두 지갑 ID를 정렬해 동일한 순서로 잠그는 규칙을 서비스 경계에서 명시할 수 있다.
- 트랜잭션 범위 Advisory Lock은 별도 해제 코드 없이 커밋·롤백까지 유지된다.

Alternatives and trade-offs:

- 항상 존재하는 `wallet` 행을 `FOR UPDATE`로 잠그는 방식도 동일한 직렬화 범위를 만들 수 있다.
- 행 락은 DB 데이터와 락 대상의 관계가 명시적이고 누락 가능성이 낮다는 장점이 있다.
- Advisory Lock은 잔액 생성 전에도 잠글 수 있지만 모든 잔액 변경 경로가 같은 락 규칙에 참여해야 한다.
- 지갑 전체를 잠그므로 서로 다른 통화 거래도 대기하며, 통화별 행 락보다 경합 범위가 넓다.
- 따라서 Advisory Lock이 항상 우월해서가 아니라 현재의 잔액 생성과 지갑 단위 직렬화 요구에 맞아
  선택한다. 락 누락과 키 충돌은 테스트와 키 대역 규칙으로 보완한다.

### 5.4 Required verification

- 동일 송신자·수신자의 100건 동시 송금에서 잔액 오차가 없어야 한다.
- A에서 B, B에서 A로 동시에 송금해도 데드락 없이 허용 가능한 결과만 남아야 한다.
- 송금과 전액 출금 경합에서 초과 차감이 없어야 한다.
- 거래 원장 저장 실패 시 잔액 변경과 신규 통화 잔액 생성이 롤백돼야 한다.
- 입력 순서와 무관하게 두 지갑 락을 오름차순으로 획득해야 한다.

### 5.5 Completion criteria

- 위 설계가 코드에 반영되고 PostgreSQL 기반 통합 테스트가 통과한다.
- 락 범위, 락 해제 시점, 락 순서, 트랜잭션 보장 범위를 문서에서 설명할 수 있다.

## 6. Step 2 - Transfer Request Idempotency

### 6.1 API contract

- 클라이언트는 송금 요청마다 UUID 형식의 `Idempotency-Key` 헤더를 전송한다.
- 서버는 인증된 `userId`와 `Idempotency-Key` 조합으로 요청을 식별한다.
- 수신자, 금액, 출금 통화, 입금 통화, 거래 유형을 정규화한 SHA-256 요청 해시를 저장한다.
- 최초 성공 응답은 `transferId`와 최종 상태를 포함하고 중복 완료 요청도 같은 결과를 반환한다.

### 6.2 Persistence and concurrency

- 거래 요청 테이블에 `user_id`, `idempotency_key`, `request_hash`, `status`, 결과 식별자를 저장한다.
- `(user_id, idempotency_key)`에 DB 유니크 제약을 둔다.
- 선조회는 빠른 응답에 사용할 수 있지만 DB 유니크 제약 위반을 최종 중복 판정으로 사용한다.
- 요청 예약은 짧은 별도 트랜잭션에서 커밋해 동시 요청 중 하나만 처리 권한을 갖게 한다.
- 5단계 상태 관리가 같은 거래 요청 레코드를 확장하도록 설계해 임시 테이블을 만들지 않는다.

### 6.3 Duplicate policy

| Existing state | Same request hash | Different request hash |
| --- | --- | --- |
| `COMPLETED` | 기존 결과 반환 | `409 IDEMPOTENCY_KEY_REUSED` |
| `REQUESTED`, `VALIDATING`, `PROCESSING` | `409 TRANSACTION_IN_PROGRESS` | `409 IDEMPOTENCY_KEY_REUSED` |
| `FAILED` | 저장된 실패 결과 반환, 자동 재실행 금지 | `409 IDEMPOTENCY_KEY_REUSED` |

의도적인 새 송금은 새 키로 요청한다. 이상 거래 탐지의 내부 Redis 기록 ID는 클라이언트 멱등성 키를
대체하지 않는다.

### 6.4 Required verification

- 동일 키 순차 재전송에서 잔액 변경과 원장 저장이 한 번만 발생한다.
- 동일 키 30건 동시 요청에서도 실제 송금은 한 번만 실행된다.
- 최초 응답 유실 후 재시도하면 기존 성공 결과를 반환한다.
- 같은 사용자가 같은 키로 다른 요청 본문을 보내면 거부한다.
- 서로 다른 사용자는 같은 UUID 값을 독립적으로 사용할 수 있다.
- 중복 요청은 Redis 이상 거래 횟수에도 중복 집계되지 않는다.

## 7. Step 3 - Transaction History Top-N and Keyset Optimization

### 7.1 Query design

- 카드, 지갑, 환전 테이블별로 사용자·거래시각·거래 ID 복합 인덱스를 사용한다.
- 각 소스는 정렬과 조건을 DB에서 적용하고 최신 `N+1`건만 DTO Projection으로 조회한다.
- 애플리케이션은 `transactionTime`, `sourceType`, `transactionId`의 고정된 순서로 병합한다.
- 커서는 내부 구현을 노출하지 않는 불투명 토큰으로 제공한다.
- 동일한 거래시각에서도 페이지 간 누락과 중복이 없도록 소스별 위치를 커서에 보존한다.
- 거래 유형, 지갑 방향, 가맹점 조건으로 조회할 필요가 없는 소스는 호출하지 않는다.

### 7.2 Benchmark contract

- 고정 시드로 생성한 카드·지갑·환전 합계 1,000,000건을 사용한다.
- 페이지 크기 50, 워밍업 50회, 측정 300회를 기본 조건으로 기록한다.
- 기존 전체 `UNION` 병합·정렬과 테이블별 Top-N 제한 후 병합을 비교한다.
- 평균, p95, `EXPLAIN ANALYZE` 결과와 데이터 분포를 함께 남긴다.
- 인덱스 후보는 실제 측정 결과가 개선된 경우에만 채택한다.

### 7.3 Required verification

- 첫 페이지, 중간 페이지, 마지막 페이지, 빈 결과를 검증한다.
- 동일 시각·서로 다른 소스의 거래가 연속 페이지에서 누락되거나 중복되지 않아야 한다.
- 금액, 통화, 거래 유형, 가맹점, 지갑 방향 필터 조합을 검증한다.
- 동일 데이터셋과 조건에서 기준 쿼리와 최적화 쿼리를 재측정한다.

## 8. Step 4 - Resilience4j Fraud Detection Failure Handling

### 8.1 Scope

- 자체 구현 `FraudRedisCircuitBreaker`와 관련 상태 관리 코드를 제거한다.
- Redis Lua Script와 누적 금액, 거래 빈도, 동일 금액 반복, 심야 거래 규칙은 변경하지 않는다.
- Resilience4j CircuitBreaker와 Retry, Actuator/Micrometer 관측을 추가한다.
- 송금과 카드 결제의 기존 Fail-Closed 정책을 유지한다.

### 8.2 Invocation boundary and operator order

Redis Lua 실행을 별도 Bean으로 분리하거나 함수형 API를 사용해 Spring AOP self-invocation을 피한다.
한 거래의 모든 Redis 재시도를 CircuitBreaker가 하나의 최종 결과로 평가하도록 CircuitBreaker를
바깥쪽, Retry를 안쪽에 둔다.

```text
CircuitBreaker(
    Retry(
        Redis Lua execution
    )
)
```

함수형 API를 사용할 경우 의미는 다음과 같다.

```java
Supplier<Result> retried = Retry.decorateSupplier(retry, redisLuaExecutor::execute);
Supplier<Result> guarded = CircuitBreaker.decorateSupplier(circuitBreaker, retried);
return guarded.get();
```

- Retry가 첫 실패 후 성공하면 CircuitBreaker에는 성공 한 건으로 기록한다.
- Retry가 모두 실패하면 CircuitBreaker에는 실패 한 건으로 기록한다.
- CircuitBreaker가 `OPEN`이면 Retry와 Redis를 호출하지 않고 즉시 차단한다.
- `HALF_OPEN`에서는 설정된 수의 시험 호출만 허용한다.

### 8.3 Exception classification

- Redis 연결 오류와 명령 타임아웃만 Retry 및 CircuitBreaker 실패로 기록한다.
- Lua 결과 해석 후 발생하는 `SUSPICIOUS_TRANSACTION`은 보호 구간 밖의 업무 예외로 처리한다.
- 업무 예외는 CircuitBreaker 실패율에 포함하지 않는다.
- `CallNotPermittedException`과 Redis 재시도 최종 실패는 `FRAUD_DETECTION_UNAVAILABLE`로 변환한다.
- 예상하지 못한 Redis/Lua 기술 오류도 거래는 Fail-Closed하되 연결·타임아웃 실패율과 구분해 기록한다.

### 8.4 Configuration

다음 값을 애플리케이션 설정으로 분리한다.

- Sliding Window 유형과 크기
- 최소 호출 수
- 실패율 임계치
- 느린 호출 시간과 느린 호출률 임계치
- `OPEN` 유지 시간
- `HALF_OPEN` 허용 요청 수
- `HALF_OPEN` 자동 전환 여부
- Retry 최대 시도 횟수와 대기 시간
- Retry 대상 예외

### 8.5 Metrics and events

- Actuator에서 CircuitBreaker 상태와 이벤트를 확인할 수 있게 한다.
- Micrometer에서 성공, 실패, 느린 호출, 차단된 호출 수를 확인할 수 있게 한다.
- `CLOSED -> OPEN -> HALF_OPEN -> CLOSED/OPEN` 전환을 이벤트와 메트릭으로 남긴다.
- 기존 Prometheus 수집 구성과 연결되는지 확인한다.
- CircuitBreaker 상태가 서버 인스턴스별이라는 사실을 운영 문서에 명시한다.

### 8.6 Required verification

- Redis 일시 장애 후 Retry 성공
- Retry 최종 실패 후 `FRAUD_DETECTION_UNAVAILABLE`
- 실패율 임계치 초과 후 `OPEN`
- `OPEN` 상태에서 실제 Redis 호출 없이 즉시 차단
- 대기 시간 후 `HALF_OPEN`
- `HALF_OPEN`에서 설정된 수의 요청만 허용
- Redis 복구 성공 시 `CLOSED`
- `HALF_OPEN` 시험 요청 실패 시 다시 `OPEN`
- 의심 거래 업무 예외가 CircuitBreaker 실패율에 포함되지 않음
- Redis 장애 시 송금·카드 결제 잔액 변경 없음
- 기존 누적 금액, 거래 빈도, 동일 금액 반복 탐지 회귀 테스트 통과
- CircuitBreaker 상태, 호출 수, 느린 호출, 차단 호출, 상태 전환 메트릭 확인

## 9. Step 5 - Transaction Lifecycle and Failure Tracking

### 9.1 State model

```text
REQUESTED -> VALIDATING -> PROCESSING -> COMPLETED
     |           |             |
     +-----------+-------------+-> FAILED
```

- 엔티티 메서드에서 허용된 상태 전이만 수행한다.
- `transferId`, 상태, 실패 단계, 오류 코드, 재시도 가능 여부, 각 상태 시각, 시도 횟수, 버전을 저장한다.
- `@Version`을 사용해 중복 상태 전이와 운영 작업 충돌을 탐지한다.

### 9.2 Transaction boundaries

- `REQUESTED` 예약은 별도 짧은 트랜잭션으로 먼저 커밋한다.
- 멱등성 처리 권한을 가진 요청만 `VALIDATING`으로 전이한다.
- 이상 거래 검증을 통과하면 `PROCESSING`으로 전이한다.
- 잔액 변경, 거래 원장 저장, `COMPLETED` 전이는 하나의 DB 트랜잭션으로 커밋한다.
- 예외 발생 시 자금 트랜잭션을 롤백한 뒤 별도 트랜잭션으로 `FAILED`와 실패 단계를 저장한다.
- 거래 원장은 `transferId`를 유니크하게 참조해 상태와 실제 자금 이동을 대조할 수 있게 한다.
- 장시간 `PROCESSING` 상태를 조회하고 점검할 수 있게 한다.

### 9.3 Failure data

- 실패 단계: 요청 검증, 이상 거래 검증, 잔액 검증, 자금 이동, 원장 저장
- 오류 코드: 외부 응답에 사용하는 안정적인 코드
- 재시도 가능 여부: 운영 판단용이며 동일 멱등성 키 자동 재실행과는 분리한다.
- 실패 상세 메시지에는 개인정보와 비밀번호를 저장하지 않는다.

### 9.4 Compensation policy

현재 내부 지갑 송금은 하나의 PostgreSQL 트랜잭션으로 처리하므로 정상적인 실패는 롤백한다.
외부 결제·정산처럼 이미 커밋된 외부 효과를 되돌려야 하는 흐름이 없으면
`COMPENSATION_REQUIRED`, `COMPENSATED`를 구현하지 않는다.

### 9.5 Required verification

- 정상 요청의 전체 상태 전이
- 각 실패 단계에서 `FAILED`와 오류 정보 영속화
- 자금 트랜잭션 롤백 후에도 실패 상태가 남는지 검증
- 허용되지 않은 상태 전이 차단
- 동일 거래의 중복 완료와 중복 원장 저장 차단
- 장시간 `PROCESSING` 거래 조회

## 10. Current-state Assessment

### 10.1 Step 1

Already present:

- `TransactionAdvisoryLock`이 `pg_advisory_xact_lock(walletId)`을 사용한다.
- `WalletServiceImpl#acquireInOrder`가 두 지갑 ID를 오름차순으로 잠근다.
- 충전, 출금, 송금과 `WalletTransactionService`의 원장 저장이 기본 `REQUIRED` 전파로 같은 트랜잭션에 참여한다.
- 락 순서 단위 테스트, 100건 동시 송금, 송금·출금 경합, 원장 실패 롤백 테스트가 존재한다.

Resolved in Step 1:

- A에서 B, B에서 A로 동시에 송금하는 양방향 데드락 테스트를 추가했다.
- Testcontainers를 `2.0.5`로 올리고 Docker 29에서 PostgreSQL 통합 테스트를 실행했다.
- 지갑 락은 양수 지갑 ID, 환율 스케줄러 전역 락은 음수 키를 사용하도록 키 대역을 분리했다.
- 송신 잔액은 출금 통화 금액, 수신 잔액은 환전된 입금 통화 금액으로 변경한다.
- 지갑 원장에 nullable `receivedAmount`를 추가해 외화 송금의 출금액과 입금액을 함께 저장한다.

### 10.2 Step 2

Already present:

- 없음. Redis Lua 재시도 중에는 한 `evaluate` 호출이 같은 임의 UUID를 재사용하지만 이는 API 멱등성이 아니다.

Gaps and expected scope:

- `WalletController`는 멱등성 헤더를 받지 않고 송금 성공 시 `204`만 반환한다.
- `WalletTransferRequest`, `WalletFacadeService`, `WalletService`에 요청 식별자나 결과 반환 계약이 없다.
- 거래 요청 엔티티, `(userId, idempotencyKey)` 유니크 제약, 요청 해시, 중복 정책, 관련 오류 코드가 없다.
- 즉시 송금과 예약 송금이 같은 Facade 메서드를 공유한다. Step 2는 우선 즉시 송금에 적용하고,
  예약 등록과 예약 실행의 멱등성은 별도 범위로 둘지 승인받아야 한다.

### 10.3 Step 3

Already present:

- 사용자/거래시각/거래 ID 복합 인덱스, QueryDSL DTO Projection, 소스별 제한 조회가 구현돼 있다.
- 서비스 병합 정렬과 소스별 커서 조건, 필터 기반 소스 제거 테스트가 존재한다.
- 1,000,000건 벤치마크와 페이지 크기 50 재측정 결과가 `docs/refactoring-roadmap.md`에 기록돼 있다.

Gaps and risks:

- 관련 Repository, 서비스, 테스트, 벤치마크 파일에 커밋되지 않은 사용자 변경사항이 존재한다.
  Step 3 전까지 수정하지 않고 현재 변경을 보존한다.
- 현재 `/v2/transaction`은 `List<TransactionResponse>`만 반환하고 다음 커서를 제공하지 않는다.
- 클라이언트가 `cursor`, `cursorTransactionType`, `cursorTransactionId` 세 필드를 직접 구성해야 하므로
  명세의 불투명 커서 계약을 충족하지 않는다.
- 동일 시각 거래의 소스 간 순서는 단위 테스트가 있지만 실제 연속 페이지 통합 테스트가 부족하다.
- 기존 문서의 평균 약 35배 개선 수치는 재현 가능한 실행 명령과 원본 결과 연결이 필요하다.

### 10.4 Step 4

Already present:

- Lua Script가 이력 정리, 조회, 규칙 평가, 정상·의심 시도 기록을 원자적으로 처리한다.
- 같은 `evaluate` 호출 안의 Redis 재시도는 동일 기록 ID를 사용한다.
- 송금과 카드 결제가 잔액 변경 전에 동기로 이상 거래 검증을 수행한다.
- Redis 장애는 `FRAUD_DETECTION_UNAVAILABLE`로 변환되고 거래는 Fail-Closed된다.
- Actuator와 Prometheus Registry 의존성, Prometheus의 `/actuator/prometheus` 수집 경로가 존재한다.

Gaps and risks:

- Resilience4j 의존성과 설정이 없다.
- `FraudRedisCircuitBreaker`가 `CLOSED`와 시간 기반 차단만 직접 관리하며 명시적인 `HALF_OPEN` 검증이 없다.
- 현재 Retry 루프가 각 Redis 실패마다 `recordFailure`를 호출해 한 거래의 재시도가 CircuitBreaker에 여러
  실패로 집계된다.
- 모든 `DataAccessException`을 동일하게 실패 처리해 연결 오류·타임아웃과 다른 기술 오류를 구분하지 않는다.
- `Thread.sleep` 고정 백오프를 직접 구현한다.
- CircuitBreaker 상태, 호출 결과, 느린 호출, 차단 호출, 상태 전환 메트릭이 없다.
- `application.yaml`에 관리 엔드포인트와 Fraud 정책 설정이 없고 이 파일 자체가 현재 추적되지 않은 사용자
  파일이므로 수정 전 내용을 보존해야 한다.
- Actuator 의존성이 `build.gradle`에 중복 선언돼 있다.
- 기존 단위 테스트는 Retry 최종 실패와 단순 차단만 검증하며 Retry 성공, 실패율 기반 OPEN,
  HALF_OPEN 복구·재개방, 허용 요청 수, 업무 예외 제외, 메트릭을 검증하지 않는다.
- 송금 Fail-Closed 테스트는 있지만 카드 결제 잔액 불변 테스트는 추가 확인이 필요하다.

Expected production-file scope after approval:

- Remove: `FraudRedisCircuitBreaker.java`
- Change: `build.gradle`, `FraudRuleEvaluator.java`, Fraud 관련 설정 클래스, 환경 설정 파일
- Add: Redis Lua 전용 실행 Bean, Resilience4j 실행/설정 Bean, 상태 전환 관측 컴포넌트
- Preserve without modification: `src/main/resources/lua/fraud_check.lua`와 탐지 규칙
- Update tests only in Fraud, wallet Fail-Closed, card payment 관련 범위

### 10.5 Step 5

Already present:

- Step 2에서 즉시 송금 요청 Aggregate인 `WalletTransfer`를 추가했다.
- `(senderUserId, idempotencyKey)` 유니크 제약과 `@Version`이 존재한다.
- `REQUESTED`, `PROCESSING`, `COMPLETED`, `FAILED` 상태가 존재한다.
- 요청 예약은 `REQUIRES_NEW`, 자금 이동·원장·`COMPLETED`는 하나의 트랜잭션이다.
- 자금 트랜잭션 실패 후 별도 `REQUIRES_NEW`로 `FAILED`와 오류 코드를 저장한다.
- 거래 원장은 nullable unique `transferId`를 저장한다.

Gaps and expected scope:

- `VALIDATING` 상태가 없고 Fraud 검증과 자금 처리 단계를 구분하지 못한다.
- 현재 `PROCESSING` 전이는 자금 트랜잭션 안에서 발생해 실패·서버 종료 시 롤백된다. 운영 DB에는 다시
  `REQUESTED`로 남으므로 실제로 어느 단계까지 갔는지 알 수 없다.
- 실패 코드는 있지만 실패 단계, 재시도 가능 여부와 상태별 시각이 없다.
- 예상하지 못한 런타임 예외는 모두 같은 코드로 기록돼 운영 분류가 어렵다.
- 장시간 `PROCESSING` 거래 조회와 운영 점검 흐름이 없다.
- `COMPLETED`와 원장 존재 여부를 대조하는 정합성 점검이 없다.
- 사용되지 않는 `WalletTransactionStatus`가 즉시 송금 상태 모델과 별도로 남아 혼동을 준다.

### 10.6 Step 1 approval decisions

1. 외화 송금 원장에 출금액과 입금액을 모두 저장하도록 Step 1 범위에 포함한다.
   - Recommendation: 포함한다. 송신 잔액은 출금 통화 금액, 수신 잔액은 환전된 입금 통화 금액으로
     변경하고 원장에도 두 금액을 함께 기록해야 외화 송금을 재구성할 수 있다.
2. Advisory Lock 키 공간을 지갑과 환율 작업이 충돌하지 않도록 분리한다.
   - Recommendation: 분리한다. 양수 지갑 ID는 지갑 락에 사용하고 시스템 전역 락은 음수 키 대역을
     사용하도록 규칙을 고정하면 해시 충돌 없이 용도를 구분할 수 있다.
3. Step 2 멱등성 범위를 우선 즉시 송금으로 한정한다.
   - Recommendation: 즉시 송금만 먼저 적용한다. 예약 송금은 예약 등록 요청과 예약 시각의 실제 실행에
     서로 다른 멱등성 경계가 필요하므로 별도 명세 없이 같은 처리로 묶지 않는다.
4. 송금 API는 UUID 형식의 `Idempotency-Key` 헤더와 `transferId`, `status` 응답을 사용한다.
   - Recommendation: 승인한다. 멱등성 키를 비즈니스 요청 본문과 분리하고 중복 완료 요청에 같은 결과를
     반환할 수 있게 한다.
5. 실패한 동일 키는 자동 재실행하지 않고 저장된 실패 결과를 반환한다.
   - Recommendation: 승인한다. 네트워크 재전송과 새로운 송금 의도를 분리해 중복 자금 이동을 우선 방지한다.
6. 위 결정과 전체 5단계 명세는 승인됐으며 Step 1 구현과 검증을 완료했다.

## 11. Change and Verification Log

### 11.1 Step 1 - 2026-06-19

Implemented:

- `WalletServiceImpl`이 송신 통화의 요청 금액과 환전된 수신 통화 금액을 분리해 전달한다.
- `WalletBalanceService`가 송신 잔액에서 `sentAmount`를 차감하고 수신 잔액에 `receivedAmount`를 더한다.
- `WalletTransaction`에 nullable `receivedAmount`를 추가해 기존 원장과 호환하면서 외화 송금의 두 금액을 저장한다.
- `WalletTransactionService#saveTransferTransaction`이 출금액과 입금액을 함께 원장에 저장한다.
- 지갑 Advisory Lock은 양수 지갑 ID만 허용하고 환율 스케줄러 전역 락은 음수 키를 사용한다.
- 양방향 동시 송금과 송금 원장 저장 실패 롤백 통합 테스트를 추가했다.

Files changed for Step 1:

- `global/common/Constants.java`
- `domain/exchangeRate/util/ExchangeRateLockManager.java`
- `domain/wallet/wallet/service/TransactionAdvisoryLock.java`
- `domain/wallet/wallet/service/impl/WalletServiceImpl.java`
- `domain/wallet/balance/service/WalletBalanceService.java`
- `domain/wallet/transaction/entity/WalletTransaction.java`
- `domain/wallet/transaction/service/WalletTransactionService.java`
- Step 1 관련 wallet 테스트 파일

Verification results:

- PASS: `WalletServiceImplTest`
- PASS: `TransactionAdvisoryLockTest`
- PASS: `WalletBalanceServiceTest`
- PASS: `WalletTransactionServiceTest`
- PASS: `TransactionRepositoryDynamicQueryTest`로 nullable 원장 컬럼과 기존 조회 호환성 확인
- PASS: `git diff --check`
- PASS: Testcontainers core, JUnit, PostgreSQL 모듈이 모두 `2.0.5`로 해석됨을 `dependencyInsight`로 확인
- PASS: `WalletServiceConcurrencyTest` 23건, PostgreSQL 16 Testcontainer에서 실행
- PASS: 100건 동시 송금, 양방향 송금 5회, 송금·출금 경합 5회, 충전·송금 경합 5회
- PASS: 충전 원장 실패와 송금 원장 실패 시 잔액 롤백
- INFO: 공통 Redis `GenericContainer`와 PostgreSQL 컨테이너가 Testcontainers `2.0.5`에서 정상 기동됨
- PASS: `FraudRuleEvaluatorTest`에서 불필요한 `TestUserInitializer`를 제거해 KMS와 무관한 Redis Lua
  규칙 테스트로 격리했다.
- PASS: `UserServiceTest`는 테스트 사용자 생성 중 모바일 카드 발급만 mock 처리해 운영되지 않는 KMS에
  의존하지 않도록 했다.
- PASS: 위 두 테스트를 Redis·PostgreSQL Testcontainers 환경에서 함께 재실행했다.

Remaining risks:

- 현재 거래내역 조회 DTO는 새 `receivedAmount`를 노출하지 않는다. 진행 중인 Step 3 변경을 보존하기 위해
  조회 모델 반영은 Step 3 명세에서 방향별 금액 의미와 함께 처리한다.
- 운영 DB에는 `received_amount numeric(19,4)` 컬럼 반영이 필요하다. 현재 프로젝트가 Flyway 같은 명시적
  마이그레이션 도구 없이 `ddl-auto=update`를 사용하므로 운영 전 스키마 마이그레이션 전략을 별도로 확정해야 한다.

### 11.2 Testcontainers 2.0.5 migration - 2026-06-20

- Spring Boot dependency management의 `testcontainers.version`을 `2.0.5`로 재정의했다.
- Testcontainers BOM과 `testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql` 모듈을 사용한다.
- PostgreSQLContainer import를 `org.testcontainers.postgresql` 패키지로 변경했다.
- 2.x의 non-generic `PostgreSQLContainer` API에 맞게 테스트 선언을 변경했다.
- 서드파티 Redis Testcontainer를 제거하고 표준 `GenericContainer`로 교체했다.
- 기존 테스트 시나리오와 Redis/PostgreSQL 이미지 버전은 유지했다.

## 12. Step 2 Implementation Approval Proposal

### 12.1 Scope

- 즉시 지갑 송금에만 멱등성을 적용한다.
- 예약 송금 등록과 예약 실행의 멱등성은 별도 명세로 분리한다.
- 기존 `/api/v1/wallet/transfer` 경로는 유지한다.
- 즉시 송금 엔드포인트에서 `SCHEDULED` 요청은 허용하지 않는다.

### 12.2 API changes

```http
PUT /api/v1/wallet/transfer
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

- `Idempotency-Key`는 필수 UUID 헤더다.
- 성공 응답을 `204 No Content`에서 `200 OK`로 변경한다.
- 최초 성공과 완료 후 중복 요청은 같은 응답을 반환한다.

```json
{
  "transferId": "server-generated UUID",
  "status": "COMPLETED"
}
```

### 12.3 Request hash

다음 필드를 정규화한 뒤 SHA-256 해시를 계산한다.

- 수신자 이름: trim
- 전화번호: 숫자만 유지
- 송금액: `stripTrailingZeros().toPlainString()`
- 출금·입금 통화: ISO currency code
- 거래 유형: `GENERAL`

`transferDatetime`은 즉시 송금의 의미에 포함되지 않으며 즉시 송금 요청에서는 무시하지 않고 null 여부를
검증한다. 같은 키에 다른 해시가 들어오면 `409 IDEMPOTENCY_KEY_REUSED`를 반환한다.

### 12.4 Persistence model

새 `WalletTransfer` Aggregate를 추가한다.

| Field | Purpose |
| --- | --- |
| `transferId` UUID | 외부 응답과 원장 연결에 사용하는 서버 거래 ID |
| `senderUserId` | 인증된 송신 사용자 |
| `idempotencyKey` UUID | 클라이언트 재전송 식별자 |
| `requestHash` char(64) | 같은 키의 다른 요청 방지 |
| `status` | `REQUESTED`, `PROCESSING`, `COMPLETED`, `FAILED` |
| `failureCode` | 실패한 요청에 동일 오류를 반환하기 위한 ErrorCode 이름 |
| `createdAt`, `updatedAt` | 요청 접수와 최종 처리 시각 |
| `version` | 상태 전이 충돌 감지를 위한 낙관적 락 버전 |

DB 최종 방어선:

- Unique `(sender_user_id, idempotency_key)`
- `wallet_transaction.transfer_id` unique, 기존 충전·출금 원장을 위해 nullable

### 12.5 Transaction flow

```text
Controller
  -> request hash 생성
  -> Reservation Coordinator
       -> REQUIRES_NEW 요청 INSERT + flush
       -> unique 충돌이면 기존 요청 조회
  -> 기존 요청 정책 적용
  -> 최초 요청만 WalletService.transfer(transferId, ...)
       -> 하나의 DB transaction
       -> status PROCESSING
       -> fraud validation
       -> advisory locks
       -> balances update
       -> wallet ledger with transferId
       -> status COMPLETED
       -> commit
  -> 예외 발생 시 별도 REQUIRES_NEW transaction으로 FAILED 기록
```

요청 예약과 자금 이동을 같은 트랜잭션에 넣지 않는다. 같은 트랜잭션이면 처리 실패 시 멱등성 레코드도
롤백되어 동일 요청이 다시 최초 요청으로 실행될 수 있기 때문이다.

`COMPLETED` 전이는 잔액 변경·원장 저장과 같은 트랜잭션에 둔다. 자금 이동은 커밋됐지만 상태만
`PROCESSING`으로 남는 구간을 만들지 않는다.

### 12.6 Concurrent insert handling

- 단순 `exists -> insert` 방식은 사용하지 않는다.
- 별도 Transaction Writer Bean이 요청을 INSERT하고 즉시 flush한다.
- 유니크 충돌은 트랜잭션 프록시 밖 Coordinator가 잡는다.
- 실패한 INSERT 트랜잭션이 종료된 뒤 기존 요청을 새 조회 트랜잭션에서 읽는다.
- 이 구조로 Spring의 rollback-only 상태와 self-invocation 문제를 피한다.

### 12.7 Existing request policy

| State | Same hash | Different hash |
| --- | --- | --- |
| `COMPLETED` | 저장된 `transferId`, `COMPLETED` 반환 | `409 IDEMPOTENCY_KEY_REUSED` |
| `REQUESTED`, `PROCESSING` | `409 TRANSACTION_IN_PROGRESS` | `409 IDEMPOTENCY_KEY_REUSED` |
| `FAILED` | 저장된 기존 업무 오류 재반환, 재실행 금지 | `409 IDEMPOTENCY_KEY_REUSED` |

서버가 자금 트랜잭션 도중 종료되면 예약 레코드는 `REQUESTED`로 남을 수 있다. Step 2에서는 안전성을
우선해 자동 재실행하지 않고 처리 중으로 응답한다. 장시간 미완료 요청의 판정과 복구는 Step 5에서
상태 시간과 운영 점검 정책으로 구현한다.

### 12.8 Error codes

- `IDEMPOTENCY_KEY_REUSED`: `409`, 같은 키로 다른 요청
- `TRANSACTION_IN_PROGRESS`: `409`, 최초 요청 처리 중 또는 복구 대기
- `INVALID_TRANSFER_REQUEST`: `400`, 즉시 송금 API에 예약 송금 정보 전달
- 예상하지 못한 실패는 안정적인 내부 거래 오류 코드로 변환하고 `FAILED`에 저장한다.

### 12.9 Expected file scope

New:

- WalletTransfer entity, status enum, repository
- Idempotency request hasher
- Reservation writer and coordinator
- Transfer response DTO
- Step 2 unit and PostgreSQL concurrency tests

Changed:

- `WalletController`: idempotency header and response
- `WalletFacadeService`: 예약/즉시 계약 분리와 멱등성 조정
- `WalletService`, `WalletServiceImpl`: `transferId`를 자금 트랜잭션에 전달
- `WalletTransaction`: `transferId` nullable unique 연결
- `WalletTransactionService`: 송금 원장에 `transferId` 저장
- `ErrorCode`: 멱등성·처리 중 오류 추가

Not changed:

- Redis Lua Script와 Fraud 규칙
- 거래내역 조회 최적화 파일
- 예약 송금 실행 로직

### 12.10 Required tests

- 최초 송금 성공 후 같은 키 재전송 시 같은 결과 반환
- 최초 응답 유실을 가정한 완료 요청 재조회
- 동일 키 30건 동시 요청에서 잔액 변경과 원장 저장 정확히 1회
- 처리 중 중복 요청은 `TRANSACTION_IN_PROGRESS`
- 같은 사용자·같은 키·다른 금액은 `IDEMPOTENCY_KEY_REUSED`
- 서로 다른 사용자는 같은 UUID 사용 가능
- 잔액 부족, 이상 거래, Redis 장애 실패를 같은 키로 재전송해도 재실행하지 않음
- 원장 `transferId` 중복 저장 차단
- 예약 송금 요청이 즉시 송금 API의 멱등성 흐름에 진입하지 않음

### 12.11 Approval gate

이 절의 API 응답 변경, DB 제약, 실패 재실행 금지 정책, 트랜잭션 경계를 승인받아 구현했다.

## 13. Step 2 Change and Verification Log

### 13.1 Implemented flow

- 즉시 송금 API가 필수 UUID `Idempotency-Key` 헤더를 받고 `transferId`, `status`를 반환한다.
- `WalletTransfer` Aggregate가 요청 키, 요청 해시, 상태, 실패 코드를 저장한다.
- `(sender_user_id, idempotency_key)` 유니크 제약으로 최초 INSERT 성공자만 실행 권한을 얻는다.
- 요청 예약은 `REQUIRES_NEW` 트랜잭션에서 INSERT 후 flush한다.
- 유니크 충돌은 트랜잭션 밖 Coordinator가 처리하고 기존 요청의 해시와 상태를 확인한다.
- 완료 중복은 기존 결과를 반환하고 처리 중 중복은 `TRANSACTION_IN_PROGRESS`로 차단한다.
- 실패한 동일 키는 저장된 기존 오류를 반환하며 자금 이동을 재실행하지 않는다.
- 잔액 변경, 거래 원장의 `transferId` 저장, `COMPLETED` 전이는 하나의 DB 트랜잭션으로 커밋한다.
- 자금 트랜잭션 실패 후 `FAILED`와 오류 코드는 별도 `REQUIRES_NEW` 트랜잭션으로 기록한다.
- 예약 송금은 기존 경로를 유지하고 즉시 송금 멱등성 범위에서 제외했다.

### 13.2 Frontend behavior

- 일반 송금 입력이 유지되는 동안 같은 UUID를 재사용한다.
- 네트워크 오류 후 같은 입력으로 다시 시도하면 같은 `Idempotency-Key`를 보낸다.
- 입력 내용이 바뀌면 기존 키를 폐기하고 새 키를 생성한다.
- 성공 응답 후 키를 폐기한다.
- 일반 송금의 `transferDatetime`은 null로 보내고 예약 송금만 시각을 전달한다.

### 13.3 Verification

- PASS: 요청 해시의 이름·전화번호·금액 스케일 정규화 단위 테스트
- PASS: 최초 INSERT 소유권, 완료 결과 반환, 다른 본문 충돌, 기존 실패 반환 단위 테스트
- PASS: PostgreSQL에서 완료 요청 재전송 시 같은 `transferId` 반환
- PASS: 동일 키 30건 동시 요청에서 거래 요청 1건, 잔액 변경 1회, 송금 원장 1건
- PASS: 동일 키·다른 금액은 `IDEMPOTENCY_KEY_REUSED`
- PASS: 실패 요청 재전송 시 이상 거래 검증과 자금 이동을 재실행하지 않음
- PASS: 서로 다른 사용자는 같은 UUID 키를 독립적으로 사용
- PASS: 기존 Advisory Lock 동시성, 예약 송금, 사용자, Redis Lua 탐지 회귀 테스트
- PASS: `npm run build`

### 13.4 Remaining risks

- 서버가 요청 예약 커밋 후 자금 트랜잭션 시작 전에 종료되면 `REQUESTED` 상태가 남는다.
- Step 2는 안전성을 우선해 해당 키를 자동 재실행하지 않는다.
- 장시간 `REQUESTED`/`PROCESSING` 요청의 판정, lease, 운영 조회와 복구는 Step 5에서 구현한다.
- 현재 프로젝트는 `ddl-auto=update`를 사용하므로 운영 배포 전 명시적 스키마 마이그레이션이 필요하다.

## 14. Step 3 Implementation Approval Proposal

### 14.1 Current implementation assessment

작업 트리에 다음 조회 최적화 후보 구현이 이미 존재한다. 이 변경은 사용자 작업으로 보존하며,
승인 후 그대로 채택하지 않고 이 명세와 대조하여 필요한 부분만 보완한다.

- 지갑 송신·수신, 카드, 환전 테이블을 각각 QueryDSL로 조회한다.
- 각 쿼리는 사용자 조건과 필터를 DB에 적용하고 거래시각·거래 ID 역순으로 제한 조회한다.
- 엔티티에는 사용자·거래시각·거래 ID 복합 B-tree 인덱스가 선언돼 있다.
- 서비스는 소스별 결과를 `transactionTime DESC`, `transactionType ASC`, `transactionId DESC`로 병합한다.
- 거래 유형·가맹점·지갑 방향에 따라 조회할 필요가 없는 테이블을 호출하지 않는다.
- 100만 건 데이터셋에서 전체 병합·정렬 방식과 소스별 Top-N 방식을 비교한 측정값이 존재한다.

현재 상태로 완료 처리할 수 없는 이유는 다음과 같다.

- API가 결과 목록만 반환하므로 클라이언트가 다음 페이지의 정확한 커서를 알 수 없다.
- 클라이언트가 시각·소스·거래 ID를 각각 전달해야 하므로 내부 정렬 규칙이 외부 계약으로 노출된다.
- 수신 지갑 거래도 송신 금액과 송신 통화를 사용한다. Step 1에서 추가한 `receivedAmount`가 조회에 반영되지 않았다.
- 동일 시각·서로 다른 소스의 거래를 여러 페이지에 걸쳐 조회하는 PostgreSQL 통합 테스트가 없다.
- 인덱스는 JPA `@Index`와 `ddl-auto=update`에 의존하며 운영 적용용 명시적 DDL이 없다.
- 벤치마크 결과는 문서에 있으나 실행 명령, 고정 시드, 원본 결과 파일 연결을 한 번 더 정리해야 한다.

### 14.2 API contract

기존 경로 `GET /v2/transaction`은 유지하되 응답을 목록에서 페이지 응답으로 변경한다.

```json
{
  "items": [
    {
      "transactionTime": "2026-06-20T12:00:00",
      "beforeCurrency": "KRW",
      "afterCurrency": "USD",
      "data": {}
    }
  ],
  "nextCursor": "opaque-base64url-token",
  "hasNext": true
}
```

- 첫 요청은 `cursor` 없이 호출한다.
- 다음 요청은 응답의 `nextCursor` 문자열 하나만 그대로 전달한다.
- 기존 `cursor`, `cursorTransactionType`, `cursorTransactionId` 공개 파라미터는 제거한다.
- `size`의 기본값은 50, 최솟값은 1, 최댓값은 100으로 제한한다.
- 결과가 더 없으면 `nextCursor`는 null이고 `hasNext`는 false다.
- 잘못되거나 지원하지 않는 버전의 커서는 `400 INVALID_TRANSACTION_CURSOR`로 거절한다.

커서는 URL-safe Base64로 인코딩한 버전 있는 JSON 값으로 구현한다.

```json
{
  "v": 1,
  "time": "2026-06-20T12:00:00",
  "source": "CARD",
  "id": 123
}
```

Base64는 암호화가 아니다. 커서에는 개인정보를 넣지 않으며, 모든 조회에는 인증 사용자 ID 조건을 다시
적용하므로 커서 값이 변조돼도 다른 사용자의 거래를 조회할 수 없다.

### 14.3 Fixed global ordering and keyset rule

모든 페이지는 아래 순서를 고정해서 사용한다.

```text
transactionTime DESC
sourceType ASC (WALLET -> CARD -> EXCHANGE)
transactionId DESC
```

마지막 반환 행의 세 값을 다음 커서에 저장한다. 다음 페이지에서는 다음 규칙을 적용한다.

- 커서 시각보다 오래된 거래는 모든 소스에서 조회한다.
- 커서와 같은 시각이면 커서 소스보다 뒤에 오는 소스의 거래를 포함한다.
- 커서와 같은 소스이면 커서 거래 ID보다 작은 거래만 포함한다.
- 커서 소스보다 앞에 오는 소스의 같은 시각 거래는 이미 반환됐으므로 제외한다.

각 조회 대상은 `size + 1`건까지만 가져온다. 애플리케이션에서 병합한 뒤 `size + 1`번째 결과가 있으면
`hasNext=true`로 판단하고, 사용자에게는 앞의 `size`건만 반환한다.

### 14.4 Wallet sent/received semantics

지갑 송금 한 건은 송신자와 수신자에게 다음과 같이 다르게 표시한다.

| 조회 사용자 | 방향 | 표시 금액 | 표시 통화 | 상대방 |
| --- | --- | --- | --- | --- |
| 송신자 | `SENT` | `amount` | `fromCurrency` | 수신자 |
| 수신자 | `RECEIVED` | `receivedAmount` | `toCurrency` | 송신자 |

- 같은 통화의 기존 데이터에서 `receivedAmount`가 null이면 `amount`를 대체값으로 사용한다.
- 금액 범위와 통화 필터도 표시되는 방향 기준 값에 적용한다.
- 지갑 조회 Projection에 `receivedAmount`와 방향을 포함한다.
- 응답의 사용자 ID는 원장에 저장된 송신자 ID가 아니라 현재 인증 사용자 기준으로 만든다.
- 자기 자신에게 송금된 데이터가 존재하더라도 같은 원장 행을 두 번 반환하지 않는다.

### 14.5 Source pruning and database query scope

- `transactionType=WALLET/CARD/EXCHANGE`이면 해당 테이블만 조회한다.
- `merchantName`이 있으면 카드 테이블만 조회한다.
- `direction=SENT`이면 지갑 송신 쿼리만, `RECEIVED`이면 지갑 수신 쿼리만 실행한다.
- 필터가 없으면 지갑 송신·수신, 카드, 환전의 최대 네 목록을 각각 제한 조회한다.
- 엔티티 전체를 로딩하지 않고 응답 생성에 필요한 컬럼만 DTO Projection으로 조회한다.
- 애플리케이션 병합 대상은 최대 `4 * (size + 1)`건으로 제한한다.

기본 조회 경로에는 측정상 효과가 확인된 다음 B-tree 인덱스만 유지한다.

- 지갑 송신: `(user_id, transaction_time DESC, transaction_id DESC)`
- 지갑 수신: `(counterparty_user_id, transaction_time DESC, transaction_id DESC)`
- 카드: `(user_id, transaction_time DESC, transaction_id DESC)`
- 환전 완료: `(user_id, completed_at DESC, transaction_id DESC)`

통화·금액·가맹점 인덱스, BRIN, 부분 인덱스는 현재 사용자별 Top-N 측정에서 안정적인 개선이 없으므로
이번 단계에 추가하지 않는다. 운영 적용 가능한 명시적 인덱스 DDL과 적용·롤백 절차를 문서에 남긴다.

### 14.6 Benchmark contract

- 고정 시드로 지갑·카드·환전 합계 1,000,000건을 생성한다.
- 특정 사용자에게 충분한 거래가 분포하도록 사용자 수와 소스별 건수를 함께 기록한다.
- 페이지 크기 50, 소스별 조회 크기 51, 워밍업 50회, 측정 300회를 사용한다.
- 동일한 PostgreSQL 이미지, 데이터셋, 세션 설정에서 비교한다.
- 기준은 전체 후보를 `UNION ALL`한 뒤 전역 정렬·제한하는 쿼리다.
- 개선안은 소스별 인덱스 Top-N 후 병합하는 쿼리다.
- 평균, p95, 최소·최대, `EXPLAIN (ANALYZE, BUFFERS)`를 원본 결과와 함께 저장한다.
- 포트폴리오의 개선 배수는 `기준 평균 / 개선 평균`으로 계산하고 비교 조건을 문장에 함께 적는다.

기존 기록의 페이지 크기 50 결과인 평균 `6.843ms -> 0.174ms`는 약 39.3배다. 기존 포트폴리오의
“약 35배” 표현을 유지하려면 같은 조건으로 다시 실행해 재현 범위를 확인한 뒤 보수적으로 기재한다.

### 14.7 Expected file scope

New:

- 거래내역 페이지 응답 DTO
- 불투명 커서 값·Codec
- 잘못된 커서 오류 코드와 Codec 단위 테스트
- 동일 시각 다중 소스 연속 페이지 PostgreSQL 통합 테스트
- 운영 인덱스 DDL 및 벤치마크 원본 결과 문서

Changed:

- `TransactionController`: 페이지 응답, 단일 cursor, size 기본값·범위
- `TransactionSearchCondition`: 내부 커서 구성 필드 제거
- `TransactionService`: decode, 소스별 `size + 1`, 병합, nextCursor 생성
- 지갑·카드·환전 Repository: 동적 조건과 복합 Keyset 조건 확정
- 지갑 Projection/응답: 수취 금액, 방향, 상대방 의미 반영
- 거래내역 서비스·Repository 테스트와 벤치마크

Not changed:

- 거래 생성과 잔액 변경 트랜잭션
- Redis Lua Script와 이상 거래 규칙
- Step 2 멱등성 처리 흐름
- 측정으로 효과가 확인되지 않은 추가 인덱스

### 14.8 Required tests

- 첫 페이지, 중간 페이지, 마지막 페이지, 빈 결과
- 잘못된 커서와 지원하지 않는 커서 버전 거절
- 동일 시각에 지갑·카드·환전 거래가 섞여도 페이지 간 누락·중복 없음
- 같은 소스·같은 시각·서로 다른 ID의 순서와 페이지 경계
- 송신 거래는 출금 금액·통화, 수신 거래는 실제 수취 금액·통화로 반환
- 수신 방향의 금액·통화 필터가 수취 값에 적용됨
- 거래 유형·가맹점·지갑 방향에 따른 불필요한 Repository 미호출
- 페이지당 각 Repository 조회량이 `size + 1` 이하
- 100만 건 동일 데이터셋의 기준·개선 쿼리 재측정
- 기존 거래 저장, Step 1 정합성, Step 2 멱등성 회귀 테스트

### 14.9 Approval gate

구현 전 다음 변경을 승인받는다.

- `/v2/transaction` 응답을 목록에서 `items/nextCursor/hasNext` 구조로 변경
- 공개 커서 3개를 불투명 커서 1개로 변경
- 기본 size 50, 최대 size 100 적용
- 수신 송금의 금액·통화 의미를 `receivedAmount/toCurrency`로 수정
- 현재 작업 트리의 조회 최적화 후보를 이 명세에 맞춰 보완
- 측정상 효과 없는 추가 인덱스는 도입하지 않음

위 API 응답, 커서, 수신 거래 의미, 조회 크기, 인덱스 정책을 2026-06-20 승인받아 구현했다.

## 15. Step 3 Change and Verification Log

### 15.1 Implemented query flow

```text
Controller
  -> size 1..100 검증
  -> 불투명 cursor decode
  -> 필터로 조회할 거래 소스 결정
  -> 각 소스에서 size + 1건 제한 조회
  -> transactionTime/sourceType/transactionId로 전역 정렬
  -> 동일 원장 행 중복 제거
  -> size건 반환
  -> 추가 행이 있으면 마지막 반환 행으로 nextCursor 생성
```

- `/v2/transaction` 응답을 `items`, `nextCursor`, `hasNext` 구조로 변경했다.
- 공개했던 시각·소스·ID 커서를 URL-safe Base64 토큰 하나로 변경했다.
- 커서에 버전을 포함하고 형식 오류, 알 수 없는 소스, 지원하지 않는 버전을 `T012`로 거절한다.
- 페이지 크기를 기본 50, 최소 1, 최대 100으로 제한하고 잘못된 크기를 `T013`으로 거절한다.
- 각 Repository는 엔티티 전체가 아니라 필요한 컬럼만 Projection하고 `size + 1`건으로 제한한다.
- 가맹점, 거래 소스, 지갑 방향 조건으로 불필요한 Repository 호출을 제거한다.

### 15.2 Wallet direction correction

- 송신 조회는 `amount`, `fromCurrency`, 수신자 ID를 사용한다.
- 수신 조회는 `coalesce(receivedAmount, amount)`, `toCurrency`, 송신자 ID를 사용한다.
- 수신 금액 범위와 통화 필터도 실제 수취 값에 적용한다.
- 응답 사용자 ID는 원장에 저장된 송신자가 아니라 현재 인증 사용자를 기준으로 반환한다.
- 송신·수신 목록에 같은 원장 행이 들어오면 `(sourceType, transactionId)`로 한 번만 반환한다.

### 15.3 Pagination verification

- PASS: 커서 encode/decode 왕복과 평문 내부 값 비노출
- PASS: 깨진 Base64와 지원하지 않는 커서 버전 거절
- PASS: 빈 결과, 첫 페이지, 중간 페이지, 마지막 페이지
- PASS: 각 소스 Repository 조회 크기가 `size + 1`
- PASS: 동일 시각의 지갑·카드·환전 결과가 페이지 간 누락·중복 없이 이어짐
- PASS: 같은 소스·같은 시각의 5건이 거래 ID 역순으로 3개 페이지에 이어짐
- PASS: 수신 송금이 실제 수취 금액과 송신자 상대방 ID로 반환됨
- PASS: 수신 금액·통화 QueryDSL 필터가 `receivedAmount`, `toCurrency`에 적용됨
- PASS: 거래 유형·가맹점·방향 기반 소스 제거

### 15.4 Benchmark result

- PostgreSQL 16, 고정 생성 데이터 1,000,000건, 사용자 100명
- 응답 50건, 소스별 51건, 워밍업 50회, 측정 300회
- 기준 전체 병합·정렬: 평균 4.607ms, p95 6.242ms
- 소스별 Top-N: 평균 0.228ms, p95 0.265ms
- 송신·수신·카드·환전 통합 Top-N: 평균 0.239ms, p95 0.279ms
- 평균 20.25배, p95 23.55배 개선
- 재현 명령과 Plan 근거: `docs/benchmarks/transaction-history-step3-2026-06-20.md`
- 기존 “약 35배” 대신 현재 재현 결과인 “동일 100만 건 기준 평균 약 20배”를 사용한다.

### 15.5 Schema operations

- 적용 SQL: `docs/sql/transaction-history-indexes-up.sql`
- 롤백 SQL: `docs/sql/transaction-history-indexes-down.sql`
- `CREATE/DROP INDEX CONCURRENTLY`는 PostgreSQL 트랜잭션 밖에서 한 문장씩 실행한다.
- 통화·금액·GIN·BRIN 인덱스는 현재 사용자 Top-N 경로에 추가하지 않았다.

### 15.6 Regression result and remaining risks

- PASS: 거래내역 단위, H2 QueryDSL, PostgreSQL 연속 페이지 테스트
- PASS: Step 1 Advisory Lock, Step 2 멱등성, 기존 Redis 이상 거래 관련 선별 회귀 테스트
- 전체 테스트 94개 중 이번 범위와 무관한 기존 KMS 의존 카드·로그인 테스트 7개가
  `AES 키 암호화에 실패했습니다`로 실패했다. 로컬 KMS가 운영되지 않는 현재 환경의 기존 문제다.
- 커서는 불투명한 전달 값이지만 서명하거나 암호화하지 않는다. 모든 쿼리가 인증 사용자 ID를 다시 적용해
  다른 사용자의 거래 접근에는 사용할 수 없다.
- `/v2/transaction` 응답 형태가 변경되므로 이 API를 사용하는 외부 클라이언트는 페이지 응답으로 맞춰야 한다.
- 프로젝트에 Flyway/Liquibase가 없으므로 운영 인덱스 SQL의 배포 실행은 현재 수동 절차다.

## 16. Step 4 Implementation Approval Proposal

### 16.1 Current code flow and problems

현재 이상 거래 검사는 다음 순서로 동작한다.

```text
WalletService/CardService
  -> FraudDetectionService.verify
  -> FraudRuleEvaluator.evaluate
       -> FraudRedisCircuitBreaker.checkAllowed
       -> Redis Lua execute
       -> 실패하면 recordFailure + 50ms sleep
       -> 최대 2회 반복
       -> 성공하면 recordSuccess
  -> Lua 결과가 의심이면 SUSPICIOUS_TRANSACTION
```

현재 `FraudRedisCircuitBreaker`는 다음 두 필드만 서버 메모리에 보관한다.

```java
private int consecutiveFailures;
private long openUntilMillis;
```

이 구조에는 다음 문제가 있다.

- 한 거래의 Redis 호출을 두 번 재시도하면 `recordFailure()`도 두 번 호출돼 CircuitBreaker 실패 두 건으로 센다.
- 3회 실패 기준에서 첫 거래가 2회를 차지하고 다음 거래의 첫 Redis 실패가 Circuit을 열 수 있다.
- Circuit이 Retry 도중 열려도 `checkAllowed()`는 Retry 시작 전에 한 번만 실행돼 남은 Redis 재시도가 계속될 수 있다.
- `OPEN` 시간이 지나면 시험 요청 수를 제한하는 명시적 `HALF_OPEN` 상태 없이 모든 요청이 다시 Redis로 갈 수 있다.
- 연속 실패 횟수만 보므로 Sliding Window 실패율과 느린 호출을 평가하지 못한다.
- 상태, 성공·실패·느린 호출·차단 호출·상태 전환 메트릭이 없다.
- 모든 `DataAccessException`을 같은 장애로 처리해 연결 장애, 타임아웃, Lua 결과 오류를 구분하지 않는다.

기존 Fail-Closed의 실행 위치는 올바르다. 지갑 송금과 카드 결제는 이상 거래 검사를 잔액 변경 전에
실행하므로 `FRAUD_DETECTION_UNAVAILABLE`이 발생하면 자금 변경 코드에 진입하지 않는다.

### 16.2 Dependency decision

- Java 17과 Spring Boot 3.4.2를 유지한다.
- `io.github.resilience4j:resilience4j-spring-boot3:2.3.0`을 추가한다.
- Resilience4j 3은 Java 21을 요구하므로 이번 범위에 사용하지 않는다.
- 기존 `spring-boot-starter-aop`, Actuator, Micrometer Prometheus 의존성은 유지한다.
- 중복 선언된 Actuator 의존성 한 줄은 Step 4 범위에서 정리한다.

### 16.3 Class ownership after refactoring

Remove:

- `FraudRedisCircuitBreaker`: 직접 관리한 `consecutiveFailures`, `openUntilMillis` 제거
- `FraudRuleEvaluator`의 수동 for-loop Retry와 `Thread.sleep` 제거
- `FraudPolicyProperties`의 `circuitFailureThreshold`, `circuitOpenMillis` 제거

Add:

1. `FraudRedisLuaExecutor`

```text
책임: RedisTemplate으로 기존 Lua Script 한 번 실행
입력: Redis key, amount, timestamp, recordId, 정책 값
출력: Lua 결과 문자열
금지: Retry, Circuit 상태 전환, 업무 예외 판단
```

2. `FraudRedisCommand`

```text
한 거래의 Lua 인자를 보관하는 불변 값
recordId를 Retry 밖에서 한 번 생성해 모든 재시도에서 재사용
```

3. `FraudRedisResilienceExecutor`

```text
책임: Resilience4j Retry와 CircuitBreaker 함수형 API 조합
CallNotPermittedException과 최종 기술 실패를 FRAUD_DETECTION_UNAVAILABLE로 변환
```

4. `FraudRedisFailureClassifier`

```text
예외 원인 체인을 검사해 Redis 연결 오류와 명령 타임아웃인지 판정
Retry와 CircuitBreaker가 같은 판정 규칙을 공유
```

5. `FraudCircuitBreakerEventListener`

```text
상태 전환 이벤트를 구조화 로그와 Micrometer Counter로 기록
CLOSED -> OPEN -> HALF_OPEN -> CLOSED/OPEN 추적
```

Changed:

- `FraudRuleEvaluator`: 명령 생성, Resilience Executor 호출, Lua 결과를 도메인 결과로 변환
- `FraudDetectionService`: 기존 의심 거래 알림과 업무 예외 변환 유지
- `FraudPolicyProperties`: 탐지 규칙과 위험 점수만 유지
- 설정 파일: Resilience4j CircuitBreaker/Retry와 Actuator 노출 설정 추가

Not changed:

- `lua/fraud_check.lua`
- 누적 금액, 빈도, 동일 금액 반복, 심야 거래 규칙과 위험 점수 계산
- Redis key 구조와 한 요청의 `recordId` 재사용
- 지갑 Advisory Lock, PostgreSQL 자금 트랜잭션, Step 2 멱등성
- 이상 거래 검사를 잔액 변경 전에 동기로 실행하는 순서

### 16.4 Exact invocation order

함수형 API로 다음 순서를 직접 구성한다. Spring AOP 어노테이션은 사용하지 않으므로 self-invocation 문제도
발생하지 않는다.

```java
Supplier<String> redisCall = () -> luaExecutor.execute(command);
Supplier<String> retried = Retry.decorateSupplier(retry, redisCall);
Supplier<String> guarded = CircuitBreaker.decorateSupplier(circuitBreaker, retried);
return guarded.get();
```

실제 실행 순서는 다음과 같다.

```text
CircuitBreaker 권한 확인
  -> CLOSED/HALF_OPEN 허용이면 Retry 시작
       -> Redis Lua 1차 호출
       -> 연결/타임아웃이면 50ms 대기
       -> Redis Lua 2차 호출
  -> Retry의 최종 성공/실패 한 건만 CircuitBreaker에 기록
```

- 첫 Redis 호출 실패 후 두 번째가 성공하면 CircuitBreaker 성공 1건이다.
- 두 Redis 호출이 모두 실패하면 CircuitBreaker 실패 1건이다.
- `OPEN`이면 Retry에 들어가기 전에 `CallNotPermittedException`이 발생해 Redis를 한 번도 호출하지 않는다.
- `HALF_OPEN`에서는 설정한 시험 요청 수만 Retry 묶음에 진입할 수 있다.

### 16.5 Exception classification

Retry 및 CircuitBreaker 실패율에 포함:

- Spring Data Redis `RedisConnectionFailureException`
- Spring `QueryTimeoutException`
- Lettuce `RedisCommandTimeoutException`
- 위 예외가 `DataAccessException` 또는 `RedisSystemException` 원인 체인 안에 있는 경우

Retry하지 않고 CircuitBreaker 실패율에도 포함하지 않지만 Fail-Closed 처리:

- Lua가 null 또는 해석 불가능한 결과를 반환하는 경우
- 예상하지 못한 Redis/Lua 기술 예외
- Retry 대기 중 스레드가 중단된 경우. interrupt 상태를 복구한 뒤 거래를 차단한다.

업무 결과:

- Lua가 반환한 의심 사유는 예외가 아니라 `FraudEvaluationResult.suspicious`로 보호 구간을 정상 종료한다.
- `FraudDetectionService`가 보호 구간 밖에서 `SUSPICIOUS_TRANSACTION`을 발생시킨다.
- 따라서 의심 거래는 CircuitBreaker 성공 호출로 기록되며 실패율에는 포함되지 않는다.

최종 변환:

- `CallNotPermittedException` -> `FRAUD_DETECTION_UNAVAILABLE`
- Redis 연결/타임아웃 Retry 최종 실패 -> `FRAUD_DETECTION_UNAVAILABLE`
- 그 밖의 탐지 불가능 기술 오류 -> `FRAUD_DETECTION_UNAVAILABLE`

### 16.6 Configuration contract

Resilience4j 표준 설정의 `fraudRedis` 인스턴스를 사용한다.

```yaml
resilience4j:
  circuitbreaker:
    instances:
      fraudRedis:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        slow-call-duration-threshold: 200ms
        slow-call-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 2
        automatic-transition-from-open-to-half-open-enabled: true
        register-health-indicator: true
        event-consumer-buffer-size: 100
  retry:
    instances:
      fraudRedis:
        max-attempts: 2
        wait-duration: 50ms
        event-consumer-buffer-size: 100
```

각 값은 환경 변수로 덮어쓸 수 있게 한다. Retry의 `max-attempts=2`는 최초 호출을 포함한 총 두 번이다.
예외 판정은 YAML 클래스명 나열 대신 `CircuitBreakerConfigCustomizer`와 `RetryConfigCustomizer`가
`FraudRedisFailureClassifier`를 공통 사용하도록 구성한다.

테스트에서는 동일 구성 이름을 사용하되 작은 Sliding Window와 짧은 Open 유지 시간을 적용한다.

### 16.7 State behavior

```text
CLOSED
  -> 최소 호출 수 충족 후 실패율 또는 느린 호출률이 임계치 이상
  -> OPEN

OPEN
  -> 모든 거래를 즉시 FRAUD_DETECTION_UNAVAILABLE로 차단
  -> Open 유지 시간 경과
  -> HALF_OPEN

HALF_OPEN
  -> 최대 2개의 시험 거래만 허용
  -> 시험 호출 성공률 충족: CLOSED
  -> 시험 호출 실패: OPEN
```

CircuitBreaker는 애플리케이션 서버 인스턴스별 메모리 상태다. 서버가 3대면 각 서버가 서로 다른
`CLOSED/OPEN/HALF_OPEN` 상태와 Sliding Window를 가진다. Redis 장애 자체는 모든 인스턴스에서 발생하므로
각 인스턴스가 독립적으로 열리지만 상태를 서로 공유하지 않는다.

### 16.8 Metrics and Actuator

Actuator 노출 대상:

- `/actuator/health`: `fraudRedis` 상태 포함
- `/actuator/metrics/resilience4j.circuitbreaker.state`: 현재 상태
- `/actuator/metrics/resilience4j.circuitbreaker.calls`: 성공·실패·느린 호출
- `/actuator/metrics/resilience4j.circuitbreaker.not.permitted.calls`: 차단 호출
- `/actuator/circuitbreakers`: 등록된 CircuitBreaker와 상태
- `/actuator/circuitbreakerevents`: 상태 전환 및 호출 이벤트
- `/actuator/prometheus`: Prometheus 형식 메트릭

상태 전환은 다음 두 경로에 남긴다.

- Resilience4j 이벤트 버퍼와 `/actuator/circuitbreakerevents`
- `fraud_circuitbreaker_transitions_total{from,to}` Micrometer Counter 및 구조화 로그

운영 문서에는 Resilience4j가 Redis 장애 전파를 제한하고 복구를 시험하는 장치일 뿐 금융 정합성을 직접
보장하지 않는다고 명시한다. 금융 정합성은 PostgreSQL 트랜잭션, Advisory Lock, 거래내역 저장,
그리고 탐지 실패 시 잔액 변경 전에 차단하는 Fail-Closed 정책이 담당한다.

### 16.9 Expected file scope

Remove:

- `src/main/java/.../fraud/service/FraudRedisCircuitBreaker.java`

New:

- `FraudRedisCommand`
- `FraudRedisLuaExecutor`
- `FraudRedisResilienceExecutor`
- `FraudRedisFailureClassifier`
- Resilience4j 설정 Customizer
- CircuitBreaker 상태 전환 이벤트·메트릭 Listener
- CircuitBreaker/Retry/Metric 자동화 테스트

Changed:

- `build.gradle`
- `FraudRuleEvaluator`
- `FraudPolicyProperties`
- 애플리케이션 및 테스트 설정
- 기존 Fraud 단위·Redis Lua·지갑·카드 Fail-Closed 테스트
- 이 금융 거래 고도화 문서와 운영 문서

기존 사용자 변경과 관계없는 인프라 데이터, KMS, 거래내역 조회 코드는 수정하지 않는다.

### 16.10 Required automated tests

- Redis 첫 호출 연결 실패 후 Retry 성공, Redis 실제 호출 2회, Circuit 성공 1건
- Retry 두 번 모두 실패하면 `FRAUD_DETECTION_UNAVAILABLE`, Circuit 실패 1건
- 최소 호출 수 이후 실패율 임계치 초과 시 `OPEN`
- `OPEN`에서 Redis Lua Executor 호출 0회 및 즉시 차단
- Open 유지 시간 이후 `HALF_OPEN`
- `HALF_OPEN`에서 설정된 수의 시험 요청만 허용
- Redis 복구 시험 성공 시 `CLOSED`
- `HALF_OPEN` 시험 요청 실패 시 다시 `OPEN`
- 의심 거래가 `SUSPICIOUS_TRANSACTION`으로 차단되지만 Circuit 실패율은 증가하지 않음
- 느린 호출 수와 차단 호출 수 메트릭
- 상태 전환 Counter와 이벤트
- Redis 장애 시 송금·카드 결제 잔액 변경 0건
- 기존 누적 금액·거래 빈도·동일 금액 반복·심야 거래 Lua 테스트 유지
- 관련 테스트, `git diff --check`, Actuator/Prometheus 지표 확인

### 16.11 Approval gate

구현 전 다음 사항을 승인받는다.

- Resilience4j Spring Boot 3 `2.3.0` 의존성 추가
- 수동 CircuitBreaker와 수동 Retry 제거
- 함수형 조합 `CircuitBreaker(Retry(Redis Lua))` 적용
- 연결 오류·타임아웃만 Retry와 Circuit 실패율에 포함
- 기본값: Window 10, 최소 5, 실패율 50%, 느린 호출 200ms/50%, Open 30초, Half-Open 2건
- Retry 총 2회, 고정 50ms 대기
- Actuator endpoint, Micrometer 상태 전환 Counter와 이벤트 로그 추가
- Circuit 상태가 서버 인스턴스별이며 금융 정합성 보장 수단이 아니라는 운영 문서 명시

위 의존성, 실행 순서, 예외 분류, 상태 기본값과 관측 범위를 2026-06-20 승인받아 구현했다.

## 17. Step 4 Change and Verification Log

### 17.1 Removed manual state and retry

- `FraudRedisCircuitBreaker`를 삭제했다.
- `consecutiveFailures`, `openUntilMillis`, `synchronized` 상태 관리를 제거했다.
- `FraudRuleEvaluator`의 for-loop Retry와 `Thread.sleep`을 제거했다.
- `FraudPolicyProperties`에서 Retry와 Circuit 전용 필드를 제거하고 탐지 정책만 남겼다.
- `build.gradle`에 Java 17/Spring Boot 3용 `resilience4j-spring-boot3:2.3.0`을 추가했다.
- 중복된 Spring Boot Actuator 의존성 한 줄을 제거했다.

### 17.2 Implemented class flow

```text
FraudDetectionService
  -> FraudRuleEvaluator
       -> FraudRedisCommand 생성(recordId 한 번 생성)
       -> FraudRedisResilienceExecutor
            -> CircuitBreaker
                 -> Retry
                      -> FraudRedisLuaExecutor
                           -> 기존 fraud_check.lua
       -> Lua 코드를 FraudEvaluationResult로 변환
  -> suspicious이면 보호 구간 밖에서 SUSPICIOUS_TRANSACTION
```

- `FraudRedisCommand`: 한 거래의 Redis key, 금액, 시각, 심야 여부, recordId를 보관한다.
- `FraudRedisLuaExecutor`: 기존 Lua Script를 Redis에 정확히 한 번 실행한다.
- `FraudRedisResilienceExecutor`: CircuitBreaker 바깥, Retry 안쪽 순서로 함수형 API를 조합한다.
- `FraudRedisFailureClassifier`: 원인 체인을 순회해 연결 실패와 명령 타임아웃만 판정한다.
- `FraudResilienceConfig`: Retry와 CircuitBreaker가 같은 예외 판정 규칙을 사용하게 한다.
- `FraudCircuitBreakerEventListener`: 상태 전환 로그와 Micrometer Counter를 기록한다.

### 17.3 Final result accounting

- Redis 첫 호출 실패 후 Retry 성공: Redis 2회, Circuit 성공 1건
- Redis Retry 최종 실패: Redis 2회, Circuit 실패 1건
- `OPEN`에서 차단: Redis 0회, not-permitted 1건
- 의심 거래 탐지: Redis 정상 결과이므로 Circuit 성공 1건, 실패 0건
- null/잘못된 Lua 결과: Retry 0회, Circuit 실패율 미포함, 거래는 Fail-Closed

CircuitBreaker가 Retry 전체 실행 시간을 감싸므로 느린 호출도 개별 Redis 시도가 아니라 한 거래의 최종
탐지 실행 시간을 기준으로 측정한다.

### 17.4 Configuration and override

버전 관리되는 `src/main/resources/config/application.properties`에 다음 기본값을 적용했다.

- Count-based Sliding Window 10
- 최소 호출 5
- 실패율 50%
- 느린 호출 200ms, 느린 호출률 50%
- Open 30초
- Half-Open 시험 거래 2건
- Open에서 Half-Open 자동 전환
- Retry 총 2회, 고정 50ms

모든 주요 값은 `FRAUD_CB_*`, `FRAUD_RETRY_*` 환경 변수로 덮어쓸 수 있다.

### 17.5 Metrics and events

- PASS: `resilience4j.circuitbreaker.state`
- PASS: `resilience4j.circuitbreaker.calls`
- PASS: `resilience4j.circuitbreaker.slow.calls`
- PASS: `resilience4j.circuitbreaker.not.permitted.calls`
- PASS: `fraud.circuitbreaker.transitions{from,to}`
- PASS: Prometheus, circuitbreakers, circuitbreakerevents endpoint 노출 설정
- PASS: Spring 설정에서 Window 10, 최소 5, 실패율 50%, Half-Open 2, Retry 2가 바인딩됨
- PASS: Spring Customizer에서 연결 실패는 Retry/Circuit 대상, Lua 결과 오류는 제외됨

### 17.6 State verification

- PASS: 최소 호출 수와 실패율 충족 후 `CLOSED -> OPEN`
- PASS: `OPEN` 상태에서 실제 Redis Executor 미호출
- PASS: Open 유지 시간 후 자동 `HALF_OPEN`
- PASS: Half-Open 동시 요청 3건 중 설정된 2건만 Redis 진입
- PASS: Half-Open 시험 요청 모두 성공하면 `CLOSED`
- PASS: Half-Open 시험 요청 실패하면 다시 `OPEN`
- PASS: 상태 전환 Counter와 구조화 로그

Half-Open 2건은 각각 Retry 2회를 가질 수 있으므로 Redis 실제 호출은 최대 4회다. 한 시험 거래의 첫 호출이
실패하고 두 번째가 성공하면 그 시험 거래는 성공 한 건으로 계산한다.

### 17.7 Fail-Closed and regression verification

- PASS: Redis 연결 실패 Retry 최종 실패를 `FRAUD_DETECTION_UNAVAILABLE`로 변환
- PASS: 의심 거래를 `SUSPICIOUS_TRANSACTION`으로 차단하고 Circuit 실패율에서 제외
- PASS: 지갑 송금 탐지 불가 30건에서 송·수신 잔액 변경 0건
- PASS: 카드 결제 탐지 불가 시 잔액 Lock, 출금, 카드 원장 호출 0건
- PASS: 누적 금액, 거래 빈도, 동일 금액 반복, 복수 사유와 위험 점수 Redis Lua 회귀 테스트
- PASS: Step 1 Advisory Lock, Step 2 멱등성 선별 회귀 테스트
- 전체 테스트 106개 중 7개는 기존 KMS 미기동으로 `AES 키 암호화에 실패했습니다`가 발생했다.
  이번 Resilience4j 변경과 무관한 기존 카드 관리·로그인 테스트다.

### 17.8 Operational boundaries

- CircuitBreaker 상태와 Sliding Window는 서버 인스턴스별 메모리에 존재하며 서버 간 공유되지 않는다.
- Resilience4j는 Redis 장애 중 불필요한 호출을 줄이고 제한된 복구 시험을 수행한다.
- Resilience4j는 잔액 정합성이나 거래 원자성을 보장하지 않는다.
- 금융 정합성은 PostgreSQL 트랜잭션, Advisory Lock, 잔액과 원장 동시 저장이 담당한다.
- Redis 장애 시 잔액 변경 전에 예외를 발생시키는 Fail-Closed 순서가 거래 차단을 담당한다.
- CircuitBreaker Health Indicator를 일반 liveness 재시작 조건으로 직접 사용하면 공통 Redis 장애 때 모든
  인스턴스가 동시에 재시작될 수 있다. liveness와 Redis 의존 상태를 분리해서 운영해야 한다.

## 18. Step 5 Implementation Approval Proposal

### 18.1 Scope and current limitation

대상은 Step 2와 동일하게 `Idempotency-Key`를 사용하는 즉시 지갑 송금이다. 예약 송금, 카드 결제,
환전의 기존 상태 모델을 하나의 공통 enum으로 합치지 않는다.

현재 실제 흐름은 다음과 같다.

```text
REQUIRES_NEW: REQUESTED 저장
  -> 자금 트랜잭션 시작
       -> PROCESSING 변경
       -> Fraud 검사
       -> 잔액 변경
       -> 원장 저장
       -> COMPLETED 변경
  -> 커밋
```

`PROCESSING`이 자금 트랜잭션 안에 있으므로 Fraud 실패, 잔액 부족, DB 오류 또는 서버 종료가 발생하면
`PROCESSING`도 함께 롤백된다. 이후 실패 기록까지 성공하면 `FAILED`가 남지만, 실패 기록 전 서버가
종료되면 DB에는 `REQUESTED`만 남는다. 따라서 현재 상태만으로는 다음 질문에 답할 수 없다.

- Fraud 검사 전에 멈췄는가, 검사 중에 멈췄는가?
- 자금 처리 단계에 진입했는가?
- 실패 기록 자체가 누락됐는가?
- 오래된 `REQUESTED/PROCESSING`이 실제 처리 중인가, 서버 종료로 고립됐는가?

### 18.2 Final state model

```text
REQUESTED -> VALIDATING -> PROCESSING -> COMPLETED
     |           |             |
     +-----------+-------------+-> FAILED
```

상태 의미:

| Status | Meaning |
| --- | --- |
| `REQUESTED` | 멱등성 키와 요청 본문을 DB에 접수했지만 검증을 시작하지 않음 |
| `VALIDATING` | 수신자·요청 조건과 Redis 이상 거래 검증 수행 중 |
| `PROCESSING` | 검증을 통과하고 지갑 Lock·잔액·환전·원장 트랜잭션 수행 중 |
| `COMPLETED` | 잔액 변경과 거래 원장 저장이 같은 DB 트랜잭션으로 커밋됨 |
| `FAILED` | 자금 트랜잭션이 커밋되지 않았고 실패 코드와 단계가 별도 커밋됨 |

허용 전이만 엔티티 메서드로 제공한다.

```java
markValidating(); // REQUESTED -> VALIDATING
markProcessing(); // VALIDATING -> PROCESSING
complete();       // PROCESSING -> COMPLETED
fail(...);        // REQUESTED/VALIDATING/PROCESSING -> FAILED
```

- `COMPLETED -> FAILED`, `FAILED -> PROCESSING`, 중복 `COMPLETED`는 거절한다.
- 상태 변경은 현재 예상 상태를 확인하며 `@Version`으로 동시 운영 작업과 실제 처리 충돌을 검출한다.
- 예약 송금의 별도 `WalletTransferStatus`는 변경하지 않는다.
- 사용되지 않는 `WalletTransactionStatus(PENDING/SUCCESS/FAILED)`는 제거한다.

### 18.3 Persistence fields

`WalletTransfer`에 다음 필드를 추가한다.

| Field | Purpose |
| --- | --- |
| `failureStage` | 어느 처리 구간에서 실패했는지 저장 |
| `retryable` | 운영상 새로운 거래로 재시도 가능한 유형인지 표시 |
| `validatingAt` | 검증 단계 진입 시각 |
| `processingAt` | 자금 처리 단계 진입 시각 |
| `completedAt` | 자금·원장 커밋 시각 |
| `failedAt` | 실패 기록 시각 |
| `attemptCount` | 실제 처리 권한을 얻어 검증을 시작한 횟수 |

기존 필드 의미:

- `createdAt`: `REQUESTED` 접수 시각으로 유지한다.
- `updatedAt`: 마지막 상태 전환 시각으로 사용한다.
- `failureCode`: 외부에 사용하는 안정적인 `ErrorCode.name()`을 저장한다.
- `version`: 낙관적 락과 운영 명령의 충돌 검출에 사용한다.

개인정보, 전화번호, 수신자 이름, 예외 원문과 Stack Trace는 상태 테이블에 추가 저장하지 않는다.

### 18.4 Failure stages

```java
public enum WalletTransferFailureStage {
    PARTICIPANT_VALIDATION,
    FRAUD_VALIDATION,
    LOCK_ACQUISITION,
    BALANCE_VALIDATION,
    FUNDS_AND_LEDGER_TRANSACTION,
    UNKNOWN
}
```

- `PARTICIPANT_VALIDATION`: 수신자나 지갑을 찾지 못함
- `FRAUD_VALIDATION`: 의심 거래 또는 Redis 탐지 불가
- `LOCK_ACQUISITION`: Advisory Lock 대기·중단 실패
- `BALANCE_VALIDATION`: 통화 잔액 없음 또는 잔액 부족
- `FUNDS_AND_LEDGER_TRANSACTION`: 잔액 변경과 원장 저장을 포함한 원자적 DB 처리 실패
- `UNKNOWN`: 분류되지 않은 런타임 오류

잔액 변경과 원장 저장은 같은 트랜잭션이며 JPA SQL 실행 시점도 커밋까지 지연될 수 있으므로, 실제로
구분할 수 없는 오류를 억지로 `MONEY_MOVEMENT`와 `LEDGER_PERSISTENCE`로 나누지 않는다. 두 작업을
`FUNDS_AND_LEDGER_TRANSACTION`으로 기록하고 전체 롤백 여부를 검증한다.

### 18.5 Retryable classification

`retryable`은 동일 멱등성 키를 자동 재실행한다는 뜻이 아니다. 운영자와 클라이언트가 **새로운 요청과
새로운 멱등성 키로 재시도할 수 있는 장애 유형인지** 판단하기 위한 정보다.

기본 분류:

| Error | Stage | Retryable |
| --- | --- | --- |
| `FRAUD_DETECTION_UNAVAILABLE` | `FRAUD_VALIDATION` | true |
| `LOCK_TIME_OUT`, `THREAD_INTERRUPTED` | `LOCK_ACQUISITION` | true |
| `SUSPICIOUS_TRANSACTION` | `FRAUD_VALIDATION` | false |
| `RECEIVER_NOT_FOUND`, `WALLET_NOT_FOUND` | `PARTICIPANT_VALIDATION` | false |
| `BALANCE_NOT_FOUND`, `BALANCE_NOT_AVAILABLE` | `BALANCE_VALIDATION` | false |
| `TRANSACTION_PROCESSING_FAILED` | `UNKNOWN` | false, 운영 점검 필요 |

이 매핑은 `WalletTransferFailureClassifier` 한 곳에서 관리한다.

### 18.6 Transaction boundaries and class flow

변경 후 호출 흐름:

```text
WalletFacadeService
  -> WalletTransferIdempotencyService.reserve
       -> REQUIRES_NEW: REQUESTED
  -> WalletTransferLifecycleService.markValidating
       -> REQUIRES_NEW: VALIDATING + validatingAt + attemptCount
  -> WalletService.validateImmediateTransfer
       -> 수신자 확인 + Fraud 검증
  -> WalletTransferLifecycleService.markProcessing
       -> REQUIRES_NEW: PROCESSING + processingAt
  -> WalletService.processImmediateTransfer
       -> 하나의 자금 DB transaction
       -> 지갑 Lock + 잔액 확인 + 환전
       -> 송·수신 잔액 변경
       -> transferId 원장 저장
       -> COMPLETED + completedAt
       -> commit
  -> 예외 발생
       -> REQUIRES_NEW: FAILED + stage + code + retryable + failedAt
```

클래스 변경:

1. `WalletTransferLifecycleService`

- 상태 전용 `REQUIRES_NEW` 트랜잭션을 제공한다.
- `markValidating`, `markProcessing`, `markFailed`를 담당한다.
- 자금 이동이나 Fraud 검사를 수행하지 않는다.

2. `WalletServiceImpl`

- 즉시 송금의 검증 단계와 자금 처리 단계를 분리한다.
- 검증 결과에는 수신 사용자 ID만 전달해 자금 단계에서 요청 이름·전화번호를 다시 해석하지 않게 한다.
- 자금 처리 메서드는 `PROCESSING` 상태만 허용하고 원장·잔액·`COMPLETED`를 같이 커밋한다.

3. `WalletFacadeService`

- 현재 처리 단계를 조정하고 예외 발생 시 `WalletTransferFailureClassifier` 결과를 실패 서비스에 전달한다.
- 상태 저장이 실패하면 원래 거래 예외를 덮어쓰지 않고 별도 오류 로그와 메트릭을 남긴다.

4. `WalletTransferFailureService`

- 단순 오류 코드 대신 `failureStage`, `retryable`, `failedAt`까지 별도 트랜잭션으로 저장한다.

### 18.7 Crash windows and durable result

| Crash point | Durable DB state | Money/ledger |
| --- | --- | --- |
| 예약 직후, 검증 전 | `REQUESTED` | 없음 |
| 검증 상태 커밋 후, Fraud 도중 | `VALIDATING` | 없음 |
| Fraud 성공 후 처리 상태 커밋 직후 | `PROCESSING` | 없음 |
| 자금 트랜잭션 도중 | `PROCESSING` | 전체 롤백, 없음 |
| 자금 트랜잭션 커밋 후 응답 유실 | `COMPLETED` | 정확히 한 건 |
| 정상 예외 후 실패 기록 성공 | `FAILED` | 전체 롤백, 없음 |
| 예외 직후 실패 기록 전 서버 종료 | 마지막 활성 상태 | 없음, stale 조회 대상 |

`COMPLETED` 전이는 잔액과 원장 트랜잭션 안에 그대로 둔다. 따라서 자금이 커밋됐는데 상태만
`PROCESSING`으로 남는 정상 코드 경로를 만들지 않는다.

### 18.8 Idempotency behavior after state expansion

기존 중복 정책을 유지하고 `VALIDATING`을 처리 중 상태에 추가한다.

| Existing state | Same request result |
| --- | --- |
| `REQUESTED`, `VALIDATING`, `PROCESSING` | `409 TRANSACTION_IN_PROGRESS` |
| `COMPLETED` | 기존 `transferId`, `COMPLETED` 반환 |
| `FAILED` | 저장된 기존 오류 반환, 동일 키 재실행 금지 |

새 상태 전환은 최초 DB 예약에 성공한 요청만 수행한다. 동시 중복 30건은 상태 전이와 Fraud 검사,
자금 이동을 다시 실행하지 않는다.

### 18.9 Status query contract

인증 사용자가 본인의 거래 처리 결과를 조회할 수 있게 한다.

```http
GET /api/v1/wallet/transfers/{transferId}
```

```json
{
  "transferId": "uuid",
  "status": "FAILED",
  "failureStage": "FRAUD_VALIDATION",
  "failureCode": "FRAUD_DETECTION_UNAVAILABLE",
  "retryable": true,
  "requestedAt": "...",
  "validatingAt": "...",
  "processingAt": null,
  "completedAt": null,
  "failedAt": "...",
  "lastUpdatedAt": "..."
}
```

- `senderUserId`가 인증 사용자와 일치하는 거래만 반환한다.
- 요청 해시, 멱등성 키, 내부 버전과 개인정보는 응답하지 않는다.
- 다른 사용자의 `transferId`는 존재 여부를 노출하지 않도록 동일한 NOT_FOUND로 응답한다.

### 18.10 Stale transaction operations

`WalletTransferOperationsService`에 다음 내부 조회를 추가한다.

```text
status in (REQUESTED, VALIDATING, PROCESSING)
and updatedAt < now - configuredThreshold
order by updatedAt asc
```

- 기본 stale 기준은 설정으로 분리하고 초기값은 5분으로 둔다.
- 운영 조회 결과는 `transferId`, 상태, 마지막 전환 시각, version만 포함한다.
- 자동 재실행이나 자동 실패 처리는 구현하지 않는다.
- 장시간 거래를 자동 `FAILED`로 바꾸면 아직 실행 중인 요청과 경합할 수 있으므로 운영 확인 후 별도
  복구 명령이 필요하다.
- 복구 명령을 이후 추가할 때는 예상 `version`, stale 기준, 원장 부재를 모두 확인해야 한다.

### 18.11 Ledger reconciliation invariant

`wallet_transfer_request`와 `wallet_transaction.transfer_id`를 다음 규칙으로 점검한다.

```text
COMPLETED
→ 같은 transferId의 원장 정확히 1건

REQUESTED / VALIDATING / PROCESSING / FAILED
→ 같은 transferId의 원장 0건
```

위 규칙을 검사하는 `WalletTransferReconciliationService`를 추가한다. 자동으로 잔액이나 상태를 수정하지
않고 불일치 건수와 transferId를 운영 로그·메트릭으로 노출한다.

### 18.12 Compensation decision

이번 즉시 지갑 송금은 다음 항목이 모두 하나의 PostgreSQL 트랜잭션이다.

```text
송신 잔액 차감
수신 잔액 증가
거래 원장 저장
COMPLETED 전이
```

정상 실패는 전체 롤백되므로 `COMPENSATION_REQUIRED`, `COMPENSATED` 상태를 추가하지 않는다.
외부 결제망이나 이미 커밋된 외부 효과가 도입될 때 별도 보상 모델을 설계한다.

### 18.13 Expected file scope

New:

- `WalletTransferFailureStage`
- `WalletTransferFailureClassifier`
- `WalletTransferLifecycleService`
- 상태 조회 DTO와 Query Service
- stale 운영 조회 Service와 설정
- 원장 대조 `WalletTransferReconciliationService`
- 상태 전이, 실패 단계, stale, reconciliation 테스트

Changed:

- `WalletTransfer`, `WalletTransferStatus`, `WalletTransferRepository`
- `WalletFacadeService`, `WalletService`, `WalletServiceImpl`
- `WalletTransferFailureService`, `WalletTransferIdempotencyService`
- `WalletController`, `ErrorCode`
- Step 2 멱등성 PostgreSQL 통합 테스트

Remove:

- 실제 엔티티에서 사용되지 않는 `WalletTransactionStatus`

Not changed:

- Redis Lua와 Resilience4j 설정
- Advisory Lock 키와 획득 순서
- 잔액·원장 PostgreSQL 원자적 트랜잭션
- 예약 송금 상태 모델
- 거래내역 Top-N 조회

### 18.14 Required automated tests

- 정상 거래의 `REQUESTED -> VALIDATING -> PROCESSING -> COMPLETED` 전이와 모든 시각
- Fraud 탐지 불가: `FAILED/FRAUD_VALIDATION/retryable=true`, 잔액·원장 0건
- 의심 거래: `FAILED/FRAUD_VALIDATION/retryable=false`, 잔액·원장 0건
- 수신자 없음: `FAILED/PARTICIPANT_VALIDATION`
- 잔액 부족: `FAILED/BALANCE_VALIDATION`, 자금 롤백
- Lock 실패: `FAILED/LOCK_ACQUISITION/retryable=true`
- 자금·원장 예외: `FAILED/FUNDS_AND_LEDGER_TRANSACTION`, 양쪽 잔액과 원장 전체 롤백
- 허용하지 않은 상태 전이와 중복 완료 거절
- 완료 거래를 실패로 바꾸지 못함
- 동시에 같은 상태를 변경할 때 `@Version` 충돌
- 동일 키 30건에서 상태 흐름, Fraud, 잔액, 원장 각 1회
- 상태 조회에서 본인 거래만 허용
- 오래된 활성 상태만 stale 조회되고 최신·완료·실패 거래는 제외
- `COMPLETED` 원장 1건, 비완료 원장 0건 reconciliation 정상
- 상태·원장 불일치 탐지
- 기존 Step 1~4 선별 회귀 테스트와 `git diff --check`

### 18.15 Approval gate

구현 전 다음 사항을 승인받는다.

- 즉시 송금에 `VALIDATING` 상태와 상태별 시각 추가
- 상태 변경을 별도 `REQUIRES_NEW`로 영속화
- 자금·원장·`COMPLETED`의 기존 단일 트랜잭션 유지
- 실패 단계 6종과 운영용 `retryable` 저장
- 동일 멱등성 키의 실패 거래 자동 재실행 금지 유지
- 본인 거래 상태 조회 API 추가
- stale 기준 기본 5분과 읽기 전용 운영 조회 추가
- 자동 복구·자동 재실행·보상 상태는 이번 단계에서 제외
- 완료 상태와 원장 존재 여부 대조 기능 추가

## 19. Step 5 Implementation Result

### 19.1 Implemented transaction flow

승인된 즉시 송금 경로를 다음과 같이 구현했다.

```text
WalletFacadeService
  -> WalletTransferIdempotencyService.reserve
       -> REQUIRES_NEW: REQUESTED 저장
  -> WalletTransferLifecycleService.startValidating
       -> REQUIRES_NEW: VALIDATING, validatingAt, attemptCount 저장
  -> WalletTransferValidationService.resolveReceiver
  -> WalletTransferValidationService.verifyFraud
  -> WalletTransferLifecycleService.startProcessing
       -> REQUIRES_NEW: PROCESSING, processingAt 저장
  -> WalletServiceImpl.transfer
       -> 하나의 PostgreSQL transaction
       -> Advisory Lock 획득
       -> 잔액 확인·변경
       -> transferId 거래 원장 저장
       -> COMPLETED, completedAt 저장
       -> commit
  -> 예외 발생 시 WalletTransferFailureService
       -> REQUIRES_NEW: FAILED, failureStage, failureCode, retryable, failedAt 저장
```

`PROCESSING` 진입은 자금 트랜잭션보다 먼저 별도 커밋되므로 서버가 자금 처리 중 종료되어도 마지막
진입 단계가 남는다. 잔액 변경, 원장 저장, `COMPLETED` 전이는 계속 하나의 PostgreSQL 트랜잭션에
있으므로 부분 성공은 허용하지 않는다.

### 19.2 Entity and failure classification

`WalletTransfer`에 다음 정보를 추가했다.

- 상태: `VALIDATING`
- 실패: `failureStage`, `failureCode`, `retryable`
- 시각: `validatingAt`, `processingAt`, `completedAt`, `failedAt`
- 처리 횟수: `attemptCount`
- 기존 `@Version` 유지

`WalletTransferFailureClassifier`가 예외 코드를 실패 단계와 재시도 가능 여부로 변환한다.

- Redis 탐지 불가와 Lock 대기·중단은 `retryable=true`다.
- 의심 거래, 참여자 없음, 잔액 없음·부족은 `retryable=false`다.
- 분류되지 않은 오류는 Facade가 알고 있는 현재 처리 단계를 유지하고 자동 재시도하지 않는다.
- `retryable=true`여도 동일 멱등성 키를 다시 실행하지 않으며 새 키를 사용한 새 요청 판단에만 쓴다.

### 19.3 Query and operations support

사용자 상태 조회 API를 추가했다.

```http
GET /api/v1/wallet/transfers/{transferId}
```

응답에는 상태, 실패 단계·코드, 재시도 가능 여부, 처리 횟수와 단계별 시각이 포함된다. 멱등성 키,
요청 해시, 내부 version은 노출하지 않는다. `senderUserId`까지 조회 조건에 포함해 다른 사용자의 거래는
동일한 `TRANSACTION_HISTORY_NOT_FOUND`로 처리한다.

`WalletTransferOperationsService`는 다음 읽기 전용 점검을 제공한다.

- 기본 5분 이상 갱신되지 않은 `REQUESTED/VALIDATING/PROCESSING` 조회
- `COMPLETED`는 원장 1건, 그 외 상태는 원장 0건인지 대조
- 불일치 로그와 `wallet.transfer.reconciliation.mismatches` Micrometer 카운터 기록
- 자동 상태 변경, 자동 송금 재실행, 자동 보상은 수행하지 않음

사용되지 않던 `WalletTransactionStatus` enum은 제거했다. Redis Lua와 Resilience4j 설정, Advisory Lock
키와 정렬 순서, 예약 송금 상태 모델, 거래내역 조회 구현은 변경하지 않았다.

### 19.4 Verification result

통과:

- `WalletTransferTest`: 정상·실패 상태 전이, 단계별 시각, 불법 전이, 완료 후 실패 금지
- `WalletTransferFailureClassifierTest`: Fraud·Lock·잔액·참여자 실패 분류와 retryable 정책
- `WalletTransferIdempotencyServiceTest`: 완료·실패 중복 요청 정책 유지
- `WalletTransferIdempotencyIntegrationTest`: 정상 상태 정보, 30건 중복 1회 처리, 실패 기록,
  Fraud 탐지 불가 시 잔액·원장 0건, 상태 조회 소유권
- `WalletTransferOperationsServiceTest`: stale 읽기 전용 조회, 원장 불일치와 메트릭
- 기존 지갑 동시성·자금/원장 롤백·Fraud·Resilience4j 선별 회귀 테스트

전체 테스트 결과:

```text
113 tests completed, 7 failed, 5 skipped
```

실패 7건은 이번 변경 이전부터 확인된 운영 KMS/AES 초기화 의존 테스트다.

- `CardServiceTest`: 3건
- `UserLoginScenarioTest`: 4건

Step 5 관련 테스트와 기존 Step 1~4 선별 회귀 테스트는 모두 통과했다.
