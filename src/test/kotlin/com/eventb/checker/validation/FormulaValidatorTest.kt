package com.eventb.checker.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FormulaValidatorTest {

    private val validator = FormulaValidator()

    @Test
    fun `valid predicate parses without problems`() {
        val errors = validator.validate(formulaCheck("x = 1", FormulaKind.PREDICATE))
        assertThat(errors).isEmpty()
    }

    @Test
    fun `valid predicate with set membership`() {
        val errors = validator.validate(formulaCheck("x ∈ ℤ", FormulaKind.PREDICATE))
        assertThat(errors).isEmpty()
    }

    @Test
    fun `valid predicate with conjunction`() {
        val errors = validator.validate(formulaCheck("x > 0 ∧ y > 0", FormulaKind.PREDICATE))
        assertThat(errors).isEmpty()
    }

    @Test
    fun `invalid predicate reports problems`() {
        val errors = validator.validate(formulaCheck("x ==== y", FormulaKind.PREDICATE))
        assertThat(errors).isNotEmpty
    }

    @Test
    fun `valid expression parses without problems`() {
        val errors = validator.validate(formulaCheck("x + 1", FormulaKind.EXPRESSION))
        assertThat(errors).isEmpty()
    }

    @Test
    fun `valid expression with card`() {
        val errors = validator.validate(formulaCheck("card(s)", FormulaKind.EXPRESSION))
        assertThat(errors).isEmpty()
    }

    @Test
    fun `invalid expression reports problems`() {
        val errors = validator.validate(formulaCheck("+ + +", FormulaKind.EXPRESSION))
        assertThat(errors).isNotEmpty
    }

    @Test
    fun `valid assignment parses without problems`() {
        val errors = validator.validate(formulaCheck("x ≔ x + 1", FormulaKind.ASSIGNMENT))
        assertThat(errors).isEmpty()
    }

    @Test
    fun `valid becomes-in assignment`() {
        val errors = validator.validate(formulaCheck("x :∈ ℤ", FormulaKind.ASSIGNMENT))
        assertThat(errors).isEmpty()
    }

    @Test
    fun `invalid assignment reports problems`() {
        val errors = validator.validate(formulaCheck("not an assignment at all", FormulaKind.ASSIGNMENT))
        assertThat(errors).isNotEmpty
    }

    @Test
    fun `validate check produces errors for invalid formula`() {
        val check = FormulaValidator.FormulaCheck(
            filePath = "test.bum",
            elementLabel = "inv1",
            formula = "x ==== y",
            kind = FormulaKind.PREDICATE
        )

        val errors = validator.validate(check)

        assertThat(errors).isNotEmpty
        assertThat(errors[0].filePath).isEqualTo("test.bum")
        assertThat(errors[0].element).isEqualTo("inv1")
        assertThat(errors[0].severity).isEqualTo(ValidationSeverity.ERROR)
    }

    @Test
    fun `validate check produces no errors for valid formula`() {
        val check = FormulaValidator.FormulaCheck(
            filePath = "test.bum",
            elementLabel = "inv1",
            formula = "x = 1",
            kind = FormulaKind.PREDICATE
        )

        val errors = validator.validate(check)

        assertThat(errors).isEmpty()
    }

    private fun formulaCheck(formula: String, kind: FormulaKind) = FormulaValidator.FormulaCheck(
        filePath = "test.bum",
        elementLabel = "test",
        formula = formula,
        kind = kind
    )
}
