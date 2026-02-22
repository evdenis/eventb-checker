package com.eventb.checker

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object TestZipHelper {

    fun createZip(dir: File, vararg entries: Pair<String, String>): File {
        val zipFile = File(dir, "test.zip")
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return zipFile
    }
}
