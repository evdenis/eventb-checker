package com.eventb.checker.integration

import com.eventb.checker.TestZipHelper.createZip
import com.eventb.checker.validation.ProjectValidator
import com.eventb.checker.validation.ValidationSeverity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class EndToEndTest {

    @TempDir
    lateinit var tempDir: File

    private val validator = ProjectValidator()

    @Test
    fun `valid model passes all checks`() {
        val zip = createZip(
            tempDir,
            "project/Counter.bum" to """
                <org.eventb.core.machineFile name="Counter">
                    <org.eventb.core.seesContext org.eventb.core.target="Limits"/>
                    <org.eventb.core.variable org.eventb.core.identifier="n" org.eventb.core.label="n"/>
                    <org.eventb.core.invariant org.eventb.core.label="inv1"
                        org.eventb.core.predicate="n ∈ ℕ" org.eventb.core.theorem="false"/>
                    <org.eventb.core.event org.eventb.core.label="INITIALISATION"
                        org.eventb.core.convergence="0" org.eventb.core.extended="false">
                        <org.eventb.core.action org.eventb.core.label="act1"
                            org.eventb.core.assignment="n ≔ 0"/>
                    </org.eventb.core.event>
                    <org.eventb.core.event org.eventb.core.label="increment"
                        org.eventb.core.convergence="0" org.eventb.core.extended="false">
                        <org.eventb.core.guard org.eventb.core.label="grd1"
                            org.eventb.core.predicate="n &lt; lim"/>
                        <org.eventb.core.action org.eventb.core.label="act1"
                            org.eventb.core.assignment="n ≔ n + 1"/>
                    </org.eventb.core.event>
                </org.eventb.core.machineFile>
            """.trimIndent(),
            "project/Limits.buc" to """
                <org.eventb.core.contextFile name="Limits">
                    <org.eventb.core.constant org.eventb.core.identifier="lim"/>
                    <org.eventb.core.axiom org.eventb.core.label="axm1"
                        org.eventb.core.predicate="lim ∈ ℕ" org.eventb.core.theorem="false"/>
                    <org.eventb.core.axiom org.eventb.core.label="axm2"
                        org.eventb.core.predicate="lim &gt; 0" org.eventb.core.theorem="false"/>
                </org.eventb.core.contextFile>
            """.trimIndent()
        )

        val result = validator.validate(zip.absolutePath)

        assertThat(result.isValid).isTrue()
        assertThat(result.summary.machineCount).isEqualTo(1)
        assertThat(result.summary.contextCount).isEqualTo(1)
        assertThat(result.summary.formulaCount).isGreaterThan(0)
    }

    @Test
    fun `invalid formula is reported`() {
        val zip = createZip(
            tempDir,
            "project/Bad.bum" to """
                <org.eventb.core.machineFile name="Bad">
                    <org.eventb.core.invariant org.eventb.core.label="inv1"
                        org.eventb.core.predicate="x ==== y" org.eventb.core.theorem="false"/>
                </org.eventb.core.machineFile>
            """.trimIndent()
        )

        val result = validator.validate(zip.absolutePath)

        assertThat(result.isValid).isFalse()
        assertThat(result.errors).anyMatch {
            it.severity == ValidationSeverity.ERROR && it.message.contains("Formula parse error")
        }
    }

    @Test
    fun `missing cross reference is reported`() {
        val zip = createZip(
            tempDir,
            "project/M.bum" to """
                <org.eventb.core.machineFile name="M">
                    <org.eventb.core.seesContext org.eventb.core.target="NonExistent"/>
                </org.eventb.core.machineFile>
            """.trimIndent()
        )

        val result = validator.validate(zip.absolutePath)

        assertThat(result.isValid).isFalse()
        assertThat(result.errors).anyMatch {
            it.message.contains("SEES") && it.message.contains("NonExistent")
        }
    }

    @Test
    fun `empty project produces valid result with zero counts`() {
        val zip = createZip(
            tempDir,
            "project/.project" to "<projectDescription/>"
        )

        val result = validator.validate(zip.absolutePath)

        assertThat(result.isValid).isTrue()
        assertThat(result.summary.machineCount).isEqualTo(0)
        assertThat(result.summary.contextCount).isEqualTo(0)
    }

    @Test
    fun `malformed XML reports error`() {
        val zip = createZip(
            tempDir,
            "project/Broken.bum" to "this is not xml at all <<<"
        )

        val result = validator.validate(zip.absolutePath)

        assertThat(result.isValid).isFalse()
        assertThat(result.errors).anyMatch { it.message.contains("Failed to parse XML") }
    }

    @Test
    fun `refinement chain validates correctly`() {
        val zip = createZip(
            tempDir,
            "project/Base.bum" to """
                <org.eventb.core.machineFile name="Base">
                    <org.eventb.core.variable org.eventb.core.identifier="x" org.eventb.core.label="x"/>
                    <org.eventb.core.invariant org.eventb.core.label="inv1"
                        org.eventb.core.predicate="x ∈ ℕ" org.eventb.core.theorem="false"/>
                </org.eventb.core.machineFile>
            """.trimIndent(),
            "project/Refined.bum" to """
                <org.eventb.core.machineFile name="Refined">
                    <org.eventb.core.refinesMachine org.eventb.core.target="Base"/>
                    <org.eventb.core.variable org.eventb.core.identifier="y" org.eventb.core.label="y"/>
                    <org.eventb.core.invariant org.eventb.core.label="inv1"
                        org.eventb.core.predicate="y ∈ ℕ" org.eventb.core.theorem="false"/>
                </org.eventb.core.machineFile>
            """.trimIndent()
        )

        val result = validator.validate(zip.absolutePath)

        assertThat(result.isValid).isTrue()
        assertThat(result.summary.machineCount).isEqualTo(2)
    }

    @Test
    fun `context extension validates correctly`() {
        val zip = createZip(
            tempDir,
            "project/BaseCtx.buc" to """
                <org.eventb.core.contextFile name="BaseCtx">
                    <org.eventb.core.carrierSet org.eventb.core.identifier="S"/>
                </org.eventb.core.contextFile>
            """.trimIndent(),
            "project/ExtCtx.buc" to """
                <org.eventb.core.contextFile name="ExtCtx">
                    <org.eventb.core.extendsContext org.eventb.core.target="BaseCtx"/>
                    <org.eventb.core.constant org.eventb.core.identifier="c"/>
                    <org.eventb.core.axiom org.eventb.core.label="axm1"
                        org.eventb.core.predicate="c ∈ S" org.eventb.core.theorem="false"/>
                </org.eventb.core.contextFile>
            """.trimIndent()
        )

        val result = validator.validate(zip.absolutePath)

        assertThat(result.isValid).isTrue()
        assertThat(result.summary.contextCount).isEqualTo(2)
    }
}
