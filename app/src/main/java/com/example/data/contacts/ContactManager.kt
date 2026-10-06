package com.example.data.contacts

import com.example.data.model.InlineKeyboardButton
import com.example.data.model.InlineKeyboardMarkup

class ContactManager(private val repository: ContactRepository) {

    fun hasPermission(): Boolean = repository.hasPermission()

    suspend fun getFormattedContacts(page: Int = 1, pageSize: Int = 8): Pair<String, InlineKeyboardMarkup?> {
        if (!repository.hasPermission()) {
            return Pair(
                "⚠️ <b>Contacts Permission Required</b>\n\nOpen TeleManage on your phone and grant Contacts access first.",
                null
            )
        }

        val allContacts = repository.getAllContacts()
        if (allContacts.isEmpty()) {
            return Pair("👥 <b>Contacts</b>\n\nNo contacts found on device.", null)
        }

        val totalPages = ((allContacts.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        val validPage = page.coerceIn(1, totalPages)
        val startIndex = (validPage - 1) * pageSize
        val pageItems = allContacts.drop(startIndex).take(pageSize)

        val sb = StringBuilder()
        sb.append("👥 <b>Contacts</b>\n\n")

        pageItems.forEachIndexed { index, contact ->
            val num = startIndex + index + 1
            sb.append("<b>$num.</b> <b>${escapeHtml(contact.displayName)}</b>\n")
            contact.phoneNumbers.forEach { phone ->
                val typeSuffix = if (phone.type.isNotBlank() && phone.type != "Mobile") " (${escapeHtml(phone.type)})" else ""
                sb.append("   📞 <code>${phone.number}</code>$typeSuffix\n")
            }
            sb.append("\n")
        }

        sb.append("<i>Page $validPage/$totalPages (${allContacts.size} contacts)</i>\n")
        sb.append("💡 <i>Tip: Use <code>/contact &lt;name or number&gt;</code> to search.</i>")

        // Inline keyboard pagination
        val buttons = mutableListOf<InlineKeyboardButton>()
        if (validPage > 1) {
            buttons.add(InlineKeyboardButton(text = "⬅️ Previous", callbackData = "contacts_page_${validPage - 1}"))
        }
        if (validPage < totalPages) {
            buttons.add(InlineKeyboardButton(text = "Next ➡️", callbackData = "contacts_page_${validPage + 1}"))
        }

        val keyboard = if (buttons.isNotEmpty()) {
            InlineKeyboardMarkup(inlineKeyboard = listOf(buttons))
        } else null

        return Pair(sb.toString(), keyboard)
    }

    suspend fun getFormattedContactSearch(query: String): String {
        if (!repository.hasPermission()) {
            return "⚠️ <b>Contacts Permission Required</b>\n\nOpen TeleManage on your phone and grant Contacts access first."
        }
        if (query.isBlank()) {
            return "❓ Please specify a name or phone number to search.\nExample: <code>/contact Rahul</code>"
        }

        val results = repository.searchContacts(query)
        if (results.isEmpty()) {
            return "🔍 <b>No Contacts Found</b>\n\nNo matching contact found for \"${escapeHtml(query)}\"."
        }

        val sb = StringBuilder()
        sb.append("👤 <b>Search Results for \"${escapeHtml(query)}\"</b>\n\n")

        results.forEachIndexed { index, contact ->
            sb.append("<b>${index + 1}.</b> <b>${escapeHtml(contact.displayName)}</b>\n")
            contact.phoneNumbers.forEach { phone ->
                val typeSuffix = if (phone.type.isNotBlank()) " (${escapeHtml(phone.type)})" else ""
                sb.append("   📞 <code>${phone.number}</code>$typeSuffix\n")
            }
            sb.append("\n")
        }

        return sb.toString().trimEnd()
    }

    private fun escapeHtml(str: String): String {
        return str
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
