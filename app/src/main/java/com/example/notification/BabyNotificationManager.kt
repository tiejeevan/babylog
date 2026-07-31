package com.example.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.model.CareCheckSettings
import com.example.engine.ReminderKind

object BabyNotificationManager {

    const val CHANNEL_TIMERS = "channel_timers"
    const val CHANNEL_ONGOING_TIMERS = "channel_ongoing_timers"
    const val CHANNEL_REMINDERS = "channel_reminders"
    const val CHANNEL_HEALTH = "channel_health"
    /** Dedicated phone-alarm channel (USAGE_ALARM sound, bypass DND). */
    const val CHANNEL_PHONE_ALARMS = "channel_phone_alarms_v1"
    const val CHANNEL_MESSAGES = "channel_care_messages"
    const val CHANNEL_VOICE_COMMANDS = "channel_voice_commands"

    const val EXTRA_OPEN_PEER_CHAT = "open_peer_chat"
    const val EXTRA_OPEN_VOICE_COMMANDS = "open_voice_commands"
    const val NOTIFICATION_ID_PEER_CHAT = 4300
    const val NOTIFICATION_ID_VOICE_CONFIRM = 4301
    const val NOTIFICATION_ID_ONGOING_TIMER = 2199

    const val ACTION_PAUSE_TIMER = "com.example.ACTION_PAUSE_TIMER"
    const val ACTION_RESUME_TIMER = "com.example.ACTION_RESUME_TIMER"
    const val ACTION_SWITCH_SIDE = "com.example.ACTION_SWITCH_SIDE"
    const val ACTION_STOP_TIMER = "com.example.ACTION_STOP_TIMER"

    const val REQUEST_PAUSE_TIMER = 2195
    const val REQUEST_RESUME_TIMER = 2196
    const val REQUEST_SWITCH_SIDE = 2197
    const val REQUEST_STOP_TIMER = 2198

    private data class PeerChatLine(
        val senderName: String,
        val text: String,
        val timestampMillis: Long
    )

    private val peerChatLines = mutableListOf<PeerChatLine>()
    private const val MAX_PEER_CHAT_LINES = 8

    const val REQUEST_FEED = 2001
    const val REQUEST_SLEEP = 2002
    const val REQUEST_TIMER = 2003
    const val REQUEST_DIAPER = 2004
    const val REQUEST_BABY_CHECK = 2006
    const val REQUEST_NAP_LEGACY = 2002
    const val REQUEST_MEDICINE_LEGACY = 2005
    const val REQUEST_MEDICINE_BASE = 4000

    /** Soft reminder PendingIntent codes (alarm uses REQUEST_* above). */
    const val REQUEST_FEED_REMINDER = 2011
    const val REQUEST_DIAPER_REMINDER = 2014
    const val REQUEST_BABY_CHECK_REMINDER = 2016

    const val REQUEST_SNOOZE_FEED = 3001
    const val REQUEST_SNOOZE_SLEEP = 3002
    const val REQUEST_SNOOZE_DIAPER = 3004
    const val REQUEST_SNOOZE_BABY_CHECK = 3006
    /** Per-medicine snooze PendingIntent codes: base + alarmId (1..999). */
    const val REQUEST_SNOOZE_MEDICINE_BASE = 6000

    const val NOTIFICATION_ID_FEED = 2101
    const val NOTIFICATION_ID_SLEEP = 2102
    const val NOTIFICATION_ID_DIAPER = 2104
    const val NOTIFICATION_ID_BABY_CHECK = 2106
    const val NOTIFICATION_MEDICINE_BASE = 5000

    /** Soft reminder notification IDs (distinct from ringing alarm IDs). */
    const val NOTIFICATION_ID_FEED_REMINDER = 2111
    const val NOTIFICATION_ID_DIAPER_REMINDER = 2114
    const val NOTIFICATION_ID_BABY_CHECK_REMINDER = 2116

