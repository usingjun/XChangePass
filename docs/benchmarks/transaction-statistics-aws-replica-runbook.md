# AWS EC2 3대 거래 통계 Read Replica 재현 runbook

## 1. 목적

이 문서는 로컬 Docker 기반으로 확인한 XChangePass 거래 통계 Read Replica 검증 결과를 AWS EC2 3대 환경에서 재현하기 위한 절차를 정리한다.

이 검증은 운영 반영 결과가 아니다. 목적은 로컬 노트북과 Docker 자원 공유의 영향을 줄이고, 분리된 EC2 서버 환경에서도 Hikari 기반 `MATERIALIZED_VIEW` 통계 조회 A/B 경향이 유지되는지 확인하는 것이다.

기준 문서는 `docs/benchmarks/transaction-statistics-replica-experiment.md`의 28장 `Hikari 기반 운영형 혼합 부하 검증`이다.

## 2. EC2 서버 구성

| 역할 | Public IP | Private IP | 실행 대상 |
| --- | --- | --- | --- |
| App 서버 | `3.38.132.108` | `172.31.28.10` | Spring Boot App, Redis, k6 |
| Primary DB 서버 | `15.165.209.25` | `172.31.19.209` | PostgreSQL Primary |
| Replica DB 서버 | `43.203.142.49` | `172.31.31.228` | PostgreSQL Read Replica |

서버 간 통신은 Public IP가 아니라 Private IP를 사용한다.

| 연결 | 대상 |
| --- | --- |
| App -> Primary | `172.31.19.209:5432` |
| App -> Replica | `172.31.31.228:5432` |
| Replica -> Primary replication | `172.31.19.209:5432` |

## 3. 중요한 원칙

이번 검증의 비교 대상은 아래 두 조건이다.

| 조건 | 설명 |
| --- | --- |
| Single DB + MV | 송금 API와 `MATERIALIZED_VIEW` 월별 통계 조회가 모두 Primary DB를 사용 |
| Primary + Replica + MV Hikari | 송금 API는 Primary DB를 사용하고, `MATERIALIZED_VIEW` 월별 통계 조회만 Replica DB를 사용 |

Replica routing 범위는 기존 로컬 검증과 동일하게 제한한다.

* `MATERIALIZED_VIEW` 월별 통계 조회만 Replica 후보로 routing한다.
* `@Transactional(readOnly = true)` 전체를 Replica로 보내지 않는다.
* 송금, 잔액 변경, 원장 저장은 Primary를 사용한다.
* `Idempotency-Key` 판단은 Primary를 사용한다.
* recovery, reconciliation, latest state 판단은 Primary를 사용한다.
* 최신 잔액, 최신 원장, 복구 판단에는 통계 데이터를 사용하지 않는다.
* 통계는 조회용 파생 데이터이며, 최신성은 `dataAsOf` 또는 refresh 기준 시각과 함께 해석한다.

## 4. Primary DB 서버 구성 절차

### 4.1 기본 준비

Primary DB 서버에서 Docker와 Docker Compose를 설치한다.

```bash
docker --version
docker compose version
```

검증용 PostgreSQL은 로컬 compose와 같은 PostgreSQL 15 계열을 기준으로 한다.

### 4.2 PostgreSQL Primary 실행 설정

Primary PostgreSQL에는 최소 아래 설정이 필요하다.

```text
wal_level=replica
max_wal_senders=10
max_replication_slots=10
shared_preload_libraries=pg_stat_statements
pg_stat_statements.track=all
```

`shared_preload_libraries`는 PostgreSQL 재시작이 필요한 설정이다. 이번 검증에서는 EC2 검증용 DB에서만 적용하고, 운영 설정 변경으로 해석하지 않는다.

### 4.3 replication user 생성

Primary DB에서 replication 전용 사용자를 생성한다.

```sql
CREATE ROLE <REPLICATION_USER> WITH REPLICATION LOGIN PASSWORD '<REPLICATION_PASSWORD>';
```

