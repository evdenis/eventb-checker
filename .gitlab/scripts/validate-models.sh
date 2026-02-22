#!/usr/bin/env bash
# GitLab-compatible validation loop for Event-B models.
#
# Generates JUnit XML (for MR widget) and a structured JSON report.
#
# Required env vars:
#   CHECKER_CMD   – command to invoke the checker (e.g. "java -jar eventb-checker.jar")
#   MODEL_GLOB    – glob pattern for .zip model files
#   FORMAT        – "text" or "json"
#
# Optional env vars:
#   VERBOSE_FLAG  – "--verbose" or "" (default: "")
#
# Outputs:
#   eventb-validation-results.xml  – JUnit XML with one <testcase> per model
#   eventb-validation-report.json  – structured JSON results

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
model_count=0
testcases=""

for zip in $MODEL_GLOB; do
  if [ ! -f "$zip" ]; then
    continue
  fi
  model_count=$((model_count + 1))
  model_name=$(basename "$zip")
  echo "--- Validating $zip ---"

  # Run with JSON to get structured data; capture exit code
  json_output=$($CHECKER_CMD --format json "$zip" 2>/tmp/checker_stderr) && checker_rc=0 || checker_rc=$?

  # If checker crashed (exit code 2) or output is not valid JSON, handle gracefully
  if [ "$checker_rc" -eq 2 ] || ! echo "$json_output" | jq empty 2>/dev/null; then
    stderr_msg=$(cat /tmp/checker_stderr 2>/dev/null || echo "Unknown error")
    echo "ERROR: Infrastructure error validating $zip: $stderr_msg"
    all_valid=false
    failures=$((failures + 1))
    testcases="${testcases}    <testcase name=\"${model_name}\" classname=\"eventb-validation\">
      <error message=\"Infrastructure error\">${stderr_msg}</error>
    </testcase>
"
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
    # Collect error messages for JUnit failure element
    error_messages=$(echo "$json_output" | jq -r '.errors[] | select(.severity == "ERROR") | "\(.file): \(.message)"')
    testcases="${testcases}    <testcase name=\"${model_name}\" classname=\"eventb-validation\">
      <failure message=\"${model_errors} error(s), ${model_warnings} warning(s)\">${error_messages}</failure>
    </testcase>
"
  else
    testcases="${testcases}    <testcase name=\"${model_name}\" classname=\"eventb-validation\"/>
"
  fi

  # Display output in requested format
  if [ "$FORMAT" = "json" ]; then
    echo "$json_output" | jq .
  else
    $CHECKER_CMD $VERBOSE_FLAG "$zip" || true
  fi

  echo ""
done

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
  --argjson results "$results" \
  '{valid: $valid, errorCount: $errors, warningCount: $warnings, results: $results}' \
  > eventb-validation-report.json

echo "=== Summary ==="
echo "Models checked: $model_count"
echo "Total errors:   $total_errors"
echo "Total warnings: $total_warnings"
echo "All valid:      $all_valid"

if [ "$failures" -gt 0 ]; then
  echo "FAILED: $failures model(s) failed validation"
  exit 1
fi
