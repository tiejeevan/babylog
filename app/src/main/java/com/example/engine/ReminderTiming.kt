package com.example.engine

import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.model.BabyProfile
import com.example.data.model.CareCheckSettings
import com.example.data.model.MedicineAlarm

enum class ReminderKind {
    FEED,
    DIAPER,
    SLEEP,
    BABY_CHECK,
    MEDICINE
}

/** Due time plus optional soft-reminder / ringing-alarm slots. */
data class CareCheckDeliveryTimes(
    val dueAtMillis: Long?,
    val reminderAtMillis: Long?,
    val alarmAtMillis: Long?
)

data class CareCheckTriggers(
    val feed: CareCheckDeliveryTimes,
    val diaper: CareCheckDeliveryTimes,
    val babyCheck: CareCheckDeliveryTimes,
    /** Dashboard / Need Engine only — never scheduled from Reminders UI. */
    val sleepAtMillis: Long?
) {
    val feedAtMillis: Long? get() = feed.dueAtMillis
    val diaperAtMillis: Long? get() = diaper.dueAtMillis
    val babyCheckAtMillis: Long? get() = babyCheck.dueAtMillis
}

/**
 * Pure timing math for care checks and medicine interval grids.
 */
object ReminderTiming {
    /** Minimum lead time before we arm AlarmManager (avoids immediate re-fire races). */
    const val MIN_SCHEDULE_LEAD_MS = 5_000L

    /** Soft reminder at T, ringing alarm at T + this when both channels are on. */
    const val REMINDER_TO_ALARM_DELAY_MS = 30_000L

    const val APP_DIAPER_INTERVAL_MINUTES = 180
    const val APP_BABY_CHECK_INTERVAL_MINUTES = 120

    /**
     * Next fire on a repeating grid anchored at [pilotMillis].
     * Returns [pilotMillis] only when it is still strictly in the future.
     * When [nowMillis] is at or past a slot, returns the following slot.
     * Returns 0 if pilot unset or interval invalid.
     */
    fun nextGridTriggerMillis(
        pilotMillis: Long,
        intervalMinutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        if (pilotMillis <= 0L) return 0L
        val intervalMs = intervalMinutes.coerceAtLeast(1) * 60_000L
        if (nowMillis < pilotMillis) return pilotMillis
        val elapsed = nowMillis - pilotMillis
        val steps = (elapsed / intervalMs) + 1
        return pilotMillis + steps * intervalMs
    }

    /**
     * Returns [triggerAtMillis] only when it is safely in the future.
     * Overdue triggers return 0 — do not re-arm every minute.
     */
    fun scheduleableTrigger(triggerAtMillis: Long, nowMillis: Long): Long {
        if (triggerAtMillis <= 0L) return 0L
        return if (triggerAtMillis > nowMillis + MIN_SCHEDULE_LEAD_MS) {
            triggerAtMillis
        } else {
            0L
        }
    }

    /** Next occurrence after the user dismisses an overdue care check. */
    fun nextAfterAcknowledge(intervalMinutes: Int, nowMillis: Long): Long =
        nowMillis + intervalMinutes.coerceAtLeast(1) * 60_000L

    @Deprecated("Use scheduleableTrigger — overdue catch-up caused repeat rings")
    fun coerceTrigger(triggerAtMillis: Long, nowMillis: Long): Long =
        scheduleableTrigger(triggerAtMillis, nowMillis)

    /** Next whole hour after [nowMillis] (UTC epoch alignment; fine for relative grids). */
    fun nextRoundHourMillis(nowMillis: Long): Long {
        val hourMs = 3_600_000L
        return ((nowMillis / hourMs) + 1) * hourMs
    }

    fun feedIntervalMinutes(profile: BabyProfile?, settings: CareCheckSettings): Int =
        if (settings.feedUseAppTiming) {
            (profile?.targetFeedingIntervalMinutes ?: 180).coerceAtLeast(1)
        } else {
            settings.feedCustomIntervalMinutes.coerceAtLeast(1)
        }

    fun diaperIntervalMinutes(settings: CareCheckSettings): Int =
        if (settings.diaperUseAppTiming) {
            APP_DIAPER_INTERVAL_MINUTES
        } else {
            settings.diaperIntervalMinutes.coerceAtLeast(1)
        }

    fun babyCheckIntervalMinutes(settings: CareCheckSettings): Int =
        if (settings.babyCheckUseAppTiming) {
            APP_BABY_CHECK_INTERVAL_MINUTES
        } else {
            settings.babyCheckIntervalMinutes.coerceAtLeast(1)
        }

