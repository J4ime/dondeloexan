package com.dondeloexan.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val MAX_ENTRIES = 500
    private val _entries = mutableListOf<LogEntry>()
    private val lock = Any()

    val entries: List<LogEntry>
        get() = synchronized(lock) { _entries.toList() }

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable?.let { "${it.javaClass.simpleName}: ${it.message}" }
        )
        synchronized(lock) {
            _entries.add(entry)
            if (_entries.size > MAX_ENTRIES) {
                _entries.removeAt(0)
            }
        }
        when (level) {
            LogLevel.DEBUG -> android.util.Log.d(tag, message, throwable)
            LogLevel.INFO -> android.util.Log.i(tag, message, throwable)
            LogLevel.WARN -> android.util.Log.w(tag, message, throwable)
            LogLevel.ERROR -> android.util.Log.e(tag, message, throwable)
        }
    }

    fun clear() = synchronized(lock) { _entries.clear() }

    fun d(tag: String, msg: String) = log(LogLevel.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(LogLevel.INFO, tag, msg)
    fun w(tag: String, msg: String) = log(LogLevel.WARN, tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) = log(LogLevel.ERROR, tag, msg, t)
}

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: String? = null
) {
    val formattedTime: String get() {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    val fullFormattedTime: String get() {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

enum class LogLevel(val priority: Int) {
    DEBUG(3), INFO(4), WARN(5), ERROR(6)
}
