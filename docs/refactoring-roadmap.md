# XChangePass Financial Transaction Refactoring Roadmap

## Goal

금융 거래의 정합성을 우선하며, 조회 성능 최적화 구성요소가 핵심 거래 처리에 영향을 주지 않도록 구조를 개선한다.

구현 순서는 난이도와 영향 범위를 고려하여 `1 -> 3 -> 2`로 진행한다.

## 1. Transaction-scoped concurrency control

### Objective

- 충전, 출금, 송금의 잔액 정합성을 보장한다.
- 송금 시 두 지갑의 락 획득 순서를 고정하여 데드락 가능성을 낮춘다.
- 락은 비즈니스 로직 종료 시점이 아니라 RDB 트랜잭션 종료 시점까지 유지한다.
- 잔액 변경과 해당 RDB 거래내역 저장이 함께 커밋되거나 함께 롤백되도록 한다.

### Problem

- 동일 잔액을 여러 요청이 동시에 조회하고 수정하면 갱신 유실로 최종 잔액이 잘못될 수 있다.
- 양방향 송금이 동시에 발생하고 각 요청이 서로 다른 순서로 지갑 락을 획득하면 데드락이 발생할 수 있다.
- 기존 세션 범위 Advisory Lock은 서비스 메서드의 `finally`에서 해제되어 실제 RDB 커밋 전에 다른 요청이 진입할 수 있었다.
- 잔액 변경 이후 RDB 거래내역 저장이 실패하면 금액 변경만 남고 거래내역이 유실될 수 있다.

### Implementation

- PostgreSQL `pg_advisory_xact_lock`을 사용한다.
- 락 전략 선택용 `WalletServiceFactory`, `NamedLockWalletService`, `NamedLockRepository`를 제거한다.
- 단일 `WalletServiceImpl`이 지갑 거래를 처리하고 `TransactionAdvisoryLock`은 락 획득만 담당한다.
- 송금 시 지갑 ID 오름차순으로 락을 획득한다.
- 수동 락 해제를 제거하고 커밋 또는 롤백 시 PostgreSQL이 락을 자동 해제하도록 한다.
- 잔액 변경 메서드와 RDB 거래내역 저장을 동일한 Spring 트랜잭션에서 실행한다.

### Verification

- 100개 동시 송금 작업에서 모든 작업 성공 여부와 송신자·수신자 최종 잔액을 검증한다.
- 송금과 전액 출금이 동시에 발생하면 정확히 하나만 성공하고 가능한 최종 잔액 조합만 남는지 검증한다.
- 충전과 송금의 실행 순서를 통제하여 실패 거래가 잔액을 변경하지 않는지 검증한다.
- RDB 거래내역 저장 예외 발생 시 충전 금액과 신규 통화 잔액 생성이 함께 롤백되는지 검증한다.

## 3. Stateless fraud detection and alerting

### Objective

- Redis와 Lua Script를 이용해 이상 거래 규칙을 원자적으로 평가한다.
- 동시 요청 간 탐지 사유가 섞이지 않도록 탐지기를 무상태로 만든다.
- 명확한 이상 거래는 잔액 변경 전에 차단한다.
- Redis 장애 시 이상 거래 검증을 우회하지 않고 거래를 안전하게 차단한다.

### Problem

- 기존 탐지기는 싱글톤 서비스 필드에 마지막 탐지 사유를 저장하여 동시 요청 간 사유가 덮어써질 수 있었다.
- 정상 거래는 Lua Sorted Set에 저장했지만 의심 거래는 조기 반환되어 이후 탐지 이력에서 누락됐다.
- Redis List와 Sorted Set에 거래 이력을 중복 저장하여 탐지 데이터의 기준이 두 곳으로 나뉘어 있었다.
- 거래 유형을 문자열로 전달하여 오타가 발생하면 동일 사용자의 탐지 이력이 다른 Redis 키로 분리될 수 있었다.
- 기존 비동기 탐지 구조에서는 이상 거래가 감지되어도 거래가 이미 완료되어 차단할 수 없었다.
- Redis 장애 시 거래 허용과 전체 차단 중 어떤 정책을 사용할지 정의되어 있지 않았다.

### Implementation

