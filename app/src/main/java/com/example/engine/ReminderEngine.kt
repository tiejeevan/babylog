package com.example.engine

import android.content.Context
import com.example.data.model.ActivityLog
import com.example.data.model.BabyProfile
import com.example.data.model.CareCheckSettings
import com.example.data.model.MedicineAlarm
import com.example.data.model.MedicineSubjects
import com.example.data.repository.BabyCareRepository
import com.example.notification.AlarmScheduler
import com.example.notification.AlarmSoundController
import com.example.notification.BabyNotificationManager

/**
 * Single orchestrator for care-check and medicine alarms.
 *
 * - Arms AlarmManager only for future triggers (no overdue +60s spam).
 * - Care checks: soft reminder at T and/or ringing alarm at T or T+30s.
 * - Active ringing kinds are tracked and not re-armed until Dismiss/Snooze.
 * - Soft-reminder window preserves the pending sibling alarm across reschedules.
 */
object ReminderEngine {
    private const val PREFS_LEGACY = "reminder_prefs"
    private const val KEY_LEGACY_MIGRATED = "care_check_settings_migrated_v8"
    private const val KEY_NOTIFICATIONS = "reminder_notifications_enabled"
    private const val KEY_ALARMS = "system_alarms_enabled"
    private const val KEY_MEDICINE_ENABLED = "medicine_reminder_enabled"
    private const val KEY_MEDICINE_PILOT = "medicine_pilot_time_millis"

    suspend fun ensureMigrated(context: Context, repository: BabyCareRepository): CareCheckSettings {
        val app = context.applicationContext
        val legacy = app.getSharedPreferences(PREFS_LEGACY, Context.MODE_PRIVATE)
        val already = legacy.getBoolean(KEY_LEGACY_MIGRATED, false)

        var settings = repository.getCareCheckSettingsDirect()
        if (!already) {
            val notifications = legacy.getBoolean(KEY_NOTIFICATIONS, true)
            val systemAlarms = legacy.getBoolean(KEY_ALARMS, true)
            settings = repository.saveCareCheckSettings(
                settings.copy(
                    notificationsEnabled = notifications,
                    systemAlarmsEnabled = systemAlarms,
                    sleepEnabled = false
                )
            )

            val medicineEnabled = legacy.getBoolean(KEY_MEDICINE_ENABLED, false)
            val pilot = legacy.getLong(KEY_MEDICINE_PILOT, 0L)
            if (medicineEnabled && pilot > 0L) {
                val existing = repository.getMedicineAlarmsDirect()
                if (existing.isEmpty()) {
                    repository.upsertMedicineAlarm(
                        MedicineAlarm(
                            subject = MedicineSubjects.MOM,
                            name = "Tylenol + Motrin",
                            doseNote = "Tylenol (3) and Motrin (1).",
                            intervalMinutes = 360,
                            pilotTimeMillis = pilot,
                            enabled = true
                        )
                    )
                }
            }
            legacy.edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply()
        }
        if (settings.sleepEnabled) {
            settings = repository.saveCareCheckSettings(settings.copy(sleepEnabled = false))
        }
        return settings
    }

    suspend fun rescheduleAll(
        context: Context,
        repository: BabyCareRepository,
        nowMillis: Long = System.currentTimeMillis(),
        triggerOverrides: Map<String, Long> = emptyMap()
    ) {
        val app = context.applicationContext
        val settings = ensureMigrated(app, repository)
        val profile = repository.getBabyProfileDirect()
        val logs = repository.getRecentLogs(100)
        val medicines = repository.getEnabledMedicineAlarms()
        rescheduleAll(
            context = app,
            profile = profile,
            settings = settings,
            logs = logs,
            medicines = medicines,
            nowMillis = nowMillis,
            triggerOverrides = triggerOverrides
        )
    }

