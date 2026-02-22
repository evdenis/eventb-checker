package com.eventb.checker.validation

import com.eventb.checker.TestModelBuilders.event
import com.eventb.checker.TestModelBuilders.machine
import com.eventb.checker.TestModelBuilders.project
import com.eventb.checker.model.Action
import com.eventb.checker.model.EventBProject
import com.eventb.checker.model.Invariant
import com.eventb.checker.model.Variable
import org.assertj.core.api.Assertions.assertThat
import org.eventb.core.ast.FormulaFactory
import org.junit.jupiter.api.Test

class EventCompletenessCheckerTest {

    private val typeChecker = TypeChecker(FormulaFactory.getDefault())
    private val checker = EventCompletenessChecker()

    private fun checkProject(project: EventBProject): List<ValidationError> {
        val checkedFormulas = typeChecker.checkProjectFull(project).checkedFormulas
        return checker.check(project, checkedFormulas)
    }

    @Test
    fun `complete INITIALISATION produces no warnings`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x"), Variable("y", "y")),
                    invariants = listOf(
                        Invariant("inv1", "x ∈ ℤ", false),
                        Invariant("inv2", "y ∈ ℤ", false),
                    ),
                    events = listOf(
                        event(
                            "INITIALISATION",
                            actions = listOf(
                                Action("act1", "x ≔ 0"),
                                Action("act2", "y ≔ 0"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val findings = checkProject(project)

        assertThat(findings).isEmpty()
    }

    @Test
    fun `incomplete INITIALISATION warns for missing variable`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x"), Variable("y", "y")),
                    invariants = listOf(
                        Invariant("inv1", "x ∈ ℤ", false),
                        Invariant("inv2", "y ∈ ℤ", false),
                    ),
                    events = listOf(
                        event(
                            "INITIALISATION",
                            actions = listOf(Action("act1", "x ≔ 0")),
                        ),
                    ),
                ),
            ),
        )

        val findings = checkProject(project)

        assertThat(findings).hasSize(1)
        assertThat(findings[0].severity).isEqualTo(ValidationSeverity.WARNING)
        assertThat(findings[0].message).contains("INITIALISATION").contains("y")
    }

    @Test
    fun `no INITIALISATION event produces no findings`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false)),
                    events = listOf(
                        event(
                            "inc",
                            actions = listOf(Action("act1", "x ≔ x + 1")),
                        ),
                    ),
                ),
            ),
        )

        val findings = checkProject(project)

        assertThat(findings).isEmpty()
    }

    @Test
    fun `no variables produces no findings`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    events = listOf(
                        event("INITIALISATION"),
                    ),
                ),
            ),
        )

        val findings = checkProject(project)

        assertThat(findings).isEmpty()
    }

    @Test
    fun `empty project produces no findings`() {
        val project = project()

        val findings = checkProject(project)

        assertThat(findings).isEmpty()
    }
}
