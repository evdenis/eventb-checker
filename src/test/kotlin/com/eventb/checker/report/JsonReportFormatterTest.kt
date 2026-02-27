package com.eventb.checker.report

import com.eventb.checker.validation.ValidationError
import com.eventb.checker.validation.ValidationResult
import com.eventb.checker.validation.ValidationSeverity
import com.eventb.checker.validation.ValidationSummary
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test

class JsonReportFormatterTest {

    private val formatter = JsonReportFormatter()

    private fun parse(result: ValidationResult): JSONObject = JSONObject(formatter.format(result))

    @Test
    fun `valid model with no errors produces correct JSON`() {
        val result = ValidationResult(
            errors = emptyList(),
            summary = ValidationSummary(
                machineCount = 2,
                contextCount = 1,
                formulaCount = 14,
                errorCount = 0,
                warningCount = 0,
                infoCount = 0,
            ),
        )

        val json = parse(result)

        assertThat(json.getBoolean("valid")).isTrue()
        assertThat(json.getJSONArray("errors")).isEmpty()

        val summary = json.getJSONObject("summary")
        assertThat(summary.getInt("machineCount")).isEqualTo(2)
        assertThat(summary.getInt("contextCount")).isEqualTo(1)
        assertThat(summary.getInt("formulaCount")).isEqualTo(14)
        assertThat(summary.getInt("errorCount")).isEqualTo(0)
        assertThat(summary.getInt("warningCount")).isEqualTo(0)
        assertThat(summary.getInt("infoCount")).isEqualTo(0)
    }

    @Test
    fun `invalid model with errors includes all error fields`() {
        val result = ValidationResult(
            errors = listOf(
                ValidationError(
                    filePath = "project/Counter.bum",
                    severity = ValidationSeverity.ERROR,
                    message = "Parse error in invariant",
                    element = "inv1",
                    formula = "x ==== y",
                ),
            ),
            summary = ValidationSummary(
                machineCount = 1,
                contextCount = 0,
                formulaCount = 5,
                errorCount = 1,
                warningCount = 0,
            ),
        )

        val json = parse(result)

        assertThat(json.getBoolean("valid")).isFalse()

        val error = json.getJSONArray("errors").getJSONObject(0)
        assertThat(error.getString("file")).isEqualTo("project/Counter.bum")
        assertThat(error.getString("severity")).isEqualTo("ERROR")
        assertThat(error.getString("message")).isEqualTo("Parse error in invariant")
        assertThat(error.getString("element")).isEqualTo("inv1")
        assertThat(error.getString("formula")).isEqualTo("x ==== y")
    }

    @Test
    fun `mixed severities serialize correctly`() {
        val result = ValidationResult(
            errors = listOf(
                ValidationError("a.bum", ValidationSeverity.ERROR, "err msg", "inv1", null),
                ValidationError("b.bum", ValidationSeverity.WARNING, "warn msg", null, "x ∈ ℤ"),
                ValidationError("c.buc", ValidationSeverity.INFO, "info msg", "axm1", null),
            ),
            summary = ValidationSummary(1, 1, 10, 1, 1, 1),
        )

        val json = parse(result)
        val errors = json.getJSONArray("errors")

        assertThat(errors.length()).isEqualTo(3)
        assertThat(errors.getJSONObject(0).getString("severity")).isEqualTo("ERROR")
        assertThat(errors.getJSONObject(1).getString("severity")).isEqualTo("WARNING")
        assertThat(errors.getJSONObject(2).getString("severity")).isEqualTo("INFO")

        val summary = json.getJSONObject("summary")
        assertThat(summary.getInt("errorCount")).isEqualTo(1)
        assertThat(summary.getInt("warningCount")).isEqualTo(1)
        assertThat(summary.getInt("infoCount")).isEqualTo(1)
    }

    @Test
    fun `null element and formula render as JSON null`() {
        val result = ValidationResult(
            errors = listOf(
                ValidationError("a.bum", ValidationSeverity.WARNING, "msg", null, null),
            ),
            summary = ValidationSummary(1, 0, 1, 0, 1),
        )

        val json = parse(result)
        val error = json.getJSONArray("errors").getJSONObject(0)

        assertThat(error.isNull("element")).isTrue()
        assertThat(error.isNull("formula")).isTrue()
    }