    fun rescheduleAll(
        context: Context,
        profile: BabyProfile?,
        settings: CareCheckSettings,
        logs: List<ActivityLog>,
        medicines: List<MedicineAlarm>,
        nowMillis: Long = System.currentTimeMillis(),
        triggerOverrides: Map<String, Long> = emptyMap()
    ) {
        val app = context.applicationContext
        cancelAllOwnedAlarms(app, medicines.map { it.id }, preservePendingCareAlarms = true)

        if (!settings.deliveryEnabled()) {
            BabyNotificationManager.cancelAllStickyReminders(app)
            ActiveAlarmTracker.clearAll(app)
            AlarmSoundController.stop()
            return
        }

        val babyName = profile?.name?.ifBlank { "Your Baby" } ?: "Your Baby"
        val triggers = ReminderTiming.computeCareCheckTriggers(
            profile = profile,
            settings = settings,
            logs = logs,
            nowMillis = nowMillis
        )

        maybeScheduleCareDual(
            app = app,
            settings = settings,
            kind = BabyNotificationManager.TYPE_FEED,
            delivery = applyDueOverride(
                triggers.feed,
                triggerOverrides[BabyNotificationManager.TYPE_FEED],
                settings.feedReminderEnabled,
                settings.feedAlarmEnabled
            ),
            nowMillis = nowMillis,
            title = "Feeding check for $babyName",
            message = "Target feeding window reached. Time to nurse or bottle-feed.",
            allowOverdueFire = triggerOverrides[BabyNotificationManager.TYPE_FEED] == null
        )
        maybeScheduleCareDual(
            app = app,
            settings = settings,
            kind = BabyNotificationManager.TYPE_DIAPER,
            delivery = applyDueOverride(
                triggers.diaper,
                triggerOverrides[BabyNotificationManager.TYPE_DIAPER],
                settings.diaperReminderEnabled,
                settings.diaperAlarmEnabled
            ),
            nowMillis = nowMillis,
            title = "Diaper check for $babyName",
            message = "Time to check the diaper.",
            allowOverdueFire = triggerOverrides[BabyNotificationManager.TYPE_DIAPER] == null
        )
        maybeScheduleCareDual(
            app = app,
            settings = settings,
            kind = BabyNotificationManager.TYPE_BABY_CHECK,
            delivery = applyDueOverride(
                triggers.babyCheck,
                triggerOverrides[BabyNotificationManager.TYPE_BABY_CHECK],
                settings.babyCheckReminderEnabled,
                settings.babyCheckAlarmEnabled
            ),
            nowMillis = nowMillis,
            title = "Baby check for $babyName",
            message = "Time for a quick wellness check on $babyName.",
            allowOverdueFire = triggerOverrides[BabyNotificationManager.TYPE_BABY_CHECK] == null
        )

        // Cancel any leftover sleep schedules from older builds.
        AlarmScheduler.cancel(app, BabyNotificationManager.REQUEST_SLEEP)

        for (alarm in medicines) {
            if (!alarm.enabled) continue
            val kindKey = ActiveAlarmTracker.kindKey(
                BabyNotificationManager.TYPE_MEDICINE,
                alarm.id
            )
            if (ActiveAlarmTracker.isActive(app, kindKey)) continue

            val overrideKey = "MEDICINE_${alarm.id}"
            val raw = triggerOverrides[overrideKey]
                ?: ReminderTiming.nextMedicineTrigger(alarm, nowMillis)
            val at = ReminderTiming.scheduleableTrigger(raw, nowMillis)
            if (at <= 0L) {
                if (triggerOverrides[overrideKey] == null &&
                    (settings.notificationsEnabled || settings.systemAlarmsEnabled)
                ) {
                    fireOverdueAlarm(
                        app = app,
                        settings = settings,
                        kind = BabyNotificationManager.TYPE_MEDICINE,
                        notificationId = BabyNotificationManager.medicineNotificationId(alarm.id),
                        title = medicineTitle(alarm),
                        message = alarm.doseNote.ifBlank {
                            "Time for the next dose of ${alarm.name}."
                        },
                        medicineAlarmId = alarm.id
                    )
                }
                continue
            }
            AlarmScheduler.schedule(
                context = app,
                triggerAtMillis = at,
                requestCode = BabyNotificationManager.medicineRequestCode(alarm.id),
                title = medicineTitle(alarm),
                message = alarm.doseNote.ifBlank { "Time for the next dose of ${alarm.name}." },
                reminderKind = BabyNotificationManager.TYPE_MEDICINE,
                notificationId = BabyNotificationManager.medicineNotificationId(alarm.id),
                channelId = BabyNotificationManager.CHANNEL_PHONE_ALARMS,
                settings = settings,
                medicineAlarmId = alarm.id,
                deliveryMode = BabyNotificationManager.DELIVERY_ALARM
            )
        }
    }

