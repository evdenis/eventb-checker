package com.eventb.checker.validation

import com.eventb.checker.TestModelBuilders.context
import com.eventb.checker.TestModelBuilders.event
import com.eventb.checker.TestModelBuilders.machine
import com.eventb.checker.TestModelBuilders.project
import com.eventb.checker.model.*
import org.assertj.core.api.Assertions.assertThat
import org.eventb.core.ast.FormulaFactory
import org.junit.jupiter.api.Test

class WellDefinednessCheckerTest {

    private val typeChecker = TypeChecker(FormulaFactory.getDefault())
    private val wdChecker = WellDefinednessChecker()

    private fun checkedFormulasFor(project: EventBProject): List<TypeCheckedFormula> {
        return typeChecker.checkProjectFull(project).checkedFormulas
    }

    @Test
    fun `trivial WD produces no report`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false))
                )
            )
        )

        val findings = wdChecker.check(checkedFormulasFor(project))

        assertThat(findings).isEmpty()
    }

    @Test
    fun `division generates WD condition`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x"), Variable("y", "y")),
                    invariants = listOf(
                        Invariant("inv1", "x ∈ ℤ", false),
                        Invariant("inv2", "y ∈ ℤ", false),
                        Invariant("inv3", "x ÷ y > 0", false)
                    )
                )
            )
        )

        val findings = wdChecker.check(checkedFormulasFor(project))

        assertThat(findings).isNotEmpty
        assertThat(findings).allMatch { it.severity == ValidationSeverity.INFO }
        assertThat(findings).anyMatch { it.message.contains("Well-definedness condition") }
    }

    @Test
    fun `function application generates WD condition`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("S", "S")),
                    constants = listOf(Constant("f", "f")),
                    axioms = listOf(
                        Axiom("axm1", "f ∈ S ⇸ ℤ", false)
                    )
                )
            ),
            machines = listOf(
                machine(
                    "M1",
                    seesContexts = listOf("C1"),
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(
                        Invariant("inv1", "x ∈ S", false),
                        Invariant("inv2", "f(x) = 1", false)
                    )
                )
            )
        )

        val findings = wdChecker.check(checkedFormulasFor(project))

        assertThat(findings).isNotEmpty
        assertThat(findings).allMatch { it.severity == ValidationSeverity.INFO }
        assertThat(findings).anyMatch { it.message.contains("Well-definedness condition") }
    }

    @Test
    fun `simple conjunction has trivial WD`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("a", "a"), Variable("b", "b")),
                    invariants = listOf(
                        Invariant("inv1", "a ∈ ℤ", false),
                        Invariant("inv2", "b ∈ ℤ", false),
                        Invariant("inv3", "a ∈ ℤ ∧ b ∈ ℤ", false)
                    )
                )
            )
        )

        val findings = wdChecker.check(checkedFormulasFor(project))

        assertThat(findings).isEmpty()
    }

    @Test
    fun `empty formula list produces no findings`() {
        val findings = wdChecker.check(emptyList())

        assertThat(findings).isEmpty()
    }

    @Test
    fun `all findings are INFO severity`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x"), Variable("y", "y")),
                    invariants = listOf(
                        Invariant("inv1", "x ∈ ℤ", false),
                        Invariant("inv2", "y ∈ ℤ", false),
                        Invariant("inv3", "x ÷ y > 0", false)
                    )
                )
            )
        )

        val findings = wdChecker.check(checkedFormulasFor(project))

        assertThat(findings).isNotEmpty
        assertThat(findings).allMatch { it.severity == ValidationSeverity.INFO }
    }
}
