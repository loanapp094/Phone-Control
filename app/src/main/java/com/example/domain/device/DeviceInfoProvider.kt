package com.example.domain.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import com.example.data.model.DeviceStatus
import java.text.DecimalFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class DeviceInfoProvider(private val context: Context) {

    private val decimalFormat = DecimalFormat("#,##0.00")

    fun getDeviceStatus(isServiceRunning: Boolean, serviceStartTimeMs: Long = 0L): DeviceStatus {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        // Battery percentage
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) ((level.toFloat() / scale.toFloat()) * 100).toInt() else 0

        // Charging state & plug type
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val chargePlug = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val chargingSource = when {
            !isCharging -> "Not Charging"
            chargePlug == BatteryManager.BATTERY_PLUGGED_AC -> "AC Wall Charger"
            chargePlug == BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
            chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Dock"
            else -> "Charging"
        }

        // Battery Health
        val healthCode = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val batteryHealth = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Normal"
        }

        // Battery temperature (given in tenths of a degree Celsius)
        val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val batteryTempC = tempRaw / 10f

        // Internal Storage
        val stat = StatFs(Environment.getDataDirectory().path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong
        val totalStorageBytes = totalBlocks * blockSize
        val availableStorageBytes = availableBlocks * blockSize

        // Network Status
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connMgr?.activeNetwork
        val caps = connMgr?.getNetworkCapabilities(activeNetwork)
        val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val networkType = when {
            !isConnected -> "Offline"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular (Mobile Data)"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
            else -> "Connected"
        }

        // Service Uptime
        val uptimeStr = if (isServiceRunning && serviceStartTimeMs > 0L) {
            val diffMs = System.currentTimeMillis() - serviceStartTimeMs
            formatDuration(diffMs)
        } else if (isServiceRunning) {
            "Active"
        } else {
            "Stopped"
        }

        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase(Locale.getDefault()) }
        val model = Build.MODEL
        val deviceName = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"

        return DeviceStatus(
            manufacturer = manufacturer,
            model = model,
            deviceName = deviceName,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            batteryPercent = batteryPct,
            isCharging = isCharging,
            chargingSource = chargingSource,
            batteryHealth = batteryHealth,
            batteryTemperatureCelsius = batteryTempC,
            totalInternalStorageBytes = totalStorageBytes,
            availableInternalStorageBytes = availableStorageBytes,
            networkType = networkType,
            isNetworkConnected = isConnected,
            isServiceRunning = isServiceRunning,
            serviceUptimeFormatted = uptimeStr
        )
    }

    fun formatBytes(bytes: Long): String {
        val gb = bytes.toDouble() / (1024 * 1024 * 1024)
        return if (gb >= 1.0) {
            "${decimalFormat.format(gb)} GB"
        } else {
            val mb = bytes.toDouble() / (1024 * 1024)
            "${decimalFormat.format(mb)} MB"
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
        return when {
            hours > 0 -> String.format(Locale.getDefault(), "%dh %02dm %02ds", hours, minutes, seconds)
            minutes > 0 -> String.format(Locale.getDefault(), "%dm %02ds", minutes, seconds)
            else -> String.format(Locale.getDefault(), "%ds", seconds)
        }
    }

    fun generateStatusTelegramMessage(
        isServiceRunning: Boolean,
        serviceStartTimeMs: Long,
        exportProgress: com.example.domain.export.ExportProgress? = null
    ): String {
        val s = getDeviceStatus(isServiceRunning, serviceStartTimeMs)
        val storageUsedStr = formatBytes(s.usedStorageBytes)
        val storageTotalStr = formatBytes(s.totalInternalStorageBytes)
        val storageFreeStr = formatBytes(s.availableInternalStorageBytes)
        val chargingIcon = if (s.isCharging) "⚡" else "🔋"
        val networkIcon = if (s.isNetworkConnected) "📶" else "❌"
        val serviceIcon = if (s.isServiceRunning) "🟢" else "🔴"

        val exportStatusText = if (exportProgress != null && exportProgress.isRunning) {
            "🔴 Export in progress\n📦 <b>Current Export:</b> ${exportProgress.type?.title} (${exportProgress.percentage}%)"
        } else {
            "🟢 Ready"
        }

        return """
            📱 <b>Device Status</b>

            <b>Model:</b> ${s.deviceName}
            <b>OS:</b> Android ${s.androidVersion} (API ${s.sdkInt})
            $chargingIcon <b>Battery:</b> ${s.batteryPercent}% (${s.chargingSource})
            💾 <b>Storage:</b> $storageUsedStr / $storageTotalStr (${s.storagePercentUsed}% used, $storageFreeStr free)
            $networkIcon <b>Network:</b> ${s.networkType}
            $serviceIcon <b>Remote Service:</b> ${if (s.isServiceRunning) "Running (${s.serviceUptimeFormatted})" else "Inactive"}
            📤 <b>Export Service:</b> $exportStatusText
        """.trimIndent()
    }

    fun generateBatteryTelegramMessage(isServiceRunning: Boolean): String {
        val s = getDeviceStatus(isServiceRunning)
        val chargingIcon = if (s.isCharging) "⚡" else "🔋"
        return """
            $chargingIcon <b>Battery Information</b>

            <b>Level:</b> ${s.batteryPercent}%
            <b>Charging State:</b> ${if (s.isCharging) "Charging" else "Discharging"}
            <b>Power Source:</b> ${s.chargingSource}
            <b>Health:</b> ${s.batteryHealth}
            <b>Temperature:</b> ${String.format(Locale.getDefault(), "%.1f", s.batteryTemperatureCelsius)} °C
        """.trimIndent()
    }

    fun generateStorageTelegramMessage(isServiceRunning: Boolean): String {
        val s = getDeviceStatus(isServiceRunning)
        val used = formatBytes(s.usedStorageBytes)
        val total = formatBytes(s.totalInternalStorageBytes)
        val free = formatBytes(s.availableInternalStorageBytes)
        val percent = s.storagePercentUsed

        // Visual progress bar
        val barLength = 10
        val filled = (percent / 10).coerceIn(0, barLength)
        val progressBar = "█".repeat(filled) + "░".repeat(barLength - filled)

        return """
            💾 <b>Storage Information</b>

            <b>Usage:</b> $used / $total ($percent%)
            <code>[$progressBar]</code>
            <b>Free Space:</b> $free
        """.trimIndent()
    }
}
