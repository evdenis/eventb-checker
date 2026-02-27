package com.eventb.checker.report

import com.eventb.checker.validation.ValidationError
import com.eventb.checker.validation.ValidationResult
import com.eventb.checker.validation.ValidationRules
import com.eventb.checker.validation.ValidationSeverity
import com.eventb.checker.validation.ValidationSummary
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test

class SarifReportFormatterTest {

    private val formatter = SarifReportFormatter()

    private fun parse(result: ValidationResult): JSONObject = JSONObject(formatter.format(result))

    private fun validSummary() = ValidationSummary(
        machineCount = 1,
        contextCount = 1,
        formulaCount = 10,
        errorCount = 0,
        warningCount = 0,
        infoCount = 0,
    )

    @Test
    fun `valid model produces correct SARIF envelope with empty results`() {
        val result = ValidationResult(errors = emptyList(), summary = validSummary())
        val sarif = parse(result)

        assertThat(sarif.getString("\$schema")).contains("sarif-schema-2.1.0")
        assertThat(sarif.getString("version")).isEqualTo("2.1.0")
        assertThat(sarif.getJSONArray("runs")).hasSize(1)

        val run = sarif.getJSONArray("runs").getJSONObject(0)
        val driver = run.getJSONObject("tool").getJSONObject("driver")
        assertThat(driver.getString("name")).isEqualTo("eventb-checker")
        assertThat(driver.has("version")).isTrue()
        assertThat(driver.getString("version")).isNotEmpty()
        assertThat(driver.has("informationUri")).isTrue()
        assertThat(driver.getJSONArray("rules")).isEmpty()

        assertThat(run.getJSONArray("results")).isEmpty()
    }

    @Test
    fun `single error with all fields produces correct SARIF result`() {
        val result = ValidationResult(
            errors = listOf(
                ValidationError(
                    filePath = "project/Counter.bum",
                    severity = ValidationSeverity.ERROR,
                    message = "Formula parse error: unexpected token",
                    element = "inv1",
                    formula = "x ==== y",
                    ruleId = ValidationRules.FORMULA_PARSE_ERROR.id,
                ),
            ),
            summary = ValidationSummary(1, 0, 5, 1, 0),
        )

        val sarif = parse(result)
        val run = sarif.getJSONArray("runs").getJSONObject(0)
        val sarifResult = run.getJSONArray("results").getJSONObject(0)

        assertThat(sarifResult.getString("ruleId")).isEqualTo("EB005")
        assertThat(sarifResult.getString("level")).isEqualTo("error")
        assertThat(sarifResult.getJSONObject("message").getString("text"))
            .isEqualTo("Formula parse error: unexpected token")

        val location = sarifResult.getJSONArray("locations").getJSONObject(0)
        assertThat(
            location.getJSONObject("physicalLocation")
                .getJSONObject("artifactLocation").getString("uri"),
        )
            .isEqualTo("project/Counter.bum")

        val logicalLocation = location.getJSONArray("logicalLocations").getJSONObject(0)
        assertThat(logicalLocation.getString("name")).isEqualTo("inv1")
        assertThat(logicalLocation.getString("kind")).isEqualTo("element")

        assertThat(sarifResult.getJSONObject("properties").getString("formula")).isEqualTo("x ==== y")
    }

    @Test
    fun `severity mapping ERROR to error, WARNING to warning, INFO to note`() {
        val result = ValidationResult(
            errors = listOf(
                ValidationError("a.bum", ValidationSeverity.ERROR, "err", ruleId = "EB001"),
                ValidationError("b.bum", ValidationSeverity.WARNING, "warn", ruleId = "EB006"),
                ValidationError("c.buc", ValidationSeverity.INFO, "info", ruleId = "EB010"),
            ),
            summary = ValidationSummary(1, 1, 10, 1, 1, 1),
        )

        val sarif = parse(result)
        val results = sarif.getJSONArray("runs").getJSONObject(0).getJSONArray("results")

        assertThat(results.getJSONObject(0).getString("level")).isEqualTo("error")
        assertThat(results.getJSONObject(1).getString("level")).isEqualTo("warning")
        assertThat(results.getJSONObject(2).getString("level")).isEqualTo("note")
    }