`<REPLICATION_USER>`와 `<REPLICATION_PASSWORD>`는 placeholder다. 실제 값은 Git에 커밋하지 않는다.

### 4.4 접속 허용 설정

`pg_hba.conf`에는 App private IP와 Replica private IP만 허용한다.

예시:

```text
# App -> Primary 일반 접속
host    xchangepass    <APP_DB_USER>          172.31.28.10/32     scram-sha-256

# Replica -> Primary replication 접속
host    replication    <REPLICATION_USER>     172.31.31.228/32    scram-sha-256
```

검증 편의를 위해 `0.0.0.0/0`을 열지 않는다. Public IP 기반 접속도 기본 경로로 사용하지 않는다.

### 4.5 pg_stat_statements 활성화

DB 생성 후 Primary에서 extension을 생성한다.

```sql
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
```

확인:

```sql
SELECT count(*) FROM pg_stat_statements;
```

## 5. Replica DB 서버 구성 절차

### 5.1 기본 준비

Replica DB 서버에서 Docker와 Docker Compose를 설치한다.

```bash
docker --version
docker compose version
```

### 5.2 Primary private IP로 base backup

Replica는 Primary public IP가 아니라 Primary private IP로 초기 base backup을 받는다.

```bash
PGPASSWORD='<REPLICATION_PASSWORD>' pg_basebackup \
  -h 172.31.19.209 \
  -p 5432 \
  -D "$PGDATA" \
  -U '<REPLICATION_USER>' \
  -Fp \
  -Xs \
  -P \
  -R
```

`-R` 옵션은 standby 설정을 생성한다. 실제 컨테이너 경로와 `$PGDATA`는 EC2에서 사용하는 PostgreSQL 실행 방식에 맞춘다.

### 5.3 Replica 실행 설정

Replica PostgreSQL에는 최소 아래 설정이 필요하다.

```text
hot_standby=on
shared_preload_libraries=pg_stat_statements
pg_stat_statements.track=all
```

Replica에서도 `pg_stat_statements`를 수집하려면 extension이 필요하다. Primary에서 생성된 extension은 DB 객체로 복제되지만, `shared_preload_libraries` 설정은 Replica 서버에도 별도로 적용되어야 한다.

### 5.4 Replica 상태 확인

Replica DB에서 아래를 확인한다.

```sql
SELECT pg_is_in_recovery();
```

기대값:

```text
true
```

read-only 조회 확인:

```sql
SELECT count(*) FROM mv_transaction_monthly_statistics;
```

쓰기 차단 확인은 선택적으로 수행한다.

```sql
CREATE TABLE replica_write_check(id bigint);
```

Replica에서는 read-only transaction 오류가 발생해야 한다. 확인 후 테스트 테이블이 생성되지 않았는지 점검한다.

### 5.5 Primary/Replica row count 확인

Primary와 Replica에서 각각 아래를 실행한다.

```sql
SELECT count(*) FROM mv_transaction_monthly_statistics;
```

두 값이 같아야 본 부하 테스트를 시작한다.

## 6. App 서버 구성 절차

### 6.1 기본 준비

App 서버에는 Docker, Docker Compose, Redis, k6 실행 환경을 준비한다.

```bash
docker --version
docker compose version
k6 version
```

k6를 host에 설치하지 않는 경우 k6 Docker image를 사용할 수 있다. 이 경우 결과 파일 경로와 auth env 파일 mount 경로를 별도로 맞춘다.

### 6.2 Redis 실행

송금 API와 fraud 관련 흐름에서 Redis가 필요하다. App 서버에서 Redis를 실행하고 Spring Boot App이 접근할 수 있게 한다.

```text
SPRING_DATA_REDIS_HOST=<REDIS_HOST>
SPRING_DATA_REDIS_PORT=<REDIS_PORT>
```

실제 Redis 비밀번호나 접속 정보가 있다면 Git에 커밋하지 않는다.

### 6.3 Spring Boot App 실행

Single DB 조건에서는 Replica datasource를 비활성화한다.

