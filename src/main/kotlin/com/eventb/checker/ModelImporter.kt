package com.eventb.checker

import com.eventb.checker.xml.XmlConstants
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

class ModelEntry(val path: String, val data: ByteArray) {
    fun inputStream(): InputStream = ByteArrayInputStream(data)
}

data class ModelContents(
    val machines: List<ModelEntry>,
    val contexts: List<ModelEntry>,
    val eventbFiles: List<ModelEntry> = emptyList(),
    val proofFiles: List<ModelEntry> = emptyList(),
    val proofObligationFiles: List<ModelEntry> = emptyList(),
    val proofStatusFiles: List<ModelEntry> = emptyList(),
    val otherFiles: List<String> = emptyList(),
)

class ModelImporter {

    companion object {
        const val EXT_EVENTB = ".eventb"
    }

    fun import(modelPath: String): ModelContents {
        val file = File(modelPath)
        require(file.exists()) { "File not found: $modelPath" }

        return when {
            file.isDirectory -> importDirectory(file)
            file.extension == "zip" -> importZip(file)
            file.extension == "eventb" -> importSingleEventbFile(file)
            else -> throw IllegalArgumentException("Expected a .zip file, directory, or .eventb file: $modelPath")
        }
    }

    private fun importZip(file: File): ModelContents {
        val acc = ModelContentsAccumulator()

        ZipFile(file).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory) continue
                val data = zip.getInputStream(entry).use { it.readAllBytes() }
                acc.categorize(entry.name, data)
            }
        }

        return acc.build()
    }

    private fun importSingleEventbFile(file: File): ModelContents {
        val parentName = file.parentFile?.name ?: "unknown"
        val entryPath = "$parentName/${file.name}"
        val data = file.readBytes()
        return ModelContents(
            machines = emptyList(),
            contexts = emptyList(),
            eventbFiles = listOf(ModelEntry(entryPath, data)),
        )
    }

    private fun importDirectory(dir: File): ModelContents {
        val acc = ModelContentsAccumulator()
        val dirName = dir.name

        dir.walk().filter { it.isFile }.forEach { file ->
            val relativePath = file.relativeTo(dir).path
            val entryPath = "$dirName/$relativePath"
            acc.categorize(entryPath, file.readBytes())
        }

        return acc.build()
    }

    private class ModelContentsAccumulator {
        val machines = mutableListOf<ModelEntry>()
        val contexts = mutableListOf<ModelEntry>()
        val eventbFiles = mutableListOf<ModelEntry>()
        val proofFiles = mutableListOf<ModelEntry>()
        val proofObligationFiles = mutableListOf<ModelEntry>()
        val proofStatusFiles = mutableListOf<ModelEntry>()
        val otherFiles = mutableListOf<String>()

        fun categorize(path: String, data: ByteArray) {
            val ext = path.substringAfterLast('.', "").let { ".$it" }
            when (ext) {
                XmlConstants.EXT_MACHINE -> machines.add(ModelEntry(path, data))
                XmlConstants.EXT_CONTEXT -> contexts.add(ModelEntry(path, data))
                EXT_EVENTB -> eventbFiles.add(ModelEntry(path, data))
                XmlConstants.EXT_PROOF -> proofFiles.add(ModelEntry(path, data))
                XmlConstants.EXT_PROOF_OBLIGATIONS -> proofObligationFiles.add(ModelEntry(path, data))
                XmlConstants.EXT_PROOF_STATUS -> proofStatusFiles.add(ModelEntry(path, data))
                else -> otherFiles.add(path)
            }
        }

        fun build() = ModelContents(machines, contexts, eventbFiles, proofFiles, proofObligationFiles, proofStatusFiles, otherFiles)
    }
}
