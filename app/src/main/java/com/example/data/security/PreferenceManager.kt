package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BotConfig(
    val botToken: String = "",
    val authorizedUserId: Long = 0L,
    val botUsername: String = "",
    val isNotificationForwardingEnabled: Boolean = true,
    val isIgnoreTelegramNotifs: Boolean = true,
    val isIgnoreOngoingNotifs: Boolean = true,
    val isAutoStartOnBoot: Boolean = false,
    val ignoredPackages: Set<String> = emptySet(),
    val isContactAccessEnabled: Boolean = true,
    val isCallHistoryAccessEnabled: Boolean = true,
    val isSmsAccessEnabled: Boolean = true,
    val isSmsNotificationForwardingEnabled: Boolean = true,
    val isRemoteSmsSendingEnabled: Boolean = true,
    val sentSmsCount: Int = 0,
    val smsRateLimitMax: Int = 10,
    val smsRateLimitWindowMinutes: Int = 10,
    val preferredSimSubscriptionId: Int = -1
)

/**
 * Manages app configuration and credentials. Sensitive items (Bot Token & User ID)
 * are encrypted with KeyStoreManager before persisting to SharedPreferences.
 */
class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyStoreManager = KeyStoreManager()

    companion object {
        private const val PREFS_NAME = "telemanage_secure_prefs"
        private const val KEY_ENCRYPTED_BOT_TOKEN = "enc_bot_token"
        private const val KEY_ENCRYPTED_USER_ID = "enc_user_id"
        private const val KEY_NOTIFICATION_FORWARDING = "notification_forwarding_enabled"
        private const val KEY_IGNORE_TELEGRAM_NOTIFS = "ignore_telegram_notifs"
        private const val KEY_IGNORE_ONGOING_NOTIFS = "ignore_ongoing_notifs"
        private const val KEY_AUTO_START_BOOT = "auto_start_on_boot"
        private const val KEY_IGNORED_PACKAGES = "ignored_packages"
        private const val KEY_LAST_KNOWN_BOT_USERNAME = "last_known_bot_username"
        private const val KEY_CONTACT_ACCESS = "contact_access_enabled"
        private const val KEY_CALL_HISTORY_ACCESS = "call_history_access_enabled"
        private const val KEY_SMS_ACCESS = "sms_access_enabled"
        private const val KEY_SMS_NOTIF_FORWARDING = "sms_notif_forwarding_enabled"
        private const val KEY_REMOTE_SMS_SENDING = "remote_sms_sending_enabled"
        private const val KEY_SENT_SMS_COUNT = "sent_sms_count"
        private const val KEY_SMS_RATE_LIMIT_MAX = "sms_rate_limit_max"
        private const val KEY_SMS_RATE_LIMIT_WINDOW = "sms_rate_limit_window"
        private const val KEY_PREFERRED_SIM_SUB_ID = "preferred_sim_sub_id"
        private const val KEY_SENT_SMS_HISTORY_JSON = "sent_sms_history_json"
        private const val KEY_SERVICE_DESIRED_ENABLED = "service_desired_enabled"
        private const val KEY_LAST_HEARTBEAT_TIMESTAMP = "last_heartbeat_timestamp"
        private const val KEY_LAST_RECOVERY_TIMESTAMP = "last_recovery_timestamp"
        private const val KEY_LAST_KNOWN_LIFECYCLE_STATE = "last_known_lifecycle_state"
    }

    private val _botConfigFlow = MutableStateFlow<BotConfig>(loadConfig())
    val botConfigFlow: StateFlow<BotConfig> = _botConfigFlow.asStateFlow()

    private fun loadConfig(): BotConfig {
        val encToken = prefs.getString(KEY_ENCRYPTED_BOT_TOKEN, "") ?: ""
        val encUserId = prefs.getString(KEY_ENCRYPTED_USER_ID, "") ?: ""
        val token = if (encToken.isNotEmpty()) keyStoreManager.decrypt(encToken) else ""
        val userIdStr = if (encUserId.isNotEmpty()) keyStoreManager.decrypt(encUserId) else ""
        val userId = userIdStr.toLongOrNull() ?: 0L

        return BotConfig(
            botToken = token,
            authorizedUserId = userId,
            botUsername = prefs.getString(KEY_LAST_KNOWN_BOT_USERNAME, "") ?: "",
            isNotificationForwardingEnabled = prefs.getBoolean(KEY_NOTIFICATION_FORWARDING, true),
            isIgnoreTelegramNotifs = prefs.getBoolean(KEY_IGNORE_TELEGRAM_NOTIFS, true),
            isIgnoreOngoingNotifs = prefs.getBoolean(KEY_IGNORE_ONGOING_NOTIFS, true),
            isAutoStartOnBoot = prefs.getBoolean(KEY_AUTO_START_BOOT, false),
            ignoredPackages = prefs.getStringSet(KEY_IGNORED_PACKAGES, emptySet()) ?: emptySet(),
            isContactAccessEnabled = prefs.getBoolean(KEY_CONTACT_ACCESS, true),
            isCallHistoryAccessEnabled = prefs.getBoolean(KEY_CALL_HISTORY_ACCESS, true),
            isSmsAccessEnabled = prefs.getBoolean(KEY_SMS_ACCESS, true),
            isSmsNotificationForwardingEnabled = prefs.getBoolean(KEY_SMS_NOTIF_FORWARDING, true),
            isRemoteSmsSendingEnabled = prefs.getBoolean(KEY_REMOTE_SMS_SENDING, true),
            sentSmsCount = prefs.getInt(KEY_SENT_SMS_COUNT, 0),
            smsRateLimitMax = prefs.getInt(KEY_SMS_RATE_LIMIT_MAX, 10),
            smsRateLimitWindowMinutes = prefs.getInt(KEY_SMS_RATE_LIMIT_WINDOW, 10),
            preferredSimSubscriptionId = prefs.getInt(KEY_PREFERRED_SIM_SUB_ID, -1)
        )
    }

    fun saveBotCredentials(botToken: String, authorizedUserId: Long, botUsername: String = "") {
        val encToken = keyStoreManager.encrypt(botToken.trim())
        val encUserId = keyStoreManager.encrypt(authorizedUserId.toString())
        prefs.edit()
            .putString(KEY_ENCRYPTED_BOT_TOKEN, encToken)
            .putString(KEY_ENCRYPTED_USER_ID, encUserId)
            .apply()

        if (botUsername.isNotEmpty()) {
            prefs.edit().putString(KEY_LAST_KNOWN_BOT_USERNAME, botUsername).apply()
        }
        _botConfigFlow.value = loadConfig()
    }

    fun setNotificationForwarding(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_FORWARDING, enabled).apply()
        _botConfigFlow.value = loadConfig()
    }

    fun setIgnoreTelegramNotifs(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IGNORE_TELEGRAM_NOTIFS, enabled).apply()
        _botConfigFlow.value = loadConfig()
    }

    fun setIgnoreOngoingNotifs(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IGNORE_ONGOING_NOTIFS, enabled).apply()
        _botConfigFlow.value = loadConfig()
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START_BOOT, enabled).apply()
        _botConfigFlow.value = loadConfig()
    }

    fun setContactAccess(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONTACT_ACCESS, enabled).apply()
        _botConfigFlow.value = loadConfig()
    }

    fun setCallHistoryAccess(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALL_HISTORY_ACCESS, enabled).apply()
        _botConfigFlow.value = loadConfig()
    }

    fun setSmsAccess(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMS_ACCESS, enabled).apply()
        _botConfigFlow.value = loadConfig()
    }

    fun setSmsNotificationForwarding(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMS_NOTIF_FORWARDING, enabled).apply()
        _botConfigFlow.value = loadConfig()
    }

    fun toggleIgnoredPackage(packageName: String) {
        val current = _botConfigFlow.value.ignoredPackages.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        prefs.edit().putStringSet(KEY_IGNORED_PACKAGES, current).apply()
        _botConfigFlow.value = loadConfig()
    }

    fun setRemoteSmsSending(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMOTE_SMS_SENDING, enabled).apply()
        _botConfigFlow.value = loadConfig()
    }

    fun incrementSentSmsCount() {
        val current = prefs.getInt(KEY_SENT_SMS_COUNT, 0)
        prefs.edit().putInt(KEY_SENT_SMS_COUNT, current + 1).apply()
        _botConfigFlow.value = loadConfig()
    }

    fun setPreferredSimSubscriptionId(subId: Int) {
        prefs.edit().putInt(KEY_PREFERRED_SIM_SUB_ID, subId).apply()
        _botConfigFlow.value = loadConfig()
    }

    fun setSmsRateLimit(maxCount: Int, windowMinutes: Int) {
        prefs.edit()
            .putInt(KEY_SMS_RATE_LIMIT_MAX, maxCount)
            .putInt(KEY_SMS_RATE_LIMIT_WINDOW, windowMinutes)
            .apply()
        _botConfigFlow.value = loadConfig()
    }

    fun getSentSmsHistory(): List<com.example.data.model.SentSmsRecord> {
        val jsonStr = prefs.getString(KEY_SENT_SMS_HISTORY_JSON, "") ?: ""
        if (jsonStr.isBlank()) return emptyList()
        return try {
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<com.example.data.model.SentSmsRecord>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    com.example.data.model.SentSmsRecord(
                        id = obj.optString("id"),
                        recipientNumber = obj.optString("recipientNumber"),
                        recipientName = if (obj.has("recipientName") && !obj.isNull("recipientName")) obj.optString("recipientName") else null,
                        messageSnippet = obj.optString("messageSnippet"),
                        timestamp = obj.optLong("timestamp"),
                        status = obj.optString("status", "SENT"),
                        simInfo = if (obj.has("simInfo") && !obj.isNull("simInfo")) obj.optString("simInfo") else null,
                        failureReason = if (obj.has("failureReason") && !obj.isNull("failureReason")) obj.optString("failureReason") else null
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addSentSmsRecord(record: com.example.data.model.SentSmsRecord) {
        val current = getSentSmsHistory().toMutableList()
        current.add(0, record)
        while (current.size > 50) {
            current.removeAt(current.size - 1)
        }
        val array = org.json.JSONArray()
        for (item in current) {
            val obj = org.json.JSONObject().apply {
                put("id", item.id)
                put("recipientNumber", item.recipientNumber)
                item.recipientName?.let { put("recipientName", it) }
                put("messageSnippet", item.messageSnippet)
                put("timestamp", item.timestamp)
                put("status", item.status)
                item.simInfo?.let { put("simInfo", it) }
                item.failureReason?.let { put("failureReason", it) }
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_SENT_SMS_HISTORY_JSON, array.toString()).apply()
    }

    fun clearSentSmsHistory() {
        prefs.edit().remove(KEY_SENT_SMS_HISTORY_JSON).apply()
    }

    fun getBotToken(): String = _botConfigFlow.value.botToken
    fun getAuthorizedUserId(): Long = _botConfigFlow.value.authorizedUserId
    fun isConfigured(): Boolean = getBotToken().isNotBlank() && getAuthorizedUserId() != 0L

    fun setServiceDesiredEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_DESIRED_ENABLED, enabled).apply()
    }

    fun isServiceDesiredEnabled(): Boolean {
        return prefs.getBoolean(KEY_SERVICE_DESIRED_ENABLED, false)
    }

    fun updateLastHeartbeat(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_HEARTBEAT_TIMESTAMP, timestamp).apply()
    }

    fun getLastHeartbeat(): Long {
        return prefs.getLong(KEY_LAST_HEARTBEAT_TIMESTAMP, 0L)
    }

    fun recordServiceRecovery(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_RECOVERY_TIMESTAMP, timestamp).apply()
    }

    fun getLastRecoveryTimestamp(): Long {
        return prefs.getLong(KEY_LAST_RECOVERY_TIMESTAMP, 0L)
    }

    fun setLastKnownLifecycleState(state: String) {
        prefs.edit().putString(KEY_LAST_KNOWN_LIFECYCLE_STATE, state).apply()
    }

    fun getLastKnownLifecycleState(): String {
        return prefs.getString(KEY_LAST_KNOWN_LIFECYCLE_STATE, "NORMAL") ?: "NORMAL"
    }
}