```bash
SPRING_DATASOURCE_URL='jdbc:postgresql://172.31.19.209:5432/xchangepass'
SPRING_DATASOURCE_USERNAME='<APP_DB_USER>'
SPRING_DATASOURCE_PASSWORD='<APP_DB_PASSWORD>'
SPRING_DATASOURCE_HIKARI_POOL_NAME='transaction-statistics-primary'
TRANSACTION_STATISTICS_REPLICA_ENABLED='false'
```

Primary + Replica 조건에서는 Replica datasource를 활성화한다.

```bash
SPRING_DATASOURCE_URL='jdbc:postgresql://172.31.19.209:5432/xchangepass'
SPRING_DATASOURCE_USERNAME='<APP_DB_USER>'
SPRING_DATASOURCE_PASSWORD='<APP_DB_PASSWORD>'
SPRING_DATASOURCE_HIKARI_POOL_NAME='transaction-statistics-primary'

TRANSACTION_STATISTICS_REPLICA_ENABLED='true'
TRANSACTION_STATISTICS_REPLICA_URL='jdbc:postgresql://172.31.31.228:5432/xchangepass'
TRANSACTION_STATISTICS_REPLICA_USERNAME='<APP_DB_USER>'
TRANSACTION_STATISTICS_REPLICA_PASSWORD='<APP_DB_PASSWORD>'
TRANSACTION_STATISTICS_REPLICA_POOL_NAME='transaction-statistics-replica'
TRANSACTION_STATISTICS_REPLICA_MAXIMUM_POOL_SIZE='10'
TRANSACTION_STATISTICS_REPLICA_MINIMUM_IDLE='2'
TRANSACTION_STATISTICS_REPLICA_CONNECTION_TIMEOUT='30000'
```

위 환경변수 이름은 현재 코드 기준이다.

* `SPRING_DATASOURCE_*`: Spring Boot 기본 Primary datasource 설정
* `SPRING_DATASOURCE_HIKARI_POOL_NAME`: Primary Hikari pool name
* `TRANSACTION_STATISTICS_REPLICA_*`: `TransactionStatisticsReplicaProperties`의 `transaction.statistics.replica.*` 설정

비밀번호, JWT secret, access token, `.env`, `.pem` 파일은 Git에 커밋하지 않는다.

### 6.4 통계 timing 수집

API 계층 timing을 수집하려면 아래 설정을 사용한다.

```bash
TRANSACTION_STATISTICS_TIMING_ENABLED='true'
TRANSACTION_STATISTICS_TIMING_OUTPUT='<LOCAL_RESULT_DIR>/api-timing.csv'
```

`api-timing.csv`는 raw 성능 결과 파일이므로 Git에 커밋하지 않는다.

### 6.5 k6 실행 준비

k6 스크립트는 기존 파일을 사용한다.

```text
k6-scripts/transaction-transfer-statistics-replica-car.js
tools/perf/run-transaction-transfer-statistics-replica-car.sh
```

필요한 auth env 파일은 App 서버의 로컬 결과 디렉터리에 둔다.

```text
build/perf/k6/transaction-transfer-auth.env
build/perf/k6/transaction-statistics-auth.env
```

auth env 파일에는 access token, sender/receiver 정보가 들어갈 수 있으므로 Git에 커밋하지 않는다.

## 7. 환경변수와 secrets 주의

아래 값은 placeholder로만 문서화한다.

* `<APP_DB_USER>`
* `<APP_DB_PASSWORD>`
* `<REPLICATION_USER>`
* `<REPLICATION_PASSWORD>`
* `<REDIS_HOST>`
* `<REDIS_PORT>`
* `<ACCESS_TOKEN>`
* `<JWT_SECRET>`
* `.pem` key

아래 파일과 결과물은 Git에 커밋하지 않는다.

* `.env`
* `application-secrets.yml`
* `.pem`
* token 파일
* raw k6 결과
* `build/perf/**`
* DB dump
* 실제 password가 포함된 compose 파일

