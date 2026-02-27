#!/usr/bin/env bash
# Shared validation loop for action.yml and ci.yml.
#
# Runs the checker once per model with --format sarif, derives all
# outputs (valid, error-count, warning-count) from the SARIF JSON,
# and produces a merged SARIF file for Code Scanning upload.
#
# Required env vars:
#   CHECKER_CMD      – command to invoke the checker (e.g. "java -jar eventb-checker.jar")
#   MODEL_GLOB       – glob pattern for .zip model files
#
# Optional env vars:
#   SHOW_INFO_FLAG   – "--show-info" or "" (default: "")

set -euo pipefail

: "${CHECKER_CMD:?CHECKER_CMD is required}"
: "${MODEL_GLOB:?MODEL_GLOB is required}"
: "${SHOW_INFO_FLAG:=}"

all_valid=true
total_errors=0
total_warnings=0
failures=0
merged_runs="[]"

for zip in $MODEL_GLOB; do
  if [ ! -f "$zip" ]; then
    continue
  fi
  echo "::group::Validating $zip"

  # Single run with SARIF output
  sarif_output=$($CHECKER_CMD --format sarif $SHOW_INFO_FLAG "$zip" 2>/tmp/checker_stderr) && checker_rc=0 || checker_rc=$?

  # If checker crashed (exit code 2) or output is not valid JSON, handle gracefully
  if [ "$checker_rc" -eq 2 ] || ! echo "$sarif_output" | jq empty 2>/dev/null; then
    stderr_msg=$(cat /tmp/checker_stderr 2>/dev/null || echo "Unknown error")
    echo "::error::Infrastructure error validating $zip: $stderr_msg"
    all_valid=false
    failures=$((failures + 1))
    echo "::endgroup::"
    continue
  fi

  # Extract counts from SARIF results
  model_errors=$(echo "$sarif_output" | jq '[.runs[0].results[] | select(.level == "error")] | length')
  model_warnings=$(echo "$sarif_output" | jq '[.runs[0].results[] | select(.level == "warning")] | length')

  total_errors=$((total_errors + model_errors))
  total_warnings=$((total_warnings + model_warnings))

  if [ "$model_errors" -gt 0 ]; then
    all_valid=false
    failures=$((failures + 1))
  fi

  # Emit GitHub annotations from SARIF results
  echo "$sarif_output" | jq -r '
    .runs[0].results[] | select(.level == "error") |
    "::error file=\(.locations[0].physicalLocation.artifactLocation.uri)::\(.message.text | gsub("%"; "%25") | gsub("\n"; "%0A") | gsub("\r"; "%0D"))"'
  echo "$sarif_output" | jq -r '
    .runs[0].results[] | select(.level == "warning") |
    "::warning file=\(.locations[0].physicalLocation.artifactLocation.uri)::\(.message.text | gsub("%"; "%25") | gsub("\n"; "%0A") | gsub("\r"; "%0D"))"'

  # Merge runs into combined SARIF
  merged_runs=$(echo "$merged_runs" | jq --argjson run "$(echo "$sarif_output" | jq '.runs[0]')" '. + [$run]')

  echo "::endgroup::"
done

# Write merged SARIF file
jq -n \
  --arg schema "https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/schemas/sarif-schema-2.1.0.json" \
  --argjson runs "$merged_runs" \
  '{"$schema": $schema, "version": "2.1.0", "runs": $runs}' \
  > eventb-checker-results.sarif

# Set outputs
echo "valid=$all_valid" >> "$GITHUB_OUTPUT"
echo "error-count=$total_errors" >> "$GITHUB_OUTPUT"
echo "warning-count=$total_warnings" >> "$GITHUB_OUTPUT"
echo "sarif-file=eventb-checker-results.sarif" >> "$GITHUB_OUTPUT"

if [ "$failures" -gt 0 ]; then
  echo "::error::$failures model(s) failed validation"
  exit 1
fi
