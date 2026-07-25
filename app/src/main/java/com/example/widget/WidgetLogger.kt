package com.example.widget

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WidgetLogger {
    private const val TAG = "BabyCareWidget"
    private const val PREFS_NAME = "widget_debug_prefs"
    private const val KEY_LOGS = "widget_logs"
    private const val KEY_LAST_ERROR = "widget_last_error"
    private const val MAX_LOGS = 30

    fun log(context: Context, message: String, isError: Boolean = false, throwable: Throwable? = null) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val fullMsg = if (throwable != null) {
            "[$timestamp] ${if (isError) "ERROR: " else ""}$message | Exception: ${throwable.localizedMessage ?: throwable.message}"
        } else {
            "[$timestamp] ${if (isError) "ERROR: " else ""}$message"
        }

        if (isError) {
            Log.e(TAG, fullMsg, throwable)
        } else {
            Log.d(TAG, fullMsg)
        }

        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentLogs = prefs.getString(KEY_LOGS, "") ?: ""
            val logLines = if (currentLogs.isEmpty()) mutableListOf() else currentLogs.split("\n").toMutableList()
            
            logLines.add(0, fullMsg) // latest first
            while (logLines.size > MAX_LOGS) {
                logLines.removeAt(logLines.size - 1)
            }

            val editor = prefs.edit().putString(KEY_LOGS, logLines.joinToString("\n"))
            if (isError) {
                editor.putString(KEY_LAST_ERROR, fullMsg)
            }
            editor.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to WidgetLogger prefs", e)
        }
    }

    fun getLogs(context: Context): List<String> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val logsStr = prefs.getString(KEY_LOGS, "") ?: ""
            if (logsStr.isBlank()) emptyList() else logsStr.split("\n")
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getLastError(context: Context): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_LAST_ERROR, null)
        } catch (e: Exception) {
            null
        }
    }

    fun clearLogs(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_LOGS).remove(KEY_LAST_ERROR).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear WidgetLogger prefs", e)
        }
    }
}
