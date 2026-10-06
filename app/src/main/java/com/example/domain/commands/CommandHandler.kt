package com.example.domain.commands

import android.content.Context
import com.example.data.api.TelegramClient
import com.example.data.calls.CallLogManager
import com.example.data.contacts.ContactManager
import com.example.data.model.InlineKeyboardButton
import com.example.data.model.InlineKeyboardMarkup
import com.example.data.model.LogType
import com.example.data.model.TelegramCallbackQuery
import com.example.data.model.TelegramMessage
import com.example.data.security.PreferenceManager
import com.example.data.sms.SmsBoxType
import com.example.data.sms.SmsManager
import com.example.domain.device.DeviceInfoProvider
import com.example.domain.export.ExportManager
import com.example.domain.export.ExportType
import com.example.domain.log.LogRepository
import com.example.domain.media.MediaRepository
import com.example.domain.sms.SmsSendManager
import com.example.service.ExportService
import com.example.service.NotificationForwarderService
import com.example.service.ServiceConnectionState
import com.example.service.TelegramRemoteService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CommandHandler(
    private val context: Context,
    private val telegramClient: TelegramClient,
    private val preferenceManager: PreferenceManager,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val mediaRepository: MediaRepository,
    private val contactManager: ContactManager,
    private val callLogManager: CallLogManager,
    private val smsManager: SmsManager,
    private val exportManager: ExportManager,
    private val smsSendManager: SmsSendManager
) {

    suspend fun handleIncomingMessage(
        message: TelegramMessage,
        isServiceRunning: Boolean,
        serviceStartTimeMs: Long
    ) = withContext(Dispatchers.IO) {
        val fromUser = message.from ?: return@withContext
        val text = message.text?.trim() ?: return@withContext
        val senderId = fromUser.id
        val chatId = message.chat.id
        val token = preferenceManager.getBotToken()
        val authorizedId = preferenceManager.getAuthorizedUserId()

        // 1. Strict Security Validation
        if (senderId != authorizedId) {
            val userDisplayName = "${fromUser.firstName} ${fromUser.lastName ?: ""}".trim()
            LogRepository.addLog(
                type = LogType.SECURITY,
                title = "Unauthorized Access Blocked",
                details = "User ID: $senderId ($userDisplayName, @${fromUser.username ?: "none"}) attempted command: $text",
                isSuccess = false
            )

            // Inform unauthorized user
            telegramClient.sendMessage(
                token = token,
                chatId = chatId,
                text = "⛔ <b>Access Denied</b>\n\nThis device is privately managed. Your Telegram User ID (<code>$senderId</code>) is not authorized to execute commands."
            )
            return@withContext
        }

        // 2. Parse Authorized Command
        val commandParts = text.split("\\s+".toRegex())
        val rawCommand = commandParts[0].lowercase().removeSuffix("@${preferenceManager.botConfigFlow.value.botUsername.lowercase()}")

        LogRepository.addLog(
            type = LogType.COMMAND,
            title = "Command Received",
            details = "Command: $text from Authorized User ($senderId)"
        )

        when {
            rawCommand == "/start" -> {
                handleStartCommand(chatId, token, isServiceRunning, serviceStartTimeMs)
            }
            rawCommand == "/help" -> {
                handleHelpCommand(chatId, token)
            }
            rawCommand == "/status" -> {
                handleStatusCommand(chatId, token, isServiceRunning, serviceStartTimeMs)
            }
            rawCommand == "/health" -> {
                handleHealthCommand(chatId, token, isServiceRunning, serviceStartTimeMs)
            }
            rawCommand == "/restart_service" -> {
                handleRestartServiceCommand(chatId, token, isServiceRunning)
            }
            rawCommand == "/battery" -> {
                handleBatteryCommand(chatId, token, isServiceRunning)
            }
            rawCommand == "/storage" -> {
                handleStorageCommand(chatId, token, isServiceRunning)
            }
            rawCommand == "/photos" -> {
                handlePhotosCommand(chatId, token)
            }
            rawCommand == "/videos" -> {
                handleVideosCommand(chatId, token)
            }
            rawCommand == "/notifications" -> {
                handleNotificationsCommand(chatId, token)
            }
            rawCommand.startsWith("/photo_") || rawCommand == "/photo" -> {
                val param = if (rawCommand.startsWith("/photo_")) {
                    rawCommand.removePrefix("/photo_")
                } else {
                    commandParts.getOrNull(1) ?: ""
                }
                handleDownloadPhoto(chatId, token, param)
            }
            rawCommand.startsWith("/video_") || rawCommand == "/video" -> {
                val param = if (rawCommand.startsWith("/video_")) {
                    rawCommand.removePrefix("/video_")
                } else {
                    commandParts.getOrNull(1) ?: ""
                }
                handleDownloadVideo(chatId, token, param)
            }
            // Contacts commands
            rawCommand == "/contacts" -> {
                val page = commandParts.getOrNull(1)?.toIntOrNull() ?: 1
                handleContactsCommand(chatId, token, page)
            }
            rawCommand == "/contact" -> {
                val query = commandParts.drop(1).joinToString(" ")
                handleContactSearchCommand(chatId, token, query)
            }
            // Calls commands
            rawCommand == "/calls" -> {
                handleCallsCommand(chatId, token, commandParts.drop(1))
            }
            // SMS commands
            rawCommand == "/sms" -> {
                handleSmsCommand(chatId, token, commandParts.drop(1))
            }
            rawCommand.startsWith("/sms_thread_") -> {
                val threadId = rawCommand.removePrefix("/sms_thread_").toLongOrNull()
                if (threadId != null) {
                    handleSmsThreadCommand(chatId, token, threadId, 1)
                } else {
                    telegramClient.sendMessage(token, chatId, "❌ Invalid thread ID format.")
                }
            }
            // SMS Send & History commands
            rawCommand == "/send_sms" -> {
                val rawArgs = if (text.length > commandParts[0].length) {
                    text.substring(commandParts[0].length).trimStart()
                } else ""
                smsSendManager.handleSendSmsCommand(chatId, token, rawArgs)
            }
            rawCommand == "/sent_sms" -> {
                smsSendManager.handleSentSmsHistoryCommand(chatId, token)
            }
            rawCommand == "/clear_sms_history" -> {
                smsSendManager.handleClearHistoryPrompt(chatId, token)
            }
            // Export commands
            rawCommand == "/export" -> {
                val sub = commandParts.getOrNull(1)?.lowercase()
                if (sub == null) {
                    handleExportMenuCommand(chatId, token)
                } else {
                    val exportType = ExportType.fromCommand(sub)
                    if (exportType == ExportType.ALL) {
                        handleExportAllConfirmation(chatId, token)
                    } else if (exportType != null) {
                        handleExportCategory(chatId, token, exportType)
                    } else {
                        handleExportMenuCommand(chatId, token)
                    }
                }
            }
            rawCommand == "/cancel_export" -> {
                exportManager.cancelExport(chatId)
            }
            else -> {
                telegramClient.sendMessage(
                    token = token,
                    chatId = chatId,
                    text = "❓ Unknown command: <code>$rawCommand</code>\nType /help to see the list of available commands."
                )
            }
        }
    }

    suspend fun handleIncomingCallbackQuery(
        callbackQuery: TelegramCallbackQuery
    ) = withContext(Dispatchers.IO) {
        val senderId = callbackQuery.from.id
        val authorizedId = preferenceManager.getAuthorizedUserId()
        val token = preferenceManager.getBotToken()
        val message = callbackQuery.message ?: return@withContext
        val chatId = message.chat.id
        val data = callbackQuery.data ?: return@withContext

        if (senderId != authorizedId) return@withContext

        when {
            data == "confirm_export_all" -> {
                telegramClient.sendMessage(token, chatId, "⏳ Starting full device backup...")
                ExportService.startExport(context, ExportType.ALL, chatId)
            }
            data == "cancel_export" || data == "cancel_export_confirm" -> {
                exportManager.cancelExport(chatId)
            }
            data.startsWith("export_start_") -> {
                val typeName = data.removePrefix("export_start_")
                val type = try { ExportType.valueOf(typeName) } catch (_: Exception) { null }
                if (type == ExportType.ALL) {
                    handleExportAllConfirmation(chatId, token)
                } else if (type != null) {
                    handleExportCategory(chatId, token, type)
                }
            }
            data.startsWith("contacts_page_") -> {
                val page = data.removePrefix("contacts_page_").toIntOrNull() ?: 1
                handleContactsCommand(chatId, token, page)
            }
            data.startsWith("calls_") -> {
                val stripped = data.removePrefix("calls_")
                if (stripped.startsWith("page_")) {
                    val page = stripped.removePrefix("page_").toIntOrNull() ?: 1
                    val (text, markup) = callLogManager.getFormattedCallLogs(null, page)
                    telegramClient.sendMessage(token, chatId, text, markup)
                } else {
                    val parts = stripped.split("_page_")
                    val filter = parts.getOrNull(0)
                    val page = parts.getOrNull(1)?.toIntOrNull() ?: 1
                    val (text, markup) = callLogManager.getFormattedCallLogs(filter, page)
                    telegramClient.sendMessage(token, chatId, text, markup)
                }
            }
            // SMS Send callbacks
            data.startsWith("sms_confirm_") -> {
                val reqId = data.removePrefix("sms_confirm_")
                smsSendManager.handleConfirmSend(chatId, token, reqId)
            }
            data.startsWith("sms_cancel_") -> {
                val reqId = data.removePrefix("sms_cancel_")
                smsSendManager.handleCancelSend(chatId, token, reqId)
            }
            data.startsWith("sms_pick_cancel_") -> {
                val resId = data.removePrefix("sms_pick_cancel_")
                smsSendManager.handleContactPickCancel(chatId, token, resId)
            }
            data.startsWith("sms_pick_") -> {
                val parts = data.removePrefix("sms_pick_").split("_")
                val resId = parts.getOrNull(0) ?: ""
                val idx = parts.getOrNull(1)?.toIntOrNull() ?: 1
                smsSendManager.handleContactPick(chatId, token, resId, idx)
            }
            data == "sms_clear_history_confirm" -> {
                smsSendManager.handleClearHistoryConfirm(chatId, token)
            }
            data == "sms_clear_history_cancel" -> {
                telegramClient.sendMessage(token, chatId, "❌ Clear history cancelled.")
            }
            // SMS Browser callbacks
            data.startsWith("sms_") -> {
                val stripped = data.removePrefix("sms_")
                when {
                    stripped.startsWith("page_") -> {
                        val page = stripped.removePrefix("page_").toIntOrNull() ?: 1
                        val (text, markup) = smsManager.getFormattedMessages(SmsBoxType.ALL, null, page)
                        telegramClient.sendMessage(token, chatId, text, markup)
                    }
                    stripped.startsWith("thread_") -> {
                        val sub = stripped.removePrefix("thread_")
                        val parts = sub.split("_page_")
                        val threadId = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                        val page = parts.getOrNull(1)?.toIntOrNull() ?: 1
                        val (text, markup) = smsManager.getFormattedThread(threadId, page)
                        telegramClient.sendMessage(token, chatId, text, markup)
                    }
                    stripped.contains("_page_") -> {
                        val parts = stripped.split("_page_")
                        val tag = parts.getOrNull(0) ?: ""
                        val page = parts.getOrNull(1)?.toIntOrNull() ?: 1
                        val boxType = when (tag) {
                            "today" -> SmsBoxType.TODAY
                            "unread" -> SmsBoxType.UNREAD
                            "inbox" -> SmsBoxType.INBOX
                            "sent" -> SmsBoxType.SENT
                            else -> SmsBoxType.ALL
                        }
                        val (text, markup) = smsManager.getFormattedMessages(boxType, null, page)
                        telegramClient.sendMessage(token, chatId, text, markup)
                    }
                }
            }
        }
    }

    private suspend fun handleStartCommand(
        chatId: Long,
        token: String,
        isServiceRunning: Boolean,
        serviceStartTimeMs: Long
    ) {
        val s = deviceInfoProvider.getDeviceStatus(isServiceRunning, serviceStartTimeMs)
        val welcome = """
            👋 <b>Welcome to TeleManage!</b>

            This is your private, secure device management bot for <b>${s.deviceName}</b>.
            Only your Telegram account is authorized to manage this device.

            <b>Quick Commands:</b>
            /status - Complete device telemetry & health
            /health - Real-time service, network & heartbeat diagnostics
            /restart_service - Reconnect Telegram polling session
            /battery - Battery percentage, charge type & temperature
            /storage - Internal memory usage breakdown
            /photos - View recent photos & download
            /videos - View recent videos & download
            /contacts - View and search address book
            /calls - View incoming, outgoing & missed calls
            /sms - Read recent SMS and conversation threads
            /send_sms - Send SMS message with confirmation
            /sent_sms - View TeleManage SMS sending history
            /export - Backup device data to Telegram
            /notifications - Notification forwarding status
            /help - Full command reference guide

            🟢 <b>Service Status:</b> ${if (isServiceRunning) "Online & Connected" else "Background Service Offline"}
        """.trimIndent()
        telegramClient.sendMessage(token, chatId, welcome)
    }

    private suspend fun handleHelpCommand(chatId: Long, token: String) {
        val help = """
            📱 <b>TeleManage Commands</b>

            <b>System & Diagnostics:</b>
            /status
            /health
            /restart_service
            /battery
            /storage

            📸 <b>Media</b>
            /photos
            /photo &lt;id&gt;
            /videos
            /video &lt;id&gt;

            🔔 <b>Notifications</b>
            /notifications

            👥 <b>Contacts</b>
            /contacts
            /contact &lt;name or number&gt;

            📞 <b>Calls</b>
            /calls
            /calls missed
            /calls incoming
            /calls outgoing
            /calls today

            💬 <b>SMS Browsing</b>
            /sms
            /sms today
            /sms unread
            /sms inbox
            /sms sent
            /sms search &lt;text&gt;
            /sms thread &lt;id&gt;

            📤 <b>SMS Sending</b>
            /send_sms &lt;number or contact&gt; &lt;message&gt;
            /sent_sms
            /clear_sms_history

            <i>Example:</i>
            <code>/send_sms +919876543210 Hello bro</code>

            📦 <b>Data Export</b>
            /export
            /export contacts
            /export calls
            /export sms
            /export photos
            /export videos
            /export media
            /export files
            /export all
            /cancel_export
        """.trimIndent()
        telegramClient.sendMessage(token, chatId, help)
    }

    private suspend fun handleStatusCommand(
        chatId: Long,
        token: String,
        isServiceRunning: Boolean,
        serviceStartTimeMs: Long
    ) {
        val msg = deviceInfoProvider.generateStatusTelegramMessage(
            isServiceRunning = isServiceRunning,
            serviceStartTimeMs = serviceStartTimeMs,
            exportProgress = exportManager.getCurrentProgress()
        )
        telegramClient.sendMessage(token, chatId, msg)
    }

    private suspend fun handleHealthCommand(
        chatId: Long,
        token: String,
        isServiceRunning: Boolean,
        serviceStartTimeMs: Long
    ) {
        val connState = TelegramRemoteService.connectionStateFlow.value
        val isNotifListenerActive = NotificationForwarderService.isListenerConnectedFlow.value
        val lastHeartbeat = preferenceManager.getLastHeartbeat()
        val now = System.currentTimeMillis()
        val heartbeatAgoSeconds = if (lastHeartbeat > 0) ((now - lastHeartbeat) / 1000).coerceAtLeast(0) else 0

        val serviceText = if (isServiceRunning) "Running" else "Stopped"
        val telegramConnText = when (connState) {
            ServiceConnectionState.CONNECTED -> "Connected"
            ServiceConnectionState.CONNECTING -> "Connecting..."
            ServiceConnectionState.RECONNECTING -> "Reconnecting..."
            ServiceConnectionState.AUTH_ERROR -> "Auth Error"
            ServiceConnectionState.NETWORK_ERROR -> "Network Issue"
            ServiceConnectionState.STOPPED -> "Stopped"
        }
        val notifListenerText = if (isNotifListenerActive) "Active" else "Inactive"
        val heartbeatText = if (lastHeartbeat > 0) "$heartbeatAgoSeconds seconds ago" else "Never"
        val lastLifecycle = preferenceManager.getLastKnownLifecycleState()
        val lastRecoveryTime = preferenceManager.getLastRecoveryTimestamp()
        val recoveryText = if (lastRecoveryTime > 0) {
            val recoveryAgo = ((now - lastRecoveryTime) / 1000 / 60)
            "\nRestored: ${recoveryAgo}m ago (auto-recovered by Android)"
        } else ""

        val response = """
            🟢 <b>TeleManage</b>

            <b>Service:</b> $serviceText
            <b>Telegram:</b> $telegramConnText
            <b>Notification Listener:</b> $notifListenerText
            <b>Last heartbeat:</b> $heartbeatText$recoveryText
            <b>Lifecycle State:</b> $lastLifecycle
        """.trimIndent()

        telegramClient.sendMessage(token, chatId, response)
    }

    private suspend fun handleRestartServiceCommand(
        chatId: Long,
        token: String,
        isServiceRunning: Boolean
    ) {
        if (!isServiceRunning) {
            telegramClient.sendMessage(
                token,
                chatId,
                "⚠️ <b>Remote Service is not currently running on device.</b>\n\nIf the application process was Force Stopped from Android Settings, Android isolates the package until manually opened by the user on the device."
            )
            return
        }

        telegramClient.sendMessage(
            token,
            chatId,
            "🔄 <b>Re-initializing Telegram Connection...</b>\nResetting command polling session and network sockets."
        )

        val restarted = TelegramRemoteService.restartPollingConnection(context)
        if (restarted) {
            telegramClient.sendMessage(
                token,
                chatId,
                "✅ <b>Telegram Connection Reset Successfully</b>\nService polling loop has been re-established."
            )
        } else {
            telegramClient.sendMessage(
                token,
                chatId,
                "ℹ️ Re-invoked service startup intent."
            )
        }
    }

    private suspend fun handleBatteryCommand(chatId: Long, token: String, isServiceRunning: Boolean) {
        val msg = deviceInfoProvider.generateBatteryTelegramMessage(isServiceRunning)
        telegramClient.sendMessage(token, chatId, msg)
    }

    private suspend fun handleStorageCommand(chatId: Long, token: String, isServiceRunning: Boolean) {
        val msg = deviceInfoProvider.generateStorageTelegramMessage(isServiceRunning)
        telegramClient.sendMessage(token, chatId, msg)
    }

    private suspend fun handlePhotosCommand(chatId: Long, token: String) {
        val msg = mediaRepository.generatePhotosTelegramMessage()
        telegramClient.sendMessage(token, chatId, msg)
    }

    private suspend fun handleVideosCommand(chatId: Long, token: String) {
        val msg = mediaRepository.generateVideosTelegramMessage()
        telegramClient.sendMessage(token, chatId, msg)
    }

    private suspend fun handleNotificationsCommand(chatId: Long, token: String) {
        val config = preferenceManager.botConfigFlow.value
        val state = if (config.isNotificationForwardingEnabled) "✅ ACTIVE" else "⏸️ DISABLED"
        val ignoredCount = config.ignoredPackages.size

        val msg = """
            🔔 <b>Notification Forwarding Status</b>

            <b>Status:</b> $state
            <b>Telegram Loop Filter:</b> ${if (config.isIgnoreTelegramNotifs) "Enabled" else "Disabled"}
            <b>Ongoing Notifications:</b> ${if (config.isIgnoreOngoingNotifs) "Ignored" else "Allowed"}
            <b>Filtered Apps:</b> $ignoredCount app(s) muted in app settings

            <i>To change settings, open the TeleManage app on your phone.</i>
        """.trimIndent()
        telegramClient.sendMessage(token, chatId, msg)
    }

    private suspend fun handleContactsCommand(chatId: Long, token: String, page: Int) {
        val config = preferenceManager.botConfigFlow.value
        if (!config.isContactAccessEnabled) {
            telegramClient.sendMessage(
                token,
                chatId,
                "🔒 <b>Contact access is disabled in TeleManage settings.</b>\n\nEnable it from the app."
            )
            return
        }

        if (!contactManager.hasPermission()) {
            telegramClient.sendMessage(
                token,
                chatId,
                "⚠️ <b>Contacts Permission Required</b>\n\nOpen TeleManage and grant Contacts access first."
            )
            return
        }

        val (text, markup) = contactManager.getFormattedContacts(page)
        telegramClient.sendMessage(token, chatId, text, markup)
    }

    private suspend fun handleContactSearchCommand(chatId: Long, token: String, query: String) {
        val config = preferenceManager.botConfigFlow.value
        if (!config.isContactAccessEnabled) {
            telegramClient.sendMessage(
                token,
                chatId,
                "🔒 <b>Contact access is disabled in TeleManage settings.</b>\n\nEnable it from the app."
            )
            return
        }

        if (!contactManager.hasPermission()) {
            telegramClient.sendMessage(
                token,
                chatId,
                "⚠️ <b>Contacts Permission Required</b>\n\nOpen TeleManage and grant Contacts access first."
            )
            return
        }

        val text = contactManager.getFormattedContactSearch(query)
        telegramClient.sendMessage(token, chatId, text)
    }

    private suspend fun handleCallsCommand(chatId: Long, token: String, args: List<String>) {
        val config = preferenceManager.botConfigFlow.value
        if (!config.isCallHistoryAccessEnabled) {
            telegramClient.sendMessage(
                token,
                chatId,
                "🔒 <b>Call history access is disabled in TeleManage settings.</b>\n\nEnable it from the app."
            )
            return
        }

        if (!callLogManager.hasPermission()) {
            telegramClient.sendMessage(
                token,
                chatId,
                "⚠️ <b>Call Log Permission Required</b>\n\nOpen TeleManage and grant Call Log access first."
            )
            return
        }

        val firstArg = args.getOrNull(0)?.lowercase()
        val secondArg = args.getOrNull(1)

        val (filter, page) = when {
            firstArg == null -> Pair(null, 1)
            firstArg.toIntOrNull() != null -> Pair(null, firstArg.toInt())
            firstArg in listOf("missed", "incoming", "outgoing", "today") -> {
                val p = secondArg?.toIntOrNull() ?: 1
                Pair(firstArg, p)
            }
            else -> Pair(null, 1)
        }

        val (text, markup) = callLogManager.getFormattedCallLogs(filter, page)
        telegramClient.sendMessage(token, chatId, text, markup)
    }

    private suspend fun handleSmsCommand(chatId: Long, token: String, args: List<String>) {
        val config = preferenceManager.botConfigFlow.value
        if (!config.isSmsAccessEnabled) {
            telegramClient.sendMessage(
                token,
                chatId,
                "🔒 <b>SMS access is disabled in TeleManage settings.</b>"
            )
            return
        }

        if (!smsManager.hasPermission()) {
            telegramClient.sendMessage(
                token,
                chatId,
                "⚠️ <b>SMS Access Required</b>\n\nOpen TeleManage and grant the required SMS permission first."
            )
            return
        }

        val firstArg = args.getOrNull(0)?.lowercase()
        when {
            firstArg == null -> {
                val (text, markup) = smsManager.getFormattedMessages(SmsBoxType.ALL, null, 1)
                telegramClient.sendMessage(token, chatId, text, markup)
            }
            firstArg.toIntOrNull() != null -> {
                val (text, markup) = smsManager.getFormattedMessages(SmsBoxType.ALL, null, firstArg.toInt())
                telegramClient.sendMessage(token, chatId, text, markup)
            }
            firstArg == "search" -> {
                val query = args.drop(1).joinToString(" ")
                if (query.isBlank()) {
                    telegramClient.sendMessage(token, chatId, "❓ Usage: <code>/sms search &lt;text&gt;</code>")
                    return
                }
                val (text, markup) = smsManager.getFormattedMessages(SmsBoxType.ALL, query, 1)
                telegramClient.sendMessage(token, chatId, text, markup)
            }
            firstArg == "thread" -> {
                val threadId = args.getOrNull(1)?.toLongOrNull()
                if (threadId == null) {
                    telegramClient.sendMessage(token, chatId, "❓ Usage: <code>/sms thread &lt;thread_id&gt;</code>")
                    return
                }
                handleSmsThreadCommand(chatId, token, threadId, 1)
            }
            firstArg in listOf("today", "unread", "inbox", "sent") -> {
                val boxType = when (firstArg) {
                    "today" -> SmsBoxType.TODAY
                    "unread" -> SmsBoxType.UNREAD
                    "inbox" -> SmsBoxType.INBOX
                    "sent" -> SmsBoxType.SENT
                    else -> SmsBoxType.ALL
                }
                val page = args.getOrNull(1)?.toIntOrNull() ?: 1
                val (text, markup) = smsManager.getFormattedMessages(boxType, null, page)
                telegramClient.sendMessage(token, chatId, text, markup)
            }
            else -> {
                telegramClient.sendMessage(
                    token,
                    chatId,
                    "❓ Usage:\n• <code>/sms</code>\n• <code>/sms today</code>\n• <code>/sms unread</code>\n• <code>/sms search &lt;text&gt;</code>\n• <code>/sms thread &lt;id&gt;</code>"
                )
            }
        }
    }

    private suspend fun handleSmsThreadCommand(chatId: Long, token: String, threadId: Long, page: Int) {
        val config = preferenceManager.botConfigFlow.value
        if (!config.isSmsAccessEnabled) {
            telegramClient.sendMessage(
                token,
                chatId,
                "🔒 <b>SMS access is disabled in TeleManage settings.</b>"
            )
            return
        }

        if (!smsManager.hasPermission()) {
            telegramClient.sendMessage(
                token,
                chatId,
                "⚠️ <b>SMS Access Required</b>\n\nOpen TeleManage and grant the required SMS permission first."
            )
            return
        }

        val (text, markup) = smsManager.getFormattedThread(threadId, page)
        telegramClient.sendMessage(token, chatId, text, markup)
    }

    private suspend fun handleExportMenuCommand(chatId: Long, token: String) {
        val text = """
            📦 <b>Data Export / Backup Options</b>

            Select a category to export to Telegram:
            • <code>/export contacts</code> - Address book (contacts.csv)
            • <code>/export calls</code> - Call history (call_logs.csv)
            • <code>/export sms</code> - SMS database (sms.json)
            • <code>/export photos</code> - Photos archive (photos.zip)
            • <code>/export videos</code> - Videos archive (videos.zip)
            • <code>/export media</code> - Photos & Videos (media.zip)
            • <code>/export files</code> - Application files (files.zip)
            • <code>/export all</code> - Full device backup (requires confirmation)
            • <code>/cancel_export</code> - Cancel active export
        """.trimIndent()

        val keyboard = InlineKeyboardMarkup(
            inlineKeyboard = listOf(
                listOf(
                    InlineKeyboardButton("👥 Contacts", callbackData = "export_start_CONTACTS"),
                    InlineKeyboardButton("📞 Calls", callbackData = "export_start_CALLS")
                ),
                listOf(
                    InlineKeyboardButton("💬 SMS", callbackData = "export_start_SMS"),
                    InlineKeyboardButton("📷 Photos", callbackData = "export_start_PHOTOS")
                ),
                listOf(
                    InlineKeyboardButton("🎬 Videos", callbackData = "export_start_VIDEOS"),
                    InlineKeyboardButton("📦 Full Backup", callbackData = "confirm_export_all")
                )
            )
        )

        telegramClient.sendMessage(token, chatId, text, keyboard)
    }

    private suspend fun handleExportCategory(chatId: Long, token: String, type: ExportType) {
        val config = preferenceManager.botConfigFlow.value

        // Validate specific permissions and toggles
        when (type) {
            ExportType.CONTACTS -> {
                if (!config.isContactAccessEnabled) {
                    telegramClient.sendMessage(token, chatId, "🔒 Contact access is disabled in TeleManage settings.")
                    return
                }
                if (!contactManager.hasPermission()) {
                    telegramClient.sendMessage(token, chatId, "⚠️ Contacts Permission Required. Open TeleManage and grant access first.")
                    return
                }
            }
            ExportType.CALLS -> {
                if (!config.isCallHistoryAccessEnabled) {
                    telegramClient.sendMessage(token, chatId, "🔒 Call history access is disabled in TeleManage settings.")
                    return
                }
                if (!callLogManager.hasPermission()) {
                    telegramClient.sendMessage(token, chatId, "⚠️ Call Log Permission Required. Open TeleManage and grant access first.")
                    return
                }
            }
            ExportType.SMS -> {
                if (!config.isSmsAccessEnabled) {
                    telegramClient.sendMessage(token, chatId, "🔒 SMS access is disabled in TeleManage settings.")
                    return
                }
                if (!smsManager.hasPermission()) {
                    telegramClient.sendMessage(token, chatId, "⚠️ SMS Access Required. Open TeleManage and grant access first.")
                    return
                }
            }
            ExportType.PHOTOS, ExportType.VIDEOS, ExportType.MEDIA -> {
                if (!mediaRepository.hasMediaPermission()) {
                    telegramClient.sendMessage(token, chatId, "⚠️ Media Permission Missing. Open TeleManage and grant Media access first.")
                    return
                }
            }
            else -> {}
        }

        ExportService.startExport(context, type, chatId)
    }

    private suspend fun handleExportAllConfirmation(chatId: Long, token: String) {
        val text = """
            ⚠️ <b>Full Device Export</b>

            This may contain:
            • Contacts
            • Call history
            • SMS
            • Photos
            • Videos
            • Files

            Are you sure you want to create and upload a full backup?
        """.trimIndent()

        val keyboard = InlineKeyboardMarkup(
            inlineKeyboard = listOf(
                listOf(
                    InlineKeyboardButton("✅ Start Export", callbackData = "confirm_export_all"),
                    InlineKeyboardButton("❌ Cancel", callbackData = "cancel_export_confirm")
                )
            )
        )

        telegramClient.sendMessage(token, chatId, text, keyboard)
    }

    private suspend fun handleDownloadPhoto(chatId: Long, token: String, param: String) {
        if (!mediaRepository.hasMediaPermission()) {
            telegramClient.sendMessage(
                token,
                chatId,
                "⚠️ <b>Media Permission Missing</b>\nPlease grant Media permission in the TeleManage app."
            )
            return
        }

        val photos = mediaRepository.getRecentPhotos(20)
        val target = findMediaItem(photos, param)

        if (target == null) {
            telegramClient.sendMessage(
                token,
                chatId,
                "❌ Photo not found. Send /photos to see the current list."
            )
            return
        }

        telegramClient.sendMessage(
            token,
            chatId,
            "⏳ Uploading photo <code>${target.displayName}</code> (${mediaRepository.formatBytes(target.sizeBytes)})..."
        )

        val bytes = mediaRepository.readMediaBytes(target)
        if (bytes == null) {
            telegramClient.sendMessage(
                token,
                chatId,
                "❌ Could not read photo (file may be larger than 45MB or inaccessible)."
            )
            return
        }

        val caption = "📸 <b>${target.displayName}</b>\nSize: ${mediaRepository.formatBytes(target.sizeBytes)}\nDate: ${mediaRepository.formatDate(target.dateAddedEpochSeconds)}"
        val success = telegramClient.sendPhoto(
            token = token,
            chatId = chatId,
            imageBytes = bytes,
            filename = target.displayName,
            caption = caption
        ).getOrDefault(false)

        if (success) {
            LogRepository.addLog(
                type = LogType.MEDIA,
                title = "Photo Sent",
                details = "Sent ${target.displayName} (${mediaRepository.formatBytes(target.sizeBytes)}) to Telegram"
            )
        }
    }

    private suspend fun handleDownloadVideo(chatId: Long, token: String, param: String) {
        if (!mediaRepository.hasMediaPermission()) {
            telegramClient.sendMessage(
                token,
                chatId,
                "⚠️ <b>Media Permission Missing</b>\nPlease grant Media permission in the TeleManage app."
            )
            return
        }

        val videos = mediaRepository.getRecentVideos(20)
        val target = findMediaItem(videos, param)

        if (target == null) {
            telegramClient.sendMessage(
                token,
                chatId,
                "❌ Video not found. Send /videos to see the current list."
            )
            return
        }

        telegramClient.sendMessage(
            token,
            chatId,
            "⏳ Uploading video <code>${target.displayName}</code> (${mediaRepository.formatBytes(target.sizeBytes)})..."
        )

        val bytes = mediaRepository.readMediaBytes(target)
        if (bytes == null) {
            telegramClient.sendMessage(
                token,
                chatId,
                "❌ Could not read video (file may be larger than 45MB or inaccessible)."
            )
            return
        }

        val caption = "🎬 <b>${target.displayName}</b>\nSize: ${mediaRepository.formatBytes(target.sizeBytes)}\nDuration: ${mediaRepository.formatDuration(target.durationSeconds)}"
        val success = telegramClient.sendMediaDocument(
            token = token,
            chatId = chatId,
            mediaBytes = bytes,
            filename = target.displayName,
            caption = caption,
            isVideo = true
        ).getOrDefault(false)

        if (success) {
            LogRepository.addLog(
                type = LogType.MEDIA,
                title = "Video Sent",
                details = "Sent ${target.displayName} (${mediaRepository.formatBytes(target.sizeBytes)}) to Telegram"
            )
        }
    }

    private fun findMediaItem(items: List<com.example.data.model.MediaItem>, param: String): com.example.data.model.MediaItem? {
        val trimmed = param.trim()
        val index = trimmed.toIntOrNull()
        if (index != null && index in 1..items.size) {
            return items[index - 1]
        }
        val id = trimmed.toLongOrNull()
        if (id != null) {
            val byId = items.find { it.id == id }
            if (byId != null) return byId
        }
        return items.find { it.displayName.equals(trimmed, ignoreCase = true) }
    }
}