## 8. Smoke test 체크리스트

본 부하 전에 아래 항목을 모두 확인한다.

| 항목 | 확인 방법 | 기대값 |
| --- | --- | --- |
| App -> Primary 연결 | App 서버에서 Primary JDBC 접속 | 성공 |
| App -> Replica 연결 | App 서버에서 Replica JDBC 접속 | 성공 |
| Primary recovery 상태 | `SELECT pg_is_in_recovery();` | `false` |
| Replica recovery 상태 | `SELECT pg_is_in_recovery();` | `true` |
| 송금 API 1건 | `PUT /api/v1/wallet/transfer` | HTTP 200, `COMPLETED` |
| MV 통계 조회 | `GET /api/v1/transactions/statistics/monthly?...&mode=MATERIALIZED_VIEW` | HTTP 200 |
| Single DB routing | `api-timing.csv`의 `datasource_target` | `primary` |
| Replica routing | `api-timing.csv`의 `datasource_target` | `replica` |
| Hikari pool tag | Actuator Hikari metric | Primary/Replica pool 분리 |
| WAL lag | Primary `pg_stat_replication` | 실행 전 0 또는 catchup 확인 |
| MV row count | Primary/Replica `count(*)` | 동일 |

Actuator Hikari metric 예시:

```text
/actuator/metrics/hikaricp.connections.active?tag=pool:transaction-statistics-primary
/actuator/metrics/hikaricp.connections.active?tag=pool:transaction-statistics-replica
/actuator/metrics/hikaricp.connections.pending?tag=pool:transaction-statistics-primary
/actuator/metrics/hikaricp.connections.pending?tag=pool:transaction-statistics-replica
/actuator/metrics/hikaricp.connections.acquire?tag=pool:transaction-statistics-primary
/actuator/metrics/hikaricp.connections.acquire?tag=pool:transaction-statistics-replica
```

WAL lag 확인 예시:

```sql
SELECT
  application_name,
  state,
  sync_state,
  pg_wal_lsn_diff(sent_lsn, replay_lsn)::bigint AS byte_lag
FROM pg_stat_replication;
```

## 9. A/B 실행 계획

### 9.1 우선 실행: burst 시나리오

로컬 Section 28과 같은 burst 조건을 우선 재현한다.

| 항목 | 값 |
| --- | --- |
| Condition | `ops-burst-t10-s50-s300-hikari` |
| Duration | `10m` |
| Transfer rate | `10/s` |
| Statistics pre-burst | `0m~4m`, `50/s` |
| Statistics burst | `4m~6m`, `300/s` |
| Statistics post-burst | `6m~10m`, `50/s` |
| Mode | `MATERIALIZED_VIEW` |
| Single DB | 3회 |
| Primary + Replica | 3회 |

실행 전 각 run의 결과 디렉터리를 분리한다.

예시:

```text
build/perf/aws-replica-ops-burst-YYYYMMDD/
```

raw 결과는 Git에 커밋하지 않는다.

### 9.2 선택 실행: steady 시나리오

시간이 충분하면 steady 조건을 추가한다.

| 항목 | 값 |
| --- | --- |
| Condition | `ops-steady-t10-s100-hikari` |
| Duration | `10m` |
| Transfer rate | `10/s` |
| Statistics rate | `100/s` |
| Mode | `MATERIALIZED_VIEW` |
| Single DB | 3회 |
| Primary + Replica | 3회 |

## 10. 수집 지표

### 10.1 k6 HTTP 지표

* transfer avg
* transfer p95
* transfer p99
* statistics avg
* statistics p95
* statistics p99
* transfer error rate
* statistics error rate
* dropped iterations

### 10.2 burst phase별 지표

* pre-burst transfer avg/p95/p99
* burst transfer avg/p95/p99
* post-burst transfer avg/p95/p99
* pre-burst statistics avg/p95/p99
* burst statistics avg/p95/p99
* post-burst statistics avg/p95/p99

### 10.3 API timing 지표

