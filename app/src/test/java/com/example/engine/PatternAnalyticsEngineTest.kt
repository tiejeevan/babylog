package com.example.engine

import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.model.BabyProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class PatternAnalyticsEngineTest {

    private val fixedNow: Long = Calendar.getInstance().apply {
        set(2026, Calendar.JULY, 25, 15, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val profile = BabyProfile(
        name = "Test Baby",
        targetFeedingIntervalMinutes = 180,
        targetNapIntervalMinutes = 150
    )

    @Test
    fun emptyLogs_notEnoughData() {
        val report = PatternAnalyticsEngine.analyze(emptyList(), profile, 7, fixedNow)
        assertFalse(report.hasEnoughData)
        assertEquals(0, report.distinctActiveDays)
        assertTrue(report.insights.isEmpty())
        assertNull(report.highlightInsight)
        assertEquals(7, report.dailyFeeds.size)
        assertEquals(24, report.hourBins.size)
    }

    @Test
    fun computeIntervals_requiresTwoEvents() {
        assertNull(PatternAnalyticsEngine.computeIntervals(listOf(1000L), 180))
        assertNull(PatternAnalyticsEngine.computeIntervals(emptyList(), 180))
    }

    @Test
    fun computeIntervals_averagesGaps() {
        val t0 = fixedNow - TimeUnit.HOURS.toMillis(6)
        val t1 = t0 + TimeUnit.HOURS.toMillis(3)
        val t2 = t1 + TimeUnit.HOURS.toMillis(3)
        val stats = PatternAnalyticsEngine.computeIntervals(listOf(t0, t1, t2), 180)
        assertNotNull(stats)
        assertEquals(180.0, stats!!.averageMinutes, 0.01)
        assertEquals(2, stats.sampleCount)
        assertEquals(0.0, stats.deltaFromTargetMinutes, 0.01)
    }

    @Test
    fun computeIntervals_filtersOutliers() {
        val t0 = fixedNow
        val t1 = t0 + TimeUnit.MINUTES.toMillis(5) // too short
        val t2 = t1 + TimeUnit.HOURS.toMillis(3)
        val stats = PatternAnalyticsEngine.computeIntervals(listOf(t0, t1, t2), 180)
        assertNotNull(stats)
        assertEquals(1, stats!!.sampleCount)
        assertEquals(180.0, stats.averageMinutes, 0.01)
    }

    @Test
    fun nightDaySplit_overnightSleep() {
        val start = Calendar.getInstance().apply {
            timeInMillis = fixedNow
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = start + TimeUnit.HOURS.toMillis(8) // 22:00 → 06:00 all night
        val (night, day) = PatternAnalyticsEngine.splitNightDaySleepMinutes(start, end)
        assertEquals(480L, night)
        assertEquals(0L, day)
    }

    @Test
    fun nightDaySplit_daytimeNap() {
        val start = Calendar.getInstance().apply {
            timeInMillis = fixedNow
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = start + TimeUnit.HOURS.toMillis(1)
        val (night, day) = PatternAnalyticsEngine.splitNightDaySleepMinutes(start, end)
        assertEquals(0L, night)
        assertEquals(60L, day)
    }

    @Test
    fun hourBins_countFeedsByHour() {
        val day0 = PatternAnalyticsEngine.startOfDayMillis(fixedNow) - TimeUnit.DAYS.toMillis(2)
        val day1 = day0 + TimeUnit.DAYS.toMillis(1)
        val day2 = day0 + TimeUnit.DAYS.toMillis(2)

        fun atHour(dayStart: Long, hour: Int): Long =
            Calendar.getInstance().apply {
                timeInMillis = dayStart
                set(Calendar.HOUR_OF_DAY, hour)
            }.timeInMillis

        val logs = listOf(
            feed(atHour(day0, 8)),
            feed(atHour(day1, 8)),
            feed(atHour(day2, 8)),
            diaper(atHour(day0, 9)),
            sleep(atHour(day1, 14), minutes = 60)
        )

        val report = PatternAnalyticsEngine.analyze(logs, profile, 7, fixedNow)
        assertTrue(report.hasEnoughData)
        assertEquals(3, report.hourBins[8].feedCount)
        assertEquals(1, report.hourBins[9].diaperCount)
        assertEquals(1, report.hourBins[14].sleepCount)
        assertEquals(3, report.distinctActiveDays)
    }

    @Test
    fun breastBalance_andInsights() {
        val day0 = PatternAnalyticsEngine.startOfDayMillis(fixedNow) - TimeUnit.DAYS.toMillis(3)
        val logs = (0..3).flatMap { dayOffset ->
            val day = day0 + TimeUnit.DAYS.toMillis(dayOffset.toLong())
            listOf(
                breastFeed(day + TimeUnit.HOURS.toMillis(8), leftSec = 600, rightSec = 120),
                sleep(day + TimeUnit.HOURS.toMillis(22), minutes = 120),
                diaper(day + TimeUnit.HOURS.toMillis(10))
            )
        }
        val report = PatternAnalyticsEngine.analyze(logs, profile, 7, fixedNow)
        assertTrue(report.hasEnoughData)
        assertTrue(report.breastBalance.leftPercent > report.breastBalance.rightPercent)
        assertTrue(report.insights.any { it.category == "feeding" })
        assertNotNull(report.highlightInsight)
        assertTrue(report.nightSleepMinutes > 0)
    }

    @Test
    fun softDeletedLogs_excluded() {
        val day0 = PatternAnalyticsEngine.startOfDayMillis(fixedNow) - TimeUnit.DAYS.toMillis(2)
        val logs = listOf(
            feed(day0 + TimeUnit.HOURS.toMillis(8)).copy(isDeleted = true),
            feed(day0 + TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(8)),
            feed(day0 + TimeUnit.DAYS.toMillis(2) + TimeUnit.HOURS.toMillis(8))
        )
        val report = PatternAnalyticsEngine.analyze(logs, profile, 7, fixedNow)
        assertEquals(2, report.distinctActiveDays)
        assertFalse(report.hasEnoughData)
    }

    @Test
    fun toAiContextSummary_includesRange() {
        val day0 = PatternAnalyticsEngine.startOfDayMillis(fixedNow) - TimeUnit.DAYS.toMillis(3)
        val logs = (0..3).map { offset ->
            feed(day0 + TimeUnit.DAYS.toMillis(offset.toLong()) + TimeUnit.HOURS.toMillis(8))
        }
        val report = PatternAnalyticsEngine.analyze(logs, profile, 14, fixedNow)
        val summary = report.toAiContextSummary()
        assertTrue(summary.contains("14d"))
        assertTrue(summary.contains("feeds"))
    }

    private fun feed(start: Long) = ActivityLog(
        activityType = ActivityTypes.BOTTLE,
        startTimeMillis = start,
        endTimeMillis = start + TimeUnit.MINUTES.toMillis(15),
        volumeMl = 120,
        timestampMillis = start
    )

    private fun breastFeed(start: Long, leftSec: Long, rightSec: Long) = ActivityLog(
        activityType = ActivityTypes.BREASTFEEDING,
        startTimeMillis = start,
        endTimeMillis = start + TimeUnit.SECONDS.toMillis(leftSec + rightSec),
        leftBreastDurationSec = leftSec,
        rightBreastDurationSec = rightSec,
        timestampMillis = start
    )

    private fun diaper(start: Long) = ActivityLog(
        activityType = ActivityTypes.DIAPER,
        startTimeMillis = start,
        diaperStatus = "Wet",
        timestampMillis = start
    )

    private fun sleep(start: Long, minutes: Long) = ActivityLog(
        activityType = ActivityTypes.SLEEP,
        startTimeMillis = start,
        endTimeMillis = start + TimeUnit.MINUTES.toMillis(minutes),
        durationSeconds = minutes * 60,
        timestampMillis = start
    )
}
