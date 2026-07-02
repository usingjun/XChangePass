# Agent Rules

## 목적

이 문서는 Codex가 XChangePass 프로젝트에서 작업할 때 반드시 따라야 하는 기본 규칙이다.

Codex는 코드를 수정하기 전에 기존 구조와 도메인 규칙을 먼저 읽고, 최소 변경으로 작업해야 한다.

## 읽는 순서

코드를 수정하기 전에 아래 문서를 순서대로 읽는다.

1. docs/agent/project-context.md
2. docs/agent/xchangepass-rules.md
3. docs/agent/prompt-cache-rules.md
4. docs/agent/task-template.md


## 기본 작업 규칙

* 기존 코드를 먼저 읽고 수정한다.
* 명시적으로 요청받지 않은 아키텍처 변경은 하지 않는다.
* 변경 범위는 작고 리뷰 가능하게 유지한다.
* 기존 패키지 구조, 네이밍, 코드 스타일을 따른다.
* 비즈니스 로직을 변경할 경우 테스트를 추가하거나 수정한다.
* secrets, env 파일, 배포 설정은 요청 없이는 수정하지 않는다.
* 사용자가 작업 중인 변경사항을 되돌리거나 덮어쓰지 않는다.
* 불필요한 리팩토링을 섞지 않는다.

## 고위험 영역

아래 영역은 변경 전에 반드시 기존 흐름을 먼저 분석한다.

* 송금 Transfer
* 지갑 Wallet
* 잔액 Balance
* 거래 원장 Ledger
* 거래 상태 Transaction Status
* 멱등성 Idempotency
* 이상거래 탐지 Fraud Detection
* Redis
* Circuit Breaker
* 복구 배치 Recovery Batch
* 거래내역 통합조회 Unified History Query
* 스키마 변경/마이그레이션
* Flyway 도입 시 Migration
* 외부 API 연동

## 검증

작업 후 가능한 경우 아래 명령을 기본 검증으로 실행한다.

```bash
./gradlew test
```

특정 기능을 수정한 경우 전체 테스트 대신 관련 테스트만 실행할 수 있다.

`./scripts/agent-check.sh`는 스크립트가 존재하는 경우에만 실행한다.

실행할 수 없다면 그 이유를 설명한다.

## 작업 완료 보고

작업 후 아래 내용을 정리한다.

1. 변경한 파일
2. 변경 내용
3. 변경 이유
4. 실행한 검증
5. 남은 위험 또는 추가 확인이 필요한 부분
