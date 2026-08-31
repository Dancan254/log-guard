#!/usr/bin/env bash
# Builds a Boot app whose only log-guard configuration is the dependency itself, runs it, and
# checks the two masking layers reached the console.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
output="$(mktemp)"
trap 'rm -f "$output"' EXIT

(cd "$here/clean-app" && mvn --batch-mode -q clean package)
java -jar "$here"/clean-app/target/clean-app-0.0.1-SNAPSHOT.jar > "$output" 2>&1 || true

echo "--- app output ---"
grep SMOKE "$output" || true
echo "------------------"

fail=0
grep -q 'SMOKE type-aware Customer(id=42, email=j\*\*\*\*@acme.io, nationalId=\*\*\*, city=Nairobi)' "$output" \
    || { echo "FAIL: the type-aware layer did not mask"; fail=1; }
grep -q 'SMOKE pattern mailing \*\*\* now' "$output" \
    || { echo "FAIL: the pattern layer did not mask"; fail=1; }
grep -q 'jane.wanjiru@acme.io' "$output" \
    && { echo "FAIL: a raw address reached the console"; fail=1; }

[ "$fail" -eq 0 ] && echo "OK: the starter masked with no configuration beyond the dependency"
exit "$fail"
