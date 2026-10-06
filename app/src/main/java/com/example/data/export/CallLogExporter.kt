package com.example.data.export

import com.example.data.calls.CallLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallLogExporter(private val callLogRepository: CallLogRepository) {

    suspend fun exportToCsv(outputFile: File): Result<File> = withContext(Dispatchers.IO) {
        if (!callLogRepository.hasPermission()) {
            return@withContext Result.failure(SecurityException("READ_CALL_LOG permission is required"))
        }

        try {
            outputFile.parentFile?.mkdirs()
            val calls = callLogRepository.getCallLogs(limit = 1000)
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            OutputStreamWriter(FileOutputStream(outputFile), StandardCharsets.UTF_8).use { writer ->
                // Header
                writer.write("Name,Number,Type,Time,Duration\n")

                for (call in calls) {
                    val safeName = escapeCsv(call.cachedName ?: "")
                    val safeNumber = escapeCsv(call.number)
                    val safeType = escapeCsv(call.type.name)
                    val safeTime = escapeCsv(sdf.format(Date(call.timestamp)))
                    val duration = call.durationSeconds

                    writer.write("$safeName,$safeNumber,$safeType,$safeTime,$duration\n")
                }
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
