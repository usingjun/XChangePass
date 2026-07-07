#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/infra/docker-compose.transaction-statistics-replica.yml"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-xcp-statistics-replica-ab}"
OUTPUT_DIR="${OUTPUT_DIR:-$ROOT_DIR/build/perf/replica-highload-ab}"
SUMMARY_CSV="$OUTPUT_DIR/summary.csv"
APP_PORT="${APP_PORT:-18080}"
BASE_URL="${BASE_URL:-http://localhost:$APP_PORT}"
PG_URL="${PG_URL:-jdbc:postgresql://localhost:15432/xchangepass}"
PG_USER="${PG_USER:-postgres}"
PG_PASSWORD="${PG_PASSWORD:-postgres}"
STATS_ROWS="${TRANSACTION_STATISTICS_K6_SEED_ROWS:-100000}"
STATS_USERS="${TRANSACTION_STATISTICS_K6_SEED_USERS:-1000}"
STATS_MONTHS="${TRANSACTION_STATISTICS_K6_SEED_MONTHS:-12}"
TRANSFER_PAIRS="${TRANSACTION_TRANSFER_K6_PAIRS:-10000}"
SAMPLE_INTERVAL_SECONDS="${SAMPLE_INTERVAL_SECONDS:-5}"
RUN_LEVELS="${RUN_LEVELS:-sanity,s30,s50}"
RUN_HIGH3="${RUN_HIGH3:-false}"
RUN_EXTRA_WRITE="${RUN_EXTRA_WRITE:-false}"
RUN_DEEP="${RUN_DEEP:-false}"
RUN_T10_S200="${RUN_T10_S200:-false}"

mkdir -p "$OUTPUT_DIR"

if [ ! -f "$SUMMARY_CSV" ]; then
  echo "condition,run_order,scenario,label,duration,transfer_rate,statistics_rate,replica_enabled,transfer_avg_ms,transfer_p95_ms,transfer_p99_ms,transfer_min_ms,transfer_max_ms,transfer_waiting_avg_ms,transfer_waiting_p95_ms,transfer_blocked_avg_ms,transfer_blocked_p95_ms,transfer_receiving_avg_ms,transfer_receiving_p95_ms,transfer_count,transfer_error_rate,statistics_avg_ms,statistics_p95_ms,statistics_p99_ms,statistics_min_ms,statistics_max_ms,statistics_waiting_avg_ms,statistics_waiting_p95_ms,statistics_blocked_avg_ms,statistics_blocked_p95_ms,statistics_receiving_avg_ms,statistics_receiving_p95_ms,statistics_count,statistics_error_rate,http_reqs,raw_json,raw_text,db_metrics_dir" > "$SUMMARY_CSV"
fi

app_pid=""
sampler_pid=""
sampler_stop_file=""

cleanup_processes() {
  if [ -n "${sampler_pid:-}" ] && kill -0 "$sampler_pid" >/dev/null 2>&1; then
    touch "$sampler_stop_file" || true
    wait "$sampler_pid" || true
  fi
  if [ -n "${app_pid:-}" ] && kill -0 "$app_pid" >/dev/null 2>&1; then
    kill "$app_pid" || true
    wait "$app_pid" || true
  fi
}

trap cleanup_processes EXIT

run_sql() {
  local container="$1"
  local sql="$2"
  docker exec "$container" psql -U postgres -d xchangepass -At -F ',' -c "$sql"
}

run_sql_file() {
  local container="$1"
  local output_file="$2"
  local sql="$3"
  run_sql "$container" "$sql" >> "$output_file" 2>> "$output_file.err" || true
}

wait_for_container_health() {
  local container="$1"
  local attempt
  for attempt in $(seq 1 90); do
    local status
    status="$(docker inspect -f '{{.State.Health.Status}}' "$container" 2>/dev/null || true)"
    if [ "$status" = "healthy" ]; then
      return 0
    fi
    sleep 2
  done
  echo "$container healthcheck timeout" >&2
  return 1
}

wait_for_app() {
  local attempt
  for attempt in $(seq 1 90); do
    local status
    status="$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/actuator/health" || true)"
    if [ "$status" = "200" ] || [ "$status" = "401" ] || [ "$status" = "403" ]; then
      sleep 5
      return 0
    fi
    sleep 2
  done
  echo "application readiness timeout: $BASE_URL" >&2
  return 1
}

