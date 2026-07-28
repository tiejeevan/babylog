package com.example.engine

import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.model.BabyProfile
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

enum class PatternRangeDays(val days: Int) {
    SEVEN(7),
    FOURTEEN(14),
    THIRTY(30)
}

data class DailyFeedPoint(
    val dayStartMillis: Long,
    val feedCount: Int,
    val volumeMl: Int
)

data class DailySleepPoint(
    val dayStartMillis: Long,
    val sleepMinutes: Long,
    val napCount: Int
)

data class DailyDiaperPoint(
    val dayStartMillis: Long,
    val wetCount: Int,
    val dirtyCount: Int,
    val totalCount: Int
)

data class HourBin(
    val hour: Int,
    val feedCount: Int,
    val sleepCount: Int,
    val diaperCount: Int
) {
    val totalCount: Int get() = feedCount + sleepCount + diaperCount
}

data class IntervalStats(
    val averageMinutes: Double,
    val sampleCount: Int,
    val targetMinutes: Int,
    val deltaFromTargetMinutes: Double
)

data class BreastSideBalance(
    val leftSeconds: Long,
    val rightSeconds: Long
) {
    val totalSeconds: Long get() = leftSeconds + rightSeconds
    val leftPercent: Int
        get() = if (totalSeconds <= 0) 50 else ((leftSeconds * 100.0) / totalSeconds).roundToInt()
    val rightPercent: Int
        get() = if (totalSeconds <= 0) 50 else 100 - leftPercent
}

data class PatternInsight(
    val title: String,
    val detail: String,
    val category: String
)

data class PatternReport(
    val rangeDays: Int,
    val rangeStartMillis: Long,
    val rangeEndMillis: Long,
    val distinctActiveDays: Int,
    val hasEnoughData: Boolean,
    val dailyFeeds: List<DailyFeedPoint>,
    val dailySleep: List<DailySleepPoint>,
    val dailyDiapers: List<DailyDiaperPoint>,
    val hourBins: List<HourBin>,
    val feedInterval: IntervalStats?,
    val napInterval: IntervalStats?,
    val nightSleepMinutes: Long,
    val daySleepMinutes: Long,
    val breastBalance: BreastSideBalance,
    val insights: List<PatternInsight>,
    val highlightInsight: PatternInsight?
) {
    val nightSleepPercent: Int
        get() {
            val total = nightSleepMinutes + daySleepMinutes
            return if (total <= 0) 0 else ((nightSleepMinutes * 100.0) / total).roundToInt()
        }

    fun toAiContextSummary(): String {
        if (!hasEnoughData) {
            return "Not enough logged days yet for reliable pattern analysis (need ~3 days)."
        }
        val feedAvg = if (dailyFeeds.isNotEmpty()) {
            dailyFeeds.map { it.feedCount }.average().let { "%.1f".format(it) }
        } else "0"
        val sleepAvg = if (dailySleep.isNotEmpty()) {
            dailySleep.map { it.sleepMinutes }.average().let { "%.0f".format(it) }
        } else "0"
        val parts = mutableListOf(
            "Last ${rangeDays}d: avg $feedAvg feeds/day, avg ${sleepAvg}m sleep/day, night sleep ~${nightSleepPercent}% of total."
        )
        feedInterval?.let {
            parts += "Avg feed interval ${it.averageMinutes.roundToInt()}m (target ${it.targetMinutes}m)."
        }
        napInterval?.let {
            parts += "Avg awake/nap interval ${it.averageMinutes.roundToInt()}m (target ${it.targetMinutes}m)."
        }
        if (breastBalance.totalSeconds > 0) {
            parts += "Breast side balance L${breastBalance.leftPercent}% / R${breastBalance.rightPercent}%."
        }
        insights.take(3).forEach { parts += "${it.title}: ${it.detail}" }
        return parts.joinToString(" ")
    }
}

object PatternAnalyticsEngine {

    private const val MIN_ACTIVE_DAYS = 3
    private const val NIGHT_START_HOUR = 20
    private const val NIGHT_END_HOUR = 6