    /**
     * After Dismiss: clear active flag and arm a clean next window for that kind.
     */
    suspend fun acknowledgeAndReschedule(
        context: Context,
        repository: BabyCareRepository,
        reminderKind: String,
        medicineAlarmId: Long = 0L,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val app = context.applicationContext
        val kind = BabyNotificationManager.normalizeKind(reminderKind)
        val kindKey = ActiveAlarmTracker.kindKey(kind, medicineAlarmId)
        cancelCareDelivery(app, kind)
        ActiveAlarmTracker.clear(app, kindKey)
        cancelCareSnooze(app, kind, medicineAlarmId)

        val settings = ensureMigrated(app, repository)
        val profile = repository.getBabyProfileDirect()
        val overrides = mutableMapOf<String, Long>()

        when (kind) {
            BabyNotificationManager.TYPE_FEED -> {
                overrides[kind] = ReminderTiming.nextAfterAcknowledge(
                    ReminderTiming.feedIntervalMinutes(profile, settings),
                    nowMillis
                )
            }
            BabyNotificationManager.TYPE_DIAPER -> {
                overrides[kind] = ReminderTiming.nextAfterAcknowledge(
                    ReminderTiming.diaperIntervalMinutes(settings),
                    nowMillis
                )
            }
            BabyNotificationManager.TYPE_BABY_CHECK -> {
                overrides[kind] = ReminderTiming.nextAfterAcknowledge(
                    ReminderTiming.babyCheckIntervalMinutes(settings),
                    nowMillis
                )
            }
            BabyNotificationManager.TYPE_MEDICINE -> {
                val alarm = repository.getMedicineAlarmById(medicineAlarmId)
                if (alarm != null) {
                    repository.upsertMedicineAlarm(
                        alarm.copy(
                            pilotTimeMillis = nowMillis,
                            updatedAtMillis = nowMillis
                        )
                    )
                    overrides["MEDICINE_${alarm.id}"] =
                        ReminderTiming.nextAfterAcknowledge(alarm.intervalMinutes, nowMillis)
                }
            }
        }

        rescheduleAll(app, repository, nowMillis, overrides)
    }

    fun cancelCareDelivery(context: Context, kind: String) {
        val app = context.applicationContext
        val normalized = BabyNotificationManager.normalizeKind(kind)
        BabyNotificationManager.reminderRequestCode(normalized)?.let { AlarmScheduler.cancel(app, it) }
        BabyNotificationManager.alarmRequestCode(normalized)?.let { AlarmScheduler.cancel(app, it) }
        BabyNotificationManager.reminderNotificationId(normalized)?.let {
            BabyNotificationManager.cancelStickyReminder(app, it)
        }
        BabyNotificationManager.cancelStickyReminder(
            app,
            BabyNotificationManager.notificationIdForType(normalized)
        )
        ActiveAlarmTracker.clearReminderWindow(app, ActiveAlarmTracker.kindKey(normalized))
    }

    private fun applyDueOverride(
        delivery: CareCheckDeliveryTimes,
        overrideDue: Long?,
        reminderEnabled: Boolean,
        alarmEnabled: Boolean
    ): CareCheckDeliveryTimes {
        if (overrideDue == null) return delivery
        return ReminderTiming.splitDeliveryTimes(overrideDue, reminderEnabled, alarmEnabled)
    }

