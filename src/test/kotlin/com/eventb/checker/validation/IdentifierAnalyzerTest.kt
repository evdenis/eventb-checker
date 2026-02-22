package com.eventb.checker.validation

import com.eventb.checker.TestModelBuilders.context
import com.eventb.checker.TestModelBuilders.event
import com.eventb.checker.TestModelBuilders.machine
import com.eventb.checker.TestModelBuilders.project
import com.eventb.checker.model.*
import org.assertj.core.api.Assertions.assertThat
import org.eventb.core.ast.FormulaFactory
import org.junit.jupiter.api.Test

class IdentifierAnalyzerTest {

    private val typeChecker = TypeChecker(FormulaFactory.getDefault())
    private val analyzer = IdentifierAnalyzer()

    private fun analyzeProject(project: EventBProject): List<ValidationError> {
        val checkedFormulas = typeChecker.checkProjectFull(project).checkedFormulas
        return analyzer.analyze(project, checkedFormulas)
    }

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
                            actions = listOf(Action("act1", "n ≔ 0"))
                        ),
                        event(
                            "inc",
                            actions = listOf(Action("act1", "n ≔ n + 1"))
                        )
                    )
                )
            )
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
                            actions = listOf(Action("act1", "x ≔ 0"))
                        )
                    )
                )
            )
        )

        val findings = analyzeProject(project)

        assertThat(findings).anyMatch {
            it.severity == ValidationSeverity.WARNING && it.message.contains("Dead variable") && it.message.contains("unused")
        }
    }

    @Test
    fun `dead constant detected`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("S", "S")),
                    constants = listOf(Constant("used", "used"), Constant("unused", "unused")),
                    axioms = listOf(Axiom("axm1", "used ∈ S", false))
                )
            )
        )

        val findings = analyzeProject(project)

        assertThat(findings).anyMatch {
            it.severity == ValidationSeverity.WARNING && it.message.contains("Dead constant") && it.message.contains("unused")
        }
    }

    @Test
    fun `unmodified variable detected`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false))
                )
            )
        )

        val findings = analyzeProject(project)

        assertThat(findings).anyMatch {
            it.severity == ValidationSeverity.INFO && it.message.contains("Unmodified variable") && it.message.contains("x")
        }
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
                            actions = listOf(Action("act1", "n ≔ n + 1"))
                        )
                    )
                )
            )
        )

        val findings = analyzeProject(project)

        assertThat(findings).noneMatch {
            it.message.contains("Unmodified variable") && it.message.contains("'n'")
        }
    }

    @Test
    fun `carrier set not flagged as dead constant`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("S", "S")),
                    axioms = listOf(Axiom("axm1", "finite(S)", false))
                )
            )
        )

        val findings = analyzeProject(project)

        assertThat(findings).noneMatch {
            it.message.contains("Dead constant") && it.message.contains("S")
        }
    }

    @Test
    fun `parameter not flagged as dead variable`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("S", "S"))
                )
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
                            actions = listOf(Action("act1", "x ≔ p"))
                        )
                    )
                )
            )
        )

        val findings = analyzeProject(project)

        assertThat(findings).noneMatch {
            it.message.contains("Dead variable") && it.message.contains("p")
        }
    }

    @Test
    fun `empty project produces no findings`() {
        val project = project()

        val findings = analyzeProject(project)

        assertThat(findings).isEmpty()
    }
}
