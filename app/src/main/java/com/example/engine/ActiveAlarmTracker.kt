package com.example.engine

import android.content.Context

/**
 * Tracks care/medicine alarms that have already fired and are waiting for
 * Done / Snooze. Prevents overdue re-arm loops that re-ring every minute.
 *
 * Also tracks the soft-reminder → ringing-alarm window so [ReminderEngine]
 * does not cancel a pending T+30s alarm when rescheduling siblings.
 */
object ActiveAlarmTracker {
    private const val PREFS = "active_alarm_tracker"
    private const val PREFIX = "active_"
    private const val PREFIX_REMINDER_SHOWN = "reminder_shown_"
    private const val PREFIX_PENDING_ALARM = "pending_alarm_"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isActive(context: Context, kindKey: String): Boolean =
        prefs(context).getBoolean(PREFIX + kindKey, false)

    fun markActive(context: Context, kindKey: String) {
        prefs(context).edit().putBoolean(PREFIX + kindKey, true).apply()
    }

    fun clear(context: Context, kindKey: String) {
        prefs(context).edit()
            .remove(PREFIX + kindKey)
            .remove(PREFIX_REMINDER_SHOWN + kindKey)
            .remove(PREFIX_PENDING_ALARM + kindKey)
            .apply()
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun markReminderShown(context: Context, kindKey: String, pendingAlarmAtMillis: Long) {
        prefs(context).edit()
            .putBoolean(PREFIX_REMINDER_SHOWN + kindKey, true)
            .putLong(PREFIX_PENDING_ALARM + kindKey, pendingAlarmAtMillis)
            .apply()
    }

    fun isReminderShown(context: Context, kindKey: String): Boolean =
        prefs(context).getBoolean(PREFIX_REMINDER_SHOWN + kindKey, false)

    fun pendingAlarmAt(context: Context, kindKey: String): Long =
        prefs(context).getLong(PREFIX_PENDING_ALARM + kindKey, 0L)

    fun clearReminderWindow(context: Context, kindKey: String) {
        prefs(context).edit()
            .remove(PREFIX_REMINDER_SHOWN + kindKey)
            .remove(PREFIX_PENDING_ALARM + kindKey)
            .apply()
    }

    fun kindKey(reminderKind: String, medicineAlarmId: Long = 0L): String {
        val kind = reminderKind.uppercase()
        return if (kind == "MEDICINE" && medicineAlarmId > 0L) {
            "MEDICINE_$medicineAlarmId"
        } else {
            kind
        }
    }
}