`api-timing.csv`에서 아래 metric을 확인한다.

* `statistics.api.total`
* `statistics.service.total`
* `statistics.service.query`
* `statistics.repository.total`
* `statistics.repository.jdbc`
* `statistics.mapping`
* `datasource_target`

동일성 판단에서 `dataAsOf`는 제외하고, latency 판단에서는 `datasource_target`이 `primary` 또는 `replica`로 의도대로 기록됐는지 확인한다.

### 10.4 Hikari 지표

Primary/Replica pool tag별로 아래를 확인한다.

* `hikaricp.connections.active`
* `hikaricp.connections.idle`
* `hikaricp.connections.pending`
* `hikaricp.connections.acquire`

pool name:

* Primary: `transaction-statistics-primary`
* Replica: `transaction-statistics-replica`

### 10.5 PostgreSQL 지표

`pg_stat_statements` query group:

* `statistics_mv`
* `balance`
* `wallet_transfer_request`
* `wallet_transaction`
* `idempotency`
* `fraud`
* `wallet_other`

확인 항목:

* calls
* total_exec_time
* mean_exec_time
* rows
* shared_blks_hit
* shared_blks_read
* temp_blks_read
* temp_blks_written

### 10.6 서버 지표

* Primary CPU avg/max
* Replica CPU avg/max
* Primary memory
* Replica memory
* WAL lag during max
* WAL lag after
* Primary MV row count
* Replica MV row count

## 11. 결과 문서화 템플릿

추후 `docs/benchmarks/transaction-statistics-replica-experiment.md`의 29장에 아래 형식으로 반영한다.

