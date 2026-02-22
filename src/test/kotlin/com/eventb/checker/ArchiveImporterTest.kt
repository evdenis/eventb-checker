package com.eventb.checker

import com.eventb.checker.TestZipHelper.createZip
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ArchiveImporterTest {

    @TempDir
    lateinit var tempDir: File

    private val importer = ArchiveImporter()

    @Test
    fun `import categorizes machine and context files`() {
        val zip = createZip(
            tempDir,
            "project/MyMachine.bum" to "<org.eventb.core.machineFile/>",
            "project/MyContext.buc" to "<org.eventb.core.contextFile/>",
            "project/.project" to "<projectDescription/>"
        )

        val contents = importer.import(zip.absolutePath)

        assertThat(contents.machines).hasSize(1)
        assertThat(contents.machines[0].path).isEqualTo("project/MyMachine.bum")
        assertThat(contents.contexts).hasSize(1)
        assertThat(contents.contexts[0].path).isEqualTo("project/MyContext.buc")
        assertThat(contents.otherFiles).hasSize(1)
    }

    @Test
    fun `import handles empty zip`() {
        val zip = createZip(tempDir)

        val contents = importer.import(zip.absolutePath)

        assertThat(contents.machines).isEmpty()
        assertThat(contents.contexts).isEmpty()
        assertThat(contents.otherFiles).isEmpty()
    }

    @Test
    fun `import handles nested directories`() {
        val zip = createZip(
            tempDir,
            "root/sub/Deep.bum" to "<org.eventb.core.machineFile/>"
        )

        val contents = importer.import(zip.absolutePath)

        assertThat(contents.machines).hasSize(1)
        assertThat(contents.machines[0].path).isEqualTo("root/sub/Deep.bum")
    }

    @Test
    fun `import rejects non-zip file`() {
        val notZip = File(tempDir, "test.txt")
        notZip.writeText("not a zip")

        assertThatThrownBy { importer.import(notZip.absolutePath) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `import rejects non-existent file`() {
        assertThatThrownBy { importer.import("/nonexistent/path.zip") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
