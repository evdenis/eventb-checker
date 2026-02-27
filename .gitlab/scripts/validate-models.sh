#!/usr/bin/env bash
# GitLab-compatible validation loop for Event-B models.
#
# Runs the checker once per model with --format sarif, derives all
# outputs from the SARIF JSON, and produces JUnit XML for MR widgets
# plus a merged SARIF file.
#
# Required env vars:
#   CHECKER_CMD      – command to invoke the checker (e.g. "java -jar eventb-checker.jar")
#   MODEL_GLOB       – glob pattern for .zip model files
#
# Optional env vars:
#   SHOW_INFO_FLAG   – "--show-info" or "" (default: "")
#
# Outputs:
#   eventb-validation-results.xml  – JUnit XML with one <testcase> per model
#   eventb-validation-report.json  – structured JSON results
#   eventb-checker-results.sarif   – merged SARIF file

set -euo pipefail

: "${CHECKER_CMD:?CHECKER_CMD is required}"
: "${MODEL_GLOB:?MODEL_GLOB is required}"
: "${SHOW_INFO_FLAG:=}"

all_valid=true
total_errors=0
total_warnings=0
failures=0
model_count=0
testcases=""
merged_runs="[]"

for zip in $MODEL_GLOB; do
  if [ ! -f "$zip" ]; then
    continue
  fi
  model_count=$((model_count + 1))
  model_name=$(basename "$zip")
  echo "--- Validating $zip ---"

  # Single run with SARIF output
  sarif_output=$($CHECKER_CMD --format sarif $SHOW_INFO_FLAG "$zip" 2>/tmp/checker_stderr) && checker_rc=0 || checker_rc=$?

  # If checker crashed (exit code 2) or output is not valid JSON, handle gracefully
  if [ "$checker_rc" -eq 2 ] || ! echo "$sarif_output" | jq empty 2>/dev/null; then
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

  # Extract counts from SARIF results
  model_errors=$(echo "$sarif_output" | jq '[.runs[0].results[] | select(.level == "error")] | length')
  model_warnings=$(echo "$sarif_output" | jq '[.runs[0].results[] | select(.level == "warning")] | length')

  total_errors=$((total_errors + model_errors))
  total_warnings=$((total_warnings + model_warnings))

  if [ "$model_errors" -gt 0 ]; then
    all_valid=false
    failures=$((failures + 1))
    # Collect error messages for JUnit failure element
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

  # Merge runs into combined SARIF
  merged_runs=$(echo "$merged_runs" | jq --argjson run "$(echo "$sarif_output" | jq '.runs[0]')" '. + [$run]')

  echo ""
done

# Write JUnit XML report
cat > eventb-validation-results.xml <<XMLEOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="eventb-validation" tests="${model_count}" failures="${failures}" errors="0">
${testcases}</testsuite>
XMLEOF

# Write structured JSON report (for backward compatibility)
jq -n \
  --argjson valid "$all_valid" \
  --argjson errors "$total_errors" \
  --argjson warnings "$total_warnings" \
  '{valid: $valid, errorCount: $errors, warningCount: $warnings}' \
  > eventb-validation-report.json

# Write merged SARIF file
jq -n \
  --arg schema "https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/schemas/sarif-schema-2.1.0.json" \
  --argjson runs "$merged_runs" \
  '{"$schema": $schema, "version": "2.1.0", "runs": $runs}' \
  > eventb-checker-results.sarif

echo "=== Summary ==="
echo "Models checked: $model_count"
echo "Total errors:   $total_errors"
echo "Total warnings: $total_warnings"
echo "All valid:      $all_valid"

if [ "$failures" -gt 0 ]; then
  echo "FAILED: $failures model(s) failed validation"
  exit 1
fi
