#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
BUILD_DIR="$ROOT_DIR/build/perf"
mkdir -p "$BUILD_DIR"

POSTGRES_JAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.postgresql/postgresql" -name 'postgresql-*.jar' | sort | tail -1)"

CP="$POSTGRES_JAR:$BUILD_DIR"

javac -cp "$CP" -d "$BUILD_DIR" "$ROOT_DIR/tools/perf/TransactionReadBenchmark.java"
java -cp "$CP" TransactionReadBenchmark
