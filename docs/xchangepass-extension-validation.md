# XChangePass Extension Validation

검증일: 2026-06-29

이 문서는 XChangePass 확장 작업의 최종 검증 수치를 정리한다.
원문 5번 `검증 수치 뽑기`에 해당하며, 현재 합의한 구현 범위 기준으로 작성한다.

## 검증 범위

현재 검증 대상은 다음 네 영역이다.

- 거래 상태 이벤트 기반 장애 추적
- 시간별 거래 모니터링 summary 집계
- 환율 API 동기화 안전성
- 운영자 장애 작업 Admin 안전장치

원문에 있던 Spring Batch chunk/partitioning, multi-thread step, 배치 재시작 검증은 현재 구현 범위에서 제외했다.
100만 건 기준 DB 집계가 충분히 빠르다고 판단했기 때문에, Spring Batch 병렬화는 필요성이 명확해질 때 진행한다.

## 1. 거래 모니터링 집계 성능

### 100만 건 Java grouping vs DB GROUP BY

기준 문서:

- `docs/benchmarks/transaction-monitoring-aggregation-comparison-2026-06-27.md`
- `docs/benchmarks/transaction-monitoring-aggregation-comparison-2026-06-27.csv`

측정 조건:

- 이벤트 수: 1,000,000건
- 집계 범위: 2026-06-26 00:00 ~ 2026-06-27 00:00
- 버킷: 1시간
- 인덱스: `BTREE (occurred_at, id)`

결과:

| 방식 | Source | Summary | Diff | Summary rows | Elapsed ms | Events/sec |
|---|---:|---:|---:|---:|---:|---:|
| Java grouping | 1,000,000 | 1,000,000 | 0 | 336 | 2,866 | 348,918.35 |
| DB GROUP BY | 1,000,000 | 1,000,000 | 0 | 336 | 566 | 1,766,784.45 |

판단:

- DB `GROUP BY`가 Java grouping보다 약 5.06배 빠르다.
- Source count와 summary count가 모두 1,000,000으로 일치했고, diff는 0이다.
- 운영 관제성 집계는 Java 메모리 집계보다 DB 집계를 기본 경로로 채택한다.
- Java grouping은 정확성 비교/테스트용 fallback 성격으로 유지한다.

## 2. 인덱스 후보 비교

기준 문서:

- `docs/benchmarks/transaction-monitoring-index-comparison-2026-06-27.md`
- `docs/benchmarks/transaction-monitoring-index-comparison-2026-06-27.csv`

측정 조건:

- 이벤트 수: 1,000,000건
- 집계 범위: 2026-06-26 00:00 ~ 2026-06-27 00:00
- 측정 방식: Java grouping baseline에서 PostgreSQL 인덱스 후보 비교

결과:

| 인덱스 | Source | Summary | Diff | Elapsed ms | Events/sec |
|---|---:|---:|---:|---:|---:|
| 추가 인덱스 없음 | 1,000,000 | 1,000,000 | 0 | 2,822 | 354,358.61 |
| BRIN | 1,000,000 | 1,000,000 | 0 | 2,537 | 394,166.34 |
| BTREE | 1,000,000 | 1,000,000 | 0 | 2,319 | 431,220.35 |
| BRIN + BTREE | 1,000,000 | 1,000,000 | 0 | 2,377 | 420,698.36 |

판단:

- 현재 집계 사이클 전체 시간 기준으로는 `BTREE (occurred_at, id)`가 가장 안정적이다.
- 단일 시간대 `EXPLAIN`에서는 BRIN이 선택되는 케이스가 있었지만, 전체 집계 시간에서는 BTREE가 가장 좋았다.
- 현재 운영 기준 인덱스는 BTREE로 확정한다.
- BRIN은 대량 append-only 이벤트 테이블이 수천만~수억 건으로 커지고, 저장 공간/넓은 범위 스캔이 중요해질 때 재검토한다.

## 3. 10만 건 baseline

기준 문서:

- `docs/benchmarks/transaction-monitoring-summary-2026-06-27.md`
- `docs/benchmarks/transaction-monitoring-summary-2026-06-27.csv`

결과:

| 항목 | 값 |
|---|---:|
| Source count | 100,000 |
| Summary count | 100,000 |
| Diff | 0 |
| Total elapsed ms | 878 |
| Avg elapsed ms/hour | 36.58 |
| Max elapsed ms/hour | 106 |
| Avg events/sec | 113,895.22 |

판단:

- 10만 건 기준 summary 정확성은 source/summary count diff 0으로 확인했다.
- 이후 100만 건 비교에서 DB `GROUP BY` 방식으로 개선 방향을 확정했다.