    @Test
    fun `null element and formula are omitted from SARIF output`() {
        val result = ValidationResult(
            errors = listOf(
                ValidationError("a.bum", ValidationSeverity.WARNING, "msg", null, null, ruleId = "EB001"),
            ),
            summary = ValidationSummary(1, 0, 1, 0, 1),
        )

        val sarif = parse(result)
        val sarifResult = sarif.getJSONArray("runs").getJSONObject(0)
            .getJSONArray("results").getJSONObject(0)

        val location = sarifResult.getJSONArray("locations").getJSONObject(0)
        assertThat(location.has("logicalLocations")).isFalse()
        assertThat(sarifResult.has("properties")).isFalse()
    }

    @Test
    fun `rules array contains only rules referenced by results`() {
        val result = ValidationResult(
            errors = listOf(
                ValidationError("a.bum", ValidationSeverity.ERROR, "err1", ruleId = ValidationRules.FORMULA_PARSE_ERROR.id),
                ValidationError("b.bum", ValidationSeverity.WARNING, "err2", ruleId = ValidationRules.TYPE_ERROR.id),
                ValidationError("c.bum", ValidationSeverity.ERROR, "err3", ruleId = ValidationRules.FORMULA_PARSE_ERROR.id),
            ),
            summary = ValidationSummary(1, 0, 5, 2, 1),
        )

        val sarif = parse(result)
        val rules = sarif.getJSONArray("runs").getJSONObject(0)
            .getJSONObject("tool").getJSONObject("driver").getJSONArray("rules")

        assertThat(rules.length()).isEqualTo(2)

        val ruleIds = (0 until rules.length()).map { rules.getJSONObject(it).getString("id") }.toSet()
        assertThat(ruleIds).containsExactlyInAnyOrder("EB005", "EB006")

        val rule = rules.getJSONObject(0)
        assertThat(rule.has("shortDescription")).isTrue()
        assertThat(rule.has("fullDescription")).isTrue()
    }

    @Test
    fun `no ruleId means result has no ruleId field`() {
        val result = ValidationResult(
            errors = listOf(
                ValidationError("a.bum", ValidationSeverity.ERROR, "some error"),
            ),
            summary = ValidationSummary(1, 0, 1, 1, 0),
        )

        val sarif = parse(result)
        val sarifResult = sarif.getJSONArray("runs").getJSONObject(0)
            .getJSONArray("results").getJSONObject(0)

        assertThat(sarifResult.has("ruleId")).isFalse()
    }

    @Test
    fun `unicode math symbols are preserved`() {
        val result = ValidationResult(
            errors = listOf(
                ValidationError(
                    filePath = "M.bum",
                    severity = ValidationSeverity.WARNING,
                    message = "Type check: x ∈ ℤ ∧ y ∈ ℕ",
                    element = "inv1",
                    formula = "x ∈ ℤ ∧ y ∈ ℕ",
                    ruleId = ValidationRules.TYPE_ERROR.id,
                ),
            ),
            summary = ValidationSummary(1, 0, 1, 0, 1),
        )

        val sarif = parse(result)
        val sarifResult = sarif.getJSONArray("runs").getJSONObject(0)
            .getJSONArray("results").getJSONObject(0)

        assertThat(sarifResult.getJSONObject("message").getString("text"))
            .isEqualTo("Type check: x ∈ ℤ ∧ y ∈ ℕ")
        assertThat(sarifResult.getJSONObject("properties").getString("formula"))
            .isEqualTo("x ∈ ℤ ∧ y ∈ ℕ")
    }

    @Test
    fun `overall structure validation with multiple errors`() {
        val result = ValidationResult(
            errors = listOf(
                ValidationError("a.bum", ValidationSeverity.ERROR, "err", "inv1", "x = 1", ruleId = "EB005"),
                ValidationError("b.bum", ValidationSeverity.WARNING, "warn", null, null, ruleId = "EB006"),
            ),
            summary = ValidationSummary(2, 0, 5, 1, 1),
        )

        val sarif = parse(result)

        assertThat(sarif.has("\$schema")).isTrue()
        assertThat(sarif.has("version")).isTrue()
        assertThat(sarif.has("runs")).isTrue()

        val run = sarif.getJSONArray("runs").getJSONObject(0)
        assertThat(run.has("tool")).isTrue()
        assertThat(run.has("results")).isTrue()
        assertThat(run.getJSONArray("results").length()).isEqualTo(2)

        for (i in 0 until run.getJSONArray("results").length()) {
            val r = run.getJSONArray("results").getJSONObject(i)
            assertThat(r.has("message")).isTrue()
            assertThat(r.has("level")).isTrue()
            assertThat(r.has("locations")).isTrue()
        }
    }
}
