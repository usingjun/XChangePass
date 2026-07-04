#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
BUILD_DIR="$ROOT_DIR/build/perf"
mkdir -p "$BUILD_DIR/k6"

if [ -z "${JWT_SECRET:-}" ]; then
  JWT_SECRET="$(awk '
    /^jwt:/ { in_jwt = 1; next }
    in_jwt && /^[^[:space:]]/ { in_jwt = 0 }
    in_jwt && /^[[:space:]]+secret:/ { print $2; exit }
  ' "$ROOT_DIR/src/main/resources/application.yaml")"
fi

if [ -z "${JWT_SECRET:-}" ]; then
  echo "JWT_SECRET is required. Set JWT_SECRET or provide src/main/resources/application.yaml." >&2
  exit 1
fi

export JWT_SECRET

POSTGRES_JAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.postgresql/postgresql" -name 'postgresql-*.jar' | sort | tail -1)"
SECURITY_CRYPTO_JAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.springframework.security/spring-security-crypto" -name 'spring-security-crypto-*.jar' ! -name '*sources.jar' | sort | tail -1)"
JJWT_API_JAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/io.jsonwebtoken/jjwt-api" -name 'jjwt-api-*.jar' ! -name '*sources.jar' | sort | tail -1)"
JJWT_IMPL_JAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/io.jsonwebtoken/jjwt-impl" -name 'jjwt-impl-*.jar' ! -name '*sources.jar' | sort | tail -1)"
JJWT_JACKSON_JAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/io.jsonwebtoken/jjwt-jackson" -name 'jjwt-jackson-*.jar' ! -name '*sources.jar' | sort | tail -1)"
JACKSON_DATABIND_JAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/com.fasterxml.jackson.core/jackson-databind" -name 'jackson-databind-*.jar' ! -name '*sources.jar' | sort | tail -1)"
JACKSON_CORE_JAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/com.fasterxml.jackson.core/jackson-core" -name 'jackson-core-*.jar' ! -name '*sources.jar' | sort | tail -1)"
JACKSON_ANNOTATIONS_JAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/com.fasterxml.jackson.core/jackson-annotations" -name 'jackson-annotations-*.jar' ! -name '*sources.jar' | sort | tail -1)"
COMMONS_LOGGING_JAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/commons-logging/commons-logging" -name 'commons-logging-*.jar' ! -name '*sources.jar' | sort | tail -1)"

CP="$POSTGRES_JAR:$SECURITY_CRYPTO_JAR:$JJWT_API_JAR:$JJWT_IMPL_JAR:$JJWT_JACKSON_JAR:$JACKSON_DATABIND_JAR:$JACKSON_CORE_JAR:$JACKSON_ANNOTATIONS_JAR:$COMMONS_LOGGING_JAR:$BUILD_DIR"

javac -cp "$CP" -d "$BUILD_DIR" "$ROOT_DIR/tools/perf/TransactionTransferK6Seed.java"
java -cp "$CP" TransactionTransferK6Seed

echo "auth env: $ROOT_DIR/build/perf/k6/transaction-transfer-auth.env"
