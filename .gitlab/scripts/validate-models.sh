#!/usr/bin/env bash
# GitLab CI validation loop — produces JUnit XML, JSON report, and SARIF.
#
# Required env vars:
#   CHECKER_CMD      – command to invoke the checker
#   MODEL_GLOB       – glob pattern for .zip model files
#
# Optional env vars:
#   SHOW_INFO_FLAG   – "--show-info" or "" (default: "")
#   PROOFS_FLAG      – "--proofs" or "" (default: "")
#
# Outputs:
#   eventb-validation-results.xml  – JUnit XML with one <testcase> per model
#   eventb-validation-report.json  – structured JSON results
#   eventb-checker-results.sarif   – merged SARIF file

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../../scripts/validate-models-core.sh
source "$SCRIPT_DIR/../../scripts/validate-models-core.sh"

model_count=0
testcases=""

on_model_start() {
  model_count=$((model_count + 1))
  echo "--- Validating $1 ---"
}

on_model_crash() {
  local model_name
  model_name=$(basename "$1")
  testcases="${testcases}    <testcase name=\"${model_name}\" classname=\"eventb-validation\">
      <error message=\"Infrastructure error\">${2}</error>
    </testcase>
"
  echo "ERROR: Infrastructure error validating $1: $2"
}

on_model_result() {
  local zip="$1"
  local sarif_output="$2"
  local model_errors="$3"
  local model_warnings="$4"
  local model_name
  model_name=$(basename "$zip")

  if [ "$model_errors" -gt 0 ]; then
    local error_messages
    error_messages=$(echo "$sarif_output" | jq -r '
      .runs[0].results[] | select(.level == "error") |
      "\(.locations[0].physicalLocation.artifactLocation.uri): \(.message.text)"')
    testcases="${testcases}    <testcase name=\"${model_name}\" classname=\"eventb-validation\">
      <failure message=\"${model_errors} error(s), ${model_warnings} warning(s)\">${error_messages}</failure>
    </testcase>
"
  else
    testcases="${testcases}    <testcase name=\"${model_name}\" classname=\"eventb-validation\"/>
"
  fi
}

on_model_end() {
  echo ""
}

on_complete() {
  local all_valid="$1"
  local total_errors="$2"
  local total_warnings="$3"
  local failures="$4"

  # Write JUnit XML report
  cat > eventb-validation-results.xml <<XMLEOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="eventb-validation" tests="${model_count}" failures="${failures}" errors="0">
${testcases}</testsuite>
XMLEOF

  # Write structured JSON report
  jq -n \
    --argjson valid "$all_valid" \
    --argjson errors "$total_errors" \
    --argjson warnings "$total_warnings" \
    '{valid: $valid, errorCount: $errors, warningCount: $warnings}' \
    > eventb-validation-report.json

  echo "=== Summary ==="
  echo "Models checked: $model_count"
  echo "Total errors:   $total_errors"
  echo "Total warnings: $total_warnings"
  echo "All valid:      $all_valid"

  if [ "$failures" -gt 0 ]; then
    echo "FAILED: $failures model(s) failed validation"
  fi
}

validate_models
