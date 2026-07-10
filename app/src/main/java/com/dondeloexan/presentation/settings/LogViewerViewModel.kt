package com.dondeloexan.presentation.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.util.AppLogger
import com.dondeloexan.util.LogEntry
import com.dondeloexan.util.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

class LogViewerViewModel : ViewModel() {

    private val _activeFilter = MutableStateFlow<LogLevel?>(null)
    val activeFilter: StateFlow<LogLevel?> = _activeFilter.asStateFlow()

    private val _logsTrigger = MutableStateFlow(0L)

    val filteredLogs: StateFlow<List<LogEntry>> = combine(
        _logsTrigger, _activeFilter
    ) { _, filter ->
        val logs = AppLogger.entries
        if (filter == null) logs
        else logs.filter { it.level == filter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshLogs() {
        _logsTrigger.value = System.currentTimeMillis()
    }

    fun setFilter(level: LogLevel?) {
        _activeFilter.value = level
    }

    fun clearLogs() {
        AppLogger.clear()
        _logsTrigger.value = System.currentTimeMillis()
    }

    private fun buildLogText(): String {
        val filtered = if (_activeFilter.value == null) AppLogger.entries
        else AppLogger.entries.filter { it.level == _activeFilter.value }

        return filtered.joinToString("\n") { entry ->
            "${entry.fullFormattedTime} [${entry.level}] ${entry.tag}: ${entry.message}" +
                if (entry.throwable != null) "\n  ${entry.throwable}" else ""
        }
    }

    fun shareLogs(context: Context) {
        val logText = buildLogText()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "DondeLoExan - Logs")
            putExtra(Intent.EXTRA_TEXT, logText)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir logs"))
    }
}
