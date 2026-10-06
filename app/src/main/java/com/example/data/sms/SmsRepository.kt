package com.example.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SmsBoxType {
    ALL,
    INBOX,
    SENT,
    UNREAD,
    TODAY
}

data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val isRead: Boolean,
    val isSent: Boolean
)

class SmsRepository(private val context: Context) {

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getMessages(
        boxType: SmsBoxType = SmsBoxType.ALL,
        searchQuery: String? = null,
        threadId: Long? = null,
        limit: Int = 100
    ): List<SmsMessage> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val messages = mutableListOf<SmsMessage>()
        val uri: Uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE
        )

        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        if (threadId != null) {
            selectionParts.add("${Telephony.Sms.THREAD_ID} = ?")
            selectionArgs.add(threadId.toString())
        }

        when (boxType) {
            SmsBoxType.INBOX -> {
                selectionParts.add("${Telephony.Sms.TYPE} = ?")
                selectionArgs.add(Telephony.Sms.MESSAGE_TYPE_INBOX.toString())
            }
            SmsBoxType.SENT -> {
                selectionParts.add("${Telephony.Sms.TYPE} = ?")
                selectionArgs.add(Telephony.Sms.MESSAGE_TYPE_SENT.toString())
            }
            SmsBoxType.UNREAD -> {
                selectionParts.add("${Telephony.Sms.READ} = 0")
            }
            SmsBoxType.TODAY -> {
                val startOfDay = getStartOfTodayEpoch()
                selectionParts.add("${Telephony.Sms.DATE} >= ?")
                selectionArgs.add(startOfDay.toString())
            }
            SmsBoxType.ALL -> {}
        }

        if (!searchQuery.isNullOrBlank()) {
            selectionParts.add("(${Telephony.Sms.BODY} LIKE ? OR ${Telephony.Sms.ADDRESS} LIKE ?)")
            val wildcard = "%$searchQuery%"
            selectionArgs.add(wildcard)
            selectionArgs.add(wildcard)
        }

        val selection = if (selectionParts.isNotEmpty()) selectionParts.joinToString(" AND ") else null
        val args = if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        try {
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                selection,
                args,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadCol = it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addressCol = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyCol = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateCol = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val readCol = it.getColumnIndexOrThrow(Telephony.Sms.READ)
                val typeCol = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)

                while (it.moveToNext() && messages.size < limit) {
                    val id = it.getLong(idCol)
                    val thread = it.getLong(threadCol)
                    val address = it.getString(addressCol) ?: "Unknown"
                    val body = it.getString(bodyCol) ?: ""
                    val date = it.getLong(dateCol)
                    val isRead = it.getInt(readCol) == 1
                    val type = it.getInt(typeCol)
                    val isSent = type == Telephony.Sms.MESSAGE_TYPE_SENT

                    messages.add(
                        SmsMessage(
                            id = id,
                            threadId = thread,
                            address = address,
                            body = body,
                            date = date,
                            isRead = isRead,
                            isSent = isSent
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        messages
    }

    private fun getStartOfTodayEpoch(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
