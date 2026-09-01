#!/usr/bin/env bash
# Fails the build when a benchmark crosses its ceiling.
#
# The ceilings are deliberately ~10x the numbers measured on a developer laptop. A shared CI runner
# is slower and noisier than any laptop, and a benchmark that fails on noise gets deleted within a
# month. What these catch is the shape of regression this project has already had once: the
# prefilter stopped short-circuiting and a clean line went from 126 ns to 14,000.
set -euo pipefail

report="${1:-$(dirname "$0")/target/benchmarks.txt}"

# benchmark name → ceiling in ns/op → what it measures
thresholds=(
  "noPiiFastPath|200|an argument with no @Pii anywhere"
  "cleanMessageThroughPatterns|3000|a log line with nothing to mask"
  "typeAwareRender|20000|rendering an annotated entity"
  "wideEntityRender|20000|rendering a 20-field entity"
  "nestedRender|60000|rendering a list of three entities"
  "messageWithPiiThroughPatterns|80000|a line that does contain an email"
  "fullPipeline|250000|both layers on one event"
)

if [ ! -f "$report" ]; then
  echo "FAIL: no benchmark report at $report" >&2
  exit 1
fi

echo "--- $report ---"
cat "$report"
echo "---"

fail=0
checked=0

for entry in "${thresholds[@]}"; do
  IFS='|' read -r name ceiling description <<< "$entry"

  # Score is the 4th column: Benchmark  Mode  Cnt  Score  ±  Error  Units
  score=$(awk -v n="$name" '$1 ~ n {gsub(",", "", $4); print $4; exit}' "$report")
  if [ -z "$score" ]; then
    echo "FAIL: $name is missing from the report — was it renamed or removed?" >&2
    fail=1
    continue
  fi

  checked=$((checked + 1))
  rounded=${score%.*}
  if [ "$rounded" -gt "$ceiling" ]; then
    printf 'FAIL: %-30s %10s ns/op exceeds %s — %s\n' "$name" "$score" "$ceiling" "$description" >&2
    fail=1
  else
    printf 'ok:   %-30s %10s ns/op (ceiling %s)\n' "$name" "$score" "$ceiling"
  fi
done

if [ "$fail" -eq 0 ]; then
  echo "$checked benchmarks within their ceilings"
fi
exit "$fail"
