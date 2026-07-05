#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
BASE_OUTPUT_DIR="$ROOT_DIR/build/perf/k6"
REPEAT_OUTPUT_DIR="$BASE_OUTPUT_DIR/mixed-repeat"
MIXED_RUNNER="$ROOT_DIR/tools/perf/run-transaction-transfer-statistics-k6-mixed.sh"
TRANSFER_SEED_RUNNER="$ROOT_DIR/tools/perf/run-transaction-transfer-k6-seed.sh"

K6_REPEAT="${K6_REPEAT:-3}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
TRANSFER_AUTH_ENV="${TRANSFER_AUTH_ENV:-$BASE_OUTPUT_DIR/transaction-transfer-auth.env}"
STATS_AUTH_ENV="${STATS_AUTH_ENV:-$BASE_OUTPUT_DIR/transaction-statistics-auth.env}"
K6_VUS="${K6_VUS:-10}"
K6_DURATION="${K6_DURATION:-30s}"
K6_SLEEP="${K6_SLEEP:-1}"
MODES="${MODES:-GROUP_BY MATERIALIZED_VIEW SUMMARY}"
REFRESH_TRANSFER_SEED_EACH_RUN="${REFRESH_TRANSFER_SEED_EACH_RUN:-true}"
TRANSACTION_TRANSFER_K6_PAIRS="${TRANSACTION_TRANSFER_K6_PAIRS:-400}"

mkdir -p "$REPEAT_OUTPUT_DIR"

SUMMARY_CSV="$REPEAT_OUTPUT_DIR/transaction-transfer-statistics-k6-mixed-repeat-summary.csv"
README="$REPEAT_OUTPUT_DIR/README.md"

echo "run,mode,transfer_avg_ms,transfer_p90_ms,transfer_p95_ms,transfer_p99_ms,transfer_max_ms,transfer_requests,transfer_error_rate,statistics_avg_ms,statistics_p90_ms,statistics_p95_ms,statistics_p99_ms,statistics_max_ms,statistics_requests,statistics_error_rate" > "$SUMMARY_CSV"

echo "# transaction transfer statistics mixed repeat" > "$README"
echo "" >> "$README"
echo "- repeat: $K6_REPEAT" >> "$README"
echo "- baseUrl: $BASE_URL" >> "$README"
echo "- vus: $K6_VUS" >> "$README"
echo "- duration: $K6_DURATION" >> "$README"
echo "- sleep: $K6_SLEEP" >> "$README"
echo "- modes: $MODES" >> "$README"
echo "- refreshTransferSeedEachRun: $REFRESH_TRANSFER_SEED_EACH_RUN" >> "$README"
echo "" >> "$README"
echo "raw 결과와 auth env는 로컬 benchmark 산출물이며 커밋하지 않는다." >> "$README"

status=0

for run in $(seq 1 "$K6_REPEAT"); do
  run_dir="$REPEAT_OUTPUT_DIR/run-$run"
  mkdir -p "$run_dir"

  echo
  echo "mixed repeat run=$run/$K6_REPEAT"

  if [ "$REFRESH_TRANSFER_SEED_EACH_RUN" = "true" ]; then
    echo "run=$run 송금 seed 재생성 중..."
    if ! TRANSACTION_TRANSFER_K6_PAIRS="$TRANSACTION_TRANSFER_K6_PAIRS" "$TRANSFER_SEED_RUNNER"; then
      echo "run=$run 송금 seed 재생성 실패" >&2
      status=1
      continue
    fi
  fi

  if ! BASE_URL="$BASE_URL" \
    TRANSFER_AUTH_ENV="$TRANSFER_AUTH_ENV" \
    STATS_AUTH_ENV="$STATS_AUTH_ENV" \
    K6_VUS="$K6_VUS" \
    K6_DURATION="$K6_DURATION" \
    K6_SLEEP="$K6_SLEEP" \
    MODES="$MODES" \
    "$MIXED_RUNNER"; then
    echo "run=$run 혼합 부하 실행 실패" >&2
    status=1
  fi

  for mode in $MODES; do
    lower_mode="$(printf '%s' "$mode" | tr '[:upper:]' '[:lower:]' | tr '_' '-')"
    source_json="$BASE_OUTPUT_DIR/transaction-transfer-statistics-mixed-$lower_mode.json"
    source_text="$BASE_OUTPUT_DIR/transaction-transfer-statistics-mixed-$lower_mode.txt"
    target_json="$run_dir/transaction-transfer-statistics-mixed-$lower_mode.json"
    target_text="$run_dir/transaction-transfer-statistics-mixed-$lower_mode.txt"

    if [ -f "$source_json" ]; then
      cp "$source_json" "$target_json"
    fi
    if [ -f "$source_text" ]; then
      cp "$source_text" "$target_text"
    fi

    if [ -f "$target_json" ]; then
      jq -r --arg run "$run" --arg mode "$mode" '
        [
          $run,
          $mode,
          (.metrics.transfer_duration.avg // 0),
          (.metrics.transfer_duration["p(90)"] // 0),
          (.metrics.transfer_duration["p(95)"] // 0),
          (.metrics.transfer_duration["p(99)"] // 0),
          (.metrics.transfer_duration.max // 0),
          ((.metrics.transfer_error_rate.passes // 0) + (.metrics.transfer_error_rate.fails // 0)),
          (.metrics.transfer_error_rate.value // 0),
          (.metrics.statistics_duration.avg // 0),
          (.metrics.statistics_duration["p(90)"] // 0),
          (.metrics.statistics_duration["p(95)"] // 0),
          (.metrics.statistics_duration["p(99)"] // 0),
          (.metrics.statistics_duration.max // 0),
          ((.metrics.statistics_error_rate.passes // 0) + (.metrics.statistics_error_rate.fails // 0)),
          (.metrics.statistics_error_rate.value // 0)
        ] | @csv
      ' "$target_json" >> "$SUMMARY_CSV"
    fi
  done
done

echo
echo "혼합 부하 반복 실행 완료."
echo "- summary csv: $SUMMARY_CSV"
echo "- readme: $README"

exit "$status"
