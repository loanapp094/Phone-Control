package com.example.data.sms

import com.example.data.contacts.ContactRepository
import com.example.data.model.InlineKeyboardButton
import com.example.data.model.InlineKeyboardMarkup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsManager(
    private val repository: SmsRepository,
    private val contactRepository: ContactRepository? = null
) {

    fun hasPermission(): Boolean = repository.hasPermission()

    suspend fun getFormattedMessages(
        boxType: SmsBoxType = SmsBoxType.ALL,
        searchQuery: String? = null,
        page: Int = 1,
        pageSize: Int = 10
    ): Pair<String, InlineKeyboardMarkup?> {
        if (!repository.hasPermission()) {
            return Pair(
                "⚠️ <b>SMS Access Required</b>\n\nOpen TeleManage and grant the required SMS permission first.",
                null
            )
        }

        val messages = repository.getMessages(
            boxType = boxType,
            searchQuery = searchQuery,
            limit = 100
        )

        val title = when {
            !searchQuery.isNullOrBlank() -> "SMS Search: \"${escapeHtml(searchQuery)}\""
            boxType == SmsBoxType.TODAY -> "Today's SMS"
            boxType == SmsBoxType.UNREAD -> "Unread SMS"
            boxType == SmsBoxType.INBOX -> "SMS Inbox"
            boxType == SmsBoxType.SENT -> "Sent SMS"
            else -> "Recent SMS"
        }

        if (messages.isEmpty()) {
            return Pair("💬 <b>$title</b>\n\nNo SMS messages found.", null)
        }

        val totalPages = ((messages.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        val validPage = page.coerceIn(1, totalPages)
        val startIndex = (validPage - 1) * pageSize
        val pageItems = messages.drop(startIndex).take(pageSize)

        // Preload contacts for name resolution if permission is granted
        val contactMap = if (contactRepository != null && contactRepository.hasPermission()) {
            try {
                contactRepository.getAllContacts().flatMap { contact ->
                    contact.phoneNumbers.map { it.number.replace("\\s|-".toRegex(), "") to contact.displayName }
                }.toMap()
            } catch (_: Exception) {
                emptyMap()
            }
        } else emptyMap()

        val sb = StringBuilder()
        sb.append("💬 <b>$title</b>\n\n")

        pageItems.forEachIndexed { index, msg ->
            val num = startIndex + index + 1
            val cleanAddr = msg.address.replace("\\s|-".toRegex(), "")
            val senderName = contactMap[cleanAddr] ?: msg.address
            val preview = if (msg.body.length > 100) "${msg.body.take(97)}..." else msg.body
            val timeStr = formatTimestamp(msg.date)
            val directionIcon = if (msg.isSent) "📤 [Sent]" else if (!msg.isRead) "🔵 [Unread]" else "📥"

            sb.append("<b>$num.</b> <b>${escapeHtml(senderName)}</b>")
            if (senderName != msg.address && msg.address.isNotBlank()) {
                sb.append(" (<code>${msg.address}</code>)")
            }
            sb.append("\n")
            sb.append("   \"${escapeHtml(preview)}\"\n")
            sb.append("   $directionIcon $timeStr • Thread: <code>/sms_thread_${msg.threadId}</code>\n\n")
        }

        sb.append("<i>Page $validPage/$totalPages (${messages.size} messages)</i>\n")
        sb.append("💡 <i>View thread: <code>/sms thread &lt;id&gt;</code></i>")

        val callbackPrefix = when {
            !searchQuery.isNullOrBlank() -> "sms_search_${searchQuery}_page_"
            boxType != SmsBoxType.ALL -> "sms_${boxType.name.lowercase()}_page_"
            else -> "sms_page_"
        }

        val buttons = mutableListOf<InlineKeyboardButton>()
        if (validPage > 1) {
            buttons.add(InlineKeyboardButton(text = "⬅️ Previous", callbackData = "${callbackPrefix}${validPage - 1}"))
        }
        if (validPage < totalPages) {
            buttons.add(InlineKeyboardButton(text = "Next ➡️", callbackData = "${callbackPrefix}${validPage + 1}"))
        }

        val keyboard = if (buttons.isNotEmpty()) {
            InlineKeyboardMarkup(inlineKeyboard = listOf(buttons))
        } else null

        return Pair(sb.toString(), keyboard)
    }

    suspend fun getFormattedThread(
        threadId: Long,
        page: Int = 1,
        pageSize: Int = 10
    ): Pair<String, InlineKeyboardMarkup?> {
        if (!repository.hasPermission()) {
            return Pair(
                "⚠️ <b>SMS Access Required</b>\n\nOpen TeleManage and grant the required SMS permission first.",
                null
            )
        }

        val messages = repository.getMessages(
            threadId = threadId,
            limit = 60
        )

        if (messages.isEmpty()) {
            return Pair("💬 <b>Conversation Thread</b>\n\nNo messages found for Thread #$threadId.", null)
        }

        val participant = messages.firstOrNull()?.address ?: "Unknown"

        val totalPages = ((messages.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        val validPage = page.coerceIn(1, totalPages)
        val startIndex = (validPage - 1) * pageSize
        val pageItems = messages.drop(startIndex).take(pageSize).reversed() // Chronological order

        val sb = StringBuilder()
        sb.append("💬 <b>Conversation Thread #$threadId</b>\n")
        sb.append("👤 <b>Participant:</b> <code>$participant</code>\n\n")

        pageItems.forEach { msg ->
            val senderLabel = if (msg.isSent) "You" else participant
            val timeStr = formatTimestamp(msg.date)
            sb.append("<b>$senderLabel</b> • <i>$timeStr</i>\n")
            sb.append("\"${escapeHtml(msg.body)}\"\n\n")
        }

        sb.append("<i>Page $validPage/$totalPages</i>")

        val buttons = mutableListOf<InlineKeyboardButton>()
        if (validPage > 1) {
            buttons.add(InlineKeyboardButton(text = "⬅️ Newer", callbackData = "sms_thread_${threadId}_page_${validPage - 1}"))
        }
        if (validPage < totalPages) {
            buttons.add(InlineKeyboardButton(text = "Older ➡️", callbackData = "sms_thread_${threadId}_page_${validPage + 1}"))
        }

        val keyboard = if (buttons.isNotEmpty()) {
            InlineKeyboardMarkup(inlineKeyboard = listOf(buttons))
        } else null

        return Pair(sb.toString(), keyboard)
    }

    private fun formatTimestamp(epochMs: Long): String {
        val isToday = android.text.format.DateUtils.isToday(epochMs)
        return if (isToday) {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            "Today ${sdf.format(Date(epochMs))}"
        } else {
            val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            sdf.format(Date(epochMs))
        }
    }

    private fun escapeHtml(str: String): String {
        return str
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