wait_for_replica_catchup() {
  local attempt
  for attempt in $(seq 1 120); do
    local primary_count
    local replica_count
    local byte_lag
    primary_count="$(run_sql xchangepass-statistics-primary "select count(*) from mv_transaction_monthly_statistics;" | tail -1 || true)"
    replica_count="$(run_sql xchangepass-statistics-replica "select count(*) from mv_transaction_monthly_statistics;" | tail -1 || true)"
    byte_lag="$(run_sql xchangepass-statistics-primary "select coalesce(max(pg_wal_lsn_diff(sent_lsn, replay_lsn)), 0)::bigint from pg_stat_replication;" | tail -1 || true)"
    if [ "$primary_count" = "$replica_count" ] && [ "${byte_lag:-1}" = "0" ] && [ -n "$primary_count" ]; then
      echo "replica catchup: primary_mv=$primary_count replica_mv=$replica_count byte_lag=$byte_lag"
      return 0
    fi
    sleep 2
  done
  echo "replica catchup timeout" >&2
  return 1
}

clear_fraud_redis_keys() {
  if command -v redis-cli >/dev/null 2>&1; then
    redis-cli --scan --pattern 'fraud:*' | while read -r key; do
      [ -n "$key" ] && redis-cli del "$key" >/dev/null || true
    done
  fi
}

reset_pg_stat_statements() {
  local replica_enabled="$1"
  run_sql xchangepass-statistics-primary "select pg_stat_statements_reset();" >/dev/null 2>&1 || true
  if [ "$replica_enabled" = "true" ]; then
    run_sql xchangepass-statistics-replica "select pg_stat_statements_reset();" >/dev/null 2>&1 || true
  fi
}

collect_pg_stat_statements() {
  local metrics_dir="$1"
  local replica_enabled="$2"
  local sql="select case when query ilike '%mv_transaction_monthly_statistics%' then 'statistics_mv' when query ilike '%wallet_transfer_request%' then 'wallet_transfer_request' when query ilike '%wallet_transaction%' then 'wallet_transaction' when query ilike '%balance%' then 'balance' when query ilike '%wallet%' then 'wallet_other' else 'other' end as query_group, sum(calls) as calls, round(sum(total_exec_time)::numeric, 3) as total_exec_time_ms, round((sum(total_exec_time) / nullif(sum(calls), 0))::numeric, 3) as mean_exec_time_ms, sum(rows) as rows, sum(shared_blks_hit) as shared_blks_hit, sum(shared_blks_read) as shared_blks_read, sum(temp_blks_read) as temp_blks_read, sum(temp_blks_written) as temp_blks_written from pg_stat_statements where query ilike '%mv_transaction_monthly_statistics%' or query ilike '%wallet_transfer_request%' or query ilike '%wallet%' or query ilike '%balance%' group by 1 order by total_exec_time_ms desc;"
  {
    echo "query_group,calls,total_exec_time_ms,mean_exec_time_ms,rows,shared_blks_hit,shared_blks_read,temp_blks_read,temp_blks_written"
    run_sql xchangepass-statistics-primary "$sql"
  } > "$metrics_dir/pg-stat-statements-primary.csv" 2> "$metrics_dir/pg-stat-statements-primary.err" || true
  if [ "$replica_enabled" = "true" ]; then
    {
      echo "query_group,calls,total_exec_time_ms,mean_exec_time_ms,rows,shared_blks_hit,shared_blks_read,temp_blks_read,temp_blks_written"
      run_sql xchangepass-statistics-replica "$sql"
    } > "$metrics_dir/pg-stat-statements-replica.csv" 2> "$metrics_dir/pg-stat-statements-replica.err" || true
  fi
}

