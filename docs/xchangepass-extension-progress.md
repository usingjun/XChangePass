# XChangePass Extension Progress

이 문서는 원문 과제 범위와 현재 구현 상태를 맞춰 보기 위한 작업 기준 문서다.
앞으로 작업 전에는 이 문서를 먼저 확인하고, 작업 후에는 이 문서를 갱신한다.

## 원문 구현 순서

1. 상태 이벤트 수집·집계·조회
2. Spring Batch 병렬 처리·재시작 가능 구조
3. 외부 시스템 연동 장애/동기화 구조
4. 운영자용 장애/작업 Admin
5. 검증 수치 뽑기

## 현재 진행 상태 요약

### 1. 상태 이벤트 수집·집계·조회

상태: 부분 완료

현재 완료된 것:

- `transaction_status_event` 저장 구조 추가
- 장애 추적용 핵심 이벤트 저장
- 실패 시 `failureStage`, `errorCode`, `retryable` 기록
- Redis 장애 차단/중복 요청/장기 미완료 복구/운영 예외 이벤트 기록
- 거래별 timeline 조회 API 추가

현재 일부 완료로 보는 이유:

- 원문 1번 완료 기준에는 `정상 거래 이벤트 순서 저장`이 포함되어 있다.
- 하지만 구현 중 범위를 줄여, 모든 정상 상태 전이를 다 남기지 않고 장애 추적에 필요한 핵심 이벤트만 남기기로 했다.
- 원문 1번에는 `최근 상태 분포 조회`, `실패 단계별 집계 조회`, `지연 거래 조회`도 포함되어 있다.
- 현재는 거래별 timeline 중심이고, 전체 분포/실패 단계/지연 조회는 아직 원문 1번 기준으로는 남아 있다.

정리:

- 축소 합의한 1-1 장애 추적 timeline 기준으로는 완료.
- 원문 1번 전체 기준으로는 부분 완료.

### 2. Spring Batch 병렬 처리·재시작 가능 구조

상태: 현재 합의 범위 완료, 원문 전체 기준 부분 완료

현재 완료된 것:

- `transaction_monitoring_summary_hourly` 저장 구조 추가
- `monitoring_batch_execution_history` 저장 구조 추가
- 시간별 summary 생성
- 같은 시간대를 다시 집계해도 중복 집계가 생기지 않도록 upsert 구조 적용
- Java grouping과 DB `GROUP BY` 비교
- 100만 건 기준 성능 측정
- `BTREE (occurred_at, id)` 방향으로 인덱스 후보 결정
- 운영 조회는 원본 event scan보다 summary/DB 집계 중심으로 가는 방향 확인
- 100만 건 기준 DB `GROUP BY`가 Java grouping보다 약 5.06배 빠른 것을 확인
- 현재 규모에서는 Spring Batch chunk/partitioning보다 DB 집계 최적화를 우선 적용하기로 판단

현재 일부 완료로 보는 이유:

- 원문 2번은 명시적으로 Spring Batch 기반 구조를 요구한다.
- 아직 Spring Batch `Job`, `Step`, `JobRepository` 기반 재시작 구조는 구현하지 않았다.
- 아직 chunk reader/processor/writer 구조가 아니다.
- 아직 partitioning 또는 multi-thread step 비교 구현은 하지 않았다.
- 아직 배치 실행 중 관제 조회 API p95 영향 측정은 하지 않았다.

정리:

