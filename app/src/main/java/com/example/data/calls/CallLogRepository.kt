package com.example.data.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.provider.CallLog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    BLOCKED,
    UNKNOWN
}

data class CallRecord(
    val id: Long,
    val number: String,
    val cachedName: String?,
    val type: CallType,
    val timestamp: Long,
    val durationSeconds: Long
)

class CallLogRepository(private val context: Context) {

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getCallLogs(filter: String? = null, limit: Int = 100): List<CallRecord> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val calls = mutableListOf<CallRecord>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        val sortOrder = "${CallLog.Calls.DATE} DESC"

        try {
            val cursor: Cursor? = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(CallLog.Calls._ID)
                val numberCol = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameCol = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val typeCol = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateCol = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durationCol = it.getColumnIndexOrThrow(CallLog.Calls.DURATION)

                while (it.moveToNext() && calls.size < limit) {
                    val id = it.getLong(idCol)
                    val number = it.getString(numberCol) ?: "Unknown"
                    val cachedName = it.getString(nameCol)
                    val rawType = it.getInt(typeCol)
                    val date = it.getLong(dateCol)
                    val duration = it.getLong(durationCol)

                    val type = when (rawType) {
                        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                        CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
                        else -> CallType.UNKNOWN
                    }

                    calls.add(
                        CallRecord(
                            id = id,
                            number = number,
                            cachedName = cachedName,
                            type = type,
                            timestamp = date,
                            durationSeconds = duration
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Apply filters in memory
        when (filter?.lowercase()?.trim()) {
            "missed" -> calls.filter { it.type == CallType.MISSED }
            "incoming" -> calls.filter { it.type == CallType.INCOMING }
            "outgoing" -> calls.filter { it.type == CallType.OUTGOING }
            "rejected" -> calls.filter { it.type == CallType.REJECTED }
            "today" -> {
                val startOfDay = getStartOfTodayEpoch()
                calls.filter { it.timestamp >= startOfDay }
            }
            else -> calls
        }
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
