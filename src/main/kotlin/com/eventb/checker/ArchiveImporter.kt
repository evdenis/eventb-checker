package com.eventb.checker

import com.eventb.checker.xml.XmlConstants
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

class ArchiveEntry(
    val path: String,
    val data: ByteArray
) {
    fun inputStream(): InputStream = ByteArrayInputStream(data)
}

data class ArchiveContents(
    val machines: List<ArchiveEntry>,
    val contexts: List<ArchiveEntry>,
    val otherFiles: List<String>
)

class ArchiveImporter {

    fun import(zipPath: String): ArchiveContents {
        val file = File(zipPath)
        require(file.exists()) { "File not found: $zipPath" }
        require(file.extension == "zip") { "Expected a .zip file: $zipPath" }

        val machines = mutableListOf<ArchiveEntry>()
        val contexts = mutableListOf<ArchiveEntry>()
        val otherFiles = mutableListOf<String>()

        ZipFile(file).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory) continue

                val name = entry.name
                val ext = name.substringAfterLast('.', "").let { ".$it" }
                val data = zip.getInputStream(entry).readAllBytes()

                when (ext) {
                    XmlConstants.EXT_MACHINE -> machines.add(ArchiveEntry(name, data))
                    XmlConstants.EXT_CONTEXT -> contexts.add(ArchiveEntry(name, data))
                    else -> otherFiles.add(name)
                }
            }
        }

        return ArchiveContents(machines, contexts, otherFiles)
    }
}
