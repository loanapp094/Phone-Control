package com.example.data.api

import android.util.Log
import com.example.data.model.InlineKeyboardMarkup
import com.example.data.model.SendMessagePayload
import com.example.data.model.TelegramResponse
import com.example.data.model.TelegramUpdate
import com.example.data.model.TelegramUser
import com.example.data.model.LogType
import com.example.domain.log.LogRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

class TelegramClient {

    companion object {
        private const val TAG = "TelegramClient"
        private const val BASE_URL = "https://api.telegram.org/bot"
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Dedicated client for polling with longer read timeout
    private val pollingHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Standard client for quick requests & uploads
    private val standardHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Verifies bot token and returns bot details.
     */
    suspend fun getMe(token: String): Result<TelegramUser> = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Bot token cannot be empty"))
        }
        try {
            val request = Request.Builder()
                .url("$BASE_URL$token/getMe")
                .get()
                .build()

            val response = standardHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            val type = Types.newParameterizedType(TelegramResponse::class.java, TelegramUser::class.java)
            val adapter = moshi.adapter<TelegramResponse<TelegramUser>>(type)
            val parsed = adapter.fromJson(body)

            if (parsed != null && parsed.ok && parsed.result != null) {
                Result.success(parsed.result)
            } else {
                val errorMsg = parsed?.description ?: "HTTP ${response.code}: Failed to verify bot token"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMe error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Polls for updates using long-polling with Telegram API.
     */
    suspend fun getUpdates(
        token: String,
        offset: Long?,
        timeoutSeconds: Int = 25
    ): Result<List<TelegramUpdate>> = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Bot token not configured"))
        }
        try {
            val urlBuilder = StringBuilder("$BASE_URL$token/getUpdates?timeout=$timeoutSeconds")
            if (offset != null) {
                urlBuilder.append("&offset=$offset")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .get()
                .build()

            val response = pollingHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            val listType = Types.newParameterizedType(List::class.java, TelegramUpdate::class.java)
            val responseType = Types.newParameterizedType(TelegramResponse::class.java, listType)
            val adapter = moshi.adapter<TelegramResponse<List<TelegramUpdate>>>(responseType)
            val parsed = adapter.fromJson(body)

            if (parsed != null && parsed.ok && parsed.result != null) {
                Result.success(parsed.result)
            } else {
                val errorMsg = parsed?.description ?: "HTTP ${response.code}: Error fetching updates"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends an HTML-formatted message to the designated chat.
     */
    suspend fun sendMessage(
        token: String,
        chatId: Long,
        text: String,
        replyMarkup: InlineKeyboardMarkup? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (token.isBlank() || chatId == 0L) {
            return@withContext Result.failure(IllegalStateException("Credentials not configured"))
        }
        try {
            val payload = SendMessagePayload(
                chatId = chatId,
                text = text,
                parseMode = "HTML",
                replyMarkup = replyMarkup
            )
            val adapter = moshi.adapter(SendMessagePayload::class.java)
            val jsonBody = adapter.toJson(payload)

            val request = Request.Builder()
                .url("$BASE_URL$token/sendMessage")
                .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .build()

            val response = standardHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            val type = Types.newParameterizedType(TelegramResponse::class.java, Any::class.java)
            val respAdapter = moshi.adapter<TelegramResponse<Any>>(type)
            val parsed = respAdapter.fromJson(body)

            if (parsed != null && parsed.ok) {
                Result.success(true)
            } else {
                val errorMsg = parsed?.description ?: "HTTP ${response.code}: Error sending message"
                LogRepository.addLog(
                    LogType.ERROR,
                    "Telegram API Error",
                    errorMsg,
                    isSuccess = false
                )
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Uploads and sends a photo via multipart request.
     */
    suspend fun sendPhoto(
        token: String,
        chatId: Long,
        imageBytes: ByteArray,
        filename: String,
        caption: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (token.isBlank() || chatId == 0L) {
            return@withContext Result.failure(IllegalStateException("Credentials not configured"))
        }
        try {
            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId.toString())
                .addFormDataPart(
                    "photo",
                    filename,
                    imageBytes.toRequestBody("image/*".toMediaTypeOrNull())
                )

            if (!caption.isNullOrBlank()) {
                builder.addFormDataPart("caption", caption)
                builder.addFormDataPart("parse_mode", "HTML")
            }

            val request = Request.Builder()
                .url("$BASE_URL$token/sendPhoto")
                .post(builder.build())
                .build()

            val response = standardHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            val type = Types.newParameterizedType(TelegramResponse::class.java, Any::class.java)
            val respAdapter = moshi.adapter<TelegramResponse<Any>>(type)
            val parsed = respAdapter.fromJson(body)

            if (parsed != null && parsed.ok) {
                Result.success(true)
            } else {
                val errorMsg = parsed?.description ?: "HTTP ${response.code}: Error uploading photo"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendPhoto error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Uploads and sends a video/document via multipart request.
     */
    suspend fun sendMediaDocument(
        token: String,
        chatId: Long,
        mediaBytes: ByteArray,
        filename: String,
        caption: String? = null,
        isVideo: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (token.isBlank() || chatId == 0L) {
            return@withContext Result.failure(IllegalStateException("Credentials not configured"))
        }
        try {
            val endpoint = if (isVideo) "sendVideo" else "sendDocument"
            val fieldName = if (isVideo) "video" else "document"
            val mimeType = if (isVideo) "video/*" else "application/octet-stream"

            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId.toString())
                .addFormDataPart(
                    fieldName,
                    filename,
                    mediaBytes.toRequestBody(mimeType.toMediaTypeOrNull())
                )

            if (!caption.isNullOrBlank()) {
                builder.addFormDataPart("caption", caption)
                builder.addFormDataPart("parse_mode", "HTML")
            }

            val request = Request.Builder()
                .url("$BASE_URL$token/$endpoint")
                .post(builder.build())
                .build()

            val response = standardHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            val type = Types.newParameterizedType(TelegramResponse::class.java, Any::class.java)
            val respAdapter = moshi.adapter<TelegramResponse<Any>>(type)
            val parsed = respAdapter.fromJson(body)

            if (parsed != null && parsed.ok) {
                Result.success(true)
            } else {
                val errorMsg = parsed?.description ?: "HTTP ${response.code}: Error uploading media"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendMediaDocument error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Streams a File directly as a document attachment without loading it into RAM.
     */
    suspend fun sendDocumentFile(
        token: String,
        chatId: Long,
        file: File,
        caption: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (token.isBlank() || chatId == 0L) {
            return@withContext Result.failure(IllegalStateException("Credentials not configured"))
        }
        if (!file.exists()) {
            return@withContext Result.failure(IllegalArgumentException("File does not exist: ${file.name}"))
        }
        try {
            val mimeType = when {
                file.name.endsWith(".zip") -> "application/zip"
                file.name.endsWith(".csv") -> "text/csv"
                file.name.endsWith(".json") -> "application/json"
                else -> "application/octet-stream"
            }

            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId.toString())
                .addFormDataPart(
                    "document",
                    file.name,
                    file.asRequestBody(mimeType.toMediaTypeOrNull())
                )

            if (!caption.isNullOrBlank()) {
                builder.addFormDataPart("caption", caption)
                builder.addFormDataPart("parse_mode", "HTML")
            }

            val request = Request.Builder()
                .url("$BASE_URL$token/sendDocument")
                .post(builder.build())
                .build()

            val response = standardHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            val type = Types.newParameterizedType(TelegramResponse::class.java, Any::class.java)
            val respAdapter = moshi.adapter<TelegramResponse<Any>>(type)
            val parsed = respAdapter.fromJson(body)

            if (parsed != null && parsed.ok) {
                Result.success(true)
            } else {
                val errorMsg = parsed?.description ?: "HTTP ${response.code}: Error uploading document"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendDocumentFile error: ${e.message}")
            Result.failure(e)
        }
    }
}
