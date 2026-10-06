package com.example.data.export

import com.example.data.sms.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsExporter(private val smsRepository: SmsRepository) {

    suspend fun exportToJson(outputFile: File): Result<File> = withContext(Dispatchers.IO) {
        if (!smsRepository.hasPermission()) {
            return@withContext Result.failure(SecurityException("READ_SMS permission is required"))
        }

        try {
            outputFile.parentFile?.mkdirs()
            val messages = smsRepository.getMessages(limit = 2000)
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            val jsonArray = JSONArray()
            for (msg in messages) {
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("threadId", msg.threadId)
                    put("address", msg.address)
                    put("body", msg.body)
                    put("date", sdf.format(Date(msg.date)))
                    put("type", if (msg.isSent) "SENT" else "INBOX")
                    put("read", msg.isRead)
                }
                jsonArray.put(obj)
            }

            OutputStreamWriter(FileOutputStream(outputFile), StandardCharsets.UTF_8).use { writer ->
                writer.write(jsonArray.toString(2))
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