- 집계 테이블, upsert, DB grouping, 100만 건 튜닝 사이클은 완료.
- 현재 합의한 2번 성능 개선 사이클은 마무리한다.
- 원문 2번의 Spring Batch 병렬 처리·재시작 가능 구조 전체 기준으로는 부분 완료.
- 현재 판단은 Spring Batch를 바로 구현하지 않고 보류한다.
- 이유는 100만 건 기준 DB `GROUP BY`가 566ms로 충분히 빠르고, Java grouping 2866ms 대비 명확한 개선이 확인됐기 때문이다.
- chunk/partitioning은 DB 집계로도 부족하거나, 실행 시간이 운영 SLA를 넘거나, 실패 후 재시작을 Spring Batch 메타데이터로 관리해야 할 필요가 명확해질 때 진행한다.
- DB partitioning도 지금은 보류한다. 이벤트 테이블이 수천만~수억 건 규모로 커지거나 보관/삭제/아카이빙 정책이 필요해질 때 PostgreSQL range partitioning을 별도 단계로 검토한다.

### 3. 외부 시스템 연동 장애/동기화 구조

상태: 3-5 검증 완료

원문 3번을 현재 XChangePass에 맞춰 환율 API 동기화 안정화로 적용한다.

해야 할 것:

- `exchange_rate_sync_history`
- `exchange_rate_sync_snapshot`
- `exchange_rate_sync_failure`
- 환율 API 응답 snapshot 저장
- 환율 응답 validate
- 실패 응답 failure 격리
- 전체 검증 성공 시에만 `exchange_rate_temp` 저장 후 active `exchange_rate` swap
- 실패 시 기존 active `exchange_rate` 유지

구현 단계 명세:

1. 환율 동기화 이력/스냅샷/실패 격리 테이블 추가
   - `exchange_rate_sync_history`
   - `exchange_rate_sync_snapshot`
   - `exchange_rate_sync_failure`
   - 기존 `ExchangeService` 동작과 active swap 흐름은 아직 변경하지 않는다.
   - 완료: sync history/snapshot/failure 엔티티와 repository를 추가했고 저장/조회 테스트를 통과했다.

2. 환율 응답 검증기 추가
   - `ExchangeRateResponse`가 active 후보로 적합한지 검증한다.
   - null response, baseCurrency 불일치, `conversion_rates` null/empty, 필수 통화 누락, 0 이하 환율값을 실패로 본다.
   - 3-2 완료: `ExchangeRateResponseValidator`와 검증 결과 객체를 추가했고 validator 단위 테스트를 통과했다.

3. Snapshot/failure 기록 서비스 추가
   - 환율 API 성공 응답 원문 payload와 payload hash를 snapshot으로 저장한다.
   - API 호출 실패/검증 실패는 failure로 격리한다.
   - 이 단계에서도 active `exchange_rate`는 변경하지 않는다.
   - 3-3 완료: `ExchangeRateSyncRecordService`를 추가했고 history/snapshot/failure 기록 테스트를 통과했다.

4. 기존 환율 동기화 흐름 연결
   - `ExchangeService.fetchAndSaveAllExchangeRates()` 흐름에 sync history/snapshot/validation/failure를 연결한다.
   - 전체 baseCurrency 검증 성공 시에만 `exchange_rate_temp` 저장 후 active swap을 수행한다.
   - 일부라도 실패하면 swap하지 않고 기존 active `exchange_rate`를 유지한다.
   - 구현 방식은 baseCurrency 하나를 처리하는 `fetchValidateAndSaveExchangeRate(...)` private 메서드를 두는 방식으로 진행한다.
   - 3-4 완료: 전체 성공/검증 실패/API 실패/temp 저장 실패/swap 실패 단위 테스트를 통과했다.

5. 장애 시나리오 테스트
   - 특정 baseCurrency API 실패 시 active 유지
   - `conversion_rates` null 시 active 유지
   - 필수 통화 누락 시 active 유지
   - 0 이하 환율값 시 active 유지
   - swap 실패 시 history/failure 기록
   - 3-5 완료: API 실패, null `conversion_rates`, 필수 통화 누락, 0 이하 환율값, temp 저장 실패, swap 실패, 실패 동기화 후 기존 active 환율 조회 유지 테스트를 통과했다.

### 4. 운영자용 장애/작업 Admin

