package com.example.data.export

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class FileExporter(private val context: Context) {

    suspend fun exportAccessibleFiles(outputDir: File): Result<List<File>> = withContext(Dispatchers.IO) {
        try {
            outputDir.mkdirs()
            val copiedFiles = mutableListOf<File>()

            // App-specific documents and files that this app legitimately manages
            val sourceDirs = listOfNotNull(
                context.getExternalFilesDir(null),
                context.filesDir
            )

            val manifestList = JSONArray()

            for (srcDir in sourceDirs) {
                val files = srcDir.listFiles() ?: continue
                for (file in files) {
                    if (file.isFile && !file.name.endsWith(".key") && !file.name.contains("telemanage_secure")) {
                        val target = File(outputDir, file.name)
                        FileInputStream(file).use { input ->
                            FileOutputStream(target).use { output ->
                                input.copyTo(output)
                            }
                        }
                        copiedFiles.add(target)

                        manifestList.put(JSONObject().apply {
                            put("name", file.name)
                            put("size", file.length())
                        })
                    }
                }
            }

            // Write files_manifest.json
            val manifestFile = File(outputDir, "files_manifest.json")
            manifestFile.writeText(manifestList.toString(2))
            copiedFiles.add(manifestFile)

            Result.success(copiedFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
