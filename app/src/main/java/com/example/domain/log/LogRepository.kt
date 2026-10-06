package com.example.domain.log

import com.example.data.model.LogEvent
import com.example.data.model.LogType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogRepository {

    private const val MAX_LOGS = 200
    private val _logsFlow = MutableStateFlow<List<LogEvent>>(emptyList())
    val logsFlow: StateFlow<List<LogEvent>> = _logsFlow.asStateFlow()

    @Synchronized
    fun addLog(type: LogType, title: String, details: String, isSuccess: Boolean = true) {
        val event = LogEvent(
            type = type,
            title = title,
            details = details,
            isSuccess = isSuccess
        )
        val current = _logsFlow.value.toMutableList()
        current.add(0, event) // newest first
        if (current.size > MAX_LOGS) {
            current.removeAt(current.size - 1)
        }
        _logsFlow.value = current
    }

    @Synchronized
    fun clearLogs() {
        _logsFlow.value = emptyList()
    }

    fun formatTimestamp(epochMs: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }
}