    const val EXTRA_REMINDER_TYPE = "reminder_type"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_CHANNEL_ID = "channel_id"
    const val EXTRA_MEDICINE_ALARM_ID = "medicine_alarm_id"
    const val EXTRA_STICKY = "sticky"
    const val EXTRA_DELIVERY_MODE = "delivery_mode"
    const val EXTRA_PENDING_ALARM_AT = "pending_alarm_at"

    const val DELIVERY_ALARM = "alarm"
    const val DELIVERY_REMINDER = "reminder"

    const val TYPE_FEED = "FEED"
    const val TYPE_SLEEP = "SLEEP"
    const val TYPE_DIAPER = "DIAPER"
    const val TYPE_BABY_CHECK = "BABY_CHECK"
    const val TYPE_MEDICINE = "MEDICINE"

    // Legacy aliases for older PendingIntents
    const val TYPE_NAP = TYPE_SLEEP
    const val TYPE_FEED_LEGACY = "feed"
    const val TYPE_NAP_LEGACY = "nap"
    const val TYPE_DIAPER_LEGACY = "diaper"
    const val TYPE_MEDICINE_LEGACY = "medicine"

    const val ACTION_BABY_ALARM = "com.example.ACTION_BABY_ALARM"
    const val ACTION_REMINDER_DONE = "com.example.ACTION_REMINDER_DONE"
    const val ACTION_REMINDER_SNOOZE = "com.example.ACTION_REMINDER_SNOOZE"

    const val SNOOZE_MS = 10 * 60_000L

    fun medicineRequestCode(alarmId: Long): Int =
        REQUEST_MEDICINE_BASE + (alarmId.coerceIn(1L, 999L).toInt())

    fun medicineNotificationId(alarmId: Long): Int =
        NOTIFICATION_MEDICINE_BASE + (alarmId.coerceIn(1L, 999L).toInt())

    fun reminderRequestCode(reminderKind: String): Int? =
        when (normalizeKind(reminderKind)) {
            TYPE_FEED -> REQUEST_FEED_REMINDER
            TYPE_DIAPER -> REQUEST_DIAPER_REMINDER
            TYPE_BABY_CHECK -> REQUEST_BABY_CHECK_REMINDER
            else -> null
        }

    fun alarmRequestCode(reminderKind: String): Int? =
        when (normalizeKind(reminderKind)) {
            TYPE_FEED -> REQUEST_FEED
            TYPE_DIAPER -> REQUEST_DIAPER
            TYPE_BABY_CHECK -> REQUEST_BABY_CHECK
            TYPE_SLEEP -> REQUEST_SLEEP
            else -> null
        }

    fun reminderNotificationId(reminderKind: String): Int? =
        when (normalizeKind(reminderKind)) {
            TYPE_FEED -> NOTIFICATION_ID_FEED_REMINDER
            TYPE_DIAPER -> NOTIFICATION_ID_DIAPER_REMINDER
            TYPE_BABY_CHECK -> NOTIFICATION_ID_BABY_CHECK_REMINDER
            else -> null
        }

    fun snoozeRequestCode(reminderKind: String, medicineAlarmId: Long = 0L): Int? =
        when (normalizeKind(reminderKind)) {
            TYPE_FEED -> REQUEST_SNOOZE_FEED
            TYPE_SLEEP -> REQUEST_SNOOZE_SLEEP
            TYPE_DIAPER -> REQUEST_SNOOZE_DIAPER
            TYPE_BABY_CHECK -> REQUEST_SNOOZE_BABY_CHECK
            TYPE_MEDICINE -> if (medicineAlarmId > 0L) {
                REQUEST_SNOOZE_MEDICINE_BASE + medicineAlarmId.coerceIn(1L, 999L).toInt()
            } else {
                null
            }
            else -> null
        }

