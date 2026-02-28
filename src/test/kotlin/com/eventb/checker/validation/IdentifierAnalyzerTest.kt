package com.eventb.checker.validation

import com.eventb.checker.TestModelBuilders.checkedFormulas
import com.eventb.checker.TestModelBuilders.context
import com.eventb.checker.TestModelBuilders.event
import com.eventb.checker.TestModelBuilders.machine
import com.eventb.checker.TestModelBuilders.project
import com.eventb.checker.model.Action
import com.eventb.checker.model.Axiom
import com.eventb.checker.model.CarrierSet
import com.eventb.checker.model.Constant
import com.eventb.checker.model.EventBProject
import com.eventb.checker.model.Guard
import com.eventb.checker.model.Invariant
import com.eventb.checker.model.Parameter
import com.eventb.checker.model.Variable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IdentifierAnalyzerTest {

    private val analyzer = IdentifierAnalyzer()

    private fun analyzeProject(project: EventBProject): List<ValidationError> = analyzer.analyze(project, checkedFormulas(project))

    @Test
    fun `no warnings for used variable`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("n", "n")),
                    invariants = listOf(Invariant("inv1", "n ∈ ℤ", false)),
                    events = listOf(
                        event(
                            "INITIALISATION",
                            actions = listOf(Action("act1", "n ≔ 0")),
                        ),
                        event(
                            "inc",
                            actions = listOf(Action("act1", "n ≔ n + 1")),
                        ),
                    ),
                ),
            ),
        )

        val findings = analyzeProject(project)

        assertThat(findings).isEmpty()
    }

    @Test
    fun `dead variable detected`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x"), Variable("unused", "unused")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false)),
                    events = listOf(
                        event(
                            "INITIALISATION",
                            actions = listOf(Action("act1", "x ≔ 0")),
                        ),
                    ),
                ),
            ),
        )

        val findings = analyzeProject(project)

        assertThat(findings)
            .filteredOn {
                it.severity == ValidationSeverity.WARNING && it.message.contains("Dead variable") && it.message.contains("unused")
            }
            .singleElement()
    }

    @Test
    fun `dead constant detected`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("S", "S")),
                    constants = listOf(Constant("used", "used"), Constant("unused", "unused")),
                    axioms = listOf(Axiom("axm1", "used ∈ S", false)),
                ),
            ),
        )

        val findings = analyzeProject(project)

        assertThat(findings)
            .filteredOn {
                it.severity == ValidationSeverity.WARNING && it.message.contains("Dead constant") && it.message.contains("unused")
            }
            .singleElement()
    }

    @Test
    fun `unmodified variable detected`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false)),
                ),
            ),
        )

        val findings = analyzeProject(project)

        assertThat(findings)
            .filteredOn { it.severity == ValidationSeverity.INFO && it.message.contains("Unmodified variable") && it.message.contains("x") }
            .singleElement()
    }

    @Test
    fun `assigned variable not flagged as unmodified`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("n", "n")),
                    invariants = listOf(Invariant("inv1", "n ∈ ℤ", false)),
                    events = listOf(
                        event(
                            "inc",
                            actions = listOf(Action("act1", "n ≔ n + 1")),
                        ),
                    ),
                ),
            ),
        )

        val findings = analyzeProject(project)

        assertThat(findings)
            .filteredOn { it.message.contains("Unmodified variable") && it.message.contains("'n'") }
            .isEmpty()
    }

    @Test
    fun `carrier set not flagged as dead constant`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("S", "S")),
                    axioms = listOf(Axiom("axm1", "finite(S)", false)),
                ),
            ),
        )

        val findings = analyzeProject(project)

        assertThat(findings)
            .filteredOn { it.message.contains("Dead constant") && it.message.contains("S") }
            .isEmpty()
    }

    @Test
    fun `parameter not flagged as dead variable`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("S", "S")),
                ),
            ),
            machines = listOf(
                machine(
                    "M1",
                    seesContexts = listOf("C1"),
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ S", false)),
                    events = listOf(
                        event(
                            "update",
                            parameters = listOf(Parameter("p", "p")),
                            guards = listOf(Guard("grd1", "p ∈ S", false)),
                            actions = listOf(Action("act1", "x ≔ p")),
                        ),
                    ),
                ),
            ),
        )

        val findings = analyzeProject(project)

        assertThat(findings)
            .filteredOn { it.message.contains("Dead variable") && it.message.contains("p") }
            .isEmpty()
    }

    @Test
    fun `empty project produces no findings`() {
        val project = project()

        val findings = analyzeProject(project)

        assertThat(findings).isEmpty()
    }
}