sample_db_metrics() {
  local metrics_dir="$1"
  local replica_enabled="$2"
  local stop_file="$3"
  local actuator_token="$4"
  echo "collected_at,datname,state,count" > "$metrics_dir/activity-state-primary.csv"
  echo "collected_at,wait_event_type,wait_event,count" > "$metrics_dir/activity-wait-primary.csv"
  echo "collected_at,application_name,state,sync_state,byte_lag" > "$metrics_dir/replication-primary.csv"
  echo "collected_at,container,cpu_percent,memory_usage" > "$metrics_dir/docker-stats.csv"
  echo "collected_at,metric,statistic,value,available_tags" > "$metrics_dir/app-actuator-metrics.csv"
  if [ "$replica_enabled" = "true" ]; then
    echo "collected_at,datname,state,count" > "$metrics_dir/activity-state-replica.csv"
    echo "collected_at,wait_event_type,wait_event,count" > "$metrics_dir/activity-wait-replica.csv"
    echo "collected_at,replay_delay" > "$metrics_dir/replay-delay-replica.csv"
  fi
  while [ ! -f "$stop_file" ]; do
    run_sql_file xchangepass-statistics-primary "$metrics_dir/activity-state-primary.csv" "select now(), datname, coalesce(state, 'none'), count(*) from pg_stat_activity where datname = 'xchangepass' group by 1, 2, 3 order by 1, 2, 3;"
    run_sql_file xchangepass-statistics-primary "$metrics_dir/activity-wait-primary.csv" "select now(), coalesce(wait_event_type, 'none'), coalesce(wait_event, 'none'), count(*) from pg_stat_activity where datname = 'xchangepass' group by 1, 2, 3 order by count(*) desc;"
    run_sql_file xchangepass-statistics-primary "$metrics_dir/replication-primary.csv" "select now(), application_name, state, sync_state, pg_wal_lsn_diff(sent_lsn, replay_lsn)::bigint from pg_stat_replication;"
    if [ "$replica_enabled" = "true" ]; then
      run_sql_file xchangepass-statistics-replica "$metrics_dir/activity-state-replica.csv" "select now(), datname, coalesce(state, 'none'), count(*) from pg_stat_activity where datname = 'xchangepass' group by 1, 2, 3 order by 1, 2, 3;"
      run_sql_file xchangepass-statistics-replica "$metrics_dir/activity-wait-replica.csv" "select now(), coalesce(wait_event_type, 'none'), coalesce(wait_event, 'none'), count(*) from pg_stat_activity where datname = 'xchangepass' group by 1, 2, 3 order by count(*) desc;"
      run_sql_file xchangepass-statistics-replica "$metrics_dir/replay-delay-replica.csv" "select now(), coalesce((now() - pg_last_xact_replay_timestamp())::text, 'none');"
    fi
    local collected_at
    collected_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    sample_app_metrics "$metrics_dir" "$collected_at" "$actuator_token"
    docker stats --no-stream --format "{{.Name}},{{.CPUPerc}},{{.MemUsage}}" \
      xchangepass-statistics-primary xchangepass-statistics-replica 2>> "$metrics_dir/docker-stats.err" \
      | while IFS= read -r line; do
          echo "$collected_at,$line"
        done >> "$metrics_dir/docker-stats.csv" || true
    sleep "$SAMPLE_INTERVAL_SECONDS"
  done
}

stats_auth_token() {
  local stats_env="$ROOT_DIR/build/perf/k6/transaction-statistics-auth.env"
  if [ ! -f "$stats_env" ]; then
    return 0
  fi
  set -a
  # shellcheck disable=SC1090
  . "$stats_env"
  set +a
  printf '%s' "${ACCESS_TOKEN_COOKIE:-${AUTH_TOKEN:-${ACCESS_TOKEN:-${JWT_TOKEN:-}}}}"
}

sample_app_metrics() {
  local metrics_dir="$1"
  local collected_at="$2"
  local actuator_token="$3"
  local metrics=(
    "hikaricp.connections.active"
    "hikaricp.connections.idle"
    "hikaricp.connections.pending"
    "hikaricp.connections.acquire"
    "hikaricp.connections.usage"
  )
  local metric
  for metric in "${metrics[@]}"; do
    local body
    body="$(curl -s --cookie "accessToken=$actuator_token" "$BASE_URL/actuator/metrics/$metric" || true)"
    python3 - "$metrics_dir/app-actuator-metrics.csv" "$collected_at" "$metric" "$body" <<'PY'
import csv
import json
import sys

output_file, collected_at, metric_name, body = sys.argv[1:]
try:
    data = json.loads(body)
except json.JSONDecodeError:
    data = {}

measurements = data.get("measurements", [])
available_tags = ";".join(
    f"{tag.get('tag', '')}={','.join(tag.get('values', []))}"
    for tag in data.get("availableTags", [])
)

with open(output_file, "a", encoding="utf-8", newline="") as file:
    writer = csv.writer(file)
    if measurements:
        for measurement in measurements:
            writer.writerow([
                collected_at,
                metric_name,
                measurement.get("statistic", ""),
                measurement.get("value", ""),
                available_tags,
            ])
    else:
        writer.writerow([collected_at, metric_name, "", "", available_tags])
PY
  done
}

