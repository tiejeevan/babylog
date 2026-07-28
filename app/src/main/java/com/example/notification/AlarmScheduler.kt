package com.example.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import com.example.data.model.CareCheckSettings

/**
 * Schedules exact / AlarmClock wakeups for the Reminder Engine.
 */
object AlarmScheduler {

    fun schedule(
        context: Context,
        triggerAtMillis: Long,
        requestCode: Int,
        title: String,
        message: String,
        reminderKind: String,
        notificationId: Int,
        channelId: String,
        settings: CareCheckSettings,
        medicineAlarmId: Long = 0L,
        sticky: Boolean = true,
        deliveryMode: String = BabyNotificationManager.DELIVERY_ALARM,
        pendingAlarmAtMillis: Long = 0L
    ) {
        if (triggerAtMillis <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, BabyAlarmReceiver::class.java).apply {
            action = BabyNotificationManager.ACTION_BABY_ALARM
            putExtra(BabyNotificationManager.EXTRA_TITLE, title)
            putExtra(BabyNotificationManager.EXTRA_MESSAGE, message)
            putExtra(BabyNotificationManager.EXTRA_CHANNEL_ID, channelId)
            putExtra(BabyNotificationManager.EXTRA_REMINDER_TYPE, reminderKind)
            putExtra(BabyNotificationManager.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(BabyNotificationManager.EXTRA_MEDICINE_ALARM_ID, medicineAlarmId)
            putExtra(BabyNotificationManager.EXTRA_STICKY, sticky)
            putExtra(BabyNotificationManager.EXTRA_DELIVERY_MODE, deliveryMode)
            putExtra(BabyNotificationManager.EXTRA_PENDING_ALARM_AT, pendingAlarmAtMillis)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val useAlarmClock =
            settings.systemAlarmsEnabled &&
                deliveryMode == BabyNotificationManager.DELIVERY_ALARM
        try {
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
            when {
                useAlarmClock && canExact -> {
                    val showIntent = PendingIntent.getActivity(
                        context,
                        requestCode + 1000,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                        pendingIntent
                    )
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    if (canExact) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    }
                }
                else -> {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            }
        } catch (_: SecurityException) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } catch (_: SecurityException) {
                if (settings.notificationsEnabled || settings.systemAlarmsEnabled) {
                    if (deliveryMode == BabyNotificationManager.DELIVERY_REMINDER) {
                        BabyNotificationManager.showCareReminderNotification(
                            context = context,
                            title = title,
                            message = message,
                            reminderKind = reminderKind,
                            notificationId = notificationId
                        )
                    } else {
                        BabyNotificationManager.launchPhoneAlarm(
                            context = context,
                            title = title,
                            message = message,
                            reminderKind = reminderKind,
                            notificationId = notificationId,
                            medicineAlarmId = medicineAlarmId,
                            playSound = true
                        )
                    }
                }
            }
        }
    }

    fun cancel(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, BabyAlarmReceiver::class.java).apply {
            action = BabyNotificationManager.ACTION_BABY_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
