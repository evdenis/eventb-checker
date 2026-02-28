package com.eventb.checker.validation

import com.eventb.checker.TestModelBuilders.checkedFormulas
import com.eventb.checker.TestModelBuilders.context
import com.eventb.checker.TestModelBuilders.machine
import com.eventb.checker.TestModelBuilders.project
import com.eventb.checker.model.Axiom
import com.eventb.checker.model.CarrierSet
import com.eventb.checker.model.Constant
import com.eventb.checker.model.Invariant
import com.eventb.checker.model.Variable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WellDefinednessCheckerTest {

    private val wdChecker = WellDefinednessChecker()

    @Test
    fun `trivial WD produces no report`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false)),
                ),
            ),
        )

        val findings = wdChecker.check(checkedFormulas(project))

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
                        Invariant("inv3", "x ÷ y > 0", false),
                    ),
                ),
            ),
        )

        val findings = wdChecker.check(checkedFormulas(project))

        assertThat(findings).allSatisfy { assertThat(it.severity).isEqualTo(ValidationSeverity.INFO) }
        assertThat(findings).filteredOn { it.message.contains("Well-definedness condition") }.isNotEmpty
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
                        Axiom("axm1", "f ∈ S ⇸ ℤ", false),
                    ),
                ),
            ),
            machines = listOf(
                machine(
                    "M1",
                    seesContexts = listOf("C1"),
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(
                        Invariant("inv1", "x ∈ S", false),
                        Invariant("inv2", "f(x) = 1", false),
                    ),
                ),
            ),
        )

        val findings = wdChecker.check(checkedFormulas(project))

        assertThat(findings).allSatisfy { assertThat(it.severity).isEqualTo(ValidationSeverity.INFO) }
        assertThat(findings).filteredOn { it.message.contains("Well-definedness condition") }.isNotEmpty
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
                        Invariant("inv3", "a ∈ ℤ ∧ b ∈ ℤ", false),
                    ),
                ),
            ),
        )

        val findings = wdChecker.check(checkedFormulas(project))

        assertThat(findings).isEmpty()
    }

    @Test
    fun `empty formula list produces no findings`() {
        val findings = wdChecker.check(emptyList())

        assertThat(findings).isEmpty()
    }
}
