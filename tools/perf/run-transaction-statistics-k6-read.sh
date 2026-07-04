#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT="$ROOT_DIR/k6-scripts/transaction-statistics-read.js"
OUTPUT_DIR="$ROOT_DIR/build/perf/k6"

if ! command -v k6 >/dev/null 2>&1; then
  echo "k6가 설치되어 있지 않습니다. k6 설치 후 다시 실행해 주세요." >&2
  echo "예: brew install k6" >&2
  exit 127
fi

mkdir -p "$OUTPUT_DIR"

BASE_URL="${BASE_URL:-http://localhost:8080}"
FROM_MONTH="${FROM_MONTH:-2026-01}"
TO_MONTH="${TO_MONTH:-2026-12}"
USER_IDS="${USER_IDS:-1}"
K6_VUS="${K6_VUS:-10}"
K6_DURATION="${K6_DURATION:-30s}"
K6_SLEEP="${K6_SLEEP:-0.1}"
MODES="${MODES:-GROUP_BY MATERIALIZED_VIEW SUMMARY}"

echo "거래 통계 k6 read 부하 테스트를 실행합니다."
echo "서버가 먼저 실행되어 있어야 합니다. BASE_URL=$BASE_URL"
echo "결과 raw 파일은 $OUTPUT_DIR 하위에 저장되며 커밋 대상이 아닙니다."

status=0

for mode in $MODES; do
  lower_mode="$(printf '%s' "$mode" | tr '[:upper:]' '[:lower:]' | tr '_' '-')"
  json_output="$OUTPUT_DIR/transaction-statistics-read-$lower_mode.json"
  text_output="$OUTPUT_DIR/transaction-statistics-read-$lower_mode.txt"

  echo
  echo "mode=$mode 실행 중..."

  if ! BASE_URL="$BASE_URL" \
    MODE="$mode" \
    FROM_MONTH="$FROM_MONTH" \
    TO_MONTH="$TO_MONTH" \
    USER_IDS="$USER_IDS" \
    K6_VUS="$K6_VUS" \
    K6_DURATION="$K6_DURATION" \
    K6_SLEEP="$K6_SLEEP" \
    AUTH_TOKEN="${AUTH_TOKEN:-}" \
    ACCESS_TOKEN="${ACCESS_TOKEN:-}" \
    JWT_TOKEN="${JWT_TOKEN:-}" \
    ACCESS_TOKEN_COOKIE="${ACCESS_TOKEN_COOKIE:-}" \
    LOGIN_EMAIL="${LOGIN_EMAIL:-}" \
    LOGIN_PASSWORD="${LOGIN_PASSWORD:-}" \
    k6 run --summary-export "$json_output" "$SCRIPT" > "$text_output"; then
    echo "mode=$mode 실행 실패. 출력 파일: $text_output" >&2
    status=1
  else
    echo "mode=$mode 실행 완료."
    echo "- summary json: $json_output"
    echo "- text output: $text_output"
  fi
done

exit "$status"
