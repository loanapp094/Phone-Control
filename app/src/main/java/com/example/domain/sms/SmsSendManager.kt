package com.example.domain.sms

import android.os.Build
import com.example.data.api.TelegramClient
import com.example.data.contacts.ContactRepository
import com.example.data.model.ContactCandidate
import com.example.data.model.InlineKeyboardButton
import com.example.data.model.InlineKeyboardMarkup
import com.example.data.model.LogType
import com.example.data.model.PendingContactResolution
import com.example.data.model.PendingSmsRequest
import com.example.data.model.SentSmsRecord
import com.example.data.security.PreferenceManager
import com.example.data.sms.SmsSender
import com.example.domain.log.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SmsSendManager(
    private val telegramClient: TelegramClient,
    private val preferenceManager: PreferenceManager,
    private val contactRepository: ContactRepository,
    private val smsSender: SmsSender
) {

    // Thread-safe in-memory stores for active requests and duplicate protection
    private val pendingRequests = ConcurrentHashMap<String, PendingSmsRequest>()
    private val pendingContactResolutions = ConcurrentHashMap<String, PendingContactResolution>()
    private val processedRequestIds = ConcurrentHashMap.newKeySet<String>()

    // Rate limiting tracking: list of send timestamps
    private val sendTimestamps = mutableListOf<Long>()
    private val rateLimitLock = Any()

    /**
     * Checks if the client has exceeded the configured SMS rate limit.
     */
    fun isRateLimitExceeded(): Boolean {
        val config = preferenceManager.botConfigFlow.value
        val maxAllowed = config.smsRateLimitMax
        val windowMs = config.smsRateLimitWindowMinutes * 60 * 1000L
        val now = System.currentTimeMillis()

        synchronized(rateLimitLock) {
            // Purge expired timestamps
            sendTimestamps.removeAll { now - it > windowMs }
            return sendTimestamps.size >= maxAllowed
        }
    }

    private fun recordSendTimestamp() {
        synchronized(rateLimitLock) {
            sendTimestamps.add(System.currentTimeMillis())
        }
    }

    fun hasPermission(): Boolean = smsSender.hasSendSmsPermission()

    fun isRemoteSmsSendingEnabled(): Boolean = preferenceManager.botConfigFlow.value.isRemoteSmsSendingEnabled

    /**
     * Handles /send_sms <recipient> <message>
     */
    suspend fun handleSendSmsCommand(
        chatId: Long,
        token: String,
        rawArguments: String
    ) = withContext(Dispatchers.IO) {
        val config = preferenceManager.botConfigFlow.value

        // 1. Verify remote SMS sending toggle
        if (!config.isRemoteSmsSendingEnabled) {
            telegramClient.sendMessage(
                token = token,
                chatId = chatId,
                text = "🔒 <b>Remote SMS Sending Disabled</b>\n\nRemote SMS sending is currently disabled in TeleManage settings. Enable it in the app dashboard to use this feature."
            )
            return@withContext
        }

        // 2. Verify Android SEND_SMS permission
        if (!hasPermission()) {
            telegramClient.sendMessage(
                token = token,
                chatId = chatId,
                text = """
                    ⚠️ <b>SMS Sending Permission Required</b>

                    TeleManage does not have the <code>SEND_SMS</code> permission on this device.
                    Please open TeleManage on your phone, navigate to <b>Permissions</b>, and tap:
                    <b>[Grant SMS Sending Permission]</b>.
                """.trimIndent()
            )
            return@withContext
        }

        // 3. Rate limit validation
        if (isRateLimitExceeded()) {
            telegramClient.sendMessage(
                token = token,
                chatId = chatId,
                text = "⚠️ <b>SMS Rate Limit Reached</b>\n\nYou have reached the limit of ${config.smsRateLimitMax} SMS messages per ${config.smsRateLimitWindowMinutes} minutes. Please wait before sending another SMS."
            )
            return@withContext
        }

        val trimmedArgs = rawArguments.trim()
        if (trimmedArgs.isBlank()) {
            telegramClient.sendMessage(
                token = token,
                chatId = chatId,
                text = """
                    ℹ️ <b>Usage:</b>
                    <code>/send_sms &lt;number or contact&gt; &lt;message&gt;</code>

                    <b>Example:</b>
                    <code>/send_sms +919876543210 Hello bro, I will call you later.</code>
                """.trimIndent()
            )
            return@withContext
        }

        // Parse recipient and message (support space and newline separators)
        val firstSplitIndex = trimmedArgs.indexOfFirst { it == ' ' || it == '\n' }
        if (firstSplitIndex <= 0) {
            telegramClient.sendMessage(
                token = token,
                chatId = chatId,
                text = "❌ <b>Empty Message</b>\nPlease include a message to send after the recipient."
            )
            return@withContext
        }

        val recipientToken = trimmedArgs.substring(0, firstSplitIndex).trim()
        val messageText = trimmedArgs.substring(firstSplitIndex + 1).trim()

        if (messageText.isBlank()) {
            telegramClient.sendMessage(
                token = token,
                chatId = chatId,
                text = "❌ <b>Empty Message</b>\nPlease include a message to send."
            )
            return@withContext
        }

        // Check if recipient is a phone number directly or a contact name
        val normalizedCandidate = smsSender.normalizePhoneNumber(recipientToken)
        val isLikelyPhoneNumber = smsSender.isValidPhoneNumber(normalizedCandidate) &&
                (recipientToken.startsWith("+") || recipientToken.all { it.isDigit() || it == '-' || it == '(' || it == ')' })

        if (isLikelyPhoneNumber) {
            // Direct phone number
            initiateSmsConfirmation(
                chatId = chatId,
                token = token,
                phoneNumber = normalizedCandidate,
                displayName = null,
                messageText = messageText
            )
        } else {
            // Resolve contact by name
            resolveContactAndInitiate(
                chatId = chatId,
                token = token,
                query = recipientToken,
                messageText = messageText
            )
        }
    }

    private suspend fun resolveContactAndInitiate(
        chatId: Long,
        token: String,
        query: String,
        messageText: String
    ) {
        if (!contactRepository.hasPermission()) {
            // Cannot resolve contact name without contacts permission
            telegramClient.sendMessage(
                token = token,
                chatId = chatId,
                text = "⚠️ <b>Contacts Permission Missing</b>\nTo send SMS by contact name ($query), grant Contacts permission or enter the phone number directly (e.g. <code>+91XXXXXXXXXX</code>)."
            )
            return
        }

        val contacts = contactRepository.searchContacts(query)
        val matchesWithPhone = contacts.filter { it.phoneNumbers.isNotEmpty() }

        when {
            matchesWithPhone.isEmpty() -> {
                telegramClient.sendMessage(
                    token = token,
                    chatId = chatId,
                    text = "❌ No contacts found matching \"<b>$query</b>\". Please provide a direct phone number."
                )
            }
            matchesWithPhone.size == 1 -> {
                val match = matchesWithPhone.first()
                val phone = match.phoneNumbers.first().number
                initiateSmsConfirmation(
                    chatId = chatId,
                    token = token,
                    phoneNumber = smsSender.normalizePhoneNumber(phone),
                    displayName = match.displayName,
                    messageText = messageText
                )
            }
            else -> {
                // Multiple contacts found: do not auto-choose! Present choices.
                val resolutionId = UUID.randomUUID().toString().take(8)
                val candidates = matchesWithPhone.take(5).mapIndexed { idx, contact ->
                    ContactCandidate(
                        index = idx + 1,
                        name = contact.displayName,
                        phoneNumber = smsSender.normalizePhoneNumber(contact.phoneNumbers.first().number)
                    )
                }

                pendingContactResolutions[resolutionId] = PendingContactResolution(
                    query = query,
                    originalMessage = messageText,
                    candidates = candidates
                )

                val candidatesText = candidates.joinToString("\n\n") { c ->
                    "${c.index}. <b>${c.name}</b>\n   📞 ${c.phoneNumber}"
                }

                val buttonRow = candidates.map { c ->
                    InlineKeyboardButton(text = "[${c.index}]", callbackData = "sms_pick_${resolutionId}_${c.index}")
                }
                val keyboard = InlineKeyboardMarkup(
                    inlineKeyboard = listOf(
                        buttonRow,
                        listOf(InlineKeyboardButton(text = "❌ Cancel", callbackData = "sms_pick_cancel_$resolutionId"))
                    )
                )

                val response = """
                    ⚠️ <b>Multiple contacts found for "$query"</b>

                    $candidatesText

                    Please select a recipient:
                """.trimIndent()

                telegramClient.sendMessage(token, chatId, response, keyboard)
            }
        }
    }

    suspend fun handleContactPick(
        chatId: Long,
        token: String,
        resolutionId: String,
        selectedIndex: Int
    ) = withContext(Dispatchers.IO) {
        val resolution = pendingContactResolutions.remove(resolutionId) ?: run {
            telegramClient.sendMessage(token, chatId, "⚠️ Selection expired or already processed.")
            return@withContext
        }

        val chosen = resolution.candidates.find { it.index == selectedIndex }
        if (chosen == null) {
            telegramClient.sendMessage(token, chatId, "❌ Invalid selection.")
            return@withContext
        }

        initiateSmsConfirmation(
            chatId = chatId,
            token = token,
            phoneNumber = chosen.phoneNumber,
            displayName = chosen.name,
            messageText = resolution.originalMessage
        )
    }

    suspend fun handleContactPickCancel(
        chatId: Long,
        token: String,
        resolutionId: String
    ) = withContext(Dispatchers.IO) {
        pendingContactResolutions.remove(resolutionId)
        telegramClient.sendMessage(token, chatId, "❌ SMS cancelled.")
    }

    private suspend fun initiateSmsConfirmation(
        chatId: Long,
        token: String,
        phoneNumber: String,
        displayName: String?,
        messageText: String
    ) {
        val reqId = UUID.randomUUID().toString().take(8)

        // Resolve SIM card description
        val availableSims = smsSender.getAvailableSims()
        val preferredSubId = preferenceManager.botConfigFlow.value.preferredSimSubscriptionId
        val chosenSim = availableSims.find { it.subscriptionId == preferredSubId }
            ?: availableSims.firstOrNull()

        val simText = when {
            chosenSim != null -> "${chosenSim.displayName} (${chosenSim.carrierName})"
            availableSims.isNotEmpty() -> availableSims.first().displayName
            else -> "Default SIM"
        }

        val request = PendingSmsRequest(
            requestId = reqId,
            toPhone = phoneNumber,
            displayName = displayName,
            messageText = messageText,
            simSlot = chosenSim?.slotIndex ?: -1,
            simDisplayName = simText
        )
        pendingRequests[reqId] = request

        LogRepository.addLog(
            type = LogType.SECURITY,
            title = "SMS_SEND_REQUESTED",
            details = "To: ${maskPhoneNumber(phoneNumber)} | Device: ${Build.MANUFACTURER} ${Build.MODEL}"
        )

        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        val toDisplay = if (displayName != null) "$phoneNumber ($displayName)" else phoneNumber

        val confirmText = """
            ⚠️ <b>Confirm SMS</b>

            <b>To:</b>
            $toDisplay

            <b>Message:</b>
            $messageText

            <b>Device:</b>
            $deviceName

            <b>SIM:</b>
            $simText
        """.trimIndent()

        val keyboard = InlineKeyboardMarkup(
            inlineKeyboard = listOf(
                listOf(
                    InlineKeyboardButton(text = "✅ Send SMS", callbackData = "sms_confirm_$reqId"),
                    InlineKeyboardButton(text = "❌ Cancel", callbackData = "sms_cancel_$reqId")
                )
            )
        )

        telegramClient.sendMessage(token, chatId, confirmText, keyboard)
    }

    /**
     * Executes the confirmed SMS dispatch.
     */
    suspend fun handleConfirmSend(
        chatId: Long,
        token: String,
        requestId: String
    ) = withContext(Dispatchers.IO) {
        // 1. Duplicate protection check
        if (processedRequestIds.contains(requestId)) {
            telegramClient.sendMessage(token, chatId, "⚠️ This SMS request has already been processed.")
            return@withContext
        }

        val request = pendingRequests.remove(requestId)
        if (request == null) {
            telegramClient.sendMessage(token, chatId, "⚠️ Request expired or already handled.")
            return@withContext
        }

        // Mark request as processed immediately
        processedRequestIds.add(requestId)

        // 2. Final security and toggle checks
        val config = preferenceManager.botConfigFlow.value
        if (!config.isRemoteSmsSendingEnabled) {
            telegramClient.sendMessage(token, chatId, "🔒 Remote SMS sending has been disabled in app settings.")
            return@withContext
        }

        if (!hasPermission()) {
            telegramClient.sendMessage(token, chatId, "⚠️ SEND_SMS permission is not granted on this device.")
            return@withContext
        }

        if (isRateLimitExceeded()) {
            telegramClient.sendMessage(
                token = token,
                chatId = chatId,
                text = "⚠️ <b>SMS Rate Limit Reached</b>\nPlease wait before sending another SMS."
            )
            return@withContext
        }

        recordSendTimestamp()

        LogRepository.addLog(
            type = LogType.SECURITY,
            title = "SMS_SEND_CONFIRMED",
            details = "Request ID: $requestId | To: ${maskPhoneNumber(request.toPhone)}"
        )

        // Inform user dispatch is underway
        telegramClient.sendMessage(
            token = token,
            chatId = chatId,
            text = "⏳ Dispatching SMS to <code>${request.toPhone}</code> via ${request.simDisplayName}..."
        )

        val result = smsSender.sendSms(
            recipientNumber = request.toPhone,
            messageText = request.messageText,
            subId = config.preferredSimSubscriptionId
        )

        val nowFormatted = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

        if (result.isSuccess) {
            preferenceManager.incrementSentSmsCount()

            val record = SentSmsRecord(
                id = requestId,
                recipientNumber = request.toPhone,
                recipientName = request.displayName,
                messageSnippet = request.messageText.take(100),
                timestamp = System.currentTimeMillis(),
                status = "SENT",
                simInfo = request.simDisplayName
            )
            preferenceManager.addSentSmsRecord(record)

            LogRepository.addLog(
                type = LogType.COMMAND,
                title = "SMS_SENT",
                details = "Sent SMS to ${maskPhoneNumber(request.toPhone)} (${result.partCount} part(s))",
                isSuccess = true
            )

            val toDisplay = if (request.displayName != null) "${request.toPhone} (${request.displayName})" else request.toPhone
            val successMessage = """
                ✅ <b>SMS Sent</b>

                <b>To:</b>
                $toDisplay

                <b>Message:</b>
                ${request.messageText}

                <b>Device:</b>
                $deviceName

                <b>Time:</b>
                $nowFormatted
            """.trimIndent()

            telegramClient.sendMessage(token, chatId, successMessage)
        } else {
            val record = SentSmsRecord(
                id = requestId,
                recipientNumber = request.toPhone,
                recipientName = request.displayName,
                messageSnippet = request.messageText.take(100),
                timestamp = System.currentTimeMillis(),
                status = "FAILED",
                simInfo = request.simDisplayName,
                failureReason = result.failureReason
            )
            preferenceManager.addSentSmsRecord(record)

            LogRepository.addLog(
                type = LogType.ERROR,
                title = "SMS_SEND_FAILED",
                details = "To: ${maskPhoneNumber(request.toPhone)} | Reason: ${result.failureReason}",
                isSuccess = false
            )

            val failureMessage = """
                ❌ <b>SMS Failed</b>

                <b>Reason:</b>
                ${result.failureReason ?: "Unknown carrier transmission error"}
            """.trimIndent()

            telegramClient.sendMessage(token, chatId, failureMessage)
        }
    }

    suspend fun handleCancelSend(
        chatId: Long,
        token: String,
        requestId: String
    ) = withContext(Dispatchers.IO) {
        val removed = pendingRequests.remove(requestId)
        if (removed != null) {
            telegramClient.sendMessage(token, chatId, "🛑 <b>SMS Cancelled</b>\nThe pending message was cancelled and not sent.")
            LogRepository.addLog(
                type = LogType.SECURITY,
                title = "SMS_SEND_CANCELLED",
                details = "Request ID: $requestId"
            )
        } else {
            telegramClient.sendMessage(token, chatId, "ℹ️ Request was already cancelled or expired.")
        }
    }

    /**
     * Handles /sent_sms to view history of SMS messages sent through TeleManage.
     */
    suspend fun handleSentSmsHistoryCommand(
        chatId: Long,
        token: String
    ) = withContext(Dispatchers.IO) {
        val history = preferenceManager.getSentSmsHistory()
        if (history.isEmpty()) {
            telegramClient.sendMessage(
                token = token,
                chatId = chatId,
                text = "📤 <b>TeleManage SMS History</b>\n\nNo SMS messages have been sent through TeleManage yet."
            )
            return@withContext
        }

        val sdf = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault())
        val formattedList = history.take(15).mapIndexed { idx, record ->
            val recipientDisplay = if (record.recipientName != null) {
                "${record.recipientNumber} (${record.recipientName})"
            } else {
                record.recipientNumber
            }
            val statusIcon = if (record.status == "SENT") "✅" else "❌"
            """
                ${idx + 1}. $recipientDisplay
                   "$record.messageSnippet"
                   Sent: ${sdf.format(Date(record.timestamp))}
                   Status: $statusIcon ${record.status}${if (record.failureReason != null) " (${record.failureReason})" else ""}
            """.trimIndent()
        }.joinToString("\n\n")

        val message = """
            📤 <b>TeleManage SMS History</b>

            $formattedList

            Total recorded: ${history.size}
            Use <code>/clear_sms_history</code> to wipe logs.
        """.trimIndent()

        telegramClient.sendMessage(token, chatId, message)
    }

    suspend fun handleClearHistoryPrompt(
        chatId: Long,
        token: String
    ) = withContext(Dispatchers.IO) {
        val text = "⚠️ <b>Clear SMS History</b>\n\nAre you sure you want to permanently clear the SMS sending history from TeleManage?"
        val keyboard = InlineKeyboardMarkup(
            inlineKeyboard = listOf(
                listOf(
                    InlineKeyboardButton("✅ Yes, Clear History", callbackData = "sms_clear_history_confirm"),
                    InlineKeyboardButton("❌ Cancel", callbackData = "sms_clear_history_cancel")
                )
            )
        )
        telegramClient.sendMessage(token, chatId, text, keyboard)
    }

    suspend fun handleClearHistoryConfirm(
        chatId: Long,
        token: String
    ) = withContext(Dispatchers.IO) {
        preferenceManager.clearSentSmsHistory()
        telegramClient.sendMessage(token, chatId, "🗑️ <b>SMS History Cleared</b>\n\nAll local sending logs have been wiped.")
        LogRepository.addLog(
            type = LogType.SYSTEM,
            title = "SMS_HISTORY_CLEARED",
            details = "Sent SMS history wiped by user request"
        )
    }

    fun maskPhoneNumber(phone: String): String {
        val clean = phone.trim()
        return if (clean.length <= 4) {
            "***"
        } else {
            val prefix = clean.take(3)
            val suffix = clean.takeLast(4)
            val stars = "*".repeat((clean.length - 7).coerceAtLeast(3))
            "$prefix$stars$suffix"
        }
    }
}
