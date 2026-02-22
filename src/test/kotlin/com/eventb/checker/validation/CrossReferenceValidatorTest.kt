package com.eventb.checker.validation

import com.eventb.checker.TestModelBuilders.context
import com.eventb.checker.TestModelBuilders.machine
import com.eventb.checker.TestModelBuilders.project
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CrossReferenceValidatorTest {

    private val validator = CrossReferenceValidator()

    @Test
    fun `no errors when all references resolve`() {
        val project = project(
            machines = listOf(
                machine("M1", seesContexts = listOf("C1")),
                machine("M2", seesContexts = listOf("C1"), refinesMachine = "M1")
            ),
            contexts = listOf(context("C1"))
        )

        val errors = validator.validate(project)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `error for missing sees context`() {
        val project = project(
            machines = listOf(machine("M1", seesContexts = listOf("MissingCtx")))
        )

        val errors = validator.validate(project)

        assertThat(errors).hasSize(1)
        assertThat(errors[0].message).contains("SEES")
        assertThat(errors[0].message).contains("MissingCtx")
    }

    @Test
    fun `error for missing refines machine`() {
        val project = project(
            machines = listOf(machine("M1", refinesMachine = "MissingMachine"))
        )

        val errors = validator.validate(project)

        assertThat(errors).hasSize(1)
        assertThat(errors[0].message).contains("REFINES")
        assertThat(errors[0].message).contains("MissingMachine")
    }

    @Test
    fun `error for missing extends context`() {
        val project = project(
            contexts = listOf(context("C1", extendsContexts = listOf("MissingBase")))
        )

        val errors = validator.validate(project)

        assertThat(errors).hasSize(1)
        assertThat(errors[0].message).contains("EXTENDS")
        assertThat(errors[0].message).contains("MissingBase")
    }

    @Test
    fun `no errors for project with no references`() {
        val project = project(
            machines = listOf(machine("M1")),
            contexts = listOf(context("C1"))
        )

        val errors = validator.validate(project)

        assertThat(errors).isEmpty()
    }
}