    private fun maybeScheduleCareDual(
        app: Context,
        settings: CareCheckSettings,
        kind: String,
        delivery: CareCheckDeliveryTimes,
        nowMillis: Long,
        title: String,
        message: String,
        allowOverdueFire: Boolean
    ) {
        val kindKey = ActiveAlarmTracker.kindKey(kind)
        if (ActiveAlarmTracker.isActive(app, kindKey)) return

        // Soft reminder already shown — keep only the pending sibling alarm.
        if (ActiveAlarmTracker.isReminderShown(app, kindKey)) {
            val pending = ActiveAlarmTracker.pendingAlarmAt(app, kindKey)
            val at = ReminderTiming.scheduleableTrigger(pending, nowMillis)
            if (at > 0L) {
                val alarmRequest = BabyNotificationManager.alarmRequestCode(kind) ?: return
                val alarmNotif = BabyNotificationManager.notificationIdForType(kind)
                AlarmScheduler.schedule(
                    context = app,
                    triggerAtMillis = at,
                    requestCode = alarmRequest,
                    title = title,
                    message = message,
                    reminderKind = kind,
                    notificationId = alarmNotif,
                    channelId = BabyNotificationManager.CHANNEL_PHONE_ALARMS,
                    settings = settings,
                    deliveryMode = BabyNotificationManager.DELIVERY_ALARM
                )
            }
            return
        }

        val reminderRaw = delivery.reminderAtMillis
        val alarmRaw = delivery.alarmAtMillis
        if (reminderRaw == null && alarmRaw == null) return

        val reminderAt = reminderRaw?.let { ReminderTiming.scheduleableTrigger(it, nowMillis) } ?: 0L
        val alarmAt = alarmRaw?.let { ReminderTiming.scheduleableTrigger(it, nowMillis) } ?: 0L

        if (reminderAt <= 0L && alarmAt <= 0L) {
            if (allowOverdueFire && settings.deliveryEnabled()) {
                when {
                    delivery.alarmAtMillis != null -> {
                        fireOverdueAlarm(
                            app, settings, kind,
                            BabyNotificationManager.notificationIdForType(kind),
                            title, message
                        )
                    }
                    delivery.reminderAtMillis != null -> {
                        fireOverdueReminder(app, kind, title, message)
                    }
                }
            }
            return
        }

        if (reminderAt > 0L) {
            val reminderRequest = BabyNotificationManager.reminderRequestCode(kind) ?: return
            val reminderNotif = BabyNotificationManager.reminderNotificationId(kind) ?: return
            AlarmScheduler.schedule(
                context = app,
                triggerAtMillis = reminderAt,
                requestCode = reminderRequest,
                title = title,
                message = message,
                reminderKind = kind,
                notificationId = reminderNotif,
                channelId = BabyNotificationManager.CHANNEL_REMINDERS,
                settings = settings,
                deliveryMode = BabyNotificationManager.DELIVERY_REMINDER,
                pendingAlarmAtMillis = alarmRaw ?: 0L
            )
        }

        if (alarmAt > 0L) {
            val alarmRequest = BabyNotificationManager.alarmRequestCode(kind) ?: return
            val alarmNotif = BabyNotificationManager.notificationIdForType(kind)
            AlarmScheduler.schedule(
                context = app,
                triggerAtMillis = alarmAt,
                requestCode = alarmRequest,
                title = title,
                message = message,
                reminderKind = kind,
                notificationId = alarmNotif,
                channelId = BabyNotificationManager.CHANNEL_PHONE_ALARMS,
                settings = settings,
                deliveryMode = BabyNotificationManager.DELIVERY_ALARM
            )
        }
    }

    private fun fireOverdueReminder(
        app: Context,
        kind: String,
        title: String,
        message: String
    ) {
        val notifId = BabyNotificationManager.reminderNotificationId(kind) ?: return
        BabyNotificationManager.showCareReminderNotification(
            context = app,
            title = title,
            message = message,
            reminderKind = kind,
            notificationId = notifId
        )
    }