    /**
     * Splits a due time into reminder / alarm slots.
     * Both on → reminder at T, alarm at T+30s.
     * Alarm only → alarm at T.
     * Reminder only → reminder at T.
     */
    fun splitDeliveryTimes(
        dueAtMillis: Long?,
        reminderEnabled: Boolean,
        alarmEnabled: Boolean
    ): CareCheckDeliveryTimes {
        if (dueAtMillis == null || dueAtMillis <= 0L || (!reminderEnabled && !alarmEnabled)) {
            return CareCheckDeliveryTimes(null, null, null)
        }
        val reminderAt = if (reminderEnabled) dueAtMillis else null
        val alarmAt = when {
            !alarmEnabled -> null
            reminderEnabled -> dueAtMillis + REMINDER_TO_ALARM_DELAY_MS
            else -> dueAtMillis
        }
        return CareCheckDeliveryTimes(
            dueAtMillis = dueAtMillis,
            reminderAtMillis = reminderAt,
            alarmAtMillis = alarmAt
        )
    }

    fun computeCareCheckTriggers(
        profile: BabyProfile?,
        settings: CareCheckSettings,
        logs: List<ActivityLog>,
        nowMillis: Long = System.currentTimeMillis()
    ): CareCheckTriggers {
        val completedLogs = logs.filter {
            !it.isDeleted && (
                it.endTimeMillis != null ||
                    it.activityType == ActivityTypes.DIAPER ||
                    it.activityType == ActivityTypes.MEDICINE ||
                    it.activityType == ActivityTypes.TEMPERATURE
                )
        }.sortedByDescending { it.endTimeMillis ?: it.startTimeMillis }

        val lastFeed = completedLogs.firstOrNull {
            it.activityType == ActivityTypes.BREASTFEEDING || it.activityType == ActivityTypes.BOTTLE
        }
        val lastDiaper = completedLogs.firstOrNull { it.activityType == ActivityTypes.DIAPER }
        val lastSleep = completedLogs.firstOrNull { it.activityType == ActivityTypes.SLEEP }

        val feedIntervalMs = feedIntervalMinutes(profile, settings) * 60_000L
        val diaperIntervalMs = diaperIntervalMinutes(settings) * 60_000L
        val napIntervalMs =
            (profile?.targetNapIntervalMinutes ?: 150).coerceAtLeast(1) * 60_000L
        val babyIntervalMins = babyCheckIntervalMinutes(settings)

        val feedDue = if (!settings.feedActive()) {
            null
        } else if (lastFeed != null) {
            (lastFeed.endTimeMillis ?: lastFeed.startTimeMillis) + feedIntervalMs
        } else {
            nowMillis + feedIntervalMs
        }

        val diaperDue = if (!settings.diaperActive()) {
            null
        } else if (lastDiaper != null) {
            lastDiaper.startTimeMillis + diaperIntervalMs
        } else {
            nowMillis + diaperIntervalMs
        }

        val sleepAt = if (!settings.sleepEnabled) {
            null
        } else if (lastSleep != null) {
            (lastSleep.endTimeMillis ?: lastSleep.startTimeMillis) + napIntervalMs
        } else {
            nowMillis + napIntervalMs
        }

        val babyDue = if (!settings.babyCheckActive()) {
            null
        } else {
            val pilot = when {
                settings.babyCheckPilotMillis > 0L -> settings.babyCheckPilotMillis
                settings.babyCheckUseAppTiming -> nextRoundHourMillis(nowMillis)
                else -> 0L
            }
            if (pilot <= 0L) {
                null
            } else {
                nextGridTriggerMillis(
                    pilotMillis = pilot,
                    intervalMinutes = babyIntervalMins,
                    nowMillis = nowMillis
                ).takeIf { it > 0L }
            }
        }

        return CareCheckTriggers(
            feed = splitDeliveryTimes(
                feedDue,
                settings.feedReminderEnabled,
                settings.feedAlarmEnabled
            ),
            diaper = splitDeliveryTimes(
                diaperDue,
                settings.diaperReminderEnabled,
                settings.diaperAlarmEnabled
            ),
            babyCheck = splitDeliveryTimes(
                babyDue,
                settings.babyCheckReminderEnabled,
                settings.babyCheckAlarmEnabled
            ),
            sleepAtMillis = sleepAt
        )
    }

    fun nextMedicineTrigger(
        alarm: MedicineAlarm,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        if (!alarm.enabled || alarm.pilotTimeMillis <= 0L) return 0L
        return nextGridTriggerMillis(
            pilotMillis = alarm.pilotTimeMillis,
            intervalMinutes = alarm.intervalMinutes,
            nowMillis = nowMillis
        )
    }

    fun shouldRescheduleForActivity(activityType: String): Boolean =
        activityType == ActivityTypes.BREASTFEEDING ||
            activityType == ActivityTypes.BOTTLE ||
            activityType == ActivityTypes.SLEEP ||
            activityType == ActivityTypes.DIAPER
}
