package com.example.data.calls

import com.example.data.model.InlineKeyboardButton
import com.example.data.model.InlineKeyboardMarkup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallLogManager(private val repository: CallLogRepository) {

    fun hasPermission(): Boolean = repository.hasPermission()

    suspend fun getFormattedCallLogs(
        filter: String? = null,
        page: Int = 1,
        pageSize: Int = 10
    ): Pair<String, InlineKeyboardMarkup?> {
        if (!repository.hasPermission()) {
            return Pair(
                "⚠️ <b>Call Log Permission Required</b>\n\nOpen TeleManage and grant Call Log access first.",
                null
            )
        }

        val calls = repository.getCallLogs(filter = filter, limit = 100)
        val filterTitle = when (filter?.lowercase()?.trim()) {
            "missed" -> "Missed Calls"
            "incoming" -> "Incoming Calls"
            "outgoing" -> "Outgoing Calls"
            "today" -> "Today's Calls"
            else -> "Recent Calls"
        }

        if (calls.isEmpty()) {
            return Pair("📞 <b>$filterTitle</b>\n\nNo records found matching filter.", null)
        }

        val totalPages = ((calls.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        val validPage = page.coerceIn(1, totalPages)
        val startIndex = (validPage - 1) * pageSize
        val pageItems = calls.drop(startIndex).take(pageSize)

        val sb = StringBuilder()
        sb.append("📞 <b>$filterTitle</b>\n\n")

        pageItems.forEachIndexed { index, record ->
            val num = startIndex + index + 1
            val displayName = record.cachedName?.ifBlank { null } ?: record.number
            val typeStr = when (record.type) {
                CallType.INCOMING -> "Incoming"
                CallType.OUTGOING -> "Outgoing"
                CallType.MISSED -> "Missed"
                CallType.REJECTED -> "Rejected"
                CallType.BLOCKED -> "Blocked"
                CallType.UNKNOWN -> "Unknown"
            }
            val typeIcon = when (record.type) {
                CallType.INCOMING -> "📥"
                CallType.OUTGOING -> "📤"
                CallType.MISSED -> "🔴"
                CallType.REJECTED -> "⛔"
                CallType.BLOCKED -> "🚫"
                CallType.UNKNOWN -> "📞"
            }
            val durationStr = formatDuration(record.durationSeconds)
            val timeStr = formatTimestamp(record.timestamp)

            sb.append("<b>$num.</b> <b>${escapeHtml(displayName)}</b>\n")
            if (record.cachedName != null && record.cachedName.isNotBlank() && record.cachedName != record.number) {
                sb.append("   <code>${record.number}</code>\n")
            }
            sb.append("   $typeIcon $typeStr • $durationStr • $timeStr\n\n")
        }

        sb.append("<i>Page $validPage/$totalPages (${calls.size} calls)</i>\n")
        sb.append("💡 <i>Filters: <code>/calls missed</code>, <code>/calls today</code>, <code>/calls incoming</code></i>")

        val callbackPrefix = if (!filter.isNullOrBlank()) "calls_${filter}_page_" else "calls_page_"
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

    private fun formatDuration(durationSeconds: Long): String {
        val min = durationSeconds / 60
        val sec = durationSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    private fun formatTimestamp(epochMs: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - epochMs
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