## 4. 환율 API 동기화 안전성 검증

검증 대상:

- `ExchangeRateSyncRepositoryTest`
- `ExchangeRateResponseValidatorTest`
- `ExchangeRateSyncRecordServiceTest`
- `ExchangeServiceSafeSyncTest`

검증한 장애 시나리오:

| 시나리오 | 기대 결과 |
|---|---|
| API 호출 실패 | failure 기록, active 환율 유지 |
| `conversion_rates` null | validation failure 기록, active 환율 유지 |
| 필수 통화 누락 | validation failure 기록, active 환율 유지 |
| 0 이하 환율값 | validation failure 기록, active 환율 유지 |
| temp 저장 실패 | save failure 기록, active 환율 유지 |
| swap 실패 | swap failure 기록, active 환율 유지 |
| 전체 baseCurrency 성공 | temp 저장 후 active swap |

판단:

- 외부 환율 API 응답은 바로 active 환율로 반영하지 않는다.
- snapshot 저장, 응답 검증, failure 격리를 거친 뒤 전체 검증 성공 시에만 active swap한다.
- 실패 시 기존 active 환율을 유지하므로 외부 API 장애가 내부 거래 검증 기준을 오염시키지 않는다.

## 5. 운영자 장애 작업 안전성 검증

검증 대상:

- `AdminOperationAuditLogServiceTest`
- `RecoveryCaseOperationServiceTest`
- `TransactionOperationSafetyServiceTest`

검증한 항목:

| 영역 | 검증 내용 |
|---|---|
| Audit log | success/failure/blocked 결과 기록 |
| Recovery case 조회 | caseType, severity, status, transactionId, detectedAt range, limit 필터 |
| Recovery case acknowledge | open 상태만 acknowledge 허용, 중복/잘못된 상태 전이 차단 |
| Recovery case resolve | open/acknowledged 상태만 resolve 허용, resolved 상태 재처리 차단 |
| Transaction dry-run | 원장 존재/PROCESSING/COMPLETED/FAILED 안전 판단 |

Transaction dry-run 판단 기준:

| 상태/조건 | 결과 |
|---|---|
| `REQUESTED` 또는 `VALIDATING`, 원장 없음 | `MARK_FAILED_ALLOWED` |
| 원장 존재 | `KEEP_OPERATIONAL_EXCEPTION` |
| `PROCESSING` | `KEEP_OPERATIONAL_EXCEPTION` |
| `COMPLETED` | `NO_ACTION` |
| `FAILED` | `NO_ACTION` |

판단:

- 운영자 조치는 상태 전이를 직접 수행하기 전에 audit log와 dry-run을 통해 검토 가능하다.
- 이미 원장이 존재하거나 자금 처리 단계에 들어간 거래는 수동 실패 처리 후보에서 제외한다.
- 완료/실패 거래는 추가 조치가 필요 없는 대상으로 분리한다.

## 6. 실행한 검증 명령

다음 테스트 묶음이 통과했다.

```bash
./gradlew test \
  --tests TransactionStatusHourlySummaryServiceTest \
  --tests TransactionStatusEventDatasetFactoryTest \
  --tests ExchangeRateSyncRepositoryTest \
  --tests ExchangeRateResponseValidatorTest \
  --tests ExchangeRateSyncRecordServiceTest \
  --tests ExchangeServiceSafeSyncTest \
  --tests AdminOperationAuditLogServiceTest \
  --tests RecoveryCaseOperationServiceTest \
  --tests TransactionOperationSafetyServiceTest
```

추가 확인:

```bash
./gradlew compileJava
git diff --check
```

모두 성공했다.

## 최종 정리

현재 합의 범위 기준으로는 기능 구현과 핵심 검증이 완료됐다.

포트폴리오/면접에서는 다음처럼 정리한다.

> 거래 처리 테이블에 직접 운영 조회를 붙이지 않고, 장애 추적 이벤트와 시간별 summary read model을 분리했다.
> 100만 건 기준 Java 메모리 집계와 DB `GROUP BY` 집계를 비교했고, DB 집계가 약 5.06배 빨라 DB 집계와 BTREE 인덱스 경로를 채택했다.
> 외부 환율 API는 snapshot/validation/failure 격리 후 전체 검증 성공 시에만 active swap하도록 바꿔, 외부 장애가 내부 환율 기준을 오염시키지 않게 했다.
> 운영자 작업은 audit log, recovery case 상태 전이 차단, transaction dry-run으로 위험 조치를 사전에 차단하도록 구성했다.
