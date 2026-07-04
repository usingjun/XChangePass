#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT="$ROOT_DIR/k6-scripts/transaction-transfer-statistics-mixed.js"
OUTPUT_DIR="$ROOT_DIR/build/perf/k6"

if ! command -v k6 >/dev/null 2>&1; then
  echo "k6가 설치되어 있지 않습니다. k6 설치 후 다시 실행해 주세요." >&2
  echo "예: brew install k6" >&2
  exit 127
fi

mkdir -p "$OUTPUT_DIR"

TRANSFER_AUTH_ENV="${TRANSFER_AUTH_ENV:-$OUTPUT_DIR/transaction-transfer-auth.env}"
STATS_AUTH_ENV="${STATS_AUTH_ENV:-$OUTPUT_DIR/transaction-statistics-auth.env}"

if [ -f "$TRANSFER_AUTH_ENV" ]; then
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

if [ -f "$STATS_AUTH_ENV" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$STATS_AUTH_ENV"
  set +a
fi

STATS_AUTH_TOKEN="${ACCESS_TOKEN_COOKIE:-${AUTH_TOKEN:-${ACCESS_TOKEN:-${JWT_TOKEN:-}}}}"
STATS_USER_ID_VALUES="${USER_IDS:-${TEST_USER_ID:-}}"
STATS_FROM_MONTH="${FROM_MONTH:-2026-01}"
STATS_TO_MONTH="${TO_MONTH:-2026-12}"

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

BASE_URL="${BASE_URL:-http://localhost:8080}"
K6_VUS="${K6_VUS:-10}"
K6_DURATION="${K6_DURATION:-30s}"
K6_SLEEP="${K6_SLEEP:-1}"
MODES="${MODES:-GROUP_BY MATERIALIZED_VIEW SUMMARY}"

echo "송금 API + 거래 통계 API k6 혼합 부하를 실행합니다."
echo "서버가 먼저 실행되어 있어야 합니다. BASE_URL=$BASE_URL"
echo "결과 raw 파일은 $OUTPUT_DIR 하위에 저장되며 커밋 대상이 아닙니다."

status=0

for mode in $MODES; do
  lower_mode="$(printf '%s' "$mode" | tr '[:upper:]' '[:lower:]' | tr '_' '-')"
  json_output="$OUTPUT_DIR/transaction-transfer-statistics-mixed-$lower_mode.json"
  text_output="$OUTPUT_DIR/transaction-transfer-statistics-mixed-$lower_mode.txt"

  echo
  echo "mixed mode=$mode 실행 중..."

  if ! BASE_URL="$BASE_URL" \
    STATS_MODE="$mode" \
    FROM_MONTH="$STATS_FROM_MONTH" \
    TO_MONTH="$STATS_TO_MONTH" \
    K6_VUS="$K6_VUS" \
    K6_DURATION="$K6_DURATION" \
    K6_SLEEP="$K6_SLEEP" \
    TRANSFER_AUTH_TOKENS="$TRANSFER_AUTH_TOKENS" \
    SENDER_USER_IDS="$TRANSFER_SENDER_USER_IDS" \
    RECEIVER_USER_IDS="$TRANSFER_RECEIVER_USER_IDS" \
    RECEIVER_NAMES="$TRANSFER_RECEIVER_NAMES" \
    RECEIVER_PHONE_NUMBERS="$TRANSFER_RECEIVER_PHONE_NUMBERS" \
    FROM_CURRENCY="$TRANSFER_FROM_CURRENCY" \
    TO_CURRENCY="$TRANSFER_TO_CURRENCY" \
    TRANSFER_AMOUNT="$TRANSFER_AMOUNT_VALUE" \
    STATS_ACCESS_TOKEN_COOKIE="$STATS_AUTH_TOKEN" \
    STATS_USER_IDS="$STATS_USER_ID_VALUES" \
    DEBUG_STATUS="${DEBUG_STATUS:-false}" \
    k6 run --summary-export "$json_output" "$SCRIPT" > "$text_output"; then
    echo "mixed mode=$mode 실행 실패. 출력 파일: $text_output" >&2
    status=1
  else
    echo "mixed mode=$mode 실행 완료."
    echo "- summary json: $json_output"
    echo "- text output: $text_output"
  fi
done

exit "$status"