- 탐지 여부와 `FraudReason`을 하나의 불변 결과 객체로 반환한다.
- 카드와 지갑 거래 유형을 `FraudTransactionType` enum으로 제한한다.
- Lua Script를 애플리케이션에서 한 번 생성한 후 재사용한다.
- Lua Script에서 만료된 이력을 제거하고 조회·규칙 평가·거래 시도 기록을 원자적으로 실행한다.
- 정상 및 의심 거래 시도를 모두 Redis Sorted Set에 기록한다.
- 요청 ID별 탐지 결과를 저장하여 Redis 재시도 시 거래 이력이 중복 집계되지 않도록 한다.
- 중복 Redis List 저장 경로를 제거하고 Lua Sorted Set을 탐지 이력의 단일 기준으로 사용한다.
- 누적 금액, 단시간 거래 빈도, 동일 금액 반복, 심야 거래를 평가한다.
- 모든 거래 금액을 설정된 기준 통화(KRW 기본값)로 정규화하여 서로 다른 통화의 거래를 동일 기준으로 평가한다.
- Lua Script가 충족한 모든 탐지 사유를 반환하고, 사유별 점수를 합산해 운영 알림에 위험 점수를 포함한다.
- 지갑 송금과 카드 결제는 DB 잔액 락을 획득하기 전에 동기로 검증하고, 의심 거래는 예외를 발생시켜 잔액 변경을 차단한다.
- Redis 장애 시 동일 요청 ID로 백오프 재시도하고, 연속 장애가 임계치를 넘으면 Circuit Breaker를 열어 Redis 부하를 제한한다.
- Redis 장애가 복구되지 않으면 `503 Service Unavailable`로 거래를 차단하는 fail-closed 정책을 적용한다.
- 이상 거래 및 Redis 장애 운영 알림은 별도 비동기 서비스에서 전송한다.

### Policy configuration

탐지 기준과 장애 대응 정책은 `fraud.policy` 설정으로 운영 환경별 조정이 가능하다.

```yaml
fraud:
  policy:
    base-currency: KRW
    total-amount-limit: 500000
    frequency-limit: 5
    accumulation-window-seconds: 600
    frequency-window-seconds: 300
    history-ttl-seconds: 7200
    night-start: "02:30"
    night-end: "03:30"
    max-attempts: 2
    retry-backoff-millis: 50
    circuit-failure-threshold: 3
    circuit-open-millis: 30000
    total-amount-score: 50
    frequency-score: 40
    repeated-amount-score: 30
    night-time-score: 20
```

### Verification

- 단일 거래는 정상으로 평가되는지 검증한다.
- 누적 금액, 단시간 거래 빈도, 동일 금액 반복 규칙과 구체적인 탐지 사유를 검증한다.
- 복수 규칙을 동시에 충족하면 모든 사유와 합산 위험 점수를 반환하는지 검증한다.
- 외화 거래 금액이 기준 통화로 정규화되는지 검증한다.
- 의심 거래도 Sorted Set 이력에 기록되는지 검증한다.
- 정상 거래는 통과하고, 의심 거래는 차단하면서 탐지 사유를 포함한 알림을 전송하는지 검증한다.
- Redis 장애 시 백오프 재시도 후 거래를 차단하고, Circuit Breaker가 추가 Redis 호출을 막는지 검증한다.
- 의심 거래 및 Redis 장애 시 송신자·수신자 잔액이 변경되지 않는지 검증한다.

## 2. RDB transaction history and MongoDB read model

### Core principle

- 카드, 지갑, 환전 RDB 거래내역이 각 도메인의 유일한 정합성 기준(source of truth)이다.
- 거래 핵심 속성은 JSONB가 아닌 명시적 RDB 컬럼과 타입으로 관리한다.
- 잔액 변경과 해당 도메인의 RDB 거래내역 저장은 하나의 트랜잭션으로 처리한다.
- MongoDB는 통합 거래내역 조회 성능을 위한 파생 조회 모델이다.
- MongoDB 반영 실패는 거래 실패나 거래 원장 유실로 이어지지 않아야 한다.
- MongoDB 조회 모델은 세 RDB 거래내역으로부터 다시 생성할 수 있어야 한다.

### Implementation

- 카드, 지갑, 환전 거래를 각각의 RDB 테이블에 명시적 컬럼으로 저장한다.
- MongoDB 문서 ID를 `거래유형:RDB 거래 ID`로 구성하여 조회 모델 반영을 멱등하게 만든다.
- 각 거래 테이블에서 MongoDB 반영 상태와 재시도 횟수를 관리한다.
- 스케줄러가 세 RDB 거래내역을 읽어 하나의 MongoDB 통합 조회 모델로 반영한다.
- RabbitMQ DLQ와 거래내역 저장용 RabbitMQ 흐름을 제거한다.
- 거래내역 저장 실패는 금융 거래 트랜잭션을 롤백한다.
- MongoDB 조회 모델 반영 실패는 실제 거래를 롤백하지 않고 지수 백오프로 반복 재시도한다.
- 거래내역 저장 및 MongoDB 반영 실패에 대한 DLQ·Slack 알림을 제거한다.

## Progress

- [x] 1. Transaction-scoped concurrency control
- [x] 3. Stateless fraud detection and alerting
- [x] 2. RDB transaction history and MongoDB read model

## Verification

- `./gradlew compileJava compileTestJava`
- `./gradlew test --tests 'bumblebee.xchangepass.domain.transaction.entity.ProjectionStateTest'`
- 전체 통합 테스트는 Docker/Testcontainers 및 로컬 PostgreSQL 연결이 가능한 환경에서 다시 실행한다.
