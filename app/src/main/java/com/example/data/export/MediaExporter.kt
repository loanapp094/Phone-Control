package com.example.data.export

import android.content.Context
import com.example.domain.media.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MediaExporter(
    private val context: Context,
    private val mediaRepository: MediaRepository
) {

    suspend fun exportPhotos(
        outputDir: File,
        limit: Int = 50,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        if (!mediaRepository.hasMediaPermission()) {
            return@withContext Result.failure(SecurityException("Media permission required"))
        }

        try {
            outputDir.mkdirs()
            val photos = mediaRepository.getRecentPhotos(limit)
            val exportedFiles = mutableListOf<File>()

            photos.forEachIndexed { index, item ->
                val safeFileName = "photo_${item.id}_${item.displayName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")}"
                val targetFile = File(outputDir, safeFileName)

                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(65536)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                    }
                }

                if (targetFile.exists() && targetFile.length() > 0) {
                    exportedFiles.add(targetFile)
                }
                onProgress?.invoke(index + 1, photos.size)
            }

            Result.success(exportedFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportVideos(
        outputDir: File,
        limit: Int = 20,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        if (!mediaRepository.hasMediaPermission()) {
            return@withContext Result.failure(SecurityException("Media permission required"))
        }

        try {
            outputDir.mkdirs()
            val videos = mediaRepository.getRecentVideos(limit)
            val exportedFiles = mutableListOf<File>()

            videos.forEachIndexed { index, item ->
                val safeFileName = "video_${item.id}_${item.displayName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")}"
                val targetFile = File(outputDir, safeFileName)

                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(65536)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                    }
                }

                if (targetFile.exists() && targetFile.length() > 0) {
                    exportedFiles.add(targetFile)
                }
                onProgress?.invoke(index + 1, videos.size)
            }

            Result.success(exportedFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