append_k6_summary() {
  local condition="$1"
  local run_order="$2"
  local scenario="$3"
  local label="$4"
  local duration="$5"
  local transfer_rate="$6"
  local statistics_rate="$7"
  local replica_enabled="$8"
  local raw_json="$9"
  local raw_text="${10}"
  local metrics_dir="${11}"
  python3 - "$SUMMARY_CSV" "$condition" "$run_order" "$scenario" "$label" "$duration" "$transfer_rate" "$statistics_rate" "$replica_enabled" "$raw_json" "$raw_text" "$metrics_dir" <<'PY'
import csv
import json
import sys

summary_csv, condition, run_order, scenario, label, duration, transfer_rate, statistics_rate, replica_enabled, raw_json, raw_text, metrics_dir = sys.argv[1:]
with open(raw_json, encoding="utf-8") as file:
    data = json.load(file)

metrics = data.get("metrics", {})

def metric(name, key, default=""):
    metric_data = metrics.get(name, {})
    values = metric_data.get("values", metric_data)
    return values.get(key, default)

def rate_total(name):
    metric_data = metrics.get(name, {})
    return metric_data.get("passes", 0) + metric_data.get("fails", 0)

row = [
    condition,
    run_order,
    scenario,
    label,
    duration,
    transfer_rate,
    statistics_rate,
    replica_enabled,
    metric("transfer_duration", "avg"),
    metric("transfer_duration", "p(95)"),
    metric("transfer_duration", "p(99)"),
    metric("transfer_duration", "min"),
    metric("transfer_duration", "max"),
    metric("transfer_waiting_duration", "avg"),
    metric("transfer_waiting_duration", "p(95)"),
    metric("transfer_blocked_duration", "avg"),
    metric("transfer_blocked_duration", "p(95)"),
    metric("transfer_receiving_duration", "avg"),
    metric("transfer_receiving_duration", "p(95)"),
    rate_total("transfer_error_rate"),
    metric("transfer_error_rate", "value"),
    metric("statistics_duration", "avg"),
    metric("statistics_duration", "p(95)"),
    metric("statistics_duration", "p(99)"),
    metric("statistics_duration", "min"),
    metric("statistics_duration", "max"),
    metric("statistics_waiting_duration", "avg"),
    metric("statistics_waiting_duration", "p(95)"),
    metric("statistics_blocked_duration", "avg"),
    metric("statistics_blocked_duration", "p(95)"),
    metric("statistics_receiving_duration", "avg"),
    metric("statistics_receiving_duration", "p(95)"),
    rate_total("statistics_error_rate"),
    metric("statistics_error_rate", "value"),
    metric("http_reqs", "count"),
    raw_json,
    raw_text,
    metrics_dir,
]
with open(summary_csv, "a", encoding="utf-8", newline="") as file:
    csv.writer(file).writerow(row)
PY
}

start_postgres() {
  docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" down -v --remove-orphans
  docker rm -f xchangepass-statistics-primary xchangepass-statistics-replica >/dev/null 2>&1 || true
  docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" up -d
  wait_for_container_health xchangepass-statistics-primary
  wait_for_container_health xchangepass-statistics-replica
}

seed_database() {
  local run_dir="$1"
  PG_URL="$PG_URL" PG_USER="$PG_USER" PG_PASSWORD="$PG_PASSWORD" \
    TRANSACTION_STATISTICS_K6_SEED_ROWS="$STATS_ROWS" \
    TRANSACTION_STATISTICS_K6_SEED_USERS="$STATS_USERS" \
    TRANSACTION_STATISTICS_K6_SEED_MONTHS="$STATS_MONTHS" \
    "$ROOT_DIR/tools/perf/run-transaction-statistics-k6-seed.sh" > "$run_dir/statistics-seed.log" 2>&1

  PG_URL="$PG_URL" PG_USER="$PG_USER" PG_PASSWORD="$PG_PASSWORD" \
    TRANSACTION_TRANSFER_K6_PAIRS="$TRANSFER_PAIRS" \
    "$ROOT_DIR/tools/perf/run-transaction-transfer-k6-seed.sh" > "$run_dir/transfer-seed.log" 2>&1
}

