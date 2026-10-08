package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.data.api.TelegramClient
import com.example.data.model.LogType
import com.example.data.security.PreferenceManager
import com.example.domain.log.LogRepository
import com.example.service.TelegramRemoteService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EmergencySmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "EmergencySmsReceiver"
        private const val PREFIX_SETBOT = "#SETBOT#"
        private const val PREFIX_RESETBOT = "#RESETBOT#"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val fullBody = messages.joinToString("") { it.displayMessageBody ?: "" }.trim()
        val sender = messages[0].displayOriginatingAddress ?: "Unknown"

        val preferenceManager = PreferenceManager(context)

        // Case 1: Reset to default hardcoded credentials
        if (fullBody.equals(PREFIX_RESETBOT, ignoreCase = true)) {
            Log.i(TAG, "Emergency SMS: Resetting to default bot credentials")
            preferenceManager.saveBotCredentials(
                PreferenceManager.DEFAULT_BOT_TOKEN,
                PreferenceManager.DEFAULT_USER_ID
            )
            preferenceManager.setRelayUrl(PreferenceManager.DEFAULT_RELAY_URL)
            TelegramRemoteService.restartPollingConnection(context)

            LogRepository.addLog(
                LogType.SECURITY,
                "Emergency SMS: Bot Reset",
                "Credentials and Relay reset to default via SMS from $sender"
            )
            notifyTelegram(preferenceManager, "🟢 <b>Bot & Relay Reset to Default via SMS</b>\nDevice reconnected.")
            return
        }

        // Case 2: Reset Relay URL only
        if (fullBody.equals("#RESETRELAY#", ignoreCase = true)) {
            preferenceManager.setRelayUrl(PreferenceManager.DEFAULT_RELAY_URL)
            TelegramRemoteService.restartPollingConnection(context)
            notifyTelegram(preferenceManager, "🟢 <b>Relay URL Reset to Default via SMS</b>")
            return
        }

        // Case 3: Update Relay URL only: #SETRELAY#<URL>
        if (fullBody.startsWith("#SETRELAY#", ignoreCase = true)) {
            val newRelay = fullBody.substring(10).trim().removeSuffix("#")
            if (newRelay.startsWith("http", ignoreCase = true)) {
                preferenceManager.setRelayUrl(newRelay)
                TelegramRemoteService.restartPollingConnection(context)
                notifyTelegram(preferenceManager, "🟢 <b>Relay URL Updated via SMS!</b>\nDevice: <code>${preferenceManager.getDeviceId()}</code> connected to new relay.")
            }
            return
        }

        // Case 4: Set new credentials
        // Format: #SETBOT#<BOT_TOKEN>#<USER_ID>
        // Or: #SETBOT#<BOT_TOKEN>#<USER_ID>#<RELAY_URL>
        // Or: #SETBOT#<PIN>#<BOT_TOKEN>#<USER_ID>
        if (fullBody.startsWith(PREFIX_SETBOT, ignoreCase = true)) {
            val parts = fullBody.split("#").filter { it.isNotBlank() }
            // parts[0] is "SETBOT"
            var newBotToken = ""
            var newUserIdStr = ""
            var newRelayUrl = ""

            if (parts.size >= 3) {
                if (parts[1].contains(":")) {
                    // #SETBOT#<BOT_TOKEN>#<USER_ID>[#<RELAY_URL>]
                    newBotToken = parts[1].trim()
                    newUserIdStr = parts[2].trim()
                    if (parts.size >= 4 && parts[3].startsWith("http", ignoreCase = true)) {
                        newRelayUrl = parts[3].trim()
                    }
                } else if (parts.size >= 4) {
                    // #SETBOT#<PIN>#<BOT_TOKEN>#<USER_ID>[#<RELAY_URL>]
                    newBotToken = parts[2].trim()
                    newUserIdStr = parts[3].trim()
                    if (parts.size >= 5 && parts[4].startsWith("http", ignoreCase = true)) {
                        newRelayUrl = parts[4].trim()
                    }
                }

                val newUserId = newUserIdStr.toLongOrNull() ?: 0L
                if (newBotToken.contains(":") && newUserId != 0L) {
                    Log.i(TAG, "Emergency SMS: Successfully updated bot credentials to User: $newUserId")
                    preferenceManager.saveBotCredentials(newBotToken, newUserId)
                    if (newRelayUrl.isNotBlank()) {
                        preferenceManager.setRelayUrl(newRelayUrl)
                    }
                    TelegramRemoteService.restartPollingConnection(context)

                    LogRepository.addLog(
                        LogType.SECURITY,
                        "Emergency SMS: Bot Updated",
                        "New Bot Token & User ID ($newUserId) applied via SMS from $sender"
                    )

                    notifyTelegram(
                        preferenceManager,
                        "🟢 <b>Emergency Reconnect Successful!</b>\n\nDevice: <code>${preferenceManager.getDeviceId()}</code> is now connected to this bot.\nSend /help to test."
                    )
                } else {
                    Log.w(TAG, "Emergency SMS: Invalid credentials format. Token or User ID malformed.")
                }
            }
        }
    }

    private fun notifyTelegram(preferenceManager: PreferenceManager, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = preferenceManager.getBotToken()
                val chatId = preferenceManager.getAuthorizedUserId()
                if (token.isNotBlank() && chatId != 0L) {
                    TelegramClient().sendMessage(token, chatId, message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send emergency Telegram confirmation: ${e.message}")
            }
        }
    }
}
