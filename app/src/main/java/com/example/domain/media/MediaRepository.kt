package com.example.domain.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MediaRepository(private val context: Context) {

    private val decimalFormat = DecimalFormat("#,##0.0")

    fun hasMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val imagesGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
            val videosGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
            imagesGranted && videosGranted
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun getRecentPhotos(limit: Int = 10): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!hasMediaPermission()) return@withContext emptyList()

        val photos = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.MIME_TYPE
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

                while (cursor.moveToNext() && photos.size < limit) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "photo_$id.jpg"
                    val size = cursor.getLong(sizeColumn)
                    val dateAdded = cursor.getLong(dateColumn)
                    val mime = cursor.getString(mimeColumn) ?: "image/jpeg"

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    photos.add(
                        MediaItem(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            sizeBytes = size,
                            dateAddedEpochSeconds = dateAdded,
                            isVideo = false,
                            mimeType = mime
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        photos
    }

    suspend fun getRecentVideos(limit: Int = 10): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!hasMediaPermission()) return@withContext emptyList()

        val videos = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.MIME_TYPE
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

                while (cursor.moveToNext() && videos.size < limit) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "video_$id.mp4"
                    val size = cursor.getLong(sizeColumn)
                    val dateAdded = cursor.getLong(dateColumn)
                    val durationMs = cursor.getLong(durationColumn)
                    val mime = cursor.getString(mimeColumn) ?: "video/mp4"

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    videos.add(
                        MediaItem(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            sizeBytes = size,
                            dateAddedEpochSeconds = dateAdded,
                            isVideo = true,
                            durationSeconds = durationMs / 1000,
                            mimeType = mime
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        videos
    }

    suspend fun readMediaBytes(item: MediaItem, maxBytes: Long = 45 * 1024 * 1024): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    val buffer = ByteArray(8192)
                    val output = ByteArrayOutputStream()
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        totalRead += bytesRead
                        if (totalRead > maxBytes) {
                            return@withContext null // Exceeds Telegram size limit
                        }
                        output.write(buffer, 0, bytesRead)
                    }
                    output.toByteArray()
                }
            } catch (e: Exception) {
                null
            }
        }

    fun formatBytes(bytes: Long): String {
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1.0) {
            "${decimalFormat.format(mb)} MB"
        } else {
            val kb = bytes.toDouble() / 1024
            "${decimalFormat.format(kb)} KB"
        }
    }

    fun formatDate(epochSeconds: Long): String {
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        return sdf.format(Date(epochSeconds * 1000))
    }

    fun formatDuration(seconds: Long): String {
        val min = seconds / 60
        val sec = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    suspend fun generatePhotosTelegramMessage(): String {
        if (!hasMediaPermission()) {
            return "⚠️ <b>Media Permission Missing</b>\nPlease open the TeleManage app on your phone and grant Media Access."
        }
        val photos = getRecentPhotos(8)
        if (photos.isEmpty()) {
            return "📷 <b>No Recent Photos Found</b>\nMediaStore returned 0 photos in internal/external storage."
        }

        val sb = StringBuilder()
        sb.append("📸 <b>Recent Photos on Device</b>\n\n")
        photos.forEachIndexed { index, photo ->
            val num = index + 1
            val size = formatBytes(photo.sizeBytes)
            val date = formatDate(photo.dateAddedEpochSeconds)
            sb.append("<b>$num.</b> <code>${photo.displayName}</code>\n")
            sb.append("   • ID: <code>${photo.id}</code> | Size: $size | $date\n")
            sb.append("   • Download: /photo_${photo.id} or <code>/photo $num</code>\n\n")
        }
        sb.append("💡 <i>Tip: Send <code>/photo &lt;number or ID&gt;</code> to download.</i>")
        return sb.toString()
    }

    suspend fun generateVideosTelegramMessage(): String {
        if (!hasMediaPermission()) {
            return "⚠️ <b>Media Permission Missing</b>\nPlease open the TeleManage app on your phone and grant Media Access."
        }
        val videos = getRecentVideos(8)
        if (videos.isEmpty()) {
            return "🎬 <b>No Recent Videos Found</b>\nMediaStore returned 0 videos in internal/external storage."
        }

        val sb = StringBuilder()
        sb.append("🎬 <b>Recent Videos on Device</b>\n\n")
        videos.forEachIndexed { index, video ->
            val num = index + 1
            val size = formatBytes(video.sizeBytes)
            val duration = formatDuration(video.durationSeconds)
            val date = formatDate(video.dateAddedEpochSeconds)
            sb.append("<b>$num.</b> <code>${video.displayName}</code>\n")
            sb.append("   • ID: <code>${video.id}</code> | Dur: $duration | Size: $size | $date\n")
            sb.append("   • Download: /video_${video.id} or <code>/video $num</code>\n\n")
        }
        sb.append("💡 <i>Tip: Send <code>/video &lt;number or ID&gt;</code> to download.</i>")
        return sb.toString()
    }
}