start_app() {
  local replica_enabled="$1"
  local app_log="$2"
  cleanup_processes
  clear_fraud_redis_keys
  if [ "$replica_enabled" = "true" ]; then
    SPRING_DATASOURCE_URL="$PG_URL" \
      SPRING_DATASOURCE_USERNAME="$PG_USER" \
      SPRING_DATASOURCE_PASSWORD="$PG_PASSWORD" \
      TRANSACTION_STATISTICS_REPLICA_ENABLED=true \
      TRANSACTION_STATISTICS_REPLICA_URL=jdbc:postgresql://localhost:15433/xchangepass \
      TRANSACTION_STATISTICS_REPLICA_USERNAME="$PG_USER" \
      TRANSACTION_STATISTICS_REPLICA_PASSWORD="$PG_PASSWORD" \
      SERVER_PORT="$APP_PORT" \
      "$ROOT_DIR/gradlew" bootRun --args="--server.port=$APP_PORT --fraud.policy.night-start=12:00 --fraud.policy.night-end=12:01" > "$app_log" 2>&1 &
  else
    SPRING_DATASOURCE_URL="$PG_URL" \
      SPRING_DATASOURCE_USERNAME="$PG_USER" \
      SPRING_DATASOURCE_PASSWORD="$PG_PASSWORD" \
      TRANSACTION_STATISTICS_REPLICA_ENABLED=false \
      SERVER_PORT="$APP_PORT" \
      "$ROOT_DIR/gradlew" bootRun --args="--server.port=$APP_PORT --fraud.policy.night-start=12:00 --fraud.policy.night-end=12:01" > "$app_log" 2>&1 &
  fi
  app_pid="$!"
  wait_for_app
}

prepare_schema() {
  local app_log="$1"
  start_app "false" "$app_log"
  sleep 5
  cleanup_processes
}

warmup_statistics() {
  local stats_env="$ROOT_DIR/build/perf/k6/transaction-statistics-auth.env"
  set -a
  # shellcheck disable=SC1090
  . "$stats_env"
  set +a
  local token="${ACCESS_TOKEN_COOKIE:-${AUTH_TOKEN:-${ACCESS_TOKEN:-${JWT_TOKEN:-}}}}"
  local user_id="${TEST_USER_ID:-$(printf '%s' "${USER_IDS:-}" | cut -d, -f1)}"
  local index
  for index in $(seq 1 10); do
    curl -s -o /dev/null \
      --cookie "accessToken=$token" \
      "$BASE_URL/api/v1/transactions/statistics/monthly?userId=$user_id&fromMonth=2026-01&toMonth=2026-12&mode=MATERIALIZED_VIEW" || true
  done
}

