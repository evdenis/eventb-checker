#!/usr/bin/env bash
# Shared validation core for GitHub Actions and GitLab CI.
#
# Runs the checker once per model with --format sarif, derives all
# outputs (valid, error-count, warning-count) from the SARIF JSON,
# and produces a merged SARIF file.
#
# Required env vars:
#   CHECKER_CMD      – command to invoke the checker (e.g. "java -jar eventb-checker.jar")
#   MODEL_GLOB       – glob pattern for .zip model files
#
# Optional env vars:
#   SHOW_INFO_FLAG   – "--show-info" or "" (default: "")
#   PROOFS_FLAG      – "--proofs" or "" (default: "")
#
# Callers must define two callback functions before sourcing this script:
#   on_model_start  "$zip"                          – called before each model
#   on_model_result "$zip" "$sarif_output" "$model_errors" "$model_warnings"  – called after each model
#   on_model_crash  "$zip" "$stderr_msg"            – called on checker crash
#   on_model_end                                    – called after each model's output
#   on_complete "$all_valid" "$total_errors" "$total_warnings" "$failures" "$merged_runs"
#                                                   – called after the loop finishes

set -euo pipefail

: "${CHECKER_CMD:?CHECKER_CMD is required}"
: "${MODEL_GLOB:?MODEL_GLOB is required}"
: "${SHOW_INFO_FLAG:=}"
: "${PROOFS_FLAG:=}"

validate_models() {
  local all_valid=true
  local total_errors=0
  local total_warnings=0
  local failures=0
  local merged_runs="[]"

  for zip in $MODEL_GLOB; do
    if [ ! -f "$zip" ]; then
      continue
    fi
    on_model_start "$zip"

    # Single run with SARIF output
    local sarif_output
    sarif_output=$($CHECKER_CMD --format sarif $SHOW_INFO_FLAG $PROOFS_FLAG "$zip" 2>/tmp/checker_stderr) && local checker_rc=0 || local checker_rc=$?

    # If checker crashed (exit code 2) or output is not valid JSON, handle gracefully
    if [ "$checker_rc" -eq 2 ] || ! echo "$sarif_output" | jq empty 2>/dev/null; then
      local stderr_msg
      stderr_msg=$(cat /tmp/checker_stderr 2>/dev/null || echo "Unknown error")
      on_model_crash "$zip" "$stderr_msg"
      all_valid=false
      failures=$((failures + 1))
      on_model_end
      continue
    fi

    # Extract counts from SARIF results
    local model_errors
    local model_warnings
    model_errors=$(echo "$sarif_output" | jq '[.runs[0].results[] | select(.level == "error")] | length')
    model_warnings=$(echo "$sarif_output" | jq '[.runs[0].results[] | select(.level == "warning")] | length')

    total_errors=$((total_errors + model_errors))
    total_warnings=$((total_warnings + model_warnings))

    if [ "$model_errors" -gt 0 ]; then
      all_valid=false
      failures=$((failures + 1))
    fi

    on_model_result "$zip" "$sarif_output" "$model_errors" "$model_warnings"

    # Merge runs into combined SARIF
    merged_runs=$(echo "$merged_runs" | jq --argjson run "$(echo "$sarif_output" | jq '.runs[0]')" '. + [$run]')

    on_model_end
  done

  # Write merged SARIF file
  jq -n \
    --arg schema "https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/schemas/sarif-schema-2.1.0.json" \
    --argjson runs "$merged_runs" \
    '{"$schema": $schema, "version": "2.1.0", "runs": $runs}' \
    > eventb-checker-results.sarif

  on_complete "$all_valid" "$total_errors" "$total_warnings" "$failures"

  if [ "$failures" -gt 0 ]; then
    return 1
  fi
}
