package com.example.data.export

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipExportManager(private val maxArchiveSizeBytes: Long = 25 * 1024 * 1024) {

    suspend fun createZipArchives(
        sourceFiles: List<File>,
        outputDir: File,
        archiveBaseName: String,
        baseDir: File? = null,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        try {
            outputDir.mkdirs()
            val createdArchives = mutableListOf<File>()

            if (sourceFiles.isEmpty()) {
                val emptyZip = File(outputDir, "${archiveBaseName}.zip")
                ZipOutputStream(FileOutputStream(emptyZip)).use {
                    it.putNextEntry(ZipEntry("README.txt"))
                    it.write("No files included in this export.".toByteArray())
                    it.closeEntry()
                }
                return@withContext Result.success(listOf(emptyZip))
            }

            var partIndex = 1
            var currentZipFile = File(outputDir, "${archiveBaseName}_${String.format("%03d", partIndex)}.zip")
            var currentZipOut = ZipOutputStream(FileOutputStream(currentZipFile))
            var currentZipBytes = 0L
            val currentEntriesInPart = mutableSetOf<String>()

            for ((fileIndex, file) in sourceFiles.withIndex()) {
                if (!file.exists() || !file.isFile) continue

                val fileSize = file.length()
                // If adding this file exceeds limit and we already wrote files to current archive, rotate
                if (currentZipBytes > 0 && (currentZipBytes + fileSize > maxArchiveSizeBytes)) {
                    currentZipOut.close()
                    createdArchives.add(currentZipFile)

                    partIndex++
                    currentZipFile = File(outputDir, "${archiveBaseName}_${String.format("%03d", partIndex)}.zip")
                    currentZipOut = ZipOutputStream(FileOutputStream(currentZipFile))
                    currentZipBytes = 0L
                    currentEntriesInPart.clear()
                }

                val rawEntryName = if (baseDir != null && file.startsWith(baseDir)) {
                    file.relativeTo(baseDir).path.replace(File.separatorChar, '/')
                } else {
                    file.name
                }

                var finalEntryName = rawEntryName
                var duplicateCounter = 1
                while (currentEntriesInPart.contains(finalEntryName)) {
                    val dotIdx = rawEntryName.lastIndexOf('.')
                    finalEntryName = if (dotIdx > 0) {
                        "${rawEntryName.substring(0, dotIdx)}_$duplicateCounter${rawEntryName.substring(dotIdx)}"
                    } else {
                        "${rawEntryName}_$duplicateCounter"
                    }
                    duplicateCounter++
                }
                currentEntriesInPart.add(finalEntryName)

                currentZipOut.putNextEntry(ZipEntry(finalEntryName))
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(65536)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        currentZipOut.write(buffer, 0, read)
                        currentZipBytes += read
                    }
                }
                currentZipOut.closeEntry()
                onProgress?.invoke(fileIndex + 1, sourceFiles.size)
            }

            currentZipOut.close()
            if (currentZipFile.exists() && currentZipFile.length() > 0) {
                createdArchives.add(currentZipFile)
            }

            // If there's only 1 part, rename prefix_001.zip to prefix.zip for cleaner UX
            if (createdArchives.size == 1 && partIndex == 1) {
                val singleFile = File(outputDir, "${archiveBaseName}.zip")
                if (createdArchives[0].renameTo(singleFile)) {
                    createdArchives[0] = singleFile
                }
            }

            Result.success(createdArchives)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cleanDirectory(dir: File) {
        try {
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
