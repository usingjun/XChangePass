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
| 3 | 거래내역 통합조회 최적화 | Drafted | Checked | Pending | Blocked by approval | Pending |
| 4 | Redis 이상 거래 장애 대응 | Drafted | Checked | Pending | Blocked by approval | Pending |
| 5 | 거래 상태 및 실패 추적 | Drafted | Checked | Pending | Blocked by approval | Pending |

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

- `WalletTransactionStatus`에 `PENDING`, `SUCCESS`, `FAILED`가 선언돼 있다.
- 환전과 예약 송금에는 각각 별도의 상태 모델이 있다.

Gaps and expected scope:

- `WalletTransactionStatus`는 `WalletTransaction` 엔티티와 송금 처리 흐름에서 사용되지 않는다.
- 즉시 송금 요청을 나타내는 Aggregate와 `REQUESTED`, `VALIDATING`, `PROCESSING`, `COMPLETED`,
  `FAILED` 상태 전이가 없다.
- 실패 상태를 자금 트랜잭션 롤백 이후 별도 트랜잭션으로 저장하는 경계가 없다.
- 실패 단계, 오류 코드, 재시도 가능 여부, 상태 시각, 낙관적 락 버전이 없다.
- 거래 원장에 `transferId` 유니크 참조가 없어 요청 상태와 실제 자금 이동을 직접 대조할 수 없다.
- 장시간 `PROCESSING` 거래 조회와 운영 점검 흐름이 없다.

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
