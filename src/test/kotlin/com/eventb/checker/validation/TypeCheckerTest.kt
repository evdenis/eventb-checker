package com.eventb.checker.validation

import com.eventb.checker.TestModelBuilders.context
import com.eventb.checker.TestModelBuilders.event
import com.eventb.checker.TestModelBuilders.machine
import com.eventb.checker.TestModelBuilders.project
import com.eventb.checker.model.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TypeCheckerTest {

    private val typeChecker = TypeChecker()

    @Test
    fun `carrier set registers as type`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("S", "S")),
                    axioms = listOf(Axiom("axm1", "x ∈ S", false))
                )
            )
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `constant typed by axiom`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("S", "S")),
                    constants = listOf(Constant("c", "c")),
                    axioms = listOf(
                        Axiom("axm1", "c ∈ S", false),
                        Axiom("axm2", "c = c", false)
                    )
                )
            )
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `variable typed by invariant`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("n", "n")),
                    invariants = listOf(Invariant("inv1", "n ∈ ℤ", false))
                )
            )
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `type mismatch detected`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("n", "n")),
                    invariants = listOf(
                        Invariant("inv1", "n ∈ ℤ", false),
                        Invariant("inv2", "n = TRUE", false)
                    )
                )
            )
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isNotEmpty
        assertThat(errors).allMatch { it.severity == ValidationSeverity.WARNING }
        assertThat(errors).anyMatch { it.message.contains("Type error") }
    }

    @Test
    fun `context env flows to machine via SEES`() {
        val project = project(
            contexts = listOf(
                context(
                    "C1",
                    carrierSets = listOf(CarrierSet("COLOR", "COLOR")),
                    constants = listOf(Constant("red", "red")),
                    axioms = listOf(Axiom("axm1", "red ∈ COLOR", false))
                )
            ),
            machines = listOf(
                machine(
                    "M1",
                    seesContexts = listOf("C1"),
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ COLOR", false)),
                    events = listOf(
                        event(
                            "set_color",
                            actions = listOf(Action("act1", "x ≔ red"))
                        )
                    )
                )
            )
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `event parameter typed by guard`() {
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

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `untyped identifier produces warning`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    invariants = listOf(Invariant("inv1", "x = y", false))
                )
            )
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isNotEmpty
        assertThat(errors).allMatch { it.severity == ValidationSeverity.WARNING }
    }

    @Test
    fun `refinement chain inherits types`() {
        val project = project(
            machines = listOf(
                machine(
                    "Base",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false))
                ),
                machine(
                    "Refined",
                    refinesMachine = "Base",
                    variables = listOf(Variable("y", "y")),
                    invariants = listOf(
                        Invariant("inv1", "y ∈ ℤ", false),
                        Invariant("inv2", "y ≥ x", false)
                    )
                )
            )
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `context extension inherits carrier sets`() {
        val project = project(
            contexts = listOf(
                context(
                    "Base",
                    carrierSets = listOf(CarrierSet("S", "S"))
                ),
                context(
                    "Ext",
                    extendsContexts = listOf("Base"),
                    constants = listOf(Constant("c", "c")),
                    axioms = listOf(Axiom("axm1", "c ∈ S", false))
                )
            )
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `empty project produces no errors`() {
        val project = project()

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `type errors are WARNING severity not ERROR`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("n", "n")),
                    invariants = listOf(
                        Invariant("inv1", "n ∈ ℤ", false),
                        Invariant("inv2", "n = TRUE", false)
                    )
                )
            )
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isNotEmpty
        assertThat(errors).noneMatch { it.severity == ValidationSeverity.ERROR }
        assertThat(errors).allMatch { it.severity == ValidationSeverity.WARNING }
    }

    @Test
    fun `variant expression type checked`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("n", "n")),
                    invariants = listOf(Invariant("inv1", "n ∈ ℤ", false)),
                    variant = Variant("vrn1", "n")
                )
            )
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `witness type checked in event`() {
        val project = project(
            machines = listOf(
                machine(
                    "M1",
                    variables = listOf(Variable("x", "x")),
                    invariants = listOf(Invariant("inv1", "x ∈ ℤ", false)),
                    events = listOf(
                        event(
                            "evt",
                            witnesses = listOf(Witness("wit1", "x' = x + 1"))
                        )
                    )
                )
            )
        )

        val errors = typeChecker.checkProject(project)

        assertThat(errors).isEmpty()
    }
}
