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
        val trace = throwable?.let { "${it.javaClass.simpleName}: ${it.message}" }
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = trace
        )
        var coalesced: LogEntry? = null
        synchronized(lock) {
            val last = _entries.lastOrNull()
            if (last != null &&
                last.level == level &&
                last.tag == tag &&
                last.message == message &&
                last.throwable == trace
            ) {
                last.repeats++
                coalesced = last
            } else {
                _entries.add(entry)
                if (_entries.size > MAX_ENTRIES) {
                    _entries.removeAt(0)
                }
            }
        }
        emitToLogcat(level, tag, message, throwable, coalesced?.repeats ?: 0)
    }

    private fun emitToLogcat(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
        repeats: Int
    ) {
        val emit = repeats <= 2 || repeats % 100 == 0
        if (!emit) return
        val msg = if (repeats > 0) "$message (x$repeats)" else message
        when (level) {
            LogLevel.DEBUG -> android.util.Log.d(tag, msg, throwable)
            LogLevel.INFO -> android.util.Log.i(tag, msg, throwable)
            LogLevel.WARN -> android.util.Log.w(tag, msg, throwable)
            LogLevel.ERROR -> android.util.Log.e(tag, msg, throwable)
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
    var repeats: Int = 1

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
