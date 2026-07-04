# 송금 API k6 단독 baseline 결과

## 1. 목적

송금 API 단독 부하 기준선을 확보했다.

이 결과는 이후 `GROUP_BY`, `MATERIALIZED_VIEW`, `SUMMARY` 통계 API와 송금 API를 함께 실행하는 혼합 부하 테스트에서 송금 API p95/p99가 얼마나 영향을 받는지 판단하기 위한 기준선이다.

이번 결과는 송금 API 단독 부하이며, 통계 API 혼합 부하 결과가 아니다. Primary/Replica 판단도 아직 보류한다.

## 2. 송금 API 기준

| 항목 | 값 |
| --- | --- |
| Endpoint | `PUT /api/v1/wallet/transfer` |
| 인증 방식 | `accessToken` 쿠키 |
| 멱등성 방식 | 요청마다 unique `Idempotency-Key` 헤더 사용 |
| 성공 기준 | HTTP 200 + 응답 상태 `COMPLETED` |
| 요청 통화 | `KRW -> KRW` |
| 송금액 | `1.00` |

요청 본문은 실제 `WalletTransferRequest` 구조를 기준으로 구성했다.

```json
{
  "receiverName": "수신자명",
  "receiverPhoneNumber": "010-0000-0000",
  "transferAmount": "1.00",
  "fromCurrency": "KRW",
  "toCurrency": "KRW",
  "transferDatetime": null,
  "transferType": "GENERAL"
}
```

## 3. 실행 조건

| 항목 | 값 |
| --- | --- |
| BASE_URL | `http://localhost:8080` |
| VUs | 10 |
| Duration | 30s |
| Sleep | 1s |
| Sender/receiver pair | 400쌍 |
| 요청 수 | 300 |
| Raw 결과 위치 | `build/perf/k6/transaction-transfer-baseline.json`, `build/perf/k6/transaction-transfer-baseline.txt` |

송금 API는 이상거래 탐지의 사용자별 빈도 제한을 통과해야 한다. 따라서 단일 sender를 반복 사용하는 방식은 유효한 기준선이 아니며, benchmark 전용 sender/receiver pair를 충분히 분산했다.

## 4. 데이터 준비

`tools/perf/run-transaction-transfer-k6-seed.sh`로 benchmark 전용 데이터를 준비했다.

- sender/receiver 사용자 400쌍 생성
- sender/receiver 지갑 생성
- sender `KRW` 잔액 충분히 적재
- receiver `KRW` 잔액 row 생성
- sender별 access token 생성
- k6 실행용 auth env 생성

auth env와 raw k6 결과는 로컬 산출물이므로 커밋하지 않는다.

## 5. 실행 명령

```bash
TRANSACTION_TRANSFER_K6_PAIRS=400 \
  tools/perf/run-transaction-transfer-k6-seed.sh

BASE_URL=http://localhost:8080 \
AUTH_ENV=build/perf/k6/transaction-transfer-auth.env \
K6_VUS=10 \
K6_DURATION=30s \
K6_SLEEP=1 \
  tools/perf/run-transaction-transfer-k6-baseline.sh
```

## 6. 결과 요약

| Scenario | avg | p90 | p95 | p99 | max | requests | error rate |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| TRANSFER_BASELINE | 16.50ms | 19.61ms | 21.49ms | 29.20ms | 29.30ms | 300 | 0.00% |

## 7. 해석

송금 API 단독 기준선은 p95 21.49ms, p99 29.20ms로 측정됐다.

모든 요청은 HTTP 200과 `COMPLETED` 응답을 반환했다. 잔액 부족, 멱등성 충돌, 수신자 조회 실패, 이상거래 차단으로 인한 실패는 최종 실행에서 발생하지 않았다.

초기 실행에서는 sender 20쌍을 빠르게 반복 사용해 이상거래 빈도 제한에 걸렸다. 이는 송금 비즈니스 로직 문제가 아니라 benchmark 데이터 분산 조건 문제였으므로, 최종 실행에서는 sender/receiver pair를 400쌍으로 늘리고 요청을 균등 분산했다.

이번 결과는 송금 API 단독 baseline이다. 통계 조회가 송금 API에 미치는 영향은 아직 측정하지 않았다.

## 8. 다음 단계

1. 송금 API + `GROUP_BY` 통계 API 혼합 부하를 측정한다.
2. 송금 API + `MATERIALIZED_VIEW` 통계 API 혼합 부하를 측정한다.
3. 송금 API + `SUMMARY` 통계 API 혼합 부하를 측정한다.
4. 혼합 부하에서 송금 API p95/p99가 baseline 대비 얼마나 증가하는지 비교한다.
5. 혼합 부하 결과와 DB 관측 결과를 함께 보고 Primary/Replica 필요성을 판단한다.

## 9. 포트폴리오 반영 가능 문장

통계 조회가 송금 API에 미치는 영향을 판단하기 위해 먼저 송금 API 단독 k6 baseline을 측정하고, 이후 혼합 부하와 비교할 기준선을 확보했다.

송금 API의 단독 p95/p99를 기준으로 통계 조회 방식별 혼합 부하 영향을 비교하는 검증 흐름을 설계했다.
