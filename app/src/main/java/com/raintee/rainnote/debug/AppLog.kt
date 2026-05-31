package com.raintee.rainnote.debug

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {
    private const val MAX_LINES = 1000
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun d(tag: String, message: String) {
        append("D", tag, message)
        Log.d(tag, message)
    }

    @Synchronized
    fun e(tag: String, message: String, error: Throwable? = null) {
        append("E", tag, if (error == null) message else "$message: ${error.message}")
        Log.e(tag, message, error)
    }

    @Synchronized
    fun snapshot(): String = lines.joinToString(separator = "\n")

    @Synchronized
    fun clear() {
        lines.clear()
        append("D", "AppLog", "logs cleared")
    }

    private fun append(level: String, tag: String, message: String) {
        if (lines.size >= MAX_LINES) lines.removeFirst()
        lines.addLast("${timeFormat.format(Date())} $level/$tag: $message")
    }
}
