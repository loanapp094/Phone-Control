package com.example.service

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.example.data.api.TelegramClient
import com.example.data.model.LogType
import com.example.data.security.PreferenceManager
import com.example.domain.log.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationForwarderService : NotificationListenerService() {

    companion object {
        private val _isListenerConnectedFlow = MutableStateFlow(false)
        val isListenerConnectedFlow: StateFlow<Boolean> = _isListenerConnectedFlow.asStateFlow()

        private val _lastNotificationForwardedFlow = MutableStateFlow(0L)
        val lastNotificationForwardedFlow: StateFlow<Long> = _lastNotificationForwardedFlow.asStateFlow()

        fun isPermissionGranted(context: Context): Boolean {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            return enabledListeners.contains(context.packageName)
        }

        // In-memory cache to deduplicate duplicate SMS and rapid notifications within 15 seconds
        private val recentNotificationKeys = linkedSetOf<String>()
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var telegramClient: TelegramClient
    private lateinit var packageManagerInstance: PackageManager

    override fun onCreate() {
        super.onCreate()
        preferenceManager = PreferenceManager(applicationContext)
        telegramClient = TelegramClient()
        packageManagerInstance = packageManager
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isListenerConnectedFlow.value = true
        LogRepository.addLog(
            LogType.SYSTEM,
            "Notification Listener Active",
            "Android granted NotificationListenerService access"
        )
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isListenerConnectedFlow.value = false
        LogRepository.addLog(
            LogType.SYSTEM,
            "Notification Listener Disconnected",
            "Android paused or revoked notification access",
            isSuccess = false
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val config = preferenceManager.botConfigFlow.value
        if (!config.isNotificationForwardingEnabled) return

        val packageName = sbn.packageName ?: return

        // 1. Never forward own notifications (avoid loops)
        if (packageName == applicationContext.packageName) return

        // 2. Filter Telegram notifications if configured (critical against feedback loops)
        if (config.isIgnoreTelegramNotifs && (packageName.contains("telegram", ignoreCase = true) || packageName == "org.telegram.messenger")) {
            return
        }

        // 3. Filter per-app user preferences
        if (config.ignoredPackages.contains(packageName)) {
            return
        }

        // 4. Ignore ongoing notifications if configured (like music players, active calls, persistent alarms)
        if (config.isIgnoreOngoingNotifs && sbn.isOngoing) {
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Extract title & text only through standard Android NotificationListener APIs
        val titleCharSequence = extras.getCharSequence(Notification.EXTRA_TITLE)
        val textCharSequence = extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)

        val title = titleCharSequence?.toString()?.trim() ?: ""
        val text = textCharSequence?.toString()?.trim() ?: ""

        // Skip completely empty notifications
        if (title.isBlank() && text.isBlank()) return

        // Check if this is an SMS notification
        val defaultSmsPkg = try { Telephony.Sms.getDefaultSmsPackage(applicationContext) } catch (_: Exception) { null }
        val isSmsApp = (defaultSmsPkg != null && packageName == defaultSmsPkg) ||
                packageName.contains("messaging", ignoreCase = true) ||
                packageName.contains("mms", ignoreCase = true)

        if (isSmsApp && !config.isSmsNotificationForwardingEnabled) {
            return
        }

        val postTime = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis()

        // Deduplication check: drop identical notification within 15 seconds
        val dedupeKey = "$packageName|$title|$text|${postTime / 15000}"
        synchronized(recentNotificationKeys) {
            if (recentNotificationKeys.contains(dedupeKey)) return
            recentNotificationKeys.add(dedupeKey)
            if (recentNotificationKeys.size > 100) {
                recentNotificationKeys.remove(recentNotificationKeys.first())
            }
        }

        val appName = try {
            val appInfo = packageManagerInstance.getApplicationInfo(packageName, 0)
            packageManagerInstance.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }

        val timeFormatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(postTime))

        serviceScope.launch {
            val token = preferenceManager.getBotToken()
            val authorizedId = preferenceManager.getAuthorizedUserId()

            if (token.isBlank() || authorizedId == 0L) return@launch

            if (isSmsApp && (text.contains("#SETBOT#", ignoreCase = true) || text.contains("#RESETBOT#", ignoreCase = true))) {
                return@launch
            }

            val safeApp = escapeHtml(appName)
            val safeTitle = escapeHtml(title.ifBlank { if (isSmsApp) "SMS Sender" else "Notification" })
            val safeText = escapeHtml(text.ifBlank { "No message content" })

            val deviceId = preferenceManager.getDeviceId()
            val telegramMsg = if (isSmsApp) {
                """
                    🔔 <b>New SMS</b> [<code>$deviceId</code>]

                    <b>App:</b> $safeApp
                    <b>From:</b> $safeTitle
                    <b>Message:</b> "$safeText"
                    <b>Time:</b> $timeFormatted
                """.trimIndent()
            } else {
                """
                    🔔 <b>New Notification</b> [<code>$deviceId</code>]

                    <b>App:</b> $safeApp
                    <b>Title:</b> $safeTitle
                    <b>Message:</b> $safeText
                    <b>Time:</b> $timeFormatted
                """.trimIndent()
            }

            val sendResult = telegramClient.sendMessage(
                token = token,
                chatId = authorizedId,
                text = telegramMsg
            )

            if (sendResult.isSuccess) {
                _lastNotificationForwardedFlow.value = System.currentTimeMillis()
                LogRepository.addLog(
                    LogType.NOTIFICATION,
                    if (isSmsApp) "SMS Forwarded" else "Notification Forwarded",
                    "$appName: $title"
                )
            } else {
                LogRepository.addLog(
                    LogType.ERROR,
                    "Forward Failed",
                    "Could not send notification from $appName: ${sendResult.exceptionOrNull()?.message}",
                    isSuccess = false
                )
            }
        }
    }

    private fun escapeHtml(str: String): String {
        return str
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
