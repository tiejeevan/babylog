package com.example.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

object BabyNotificationManager {

    const val CHANNEL_TIMERS = "channel_timers"
    const val CHANNEL_REMINDERS = "channel_reminders"
    const val CHANNEL_HEALTH = "channel_health"

    private const val NOTIFICATION_ID_BASE = 1000

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val timerChannel = NotificationChannel(
                CHANNEL_TIMERS,
                "Timer Completion Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "System notifications when active timers (feeding/sleep) complete"
                enableVibration(true)
            }

            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Routine Feeding & Nap Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders when baby's target feeding or sleep intervals elapse"
                enableVibration(true)
            }

            val healthChannel = NotificationChannel(
                CHANNEL_HEALTH,
                "Health & Medication Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for medication, growth checks, and vaccines"
            }

            notificationManager.createNotificationChannel(timerChannel)
            notificationManager.createNotificationChannel(reminderChannel)
            notificationManager.createNotificationChannel(healthChannel)
        }
    }

    fun showSystemNotification(
        context: Context,
        title: String,
        message: String,
        channelId: String = CHANNEL_REMINDERS,
        notificationId: Int = (System.currentTimeMillis() % 10000).toInt()
    ) {
        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 300, 200, 300))

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

    fun scheduleFeedingAlarm(context: Context, timeInMillis: Long, babyName: String) {
        scheduleAlarm(
            context = context,
            triggerAtMillis = timeInMillis,
            title = "🍼 Feeding Time for $babyName",
            message = "Target feeding window reached! Time to give $babyName a nurse or bottle.",
            requestCode = 2001,
            channelId = CHANNEL_REMINDERS
        )
    }

    fun scheduleNapAlarm(context: Context, timeInMillis: Long, babyName: String) {
        scheduleAlarm(
            context = context,
            triggerAtMillis = timeInMillis,
            title = "😴 Nap Window for $babyName",
            message = "Wake window limit reached. Time to put $babyName down for a nap.",
            requestCode = 2002,
            channelId = CHANNEL_REMINDERS
        )
    }

    fun scheduleTimerCompleteAlarm(context: Context, delaySeconds: Long, activityName: String, babyName: String) {
        val triggerTime = System.currentTimeMillis() + (delaySeconds * 1000)
        scheduleAlarm(
            context = context,
            triggerAtMillis = triggerTime,
            title = "⏰ $activityName Timer Done",
            message = "$activityName session for $babyName has ended!",
            requestCode = 2003,
            channelId = CHANNEL_TIMERS
        )
    }

    private fun scheduleAlarm(
        context: Context,
        triggerAtMillis: Long,
        title: String,
        message: String,
        requestCode: Int,
        channelId: String
    ) {
        if (triggerAtMillis <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, BabyAlarmReceiver::class.java).apply {
            action = "com.example.ACTION_BABY_ALARM"
            putExtra("title", title)
            putExtra("message", message)
            putExtra("channel_id", channelId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
        } catch (e: SecurityException) {
            // Fallback if exact alarm permissions missing
            showSystemNotification(context, title, message, channelId)
        }
    }
}