run_one() {
  local condition="$1"
  local run_order="$2"
  local scenario="$3"
  local duration="$4"
  local transfer_rate="$5"
  local statistics_rate="$6"
  local replica_enabled="$7"
  local label="$condition-$scenario-$run_order"
  local safe_label
  safe_label="$(printf '%s' "$label" | tr '[:upper:]' '[:lower:]' | tr -c 'a-z0-9._-' '-')"
  local run_dir="$OUTPUT_DIR/$safe_label"
  local metrics_dir="$run_dir/db"
  mkdir -p "$metrics_dir"

  echo "=== $label 시작 ==="
  start_postgres
  prepare_schema "$run_dir/schema-bootstrap.log"
  seed_database "$run_dir"
  wait_for_replica_catchup
  start_app "$replica_enabled" "$run_dir/application.log"
  warmup_statistics
  reset_pg_stat_statements "$replica_enabled"

  sampler_stop_file="$metrics_dir/.stop"
  rm -f "$sampler_stop_file"
  sample_db_metrics "$metrics_dir" "$replica_enabled" "$sampler_stop_file" "$(stats_auth_token)" &
  sampler_pid="$!"

  SCENARIO_LABEL="$label" \
    BASE_URL="$BASE_URL" \
    MODE=MATERIALIZED_VIEW \
    TRANSFER_RATE="$transfer_rate" \
    STATISTICS_RATE="$statistics_rate" \
    DURATION="$duration" \
    TRANSFER_VUS=20 \
    STATISTICS_VUS=20 \
    TRANSFER_MAX_VUS=50 \
    STATISTICS_MAX_VUS=50 \
    DEBUG_STATUS="${DEBUG_STATUS:-false}" \
    "$ROOT_DIR/tools/perf/run-transaction-transfer-statistics-replica-car.sh"

  touch "$sampler_stop_file"
  wait "$sampler_pid" || true
  sampler_pid=""

  collect_pg_stat_statements "$metrics_dir" "$replica_enabled"
  run_sql xchangepass-statistics-primary "select count(*) from mv_transaction_monthly_statistics;" > "$metrics_dir/primary-mv-count.txt" || true
  run_sql xchangepass-statistics-replica "select count(*) from mv_transaction_monthly_statistics;" > "$metrics_dir/replica-mv-count.txt" || true
  run_sql xchangepass-statistics-primary "select coalesce(max(pg_wal_lsn_diff(sent_lsn, replay_lsn)), 0)::bigint from pg_stat_replication;" > "$metrics_dir/wal-byte-lag-after.txt" || true

  local raw_json="$ROOT_DIR/build/perf/k6/replica-car/transaction-transfer-statistics-replica-car-$safe_label.json"
  local raw_text="$ROOT_DIR/build/perf/k6/replica-car/transaction-transfer-statistics-replica-car-$safe_label.txt"
  append_k6_summary "$condition" "$run_order" "$scenario" "$label" "$duration" "$transfer_rate" "$statistics_rate" "$replica_enabled" "$raw_json" "$raw_text" "$metrics_dir"

  cleanup_processes
  docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" down -v --remove-orphans
  echo "=== $label 완료 ==="
}

run_condition() {
  local condition="$1"
  local duration="$2"
  local transfer_rate="$3"
  local statistics_rate="$4"
  local repeats="$5"

  if [ "$repeats" = "1" ]; then
    run_one "$condition" "1" "single-db-mv" "$duration" "$transfer_rate" "$statistics_rate" "false"
    run_one "$condition" "1" "primary-replica-mv" "$duration" "$transfer_rate" "$statistics_rate" "true"
    return
  fi

  run_one "$condition" "1" "single-db-mv" "$duration" "$transfer_rate" "$statistics_rate" "false"
  run_one "$condition" "1" "primary-replica-mv" "$duration" "$transfer_rate" "$statistics_rate" "true"
  run_one "$condition" "2" "primary-replica-mv" "$duration" "$transfer_rate" "$statistics_rate" "true"
  run_one "$condition" "2" "single-db-mv" "$duration" "$transfer_rate" "$statistics_rate" "false"
  run_one "$condition" "3" "single-db-mv" "$duration" "$transfer_rate" "$statistics_rate" "false"
  run_one "$condition" "3" "primary-replica-mv" "$duration" "$transfer_rate" "$statistics_rate" "true"
}

if [[ ",$RUN_LEVELS," == *",sanity,"* ]]; then
  run_condition "sanity-t5-s10" "30s" "5" "10" "1"
fi
if [[ ",$RUN_LEVELS," == *",s30,"* ]]; then
  run_condition "high-t5-s30" "3m" "5" "30" "3"
fi
if [[ ",$RUN_LEVELS," == *",s50,"* ]]; then
  run_condition "high-t5-s50" "3m" "5" "50" "3"
fi
if [ "$RUN_HIGH3" = "true" ]; then
  run_condition "high-t5-s100" "3m" "5" "100" "3"
fi
if [ "$RUN_EXTRA_WRITE" = "true" ]; then
  run_condition "high-t10-s50" "3m" "10" "50" "3"
fi
if [ "$RUN_DEEP" = "true" ]; then
  run_condition "high-t5-s200" "3m" "5" "200" "3"
  run_condition "high-t5-s300" "3m" "5" "300" "3"
  run_condition "high-t10-s100" "3m" "10" "100" "3"
fi
if [ "$RUN_T10_S200" = "true" ]; then
  run_condition "high-t10-s200" "3m" "10" "200" "3"
fi

echo "high-load A/B 완료: $SUMMARY_CSV"
