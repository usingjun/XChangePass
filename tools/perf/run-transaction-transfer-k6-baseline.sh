#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT="$ROOT_DIR/k6-scripts/transaction-transfer-baseline.js"
OUTPUT_DIR="$ROOT_DIR/build/perf/k6"

if ! command -v k6 >/dev/null 2>&1; then
  echo "k6가 설치되어 있지 않습니다. k6 설치 후 다시 실행해 주세요." >&2
  echo "예: brew install k6" >&2
  exit 127
fi

mkdir -p "$OUTPUT_DIR"

AUTH_ENV="${AUTH_ENV:-$OUTPUT_DIR/transaction-transfer-auth.env}"
if [ -f "$AUTH_ENV" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$AUTH_ENV"
  set +a
fi

BASE_URL="${BASE_URL:-http://localhost:8080}"
K6_VUS="${K6_VUS:-10}"
K6_DURATION="${K6_DURATION:-30s}"
K6_SLEEP="${K6_SLEEP:-0.1}"

required_env=(
  "AUTH_TOKENS"
  "SENDER_USER_IDS"
  "RECEIVER_USER_IDS"
  "RECEIVER_NAMES"
  "RECEIVER_PHONE_NUMBERS"
  "TRANSFER_AMOUNT"
)

for name in "${required_env[@]}"; do
  if [ -z "${!name:-}" ]; then
    echo "$name 값이 필요합니다. AUTH_ENV=$AUTH_ENV 를 확인해 주세요." >&2
    exit 1
  fi
done

json_output="$OUTPUT_DIR/transaction-transfer-baseline.json"
text_output="$OUTPUT_DIR/transaction-transfer-baseline.txt"

echo "송금 API k6 baseline을 실행합니다."
echo "서버가 먼저 실행되어 있어야 합니다. BASE_URL=$BASE_URL"
echo "결과 raw 파일은 $OUTPUT_DIR 하위에 저장되며 커밋 대상이 아닙니다."

BASE_URL="$BASE_URL" \
K6_VUS="$K6_VUS" \
K6_DURATION="$K6_DURATION" \
K6_SLEEP="$K6_SLEEP" \
AUTH_TOKENS="$AUTH_TOKENS" \
SENDER_USER_IDS="$SENDER_USER_IDS" \
RECEIVER_USER_IDS="$RECEIVER_USER_IDS" \
RECEIVER_NAMES="$RECEIVER_NAMES" \
RECEIVER_PHONE_NUMBERS="$RECEIVER_PHONE_NUMBERS" \
FROM_CURRENCY="${FROM_CURRENCY:-${CURRENCY:-KRW}}" \
TO_CURRENCY="${TO_CURRENCY:-${CURRENCY:-KRW}}" \
CURRENCY="${CURRENCY:-KRW}" \
TRANSFER_AMOUNT="$TRANSFER_AMOUNT" \
DEBUG_STATUS="${DEBUG_STATUS:-false}" \
k6 run --summary-export "$json_output" "$SCRIPT" > "$text_output"

echo "송금 API k6 baseline 실행 완료."
echo "- summary json: $json_output"
echo "- text output: $text_output"