    fun analyze(
        logs: List<ActivityLog>,
        profile: BabyProfile?,
        rangeDays: Int,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): PatternReport {
        val endMillis = currentTimeMillis
        val startOfToday = startOfDayMillis(currentTimeMillis)
        val rangeStart = startOfToday - TimeUnit.DAYS.toMillis((rangeDays - 1).toLong())

        val activeLogs = logs.filter { !it.isDeleted && it.startTimeMillis in rangeStart until endMillis }

        val dayStarts = (0 until rangeDays).map { offset ->
            rangeStart + TimeUnit.DAYS.toMillis(offset.toLong())
        }

        val dailyFeeds = dayStarts.map { dayStart ->
            val dayEnd = dayStart + TimeUnit.DAYS.toMillis(1)
            val dayLogs = activeLogs.filter { it.startTimeMillis in dayStart until dayEnd && isFeed(it) }
            DailyFeedPoint(
                dayStartMillis = dayStart,
                feedCount = dayLogs.size,
                volumeMl = dayLogs.sumOf { it.volumeMl }
            )
        }

        val dailySleep = dayStarts.map { dayStart ->
            val dayEnd = dayStart + TimeUnit.DAYS.toMillis(1)
            val dayLogs = activeLogs.filter {
                it.activityType == ActivityTypes.SLEEP && it.startTimeMillis in dayStart until dayEnd
            }
            DailySleepPoint(
                dayStartMillis = dayStart,
                sleepMinutes = dayLogs.sumOf { sleepDurationMinutes(it, currentTimeMillis) },
                napCount = dayLogs.size
            )
        }

        val dailyDiapers = dayStarts.map { dayStart ->
            val dayEnd = dayStart + TimeUnit.DAYS.toMillis(1)
            val dayLogs = activeLogs.filter {
                it.activityType == ActivityTypes.DIAPER && it.startTimeMillis in dayStart until dayEnd
            }
            var wet = 0
            var dirty = 0
            dayLogs.forEach { log ->
                when (log.diaperStatus) {
                    "Wet" -> wet++
                    "Dirty" -> dirty++
                    "Both", "Wet & Dirty" -> {
                        wet++
                        dirty++
                    }
                    else -> wet++
                }
            }
            DailyDiaperPoint(
                dayStartMillis = dayStart,
                wetCount = wet,
                dirtyCount = dirty,
                totalCount = dayLogs.size
            )
        }

        val hourBins = (0..23).map { hour ->
            var feeds = 0
            var sleeps = 0
            var diapers = 0
            activeLogs.forEach { log ->
                val h = hourOfDay(log.startTimeMillis)
                if (h != hour) return@forEach
                when {
                    isFeed(log) -> feeds++
                    log.activityType == ActivityTypes.SLEEP -> sleeps++
                    log.activityType == ActivityTypes.DIAPER -> diapers++
                }
            }
            HourBin(hour = hour, feedCount = feeds, sleepCount = sleeps, diaperCount = diapers)
        }

        val feedInterval = computeIntervals(
            events = activeLogs.filter { isFeed(it) }
                .map { it.endTimeMillis ?: it.startTimeMillis }
                .sorted(),
            targetMinutes = profile?.targetFeedingIntervalMinutes ?: 180
        )

        val napInterval = computeIntervals(
            events = activeLogs.filter { it.activityType == ActivityTypes.SLEEP }
                .map { it.endTimeMillis ?: it.startTimeMillis }
                .sorted(),
            targetMinutes = profile?.targetNapIntervalMinutes ?: 150
        )

        var nightSleep = 0L
        var daySleep = 0L
        activeLogs.filter { it.activityType == ActivityTypes.SLEEP }.forEach { log ->
            val end = log.endTimeMillis ?: currentTimeMillis
            val (night, day) = splitNightDaySleepMinutes(log.startTimeMillis, end)
            nightSleep += night
            daySleep += day
        }

        val breastBalance = BreastSideBalance(
            leftSeconds = activeLogs.filter { it.activityType == ActivityTypes.BREASTFEEDING }
                .sumOf { it.leftBreastDurationSec },
            rightSeconds = activeLogs.filter { it.activityType == ActivityTypes.BREASTFEEDING }
                .sumOf { it.rightBreastDurationSec }
        )

        val distinctActiveDays = dayStarts.count { dayStart ->
            val dayEnd = dayStart + TimeUnit.DAYS.toMillis(1)
            activeLogs.any { it.startTimeMillis in dayStart until dayEnd }
        }

        val hasEnoughData = distinctActiveDays >= MIN_ACTIVE_DAYS

        val insights = if (hasEnoughData) {
            buildInsights(
                rangeDays = rangeDays,
                dailyFeeds = dailyFeeds,
                dailySleep = dailySleep,
                dailyDiapers = dailyDiapers,
                hourBins = hourBins,
                feedInterval = feedInterval,
                napInterval = napInterval,
                nightSleepMinutes = nightSleep,
                daySleepMinutes = daySleep,
                breastBalance = breastBalance
            )
        } else {
            emptyList()
        }

        return PatternReport(
            rangeDays = rangeDays,
            rangeStartMillis = rangeStart,
            rangeEndMillis = endMillis,
            distinctActiveDays = distinctActiveDays,
            hasEnoughData = hasEnoughData,
            dailyFeeds = dailyFeeds,
            dailySleep = dailySleep,
            dailyDiapers = dailyDiapers,
            hourBins = hourBins,
            feedInterval = feedInterval,
            napInterval = napInterval,
            nightSleepMinutes = nightSleep,
            daySleepMinutes = daySleep,
            breastBalance = breastBalance,
            insights = insights,
            highlightInsight = insights.firstOrNull()
        )
    }