    private fun fireOverdueAlarm(
        app: Context,
        settings: CareCheckSettings,
        kind: String,
        notificationId: Int,
        title: String,
        message: String,
        medicineAlarmId: Long = 0L
    ) {
        val kindKey = ActiveAlarmTracker.kindKey(kind, medicineAlarmId)
        if (ActiveAlarmTracker.isActive(app, kindKey)) return
        ActiveAlarmTracker.markActive(app, kindKey)
        ActiveAlarmTracker.clearReminderWindow(app, kindKey)
        BabyNotificationManager.reminderNotificationId(kind)?.let {
            BabyNotificationManager.cancelStickyReminder(app, it)
        }
        BabyNotificationManager.launchPhoneAlarm(
            context = app,
            title = title,
            message = message,
            reminderKind = kind,
            notificationId = notificationId,
            medicineAlarmId = medicineAlarmId,
            playSound = settings.notificationsEnabled || settings.systemAlarmsEnabled
        )
    }

    private fun medicineTitle(alarm: MedicineAlarm): String {
        val subjectLabel = if (alarm.subject == MedicineSubjects.MOM) "Mom" else "Baby"
        return "$subjectLabel · ${alarm.name}"
    }

    private fun cancelAllOwnedAlarms(
        context: Context,
        medicineIds: List<Long>,
        preservePendingCareAlarms: Boolean
    ) {
        val preserve = if (preservePendingCareAlarms) {
            listOf(
                BabyNotificationManager.TYPE_FEED,
                BabyNotificationManager.TYPE_DIAPER,
                BabyNotificationManager.TYPE_BABY_CHECK
            ).filter {
                ActiveAlarmTracker.isReminderShown(context, ActiveAlarmTracker.kindKey(it))
            }.mapNotNull { BabyNotificationManager.alarmRequestCode(it) }.toSet()
        } else {
            emptySet()
        }

        fun cancelUnlessPreserved(code: Int) {
            if (code !in preserve) AlarmScheduler.cancel(context, code)
        }

        cancelUnlessPreserved(BabyNotificationManager.REQUEST_FEED)
        cancelUnlessPreserved(BabyNotificationManager.REQUEST_DIAPER)
        cancelUnlessPreserved(BabyNotificationManager.REQUEST_BABY_CHECK)
        AlarmScheduler.cancel(context, BabyNotificationManager.REQUEST_FEED_REMINDER)
        AlarmScheduler.cancel(context, BabyNotificationManager.REQUEST_DIAPER_REMINDER)
        AlarmScheduler.cancel(context, BabyNotificationManager.REQUEST_BABY_CHECK_REMINDER)
        AlarmScheduler.cancel(context, BabyNotificationManager.REQUEST_SLEEP)
        AlarmScheduler.cancel(context, BabyNotificationManager.REQUEST_NAP_LEGACY)
        for (id in medicineIds) {
            AlarmScheduler.cancel(context, BabyNotificationManager.medicineRequestCode(id))
        }
        for (i in 1..64) {
            AlarmScheduler.cancel(context, BabyNotificationManager.REQUEST_MEDICINE_BASE + i)
        }
        AlarmScheduler.cancel(context, BabyNotificationManager.REQUEST_MEDICINE_LEGACY)
    }

    fun cancelCareSnooze(
        context: Context,
        reminderKind: String,
        medicineAlarmId: Long = 0L
    ) {
        val code = BabyNotificationManager.snoozeRequestCode(reminderKind, medicineAlarmId)
            ?: return
        AlarmScheduler.cancel(context, code)
    }

    fun cancelAllCareSnoozes(context: Context) {
        AlarmScheduler.cancel(context, BabyNotificationManager.REQUEST_SNOOZE_FEED)
        AlarmScheduler.cancel(context, BabyNotificationManager.REQUEST_SNOOZE_SLEEP)
        AlarmScheduler.cancel(context, BabyNotificationManager.REQUEST_SNOOZE_DIAPER)
        AlarmScheduler.cancel(context, BabyNotificationManager.REQUEST_SNOOZE_BABY_CHECK)
        for (i in 1..64) {
            AlarmScheduler.cancel(
                context,
                BabyNotificationManager.REQUEST_SNOOZE_MEDICINE_BASE + i
            )
        }
    }
}