    @Test
    fun `special characters are properly escaped`() {
        val result = ValidationResult(
            errors = listOf(
                ValidationError(
                    filePath = "path\\to\\file.bum",
                    severity = ValidationSeverity.ERROR,
                    message = "Expected \"predicate\"\nbut got\ttab",
                    element = null,
                    formula = null,
                ),
            ),
            summary = ValidationSummary(1, 0, 1, 1, 0),
        )

        val json = parse(result)
        val error = json.getJSONArray("errors").getJSONObject(0)

        assertThat(error.getString("file")).isEqualTo("path\\to\\file.bum")
        assertThat(error.getString("message")).isEqualTo("Expected \"predicate\"\nbut got\ttab")
    }

    @Test
    fun `control characters are escaped per RFC 8259`() {
        val result = ValidationResult(
            errors = listOf(
                ValidationError(
                    filePath = "a.bum",
                    severity = ValidationSeverity.ERROR,
                    message = "backspace\b formfeed\u000C null\u0000 bell\u0007",
                    element = null,
                    formula = null,
                ),
            ),
            summary = ValidationSummary(1, 0, 1, 1, 0),
        )

        val json = parse(result)
        val error = json.getJSONArray("errors").getJSONObject(0)

        assertThat(error.getString("message")).isEqualTo("backspace\b formfeed\u000C null\u0000 bell\u0007")
    }

    @Test
    fun `unicode math symbols pass through unescaped`() {
        val result = ValidationResult(
            errors = listOf(
                ValidationError(
                    filePath = "M.bum",
                    severity = ValidationSeverity.WARNING,
                    message = "Type check: x ∈ ℤ ∧ y ∈ ℕ",
                    element = "inv1",
                    formula = "x ∈ ℤ ∧ y ∈ ℕ",
                ),
            ),
            summary = ValidationSummary(1, 0, 1, 0, 1),
        )

        val json = parse(result)
        val error = json.getJSONArray("errors").getJSONObject(0)

        assertThat(error.getString("message")).isEqualTo("Type check: x ∈ ℤ ∧ y ∈ ℕ")
        assertThat(error.getString("formula")).isEqualTo("x ∈ ℤ ∧ y ∈ ℕ")
    }

    @Test
    fun `ruleId is included in JSON output`() {
        val result = ValidationResult(
            errors = listOf(
                ValidationError(
                    filePath = "Counter.bum",
                    severity = ValidationSeverity.ERROR,
                    message = "Formula parse error",
                    element = "inv1",
                    formula = "x ==== y",
                    ruleId = "EB005",
                ),
                ValidationError(
                    filePath = "Counter.bum",
                    severity = ValidationSeverity.WARNING,
                    message = "No ruleId here",
                ),
            ),
            summary = ValidationSummary(1, 0, 5, 1, 1),
        )

        val json = parse(result)
        val errors = json.getJSONArray("errors")

        assertThat(errors.getJSONObject(0).getString("ruleId")).isEqualTo("EB005")
        assertThat(errors.getJSONObject(1).isNull("ruleId")).isTrue()
    }

    @Test
    fun `output parses as valid JSON with correct structure`() {
        val result = ValidationResult(
            errors = listOf(
                ValidationError("a.bum", ValidationSeverity.ERROR, "err", "inv1", "x = 1"),
                ValidationError("b.bum", ValidationSeverity.WARNING, "warn", null, null),
            ),
            summary = ValidationSummary(2, 0, 5, 1, 1),
        )

        val json = parse(result)

        // Verify all top-level keys are present and correctly typed
        assertThat(json.has("valid")).isTrue()
        assertThat(json.has("summary")).isTrue()
        assertThat(json.has("errors")).isTrue()
        assertThat(json.getJSONArray("errors").length()).isEqualTo(2)

        // Verify each error has the required shape
        for (i in 0 until json.getJSONArray("errors").length()) {
            val error = json.getJSONArray("errors").getJSONObject(i)
            assertThat(error.has("file")).isTrue()
            assertThat(error.has("severity")).isTrue()
            assertThat(error.has("message")).isTrue()
            assertThat(error.has("element")).isTrue()
            assertThat(error.has("formula")).isTrue()
        }
    }
}