상태: 4-4 transaction dry-run 완료, recovery case 선행 구현 stash 보관

4-1 완료:

- `admin_operation_audit_log` 엔티티 추가
- 운영자 action/target/result enum 추가
- audit log repository 추가
- audit log service 추가
- success/failure/blocked 액션 기록 테스트 통과
- adminId/target 기준 조회 테스트 통과

4-2 완료:

- `GET /admin/operations/recovery-cases` 조회 API 추가
- recovery case 필터 조회 service 추가
- caseType/severity/caseStatus/transactionId/detectedAt range/limit 필터 지원
- 상태 변경 API는 추가하지 않음
- stash 선행 구현 중 조회 query/response/service 구조만 참고하고 acknowledge/resolve는 제외

4-3 완료:

- `POST /admin/operations/recovery-cases/{caseId}/acknowledge` 추가
- `POST /admin/operations/recovery-cases/{caseId}/resolve` 추가
- recovery case 상태 변경 시 audit log success/blocked 기록
- 중복/잘못된 상태 전이 차단
- 상태 변경용 `findByIdForOperation` 조회 추가

4-4 완료:

- `POST /admin/operations/transactions/{transactionId}/dry-run` 추가
- WalletTransfer 상태와 wallet_transaction 원장 count 기준 안전 처리 가능 여부 판단
- REQUESTED/VALIDATING + 원장 없음인 경우만 `MARK_FAILED_ALLOWED`
- PROCESSING 또는 원장 존재 시 `KEEP_OPERATIONAL_EXCEPTION`
- COMPLETED/FAILED는 `NO_ACTION`
- dry-run 수행 시 audit log success 기록

현재 stash로 보관한 것:

- recovery case 목록 조회
- recovery case acknowledge
- recovery case resolve

주의:

- 이 작업은 원문 기준 4번 일부다.
- 원문 순서상 현재 4번을 진행 중이다.
- 4번 선행 구현분은 `stash@{Sun Jun 28 01:33:58 2026}: On develop: stash admin recovery case work`에 보관했다.
- stash 선행 구현분은 정식 4번 단계에서 필요한 범위만 다시 검토해서 반영한다.

### 5. 검증 수치 뽑기

상태: 현재 합의 범위 완료, 원문 전체 기준 부분 완료

현재 완료된 것:

- transaction status event 100만 건 기준 집계 성능 측정
- Java grouping vs DB `GROUP BY` 비교
- BTREE/BRIN 후보 비교
- 10만 건 summary baseline 정리
- 100만 건 DB `GROUP BY` 약 5.06배 개선 수치 정리
- 환율 API 동기화 장애 시나리오 검증 정리
- 운영자 recovery case/dry-run 안전 조치 검증 정리
- 최종 검증 문서 작성: `docs/xchangepass-extension-validation.md`

아직 남은 것:

- Spring Batch 단일/병렬 처리 시간
- Spring Batch 재시작 검증
- 배치 실행 중 관제 조회 p95 영향

현재 남은 항목을 아직 진행하지 않는 이유:

- Spring Batch chunk/partitioning은 현재 구현하지 않기로 판단했다.
- 100만 건 기준 DB `GROUP BY`가 충분히 빠르고 Java grouping 대비 약 5.06배 개선됐다.
- p95 영향 측정은 실제 운영 부하 모델과 API 호출 시나리오가 필요하므로 별도 성능 검증 단계로 분리한다.

정리:

- 현재 합의한 5번 검증 문서화 기준으로는 완료.
- 원문 5번의 Spring Batch 병렬/재시작/p95 검증까지 포함하면 부분 완료.

## 다음 작업 원칙

- 원문 순서를 기준으로 진행한다.
- 작업 전에 이 문서를 먼저 확인한다.
- 새 기능을 임의로 끼워 넣지 않는다.
- 단계별 명세 후 사용자가 허락하면 구현한다.
- 작업 후 이 문서를 갱신한다.
