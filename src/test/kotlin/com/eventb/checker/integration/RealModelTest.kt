package com.eventb.checker.integration

import com.eventb.checker.validation.ProjectValidator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Integration tests using real Rodin-exported Event-B models from
 * https://github.com/17451k/eventb-models
 */
class RealModelTest {

    private val validator = ProjectValidator()
    private val validatorWithProofs = ProjectValidator(checkProofs = true)

    private fun resourceZipPath(name: String): String {
        val url = javaClass.getResource("/samples/$name")
            ?: throw IllegalStateException("Test resource not found: /samples/$name")
        return File(url.toURI()).absolutePath
    }

    @Test
    fun `binary search model validates`() {
        val result = validator.validate(resourceZipPath("binary-search.zip"))

        assertThat(result.isValid)
            .describedAs("Validation errors: %s", result.errors)
            .isTrue()
        assertThat(result.summary.machineCount).isEqualTo(4)
        assertThat(result.summary.contextCount).isEqualTo(1)
        assertThat(result.summary.formulaCount).isGreaterThan(0)
    }

    @Test
    fun `cars on bridge model validates`() {
        val result = validator.validate(resourceZipPath("cars-on-bridge.zip"))

        assertThat(result.isValid)
            .describedAs("Validation errors: %s", result.errors)
            .isTrue()
        assertThat(result.summary.machineCount).isEqualTo(4)
        assertThat(result.summary.contextCount).isEqualTo(3)
        assertThat(result.summary.formulaCount).isGreaterThan(0)
    }

    @Test
    fun `file system model validates`() {
        val result = validator.validate(resourceZipPath("file-system.zip"))

        assertThat(result.isValid)
            .describedAs("Validation errors: %s", result.errors)
            .isTrue()
        assertThat(result.summary.machineCount).isEqualTo(1)
        assertThat(result.summary.contextCount).isEqualTo(1)
        assertThat(result.summary.formulaCount).isGreaterThan(0)
    }

    @Test
    fun `traffic light model validates`() {
        val result = validator.validate(resourceZipPath("traffic-light.zip"))

        assertThat(result.isValid)
            .describedAs("Validation errors: %s", result.errors)
            .isTrue()
        assertThat(result.summary.machineCount).isEqualTo(3)
        assertThat(result.summary.contextCount).isEqualTo(1)
        assertThat(result.summary.formulaCount).isGreaterThan(0)
    }

    @Test
    fun `base model validates`() {
        val result = validator.validate(resourceZipPath("base-model.zip"))

        assertThat(result.isValid)
            .describedAs("Validation errors: %s", result.errors)
            .isTrue()
        assertThat(result.summary.machineCount).isEqualTo(1)
        assertThat(result.summary.contextCount).isEqualTo(1)
        assertThat(result.summary.formulaCount).isGreaterThan(0)
    }

    @Test
    fun `traffic light proof summary reports undischarged POs`() {
        val result = validatorWithProofs.validate(resourceZipPath("traffic-light.zip"))

        val ps = result.summary.proofSummary
        assertThat(ps).isNotNull
        assertThat(ps!!.total).isEqualTo(32)
        assertThat(ps.discharged).isEqualTo(26)
        assertThat(ps.discharged + ps.pending + ps.unattempted).isEqualTo(ps.total)
    }

    @Test
    fun `all samples have proof files and produce summaries`() {
        val samples = listOf(
            "binary-search.zip",
            "cars-on-bridge.zip",
            "file-system.zip",
            "traffic-light.zip",
            "base-model.zip",
        )

        for (sample in samples) {
            val result = validatorWithProofs.validate(resourceZipPath(sample))
            val ps = result.summary.proofSummary
            assertThat(ps)
                .describedAs("Proof summary should be present for $sample")
                .isNotNull
            assertThat(ps!!.total)
                .describedAs("$sample should have proof obligations")
                .isGreaterThan(0)
            assertThat(ps.discharged + ps.reviewed + ps.pending + ps.unattempted)
                .describedAs("$sample PO counts should add up")
                .isEqualTo(ps.total)
        }
    }
}
