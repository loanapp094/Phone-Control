package com.example.data.model

import android.net.Uri

data class DeviceStatus(
    val manufacturer: String,
    val model: String,
    val deviceName: String,
    val androidVersion: String,
    val sdkInt: Int,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val chargingSource: String, // AC, USB, Wireless, Not Charging
    val batteryHealth: String,
    val batteryTemperatureCelsius: Float,
    val totalInternalStorageBytes: Long,
    val availableInternalStorageBytes: Long,
    val networkType: String, // Wi-Fi, Mobile Data (5G/4G/3G), Offline
    val isNetworkConnected: Boolean,
    val isServiceRunning: Boolean,
    val serviceUptimeFormatted: String
) {
    val usedStorageBytes: Long get() = totalInternalStorageBytes - availableInternalStorageBytes
    val storagePercentUsed: Int
        get() = if (totalInternalStorageBytes > 0) {
            ((usedStorageBytes.toDouble() / totalInternalStorageBytes) * 100).toInt()
        } else 0
}

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val dateAddedEpochSeconds: Long,
    val isVideo: Boolean,
    val durationSeconds: Long = 0,
    val mimeType: String = ""
)

data class CapturedNotification(
    val id: Long = System.currentTimeMillis(),
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val forwardedSuccessfully: Boolean = true
)

enum class LogType {
    COMMAND,
    NOTIFICATION,
    MEDIA,
    SECURITY,
    SYSTEM,
    ERROR
}

data class LogEvent(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: LogType,
    val title: String,
    val details: String,
    val isSuccess: Boolean = true
)

data class AppFilterItem(
    val packageName: String,
    val appName: String,
    val isIgnored: Boolean
)