    fun analyze(
        logs: List<ActivityLog>,
        profile: BabyProfile?,
        range: PatternRangeDays,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): PatternReport = analyze(logs, profile, range.days, currentTimeMillis)

    internal fun sleepDurationMinutes(log: ActivityLog, nowMillis: Long): Long {
        if (log.durationSeconds > 0 && log.endTimeMillis != null) {
            return TimeUnit.SECONDS.toMinutes(log.durationSeconds)
        }
        val end = log.endTimeMillis ?: nowMillis
        return TimeUnit.MILLISECONDS.toMinutes((end - log.startTimeMillis).coerceAtLeast(0))
    }

    internal fun computeIntervals(events: List<Long>, targetMinutes: Int): IntervalStats? {
        if (events.size < 2) return null
        val gaps = events.zipWithNext { a, b -> TimeUnit.MILLISECONDS.toMinutes(b - a).toDouble() }
            .filter { it in 15.0..720.0 }
        if (gaps.isEmpty()) return null
        val avg = gaps.average()
        return IntervalStats(
            averageMinutes = avg,
            sampleCount = gaps.size,
            targetMinutes = targetMinutes,
            deltaFromTargetMinutes = avg - targetMinutes
        )
    }

    /**
     * Splits [start, end) sleep into minutes overlapping night (20:00–06:00 local) vs day.
     */
    internal fun splitNightDaySleepMinutes(startMillis: Long, endMillis: Long): Pair<Long, Long> {
        if (endMillis <= startMillis) return 0L to 0L
        var night = 0L
        var day = 0L
        var cursor = startMillis
        while (cursor < endMillis) {
            val cal = Calendar.getInstance().apply { timeInMillis = cursor }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val isNight = hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR

            // Advance to next hour boundary or end
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.HOUR_OF_DAY, 1)
            val nextBoundary = cal.timeInMillis.coerceAtMost(endMillis)
            val chunkMinutes = TimeUnit.MILLISECONDS.toMinutes(nextBoundary - cursor)
            if (isNight) night += chunkMinutes else day += chunkMinutes
            cursor = nextBoundary
        }
        return night to day
    }

    internal fun startOfDayMillis(timeMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun hourOfDay(timeMillis: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMillis
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    private fun isFeed(log: ActivityLog): Boolean =
        log.activityType == ActivityTypes.BOTTLE || log.activityType == ActivityTypes.BREASTFEEDING

    private fun buildInsights(
        rangeDays: Int,
        dailyFeeds: List<DailyFeedPoint>,
        dailySleep: List<DailySleepPoint>,
        dailyDiapers: List<DailyDiaperPoint>,
        hourBins: List<HourBin>,
        feedInterval: IntervalStats?,
        napInterval: IntervalStats?,
        nightSleepMinutes: Long,
        daySleepMinutes: Long,
        breastBalance: BreastSideBalance
    ): List<PatternInsight> {
        val insights = mutableListOf<PatternInsight>()

        val peakFeedHours = hourBins
            .filter { it.feedCount > 0 }
            .sortedByDescending { it.feedCount }
            .take(3)
        if (peakFeedHours.isNotEmpty()) {
            val top = peakFeedHours.first()
            val cluster = peakFeedHours.map { formatHourRange(it.hour) }.distinct().take(2)
            insights += PatternInsight(
                title = "Feeds cluster around ${formatHourRange(top.hour)}",
                detail = "Most feeds in the last ${rangeDays}d fall near ${cluster.joinToString(" & ")}.",
                category = "feeding"
            )
        }

        val totalSleep = nightSleepMinutes + daySleepMinutes
        if (totalSleep > 0) {
            val nightPct = ((nightSleepMinutes * 100.0) / totalSleep).roundToInt()
            insights += PatternInsight(
                title = "Night sleep is ~$nightPct% of total",
                detail = "${formatMinutes(nightSleepMinutes)} overnight (8pm–6am) vs ${formatMinutes(daySleepMinutes)} daytime naps.",
                category = "sleep"
            )
        }

        feedInterval?.let { stats ->
            val delta = stats.deltaFromTargetMinutes.roundToInt()
            val comparison = when {
                abs(delta) <= 15 -> "right on target"
                delta > 0 -> "${abs(delta)}m longer than target"
                else -> "${abs(delta)}m shorter than target"
            }
            insights += PatternInsight(
                title = "Feed interval averages ${stats.averageMinutes.roundToInt()}m",
                detail = "Across ${stats.sampleCount} gaps, spacing is $comparison (${stats.targetMinutes}m target).",
                category = "feeding"
            )
        }

        napInterval?.let { stats ->
            insights += PatternInsight(
                title = "Awake windows average ${stats.averageMinutes.roundToInt()}m",
                detail = "Target nap interval is ${stats.targetMinutes}m (${stats.sampleCount} sleep gaps).",
                category = "sleep"
            )
        }

        if (breastBalance.totalSeconds >= 300) {
            val imbalance = abs(breastBalance.leftPercent - breastBalance.rightPercent)
            if (imbalance >= 20) {
                val heavier = if (breastBalance.leftPercent > breastBalance.rightPercent) "left" else "right"
                insights += PatternInsight(
                    title = "Breast side leans $heavier",
                    detail = "L${breastBalance.leftPercent}% / R${breastBalance.rightPercent}% over the last ${rangeDays}d.",
                    category = "feeding"
                )
            } else {
                insights += PatternInsight(
                    title = "Breast sides are balanced",
                    detail = "L${breastBalance.leftPercent}% / R${breastBalance.rightPercent}% over the last ${rangeDays}d.",
                    category = "feeding"
                )
            }
        }

        val avgFeeds = dailyFeeds.map { it.feedCount }.average()
        val avgSleep = dailySleep.map { it.sleepMinutes }.average()
        val avgDiapers = dailyDiapers.map { it.totalCount }.average()
        insights += PatternInsight(
            title = "Typical day snapshot",
            detail = "~${"%.1f".format(avgFeeds)} feeds, ~${avgSleep.roundToInt()}m sleep, ~${"%.1f".format(avgDiapers)} diaper changes per day.",
            category = "overview"
        )

        return insights.take(6)
    }

    private fun formatHourRange(hour: Int): String {
        val end = (hour + 1) % 24
        fun label(h: Int): String = when {
            h == 0 -> "12am"
            h < 12 -> "${h}am"
            h == 12 -> "12pm"
            else -> "${h - 12}pm"
        }
        return "${label(hour)}–${label(end)}"
    }

    private fun formatMinutes(minutes: Long): String {
        if (minutes < 0) return "--"
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }
}
