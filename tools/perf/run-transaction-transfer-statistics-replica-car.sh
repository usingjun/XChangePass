#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT="$ROOT_DIR/k6-scripts/transaction-transfer-statistics-replica-car.js"
OUTPUT_DIR="$ROOT_DIR/build/perf/k6/replica-car"

if ! command -v k6 >/dev/null 2>&1; then
  echo "k6가 설치되어 있지 않습니다. k6 설치 후 다시 실행해 주세요." >&2
  echo "예: brew install k6" >&2
  exit 127
fi

mkdir -p "$OUTPUT_DIR"

TRANSFER_AUTH_ENV="${TRANSFER_AUTH_ENV:-$ROOT_DIR/build/perf/k6/transaction-transfer-auth.env}"
STATS_AUTH_ENV="${STATS_AUTH_ENV:-$ROOT_DIR/build/perf/k6/transaction-statistics-auth.env}"

USE_AUTH_ENV_FILES=false
if [ -f "$TRANSFER_AUTH_ENV" ] && [ -f "$STATS_AUTH_ENV" ]; then
  USE_AUTH_ENV_FILES=true
fi

if [ "$USE_AUTH_ENV_FILES" = "false" ] && [ -f "$TRANSFER_AUTH_ENV" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$TRANSFER_AUTH_ENV"
  set +a
fi

TRANSFER_AUTH_TOKENS="${AUTH_TOKENS:-}"
TRANSFER_SENDER_USER_IDS="${SENDER_USER_IDS:-${SENDER_USER_ID:-}}"
TRANSFER_RECEIVER_USER_IDS="${RECEIVER_USER_IDS:-${RECEIVER_USER_ID:-}}"
TRANSFER_RECEIVER_NAMES="${RECEIVER_NAMES:-${RECEIVER_NAME:-}}"
TRANSFER_RECEIVER_PHONE_NUMBERS="${RECEIVER_PHONE_NUMBERS:-${RECEIVER_PHONE_NUMBER:-}}"
TRANSFER_FROM_CURRENCY="${FROM_CURRENCY:-${CURRENCY:-KRW}}"
TRANSFER_TO_CURRENCY="${TO_CURRENCY:-${CURRENCY:-KRW}}"
TRANSFER_AMOUNT_VALUE="${TRANSFER_AMOUNT:-}"

if [ "$USE_AUTH_ENV_FILES" = "false" ] && [ -f "$STATS_AUTH_ENV" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$STATS_AUTH_ENV"
  set +a
fi

STATS_AUTH_TOKEN="${ACCESS_TOKEN_COOKIE:-${AUTH_TOKEN:-${ACCESS_TOKEN:-${JWT_TOKEN:-}}}}"
STATS_USER_ID_VALUES="${USER_IDS:-${TEST_USER_ID:-}}"
STATS_FROM_MONTH="${FROM_MONTH:-2026-01}"
STATS_TO_MONTH="${TO_MONTH:-2026-12}"
TRANSFER_AUTH_ENV_FILE_VALUE=""
STATS_AUTH_ENV_FILE_VALUE=""

if [ "$USE_AUTH_ENV_FILES" = "true" ]; then
  TRANSFER_AUTH_ENV_FILE_VALUE="$TRANSFER_AUTH_ENV"
  STATS_AUTH_ENV_FILE_VALUE="$STATS_AUTH_ENV"
fi

unset \
  AUTH_TOKENS \
  SENDER_USER_IDS \
  RECEIVER_USER_IDS \
  SENDER_WALLET_IDS \
  RECEIVER_WALLET_IDS \
  RECEIVER_NAMES \
  RECEIVER_PHONE_NUMBERS \
  AUTH_TOKEN \
  ACCESS_TOKEN_COOKIE \
  ACCESS_TOKEN \
  JWT_TOKEN \
  USER_IDS \
  TEST_USER_ID

if [ "$USE_AUTH_ENV_FILES" = "false" ]; then
  required_env=(
    "TRANSFER_AUTH_TOKENS"
    "TRANSFER_SENDER_USER_IDS"
    "TRANSFER_RECEIVER_USER_IDS"
    "TRANSFER_RECEIVER_NAMES"
    "TRANSFER_RECEIVER_PHONE_NUMBERS"
    "TRANSFER_AMOUNT_VALUE"
    "STATS_AUTH_TOKEN"
    "STATS_USER_ID_VALUES"
  )

  for name in "${required_env[@]}"; do
    if [ -z "${!name:-}" ]; then
      echo "$name 값이 필요합니다. TRANSFER_AUTH_ENV=$TRANSFER_AUTH_ENV, STATS_AUTH_ENV=$STATS_AUTH_ENV 를 확인해 주세요." >&2
      exit 1
    fi
  done
fi

BASE_URL="${BASE_URL:-http://localhost:8080}"
MODE="${MODE:-MATERIALIZED_VIEW}"
SCENARIO_LABEL="${SCENARIO_LABEL:-single-db-mv}"
DURATION="${DURATION:-30s}"
TRANSFER_RATE="${TRANSFER_RATE:-10}"
STATISTICS_RATE="${STATISTICS_RATE:-10}"
TRANSFER_VUS="${TRANSFER_VUS:-20}"
STATISTICS_VUS="${STATISTICS_VUS:-20}"
TRANSFER_MAX_VUS="${TRANSFER_MAX_VUS:-50}"
STATISTICS_MAX_VUS="${STATISTICS_MAX_VUS:-50}"

safe_label="$(printf '%s' "$SCENARIO_LABEL" | tr '[:upper:]' '[:lower:]' | tr -c 'a-z0-9._-' '-')"
json_output="$OUTPUT_DIR/transaction-transfer-statistics-replica-car-$safe_label.json"
text_output="$OUTPUT_DIR/transaction-transfer-statistics-replica-car-$safe_label.txt"

echo "송금 API + MV 통계 API constant-arrival-rate 실험을 실행합니다."
echo "- scenario: $SCENARIO_LABEL"
echo "- baseUrl: $BASE_URL"
echo "- mode: $MODE"
echo "- transferRate: $TRANSFER_RATE/s"
echo "- statisticsRate: $STATISTICS_RATE/s"
echo "- duration: $DURATION"
echo "raw 결과와 auth env는 $OUTPUT_DIR 하위 로컬 산출물이며 커밋하지 않습니다."

BASE_URL="$BASE_URL" \
MODE="$MODE" \
FROM_MONTH="$STATS_FROM_MONTH" \
TO_MONTH="$STATS_TO_MONTH" \
TRANSFER_AUTH_ENV_FILE="$TRANSFER_AUTH_ENV_FILE_VALUE" \
STATS_AUTH_ENV_FILE="$STATS_AUTH_ENV_FILE_VALUE" \
DURATION="$DURATION" \
TRANSFER_RATE="$TRANSFER_RATE" \
STATISTICS_RATE="$STATISTICS_RATE" \
TRANSFER_VUS="$TRANSFER_VUS" \
STATISTICS_VUS="$STATISTICS_VUS" \
TRANSFER_MAX_VUS="$TRANSFER_MAX_VUS" \
STATISTICS_MAX_VUS="$STATISTICS_MAX_VUS" \
TRANSFER_AUTH_TOKENS="${TRANSFER_AUTH_TOKENS:-}" \
SENDER_USER_IDS="${TRANSFER_SENDER_USER_IDS:-}" \
RECEIVER_USER_IDS="${TRANSFER_RECEIVER_USER_IDS:-}" \
RECEIVER_NAMES="${TRANSFER_RECEIVER_NAMES:-}" \
RECEIVER_PHONE_NUMBERS="${TRANSFER_RECEIVER_PHONE_NUMBERS:-}" \
FROM_CURRENCY="${TRANSFER_FROM_CURRENCY:-}" \
TO_CURRENCY="${TRANSFER_TO_CURRENCY:-}" \
TRANSFER_AMOUNT="${TRANSFER_AMOUNT_VALUE:-}" \
ACCESS_TOKEN="${STATS_AUTH_TOKEN:-}" \
STATS_USER_IDS="${STATS_USER_ID_VALUES:-}" \
DEBUG_STATUS="${DEBUG_STATUS:-false}" \
k6 run --summary-export "$json_output" "$SCRIPT" > "$text_output"

echo "constant-arrival-rate 실험 완료."
echo "- summary json: $json_output"
echo "- text output: $text_output"