    fun normalizeKind(reminderType: String): String = when (reminderType) {
        TYPE_FEED, TYPE_FEED_LEGACY -> TYPE_FEED
        TYPE_SLEEP, TYPE_NAP_LEGACY, "nap" -> TYPE_SLEEP
        TYPE_DIAPER, TYPE_DIAPER_LEGACY -> TYPE_DIAPER
        TYPE_BABY_CHECK -> TYPE_BABY_CHECK
        TYPE_MEDICINE, TYPE_MEDICINE_LEGACY -> TYPE_MEDICINE
        else -> reminderType.uppercase()
    }

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
                "Routine Care Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Care reminder notifications"
                enableVibration(true)
                setBypassDnd(true)
            }

            val healthChannel = NotificationChannel(
                CHANNEL_HEALTH,
                "Health & Medication Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Medicine reminder notifications"
                enableVibration(true)
                setBypassDnd(true)
            }

            val alarmUri = android.media.RingtoneManager.getDefaultUri(
                android.media.RingtoneManager.TYPE_ALARM
            )
            val alarmAttrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val phoneAlarmChannel = NotificationChannel(
                CHANNEL_PHONE_ALARMS,
                "Phone Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Full phone-style alarms for care checks and medicine"
                enableVibration(true)
                setBypassDnd(true)
                setSound(alarmUri, alarmAttrs)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Caregiver Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming Care Sync chat and ping messages"
                enableVibration(true)
            }

            val voiceChannel = NotificationChannel(
                CHANNEL_VOICE_COMMANDS,
                "Voice Care Commands",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Confirmations when a hands-free voice command is logged"
                enableVibration(true)
            }

            val ongoingTimerChannel = NotificationChannel(
                CHANNEL_ONGOING_TIMERS,
                "Active Ongoing Timers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live ongoing timers for feeding, sleep, and pumping"
                enableVibration(false)
                setSound(null, null)
            }

            notificationManager.createNotificationChannel(timerChannel)
            notificationManager.createNotificationChannel(ongoingTimerChannel)
            notificationManager.createNotificationChannel(reminderChannel)
            notificationManager.createNotificationChannel(healthChannel)
            notificationManager.createNotificationChannel(phoneAlarmChannel)
            notificationManager.createNotificationChannel(messagesChannel)
            notificationManager.createNotificationChannel(voiceChannel)
        }
    }

    fun buildOngoingTimerNotification(
        context: Context,
        ongoingLog: ActivityLog,
        isPaused: Boolean = false,
        activeNursingSide: String = "LEFT",
        elapsedLeftSec: Long = 0,
        elapsedRightSec: Long = 0
    ): Notification {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isNursing = ongoingLog.activityType == ActivityTypes.BREASTFEEDING
        val title = when (ongoingLog.activityType) {
            ActivityTypes.BREASTFEEDING -> "🤱 Ongoing Nursing Session"
            ActivityTypes.SLEEP -> "😴 Baby Sleeping..."
            ActivityTypes.PUMPING -> "🍼 Pumping Session"
            else -> "⏱️ Active ${ongoingLog.activityType}"
        }

        val subtext = if (isNursing) {
            val sideText = if (activeNursingSide == "RIGHT") "Right Side" else "Left Side"
            "Active: $sideText • Left: ${elapsedLeftSec / 60}m, Right: ${elapsedRightSec / 60}m"
        } else {
            "Caregiver: ${ongoingLog.caregiverName}"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING_TIMERS)
            .setContentTitle(title)
            .setContentText(if (isPaused) "⏸️ Paused • $subtext" else subtext)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(!isPaused)
            .setWhen(ongoingLog.startTimeMillis)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // 1. Pause / Resume action
        if (isPaused) {
            val resumeIntent = Intent(context, TimerNotificationReceiver::class.java).apply {
                action = ACTION_RESUME_TIMER
            }
            val resumePendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_RESUME_TIMER,
                resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_media_play,
                "Resume",
                resumePendingIntent
            )
        } else {
            val pauseIntent = Intent(context, TimerNotificationReceiver::class.java).apply {
                action = ACTION_PAUSE_TIMER
            }
            val pausePendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_PAUSE_TIMER,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                pausePendingIntent
            )
        }

        // 2. Switch Side action (Nursing only)
        if (isNursing) {
            val switchSideIntent = Intent(context, TimerNotificationReceiver::class.java).apply {
                action = ACTION_SWITCH_SIDE
            }
            val switchSidePendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_SWITCH_SIDE,
                switchSideIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nextSideLabel = if (activeNursingSide == "RIGHT") "Switch to Left" else "Switch to Right"
            builder.addAction(
                android.R.drawable.ic_menu_rotate,
                nextSideLabel,
                switchSidePendingIntent
            )
        }

        // 3. Stop & Save action
        val stopIntent = Intent(context, TimerNotificationReceiver::class.java).apply {
            action = ACTION_STOP_TIMER
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_STOP_TIMER,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop & Save",
            stopPendingIntent
        )

        return builder.build()
    }

    fun showVoiceCommandConfirmation(
        context: Context,
        title: String,
        message: String
    ) {
        val app = context.applicationContext
        createNotificationChannels(app)
        wakeScreenBriefly(app)

        val confirmActivityIntent = VoiceCommandConfirmActivity.intent(app, title, message)
        val fullScreenPending = PendingIntent.getActivity(
            app,
            NOTIFICATION_ID_VOICE_CONFIRM + 1,
            confirmActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(app, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_OPEN_VOICE_COMMANDS, true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            app,
            NOTIFICATION_ID_VOICE_CONFIRM,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(app, CHANNEL_VOICE_COMMANDS)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPending)
            .setFullScreenIntent(fullScreenPending, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 120, 60, 120))
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)

        val notificationManager =
            app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_VOICE_CONFIRM, builder.build())

        // Also launch directly — more reliable than full-screen intent alone on some OEMs
        // when a microphone FGS is already holding the process in the foreground.
        try {
            app.startActivity(confirmActivityIntent)
        } catch (_: Exception) {
        }
    }

    /** Turns the display on briefly so the caregiver sees the confirmation. */
    private fun wakeScreenBriefly(context: Context) {
        try {
            val power = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                ?: return
            @Suppress("DEPRECATION")
            val wakeLock = power.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    android.os.PowerManager.ON_AFTER_RELEASE,
                "bbylog:VoiceCommandConfirm"
            )
            wakeLock.setReferenceCounted(false)
            wakeLock.acquire(3_000L)
        } catch (_: Exception) {
        }
    }

    /**
     * WhatsApp-style stacked messaging notification. Tap opens Care Chat.
     */
    fun showPeerMessageNotification(
        context: Context,
        senderName: String,
        text: String,
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        val app = context.applicationContext
        createNotificationChannels(app)

        synchronized(peerChatLines) {
            peerChatLines.add(PeerChatLine(senderName, text, timestampMillis))
            while (peerChatLines.size > MAX_PEER_CHAT_LINES) {
                peerChatLines.removeAt(0)
            }
        }

        val openIntent = Intent(app, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_OPEN_PEER_CHAT, true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            app,
            NOTIFICATION_ID_PEER_CHAT,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val user = androidx.core.app.Person.Builder()
            .setName("You")
            .build()

        val style = NotificationCompat.MessagingStyle(user)
            .setConversationTitle("Care Sync")
        synchronized(peerChatLines) {
            for (line in peerChatLines) {
                val person = androidx.core.app.Person.Builder()
                    .setName(line.senderName)
                    .setImportant(true)
                    .build()
                style.addMessage(line.text, line.timestampMillis, person)
            }
        }

        val latest = peerChatLines.lastOrNull()
        val builder = NotificationCompat.Builder(app, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(latest?.senderName ?: senderName)
            .setContentText(latest?.text ?: text)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(contentPending)
            .setVibrate(longArrayOf(0, 180, 80, 180))

        val notificationManager =
            app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_PEER_CHAT, builder.build())
    }

    fun dismissPeerChatNotification(context: Context) {
        synchronized(peerChatLines) { peerChatLines.clear() }
        val notificationManager =
            context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_PEER_CHAT)
    }

    /**
     * Rings like a real phone alarm: alarm-stream ringtone + full-screen UI + heads-up.
     */
    fun launchPhoneAlarm(
        context: Context,
        title: String,
        message: String,
        reminderKind: String,
        notificationId: Int,
        medicineAlarmId: Long = 0L,
        playSound: Boolean = true
    ) {
        val app = context.applicationContext
        createNotificationChannels(app)
        val kind = normalizeKind(reminderKind)

        if (playSound) {
            AlarmSoundController.start(app)
        }

        val ringIntent = Intent(app, AlarmRingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_REMINDER_TYPE, kind)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_MEDICINE_ALARM_ID, medicineAlarmId)
            putExtra(EXTRA_CHANNEL_ID, CHANNEL_PHONE_ALARMS)
        }
        val fullScreenPending = PendingIntent.getActivity(
            app,
            notificationId + 50,
            ringIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val donePending = actionPendingIntent(
            context = app,
            action = ACTION_REMINDER_DONE,
            reminderType = kind,
            notificationId = notificationId,
            requestCode = notificationId + 100,
            medicineAlarmId = medicineAlarmId,
            title = title,
            message = message,
            channelId = CHANNEL_PHONE_ALARMS
        )
        val snoozePending = if (kind != "timer") {
            actionPendingIntent(
                context = app,
                action = ACTION_REMINDER_SNOOZE,
                reminderType = kind,
                notificationId = notificationId,
                requestCode = notificationId + 200,
                title = title,
                message = message,
                channelId = CHANNEL_PHONE_ALARMS,
                medicineAlarmId = medicineAlarmId
            )
        } else {
            null
        }

        val dismissLabel = if (kind == TYPE_MEDICINE) "Took it" else "Dismiss"
        val builder = NotificationCompat.Builder(app, CHANNEL_PHONE_ALARMS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(fullScreenPending)
            .setFullScreenIntent(fullScreenPending, true)
            .setVibrate(longArrayOf(0, 600, 400, 600))
            .addAction(0, dismissLabel, donePending)

        if (snoozePending != null) {
            builder.addAction(0, "Snooze 10m", snoozePending)
        }

        val notificationManager =
            app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())

        try {
            app.startActivity(ringIntent)
        } catch (_: Exception) {
            // Full-screen intent / notification still covers locked devices.
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

    /**
     * Soft care-check reminder: heads-up notification, no ringtone / full-screen ring.
     * Dismiss cancels any pending sibling alarm for the same kind.
     */
    fun showCareReminderNotification(
        context: Context,
        title: String,
        message: String,
        reminderKind: String,
        notificationId: Int
    ) {
        val app = context.applicationContext
        createNotificationChannels(app)
        val kind = normalizeKind(reminderKind)

        val openIntent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            app,
            notificationId + 10,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val donePending = actionPendingIntent(
            context = app,
            action = ACTION_REMINDER_DONE,
            reminderType = kind,
            notificationId = notificationId,
            requestCode = notificationId + 100,
            title = title,
            message = message,
            channelId = CHANNEL_REMINDERS
        )

        val builder = NotificationCompat.Builder(app, CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPending)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .addAction(0, "Dismiss", donePending)

        val notificationManager =
            app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

    fun showStickyCareReminder(
        context: Context,
        title: String,
        message: String,
        reminderType: String,
        notificationId: Int,
        channelId: String = CHANNEL_PHONE_ALARMS
    ) {
        launchPhoneAlarm(
            context = context,
            title = title,
            message = message,
            reminderKind = reminderType,
            notificationId = notificationId,
            playSound = true
        )
    }

    fun showStickyMedicineReminder(
        context: Context,
        title: String,
        message: String,
        notificationId: Int,
        channelId: String = CHANNEL_PHONE_ALARMS,
        medicineAlarmId: Long = 0L
    ) {
        launchPhoneAlarm(
            context = context,
            title = title,
            message = message,
            reminderKind = TYPE_MEDICINE,
            notificationId = notificationId,
            medicineAlarmId = medicineAlarmId,
            playSound = true
        )
    }

    fun cancelStickyReminder(context: Context, notificationId: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    fun cancelAllStickyReminders(context: Context) {
        cancelStickyReminder(context, NOTIFICATION_ID_FEED)
        cancelStickyReminder(context, NOTIFICATION_ID_SLEEP)
        cancelStickyReminder(context, NOTIFICATION_ID_DIAPER)
        cancelStickyReminder(context, NOTIFICATION_ID_BABY_CHECK)
        cancelStickyReminder(context, NOTIFICATION_ID_FEED_REMINDER)
        cancelStickyReminder(context, NOTIFICATION_ID_DIAPER_REMINDER)
        cancelStickyReminder(context, NOTIFICATION_ID_BABY_CHECK_REMINDER)
        for (i in 1..64) {
            cancelStickyReminder(context, NOTIFICATION_MEDICINE_BASE + i)
        }
        cancelStickyReminder(context, 2105) // legacy medicine sticky
    }

    fun scheduleTimerCompleteAlarm(
        context: Context,
        delaySeconds: Long,
        activityName: String,
        babyName: String
    ) {
        val settings = CareCheckSettings(systemAlarmsEnabled = true, notificationsEnabled = true)
        val triggerTime = System.currentTimeMillis() + (delaySeconds * 1000)
        AlarmScheduler.schedule(
            context = context,
            triggerAtMillis = triggerTime,
            requestCode = REQUEST_TIMER,
            title = "⏰ $activityName Timer Done",
            message = "$activityName session for $babyName has ended!",
            reminderKind = "timer",
            notificationId = REQUEST_TIMER,
            channelId = CHANNEL_TIMERS,
            settings = settings,
            sticky = false
        )
    }

    fun scheduleSnooze(
        context: Context,
        reminderType: String,
        title: String,
        message: String,
        channelId: String = CHANNEL_REMINDERS,
        settings: CareCheckSettings,
        medicineAlarmId: Long = 0L
    ) {
        val kind = normalizeKind(reminderType)
        val requestCode = snoozeRequestCode(kind, medicineAlarmId) ?: return
        val notificationId = if (kind == TYPE_MEDICINE && medicineAlarmId > 0L) {
            medicineNotificationId(medicineAlarmId)
        } else {
            notificationIdForType(kind)
        }
        AlarmScheduler.schedule(
            context = context,
            triggerAtMillis = System.currentTimeMillis() + SNOOZE_MS,
            requestCode = requestCode,
            title = title,
            message = message,
            reminderKind = kind,
            notificationId = notificationId,
            channelId = channelId,
            settings = settings,
            medicineAlarmId = medicineAlarmId
        )
    }

    fun notificationIdForType(reminderType: String): Int = when (normalizeKind(reminderType)) {
        TYPE_FEED -> NOTIFICATION_ID_FEED
        TYPE_SLEEP -> NOTIFICATION_ID_SLEEP
        TYPE_DIAPER -> NOTIFICATION_ID_DIAPER
        TYPE_BABY_CHECK -> NOTIFICATION_ID_BABY_CHECK
        else -> NOTIFICATION_ID_FEED
    }

    fun kindFromType(reminderType: String): ReminderKind? = when (normalizeKind(reminderType)) {
        TYPE_FEED -> ReminderKind.FEED
        TYPE_SLEEP -> ReminderKind.SLEEP
        TYPE_DIAPER -> ReminderKind.DIAPER
        TYPE_BABY_CHECK -> ReminderKind.BABY_CHECK
        TYPE_MEDICINE -> ReminderKind.MEDICINE
        else -> null
    }

    private fun actionPendingIntent(
        context: Context,
        action: String,
        reminderType: String,
        notificationId: Int,
        requestCode: Int,
        title: String? = null,
        message: String? = null,
        channelId: String? = null,
        medicineAlarmId: Long = 0L
    ): PendingIntent {
        val intent = Intent(context, BabyAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_REMINDER_TYPE, reminderType)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_MEDICINE_ALARM_ID, medicineAlarmId)
            if (title != null) putExtra(EXTRA_TITLE, title)
            if (message != null) putExtra(EXTRA_MESSAGE, message)
            if (channelId != null) putExtra(EXTRA_CHANNEL_ID, channelId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
