#!/usr/bin/env bash
# Shared validation loop for action.yml and ci.yml.
#
# Required env vars:
#   CHECKER_CMD   – command to invoke the checker (e.g. "java -jar eventb-checker.jar")
#   MODEL_GLOB    – glob pattern for .zip model files
#   FORMAT        – "text", "json", or "sarif"
#
# Optional env vars:
#   VERBOSE_FLAG  – "--verbose" or "" (default: "")

set -euo pipefail

: "${CHECKER_CMD:?CHECKER_CMD is required}"
: "${MODEL_GLOB:?MODEL_GLOB is required}"
: "${FORMAT:=text}"
: "${VERBOSE_FLAG:=}"

all_valid=true
total_errors=0
total_warnings=0
results="[]"
failures=0

for zip in $MODEL_GLOB; do
  if [ ! -f "$zip" ]; then
    continue
  fi
  echo "::group::Validating $zip"

  # Run with JSON to get structured data; capture exit code
  json_output=$($CHECKER_CMD --format json "$zip" 2>/tmp/checker_stderr) && checker_rc=0 || checker_rc=$?

  # If checker crashed (exit code 2) or output is not valid JSON, handle gracefully
  if [ "$checker_rc" -eq 2 ] || ! echo "$json_output" | jq empty 2>/dev/null; then
    stderr_msg=$(cat /tmp/checker_stderr 2>/dev/null || echo "Unknown error")
    echo "::error::Infrastructure error validating $zip: $stderr_msg"
    all_valid=false
    failures=$((failures + 1))
    echo "::endgroup::"
    continue
  fi

  # Extract fields via jq
  model_valid=$(echo "$json_output" | jq -r '.valid')
  model_errors=$(echo "$json_output" | jq -r '.summary.errorCount')
  model_warnings=$(echo "$json_output" | jq -r '.summary.warningCount')

  # Accumulate totals
  total_errors=$((total_errors + model_errors))
  total_warnings=$((total_warnings + model_warnings))
  results=$(echo "$results" | jq --argjson obj "$json_output" '. + [$obj]')

  if [ "$model_valid" != "true" ]; then
    all_valid=false
    failures=$((failures + 1))
  fi

  # Emit GitHub annotations (percent-encode newlines/carriage returns per workflow command spec)
  echo "$json_output" | jq -r '
    .errors[] | select(.severity == "ERROR") |
    "::error file=\(.file)::\(.message | gsub("%"; "%25") | gsub("\n"; "%0A") | gsub("\r"; "%0D"))"'
  echo "$json_output" | jq -r '
    .errors[] | select(.severity == "WARNING") |
    "::warning file=\(.file)::\(.message | gsub("%"; "%25") | gsub("\n"; "%0A") | gsub("\r"; "%0D"))"'

  # Display output in requested format
  if [ "$FORMAT" = "json" ]; then
    echo "$json_output" | jq .
  elif [ "$FORMAT" = "sarif" ]; then
    $CHECKER_CMD --format sarif $VERBOSE_FLAG "$zip" || true
  else
    $CHECKER_CMD $VERBOSE_FLAG "$zip" || true
  fi

  echo "::endgroup::"
done

# Set outputs
echo "valid=$all_valid" >> "$GITHUB_OUTPUT"
echo "error-count=$total_errors" >> "$GITHUB_OUTPUT"
echo "warning-count=$total_warnings" >> "$GITHUB_OUTPUT"

# Use a delimiter for multi-line JSON output
delimiter="EOF_$(uuidgen 2>/dev/null || date +%s%N)"
echo "result-json<<$delimiter" >> "$GITHUB_OUTPUT"
echo "$results" | jq -c . >> "$GITHUB_OUTPUT"
echo "$delimiter" >> "$GITHUB_OUTPUT"

if [ "$failures" -gt 0 ]; then
  echo "::error::$failures model(s) failed validation"
  exit 1
fi
