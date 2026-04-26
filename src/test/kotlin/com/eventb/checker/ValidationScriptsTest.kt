package com.eventb.checker

import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ValidationScriptsTest {

    @TempDir
    lateinit var tempDir: File

    private val repoRoot = File(System.getProperty("user.dir"))

    @Test
    fun `github validation script fails when no models match`() {
        val result = runGitHubScript("missing/*.zip")

        assertThat(result.exitCode).isEqualTo(1)
        assertThat(result.output).contains("No model files matched")
        assertThat(result.sarifFile.exists()).isTrue()
        assertThat(JSONObject(result.sarifFile.readText()).getJSONArray("runs")).isEmpty()
        assertThat(result.githubOutput.readText()).contains("valid=false")
    }

    @Test
    fun `gitlab validation script fails when no models match`() {
        val result = runGitLabScript("missing/*.zip")

        assertThat(result.exitCode).isEqualTo(1)
        assertThat(result.output).contains("No model files matched")
        assertThat(result.gitLabJUnit.readText()).contains("""tests="1"""").contains("""failures="1"""")
        assertThat(result.gitLabJUnit.readText()).contains("MODEL_GLOB 'missing/*.zip' matched no files")
        assertThat(JSONObject(result.gitLabJson.readText()).getBoolean("valid")).isFalse()
    }

    @Test
    fun `github validation script succeeds for valid models`() {
        File(tempDir, "valid.zip").writeText("placeholder")

        val result = runGitHubScript("*.zip")

        assertThat(result.exitCode).isZero()
        assertThat(result.githubOutput.readText()).contains("valid=true")
        assertThat(result.githubOutput.readText()).contains("error-count=0")
        assertThat(result.githubOutput.readText()).contains("warning-count=0")

        val runs = JSONObject(result.sarifFile.readText()).getJSONArray("runs")
        assertThat(runs).hasSize(1)
        assertThat(runs.getJSONObject(0).getJSONArray("results")).isEmpty()
    }

    @Test
    fun `gitlab validation script fails for invalid models and writes artifacts`() {
        File(tempDir, "invalid.zip").writeText("placeholder")

        val result = runGitLabScript("*.zip")

        assertThat(result.exitCode).isEqualTo(1)
        assertThat(JSONObject(result.gitLabJson.readText()).getBoolean("valid")).isFalse()
        assertThat(JSONObject(result.gitLabJson.readText()).getInt("errorCount")).isEqualTo(1)
        assertThat(result.gitLabJUnit.readText()).contains("invalid.zip")
        assertThat(JSONObject(result.sarifFile.readText()).getJSONArray("runs")).hasSize(1)
    }

    private fun runGitHubScript(modelGlob: String): ScriptRunResult = runScript(
        script = repoRoot.resolve(".github/scripts/validate-models.sh"),
        modelGlob = modelGlob,
        includeGitHubOutput = true,
    )

    private fun runGitLabScript(modelGlob: String): ScriptRunResult = runScript(
        script = repoRoot.resolve(".gitlab/scripts/validate-models.sh"),
        modelGlob = modelGlob,
        includeGitHubOutput = false,
    )

    private fun runScript(script: File, modelGlob: String, includeGitHubOutput: Boolean): ScriptRunResult {
        val fakeChecker = writeFakeChecker()
        val githubOutput = File(tempDir, "github-output.txt")
        val process = ProcessBuilder("bash", script.absolutePath)
            .directory(tempDir)
            .redirectErrorStream(true)
            .apply {
                environment()["CHECKER_CMD"] = fakeChecker.absolutePath
                environment()["MODEL_GLOB"] = modelGlob
                environment()["SHOW_INFO_FLAG"] = ""
                environment()["PROOFS_FLAG"] = ""
                if (includeGitHubOutput) {
                    environment()["GITHUB_OUTPUT"] = githubOutput.absolutePath
                }
            }
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        return ScriptRunResult(
            exitCode = exitCode,
            output = output,
            sarifFile = File(tempDir, "eventb-checker-results.sarif"),
            gitLabJUnit = File(tempDir, "eventb-validation-results.xml"),
            gitLabJson = File(tempDir, "eventb-validation-report.json"),
            githubOutput = githubOutput,
        )
    }

    private fun writeFakeChecker(): File {
        val script = File(tempDir, "fake-checker.sh")
        script.writeText(
            """
            #!/usr/bin/env bash
            set -euo pipefail
            target="${'$'}{!#}"
            if [[ "${'$'}target" == *"invalid.zip" ]]; then
              cat <<'EOF'
            {"${'$'}schema":"https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/schemas/sarif-schema-2.1.0.json","version":"2.1.0","runs":[{"tool":{"driver":{"name":"fake","rules":[]}},"results":[{"level":"error","message":{"text":"broken model"},"locations":[{"physicalLocation":{"artifactLocation":{"uri":"invalid.zip"}}}]}]}]}
            EOF
              exit 1
            fi
            cat <<'EOF'
            {"${'$'}schema":"https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/schemas/sarif-schema-2.1.0.json","version":"2.1.0","runs":[{"tool":{"driver":{"name":"fake","rules":[]}},"results":[]}]}
            EOF
            """.trimIndent(),
        )
        check(script.setExecutable(true))
        return script
    }

    private data class ScriptRunResult(
        val exitCode: Int,
        val output: String,
        val sarifFile: File,
        val gitLabJUnit: File,
        val gitLabJson: File,
        val githubOutput: File,
    )
}
