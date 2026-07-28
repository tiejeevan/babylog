package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.database.BabyCareDatabase
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.model.MedicineSubjects
import com.example.data.repository.BabyCareRepository
import com.example.engine.ActiveAlarmTracker
import com.example.engine.ReminderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BabyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appContext = context.applicationContext

        when (action) {
            BabyNotificationManager.ACTION_BABY_ALARM -> {
                val title = intent.getStringExtra(BabyNotificationManager.EXTRA_TITLE)
                    ?: "BabyCare Alarm"
                val message = intent.getStringExtra(BabyNotificationManager.EXTRA_MESSAGE)
                    ?: "Time to check on your baby's routine."
                val reminderType = BabyNotificationManager.normalizeKind(
                    intent.getStringExtra(BabyNotificationManager.EXTRA_REMINDER_TYPE)
                        ?: BabyNotificationManager.TYPE_FEED
                )
                val medicineAlarmId = intent.getLongExtra(
                    BabyNotificationManager.EXTRA_MEDICINE_ALARM_ID,
                    0L
                )
                val deliveryMode = intent.getStringExtra(BabyNotificationManager.EXTRA_DELIVERY_MODE)
                    ?: BabyNotificationManager.DELIVERY_ALARM
                val pendingAlarmAt = intent.getLongExtra(
                    BabyNotificationManager.EXTRA_PENDING_ALARM_AT,
                    0L
                )
                val notificationId = intent.getIntExtra(
                    BabyNotificationManager.EXTRA_NOTIFICATION_ID,
                    if (reminderType == BabyNotificationManager.TYPE_MEDICINE && medicineAlarmId > 0L) {
                        BabyNotificationManager.medicineNotificationId(medicineAlarmId)
                    } else if (deliveryMode == BabyNotificationManager.DELIVERY_REMINDER) {
                        BabyNotificationManager.reminderNotificationId(reminderType)
                            ?: BabyNotificationManager.notificationIdForType(reminderType)
                    } else {
                        BabyNotificationManager.notificationIdForType(reminderType)
                    }
                )

                if (reminderType == "timer") {
                    BabyNotificationManager.showSystemNotification(
                        context = appContext,
                        title = title,
                        message = message,
                        channelId = BabyNotificationManager.CHANNEL_TIMERS,
                        notificationId = notificationId
                    )
                    return
                }

                if (deliveryMode == BabyNotificationManager.DELIVERY_REMINDER) {
                    // Soft reminder: do not cancel sibling alarm; do not rescheduleAll.
                    if (pendingAlarmAt > System.currentTimeMillis()) {
                        ActiveAlarmTracker.markReminderShown(
                            appContext,
                            ActiveAlarmTracker.kindKey(reminderType),
                            pendingAlarmAt
                        )
                    }
                    BabyNotificationManager.showCareReminderNotification(
                        context = appContext,
                        title = title,
                        message = message,
                        reminderKind = reminderType,
                        notificationId = notificationId
                    )
                    return
                }

                val kindKey = ActiveAlarmTracker.kindKey(reminderType, medicineAlarmId)
                ActiveAlarmTracker.markActive(appContext, kindKey)
                ActiveAlarmTracker.clearReminderWindow(appContext, kindKey)
                BabyNotificationManager.reminderNotificationId(reminderType)?.let {
                    BabyNotificationManager.cancelStickyReminder(appContext, it)
                }

                BabyNotificationManager.launchPhoneAlarm(
                    context = appContext,
                    title = title,
                    message = message,
                    reminderKind = reminderType,
                    notificationId = notificationId,
                    medicineAlarmId = medicineAlarmId,
                    playSound = true
                )

                // Arm only future next occurrences; active kind is skipped.
                rescheduleAllAsync(appContext)
            }

            BabyNotificationManager.ACTION_REMINDER_DONE -> {
                val notificationId = intent.getIntExtra(
                    BabyNotificationManager.EXTRA_NOTIFICATION_ID,
                    -1
                )
                val reminderType = BabyNotificationManager.normalizeKind(
                    intent.getStringExtra(BabyNotificationManager.EXTRA_REMINDER_TYPE).orEmpty()
                )
                val medicineAlarmId = intent.getLongExtra(
                    BabyNotificationManager.EXTRA_MEDICINE_ALARM_ID,
                    0L
                )
                AlarmSoundController.stop()
                if (notificationId >= 0) {
                    BabyNotificationManager.cancelStickyReminder(appContext, notificationId)
                }
                // Dismissing soft reminder (or alarm) cancels sibling pending alarm.
                ReminderEngine.cancelCareDelivery(appContext, reminderType)
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val dao = BabyCareDatabase.getDatabase(appContext).babyCareDao()
                        val repository = BabyCareRepository(dao)
                        if (reminderType == BabyNotificationManager.TYPE_MEDICINE &&
                            medicineAlarmId > 0L
                        ) {
                            val alarm = repository.getMedicineAlarmById(medicineAlarmId)
                            if (alarm != null && alarm.subject == MedicineSubjects.BABY) {
                                val now = System.currentTimeMillis()
                                repository.insertLog(
                                    ActivityLog(
                                        activityType = ActivityTypes.MEDICINE,
                                        startTimeMillis = now,
                                        endTimeMillis = now,
                                        medicineName = alarm.name,
                                        dosage = alarm.doseNote.ifBlank { null },
                                        notes = "Logged from medicine reminder"
                                    )
                                )
                            }
                        }
                        ReminderEngine.acknowledgeAndReschedule(
                            context = appContext,
                            repository = repository,
                            reminderKind = reminderType,
                            medicineAlarmId = medicineAlarmId
                        )
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            BabyNotificationManager.ACTION_REMINDER_SNOOZE -> {
                val reminderType = BabyNotificationManager.normalizeKind(
                    intent.getStringExtra(BabyNotificationManager.EXTRA_REMINDER_TYPE) ?: return
                )

                val notificationId = intent.getIntExtra(
                    BabyNotificationManager.EXTRA_NOTIFICATION_ID,
                    BabyNotificationManager.notificationIdForType(reminderType)
                )
                val title = intent.getStringExtra(BabyNotificationManager.EXTRA_TITLE)
                    ?: "BabyCare Reminder"
                val message = intent.getStringExtra(BabyNotificationManager.EXTRA_MESSAGE)
                    ?: "Snoozed care reminder."
                val channelId = intent.getStringExtra(BabyNotificationManager.EXTRA_CHANNEL_ID)
                    ?: BabyNotificationManager.CHANNEL_PHONE_ALARMS
                val medicineAlarmId = intent.getLongExtra(
                    BabyNotificationManager.EXTRA_MEDICINE_ALARM_ID,
                    0L
                )

                AlarmSoundController.stop()
                // Keep active so overdue re-arm does not defeat the snooze window.
                BabyNotificationManager.cancelStickyReminder(appContext, notificationId)
                BabyNotificationManager.reminderNotificationId(reminderType)?.let {
                    BabyNotificationManager.cancelStickyReminder(appContext, it)
                }
                ActiveAlarmTracker.clearReminderWindow(
                    appContext,
                    ActiveAlarmTracker.kindKey(reminderType, medicineAlarmId)
                )
                BabyNotificationManager.reminderRequestCode(reminderType)?.let {
                    AlarmScheduler.cancel(appContext, it)
                }

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val dao = BabyCareDatabase.getDatabase(appContext).babyCareDao()
                        val repository = BabyCareRepository(dao)
                        val settings = ReminderEngine.ensureMigrated(appContext, repository)
                        if (settings.deliveryEnabled()) {
                            BabyNotificationManager.scheduleSnooze(
                                context = appContext,
                                reminderType = reminderType,
                                title = title,
                                message = message,
                                channelId = channelId,
                                settings = settings,
                                medicineAlarmId = medicineAlarmId
                            )
                        }
                        ReminderEngine.rescheduleAll(appContext, repository)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private fun rescheduleAllAsync(context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = BabyCareDatabase.getDatabase(context).babyCareDao()
                val repository = BabyCareRepository(dao)
                ReminderEngine.rescheduleAll(context, repository)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