```markdown
## 29. AWS EC2 3대 환경 Read Replica 재현 검증

### 29.1 목적

- 로컬 Docker Section 28 결과를 EC2 3대 분리 환경에서 재현한다.
- 운영 반영 결과가 아니라 검증 환경 결과로 해석한다.

### 29.2 환경

| 역할 | Public IP | Private IP | 설명 |
| --- | --- | --- | --- |
| App | `3.38.132.108` | `172.31.28.10` | Spring Boot App, Redis, k6 |
| Primary DB | `15.165.209.25` | `172.31.19.209` | PostgreSQL Primary |
| Replica DB | `43.203.142.49` | `172.31.31.228` | PostgreSQL Read Replica |

### 29.3 실행 조건

| Condition | Duration | Transfer rate | Statistics rate | Runs |
| --- | ---: | ---: | ---: | ---: |
| `ops-burst-t10-s50-s300-hikari` | 10m | 10/s | 50/s -> 300/s -> 50/s | Single DB 3회, Primary + Replica 3회 |
| `ops-steady-t10-s100-hikari` | 10m | 10/s | 100/s | 선택 실행 |

### 29.4 Smoke test 결과

| 항목 | 결과 |
| --- | --- |
| App -> Primary |  |
| App -> Replica |  |
| Primary `pg_is_in_recovery()` |  |
| Replica `pg_is_in_recovery()` |  |
| Single DB `datasource_target` |  |
| Replica `datasource_target` |  |
| Hikari pool tag 분리 |  |
| WAL lag 시작 전 |  |
| MV row count |  |

### 29.5 k6 / HTTP 결과

| Condition | Scenario | Runs | Transfer avg | Transfer p95 | Transfer p99 | Statistics avg | Statistics p95 | Statistics p99 | Transfer error | Statistics error |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Burst | Single DB + MV | 3 |  |  |  |  |  |  |  |  |
| Burst | Primary + Replica + MV Hikari | 3 |  |  |  |  |  |  |  |  |

### 29.6 Burst phase별 결과

| Scenario | Phase | Transfer avg | Transfer p95 | Transfer p99 | Statistics avg | Statistics p95 | Statistics p99 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Single DB + MV | pre-burst |  |  |  |  |  |  |
| Single DB + MV | burst |  |  |  |  |  |  |
| Single DB + MV | post-burst |  |  |  |  |  |  |
| Primary + Replica + MV Hikari | pre-burst |  |  |  |  |  |  |
| Primary + Replica + MV Hikari | burst |  |  |  |  |  |  |
| Primary + Replica + MV Hikari | post-burst |  |  |  |  |  |  |

### 29.7 API timing 결과

| Condition | Scenario | Metric | avg | p95 | p99 |
| --- | --- | --- | ---: | ---: | ---: |
| Burst | Single DB + MV | `statistics.api.total` |  |  |  |
| Burst | Single DB + MV | `statistics.repository.jdbc` |  |  |  |
| Burst | Primary + Replica + MV Hikari | `statistics.api.total` |  |  |  |
| Burst | Primary + Replica + MV Hikari | `statistics.repository.jdbc` |  |  |  |

### 29.8 datasource_target 확인

| Condition | Scenario | datasource_target | count |
| --- | --- | --- | ---: |
| Burst | Single DB + MV | `primary` |  |
| Burst | Primary + Replica + MV Hikari | `replica` |  |

### 29.9 Hikari pool metric

| Condition | Scenario | Pool | Active avg/max | Idle avg/max | Pending avg/max | Acquire avg/max |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| Burst | Single DB + MV | `transaction-statistics-primary` |  |  |  |  |
| Burst | Primary + Replica + MV Hikari | `transaction-statistics-primary` |  |  |  |  |
| Burst | Primary + Replica + MV Hikari | `transaction-statistics-replica` |  |  |  |  |

### 29.10 pg_stat_statements

| Condition | Scenario | DB | Query group | calls | mean_exec_time | total_exec_time | shared_blks_hit | shared_blks_read |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| Burst | Single DB + MV | Primary | `statistics_mv` |  |  |  |  |  |
| Burst | Primary + Replica + MV Hikari | Replica | `statistics_mv` |  |  |  |  |  |
| Burst | Single DB + MV | Primary | `balance` |  |  |  |  |  |
| Burst | Primary + Replica + MV Hikari | Primary | `balance` |  |  |  |  |  |

### 29.11 CPU / WAL lag / MV row count

| Scenario | Primary CPU avg/max | Replica CPU avg/max | WAL lag during max | WAL lag after | Primary MV rows | Replica MV rows |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Single DB + MV |  |  |  |  |  |  |
| Primary + Replica + MV Hikari |  |  |  |  |  |  |

### 29.12 해석

- EC2 3대 환경에서도 `MATERIALIZED_VIEW` 통계 조회가 의도대로 Replica로 이동했는지 정리한다.
- 통계 API latency가 Single DB와 비교해 어떻게 달라졌는지 정리한다.
- 송금 API latency는 Replica가 직접 개선했다고 단정하지 않고, 혼합 부하에서 큰 악화가 있었는지와 Primary write path query group을 함께 본다.
- WAL lag가 통계 최신성에 미치는 영향을 `dataAsOf` 관점에서 분리해 해석한다.

### 29.13 제한 사항

- 운영 반영 결과가 아니다.
- EC2 instance type, EBS, network, Docker 설정의 영향을 받을 수 있다.
- raw k6 결과와 secrets는 문서에 포함하지 않는다.
- Primary/Replica 최종 도입 판단은 반복 실행과 lag 허용 기준 확정 이후로 미룬다.
```

## 12. 실행 전 최종 확인

실행 전 아래를 다시 확인한다.

* 서버 간 연결은 Private IP를 사용한다.
* Security Group은 필요한 포트와 private IP만 허용한다.
* `@Transactional(readOnly = true)` 전체 Replica routing은 적용하지 않는다.
* Replica routing은 `MATERIALIZED_VIEW` 월별 통계 조회에만 제한한다.
* Single DB 조건과 Replica 조건의 App 실행 설정을 명확히 분리한다.
* 결과 디렉터리는 기존 로컬 결과와 섞이지 않게 새로 만든다.
* raw 결과, token, `.env`, `.pem`, `build/perf/**`는 커밋하지 않는다.
