package com.example.engine

import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.model.BabyProfile
import com.example.data.model.CareCheckSettings
import com.example.data.model.MedicineAlarm
import com.example.data.model.MedicineSubjects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTimingTest {

    private val intervalMinutes = 360
    private val intervalMs = intervalMinutes * 60_000L

    @Test
    fun nextGrid_returnsPilotWhenStillInFuture() {
        val pilot = 1_000_000L
        val now = pilot - 60_000L
        assertEquals(pilot, ReminderTiming.nextGridTriggerMillis(pilot, intervalMinutes, now))
    }

    @Test
    fun nextGrid_advancesWhenExactlyOnPilot() {
        val pilot = 1_000_000L
        assertEquals(
            pilot + intervalMs,
            ReminderTiming.nextGridTriggerMillis(pilot, intervalMinutes, pilot)
        )
    }

    @Test
    fun nextGrid_advancesOneStepWhenJustPastPilot() {
        val pilot = 1_000_000L
        val now = pilot + 1L
        assertEquals(
            pilot + intervalMs,
            ReminderTiming.nextGridTriggerMillis(pilot, intervalMinutes, now)
        )
    }

    @Test
    fun nextGrid_advancesFromMidInterval() {
        val pilot = 1_000_000L
        val now = pilot + intervalMs / 2
        assertEquals(
            pilot + intervalMs,
            ReminderTiming.nextGridTriggerMillis(pilot, intervalMinutes, now)
        )
    }

    @Test
    fun nextGrid_advancesPastExactSlot() {
        val pilot = 1_000_000L
        val slot = pilot + 2 * intervalMs
        val now = slot + 1L
        assertEquals(
            pilot + 3 * intervalMs,
            ReminderTiming.nextGridTriggerMillis(pilot, intervalMinutes, now)
        )
    }

    @Test
    fun nextGrid_returnsZeroForUnsetPilot() {
        assertEquals(0L, ReminderTiming.nextGridTriggerMillis(0L, intervalMinutes, 5_000L))
        assertEquals(0L, ReminderTiming.nextGridTriggerMillis(-1L, intervalMinutes, 5_000L))
    }

    @Test
    fun scheduleableTrigger_skipsOverdue() {
        val now = 10_000_000L
        assertEquals(0L, ReminderTiming.scheduleableTrigger(now - 5_000L, now))
        assertEquals(0L, ReminderTiming.scheduleableTrigger(now, now))
        assertEquals(
            now + 120_000L,
            ReminderTiming.scheduleableTrigger(now + 120_000L, now)
        )
        assertEquals(0L, ReminderTiming.scheduleableTrigger(0L, now))
    }

    @Test
    fun nextAfterAcknowledge_addsInterval() {
        val now = 1_000_000L
        assertEquals(now + 180 * 60_000L, ReminderTiming.nextAfterAcknowledge(180, now))
    }

    @Test
    fun careChecks_activityBasedFromLastLog_appTiming() {
        val now = 5_000_000L
        val profile = BabyProfile(
            targetFeedingIntervalMinutes = 180,
            targetNapIntervalMinutes = 150
        )
        val settings = CareCheckSettings(
            diaperIntervalMinutes = 120,
            diaperUseAppTiming = true,
            feedUseAppTiming = true,
            babyCheckReminderEnabled = false,
            babyCheckAlarmEnabled = false,
            sleepEnabled = false
        )
        val logs = listOf(
            ActivityLog(
                activityType = ActivityTypes.BOTTLE,
                startTimeMillis = now - 60_000L,
                endTimeMillis = now - 30_000L
            ),
            ActivityLog(
                activityType = ActivityTypes.DIAPER,
                startTimeMillis = now - 10_000L
            ),
            ActivityLog(
                activityType = ActivityTypes.SLEEP,
                startTimeMillis = now - 200_000L,
                endTimeMillis = now - 100_000L
            )
        )
        val triggers = ReminderTiming.computeCareCheckTriggers(profile, settings, logs, now)
        assertEquals(now - 30_000L + 180 * 60_000L, triggers.feedAtMillis)
        // App diaper uses fixed 180m, not custom 120
        assertEquals(
            now - 10_000L + ReminderTiming.APP_DIAPER_INTERVAL_MINUTES * 60_000L,
            triggers.diaperAtMillis
        )
        assertNull(triggers.sleepAtMillis)
        assertNull(triggers.babyCheckAtMillis)
        assertEquals(triggers.feedAtMillis, triggers.feed.reminderAtMillis)
        assertEquals(
            triggers.feedAtMillis!! + ReminderTiming.REMINDER_TO_ALARM_DELAY_MS,
            triggers.feed.alarmAtMillis
        )
    }

    @Test
    fun careChecks_customFeedAndDiaperIntervals() {
        val now = 5_000_000L
        val profile = BabyProfile(targetFeedingIntervalMinutes = 180)
        val settings = CareCheckSettings(
            feedUseAppTiming = false,
            feedCustomIntervalMinutes = 90,
            diaperUseAppTiming = false,
            diaperIntervalMinutes = 120,
            babyCheckReminderEnabled = false,
            babyCheckAlarmEnabled = false
        )
        val logs = listOf(
            ActivityLog(
                activityType = ActivityTypes.BOTTLE,
                startTimeMillis = now - 60_000L,
                endTimeMillis = now - 30_000L
            ),
            ActivityLog(
                activityType = ActivityTypes.DIAPER,
                startTimeMillis = now - 10_000L
            )
        )
        val triggers = ReminderTiming.computeCareCheckTriggers(profile, settings, logs, now)
        assertEquals(now - 30_000L + 90 * 60_000L, triggers.feedAtMillis)
        assertEquals(now - 10_000L + 120 * 60_000L, triggers.diaperAtMillis)
    }

    @Test
    fun careChecks_disabledKindsAreNull() {
        val now = 1_000_000L
        val triggers = ReminderTiming.computeCareCheckTriggers(
            profile = BabyProfile(),
            settings = CareCheckSettings(
                feedReminderEnabled = false,
                feedAlarmEnabled = false,
                diaperReminderEnabled = false,
                diaperAlarmEnabled = false,
                sleepEnabled = false,
                babyCheckReminderEnabled = false,
                babyCheckAlarmEnabled = false
            ),
            logs = emptyList(),
            nowMillis = now
        )
        assertNull(triggers.feedAtMillis)
        assertNull(triggers.diaperAtMillis)
        assertNull(triggers.sleepAtMillis)
        assertNull(triggers.babyCheckAtMillis)
    }

    @Test
    fun careChecks_sleepAlwaysNullWhenDisabled() {
        val now = 2_000_000L
        val triggers = ReminderTiming.computeCareCheckTriggers(
            profile = BabyProfile(targetNapIntervalMinutes = 150),
            settings = CareCheckSettings(sleepEnabled = false),
            logs = listOf(
                ActivityLog(
                    activityType = ActivityTypes.SLEEP,
                    startTimeMillis = now - 200_000L,
                    endTimeMillis = now - 100_000L
                )
            ),
            nowMillis = now
        )
        assertNull(triggers.sleepAtMillis)
    }

    @Test
    fun careChecks_babyCheckUsesGrid() {
        val pilot = 2_000_000L
        val now = pilot + 1L
        val settings = CareCheckSettings(
            feedReminderEnabled = false,
            feedAlarmEnabled = false,
            diaperReminderEnabled = false,
            diaperAlarmEnabled = false,
            sleepEnabled = false,
            babyCheckReminderEnabled = true,
            babyCheckAlarmEnabled = true,
            babyCheckUseAppTiming = false,
            babyCheckIntervalMinutes = 120,
            babyCheckPilotMillis = pilot
        )
        val triggers = ReminderTiming.computeCareCheckTriggers(
            profile = BabyProfile(),
            settings = settings,
            logs = emptyList(),
            nowMillis = now
        )
        val due = pilot + 120 * 60_000L
        assertEquals(due, triggers.babyCheckAtMillis)
        assertEquals(due, triggers.babyCheck.reminderAtMillis)
        assertEquals(due + ReminderTiming.REMINDER_TO_ALARM_DELAY_MS, triggers.babyCheck.alarmAtMillis)
    }

    @Test
    fun splitDelivery_reminderOnlyAlarmOnlyBoth() {
        val due = 10_000_000L
        val both = ReminderTiming.splitDeliveryTimes(due, reminderEnabled = true, alarmEnabled = true)
        assertEquals(due, both.reminderAtMillis)
        assertEquals(due + ReminderTiming.REMINDER_TO_ALARM_DELAY_MS, both.alarmAtMillis)

        val reminderOnly = ReminderTiming.splitDeliveryTimes(due, true, false)
        assertEquals(due, reminderOnly.reminderAtMillis)
        assertNull(reminderOnly.alarmAtMillis)

        val alarmOnly = ReminderTiming.splitDeliveryTimes(due, false, true)
        assertNull(alarmOnly.reminderAtMillis)
        assertEquals(due, alarmOnly.alarmAtMillis)

        val neither = ReminderTiming.splitDeliveryTimes(due, false, false)
        assertNull(neither.dueAtMillis)
        assertNull(neither.reminderAtMillis)
        assertNull(neither.alarmAtMillis)
    }

    @Test
    fun medicineTrigger_respectsEnabledAndPilot() {
        val pilot = 3_000_000L
        val alarm = MedicineAlarm(
            subject = MedicineSubjects.MOM,
            name = "Ibuprofen",
            intervalMinutes = 360,
            pilotTimeMillis = pilot,
            enabled = true
        )
        assertEquals(pilot, ReminderTiming.nextMedicineTrigger(alarm, pilot - 1))
        assertEquals(0L, ReminderTiming.nextMedicineTrigger(alarm.copy(enabled = false), pilot - 1))
        assertEquals(0L, ReminderTiming.nextMedicineTrigger(alarm.copy(pilotTimeMillis = 0), pilot))
    }

    @Test
    fun shouldRescheduleForActivity_onlyCareKinds() {
        assertTrue(ReminderTiming.shouldRescheduleForActivity(ActivityTypes.BOTTLE))
        assertTrue(ReminderTiming.shouldRescheduleForActivity(ActivityTypes.DIAPER))
        assertTrue(ReminderTiming.shouldRescheduleForActivity(ActivityTypes.SLEEP))
        assertFalse(ReminderTiming.shouldRescheduleForActivity(ActivityTypes.MEDICINE))
        assertFalse(ReminderTiming.shouldRescheduleForActivity(ActivityTypes.PUMPING))
    }

    @Test
    fun careCheckSettings_deliveryEnabled() {
        assertTrue(CareCheckSettings().deliveryEnabled())
        assertFalse(
            CareCheckSettings(notificationsEnabled = false, systemAlarmsEnabled = false)
                .deliveryEnabled()
        )
        assertNotNull(CareCheckSettings(id = 1))
        assertTrue(CareCheckSettings().feedActive())
        assertFalse(
            CareCheckSettings(feedReminderEnabled = false, feedAlarmEnabled = false).feedActive()
        )
    }
}
