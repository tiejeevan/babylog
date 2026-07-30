package com.example.engine

import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.model.BabyProfile
import com.example.data.model.CareCheckSettings
import java.util.concurrent.TimeUnit

enum class UrgencyLevel {
    HIGH_URGENT,
    MEDIUM_RECOMMENDED,
    LOW_ALL_GOOD
}

data class SmartSleepGapPrompt(
    val estimatedNapStartMillis: Long,
    val minutesSinceLastActivity: Long,
    val suggestedDurationsMinutes: List<Int> = listOf(30, 45, 60, 90, 120)
)

data class BabyNeedPrediction(
    val primaryNeedTitle: String,
    val confidencePercentage: Int,
    val reasoning: String,
    val urgencyLevel: UrgencyLevel,
    val recommendedAction: String,
    val timeRemainingMinutes: Int,
    val suggestedActivityType: String
)

data class TodaySummary(
    val totalFeedVolumeMl: Int = 0,
    val feedCount: Int = 0,
    val lastFedMinutesAgo: Long = -1,
    val totalSleepMinutes: Long = 0,
    val napCount: Int = 0,
    val lastSleptMinutesAgo: Long = -1,
    val wetDiaperCount: Int = 0,
    val dirtyDiaperCount: Int = 0,
    val lastDiaperMinutesAgo: Long = -1,
    val totalPumpedVolumeMl: Int = 0,
    val lastPumpedMinutesAgo: Long = -1,
    val dayOffset: Int = 0,
    val dateLabel: String = "TODAY'S ROUTINE SUMMARY"
)

/**
 * Absolute trigger times for the next feed / diaper / nap care reminders.
 * Matches Need Engine interval rules so UI and AlarmManager stay aligned.
 */
data class RoutineTriggers(
    val feedAtMillis: Long,
    val diaperAtMillis: Long,
    val napAtMillis: Long
)

object IntelligentNeedEngine {

    const val DIAPER_INTERVAL_MINUTES = 180

    fun computeRoutineTriggers(
        profile: BabyProfile?,
        logs: List<ActivityLog>,
        currentTimeMillis: Long = System.currentTimeMillis(),
        diaperIntervalMinutes: Int = DIAPER_INTERVAL_MINUTES
    ): RoutineTriggers {
        val settings = CareCheckSettings(
            feedReminderEnabled = true,
            feedAlarmEnabled = true,
            diaperReminderEnabled = true,
            diaperAlarmEnabled = true,
            sleepEnabled = true,
            diaperUseAppTiming = false,
            diaperIntervalMinutes = diaperIntervalMinutes
        )
        val care = ReminderTiming.computeCareCheckTriggers(
            profile = profile,
            settings = settings,
            logs = logs,
            nowMillis = currentTimeMillis
        )
        return RoutineTriggers(
            feedAtMillis = care.feedAtMillis ?: (currentTimeMillis +
                (profile?.targetFeedingIntervalMinutes ?: 180).coerceAtLeast(1) * 60_000L),
            diaperAtMillis = care.diaperAtMillis ?: (currentTimeMillis +
                diaperIntervalMinutes.coerceAtLeast(1) * 60_000L),
            napAtMillis = care.sleepAtMillis ?: (currentTimeMillis +
                (profile?.targetNapIntervalMinutes ?: 150).coerceAtLeast(1) * 60_000L)
        )
    }

    fun analyzeBabyNeeds(
        profile: BabyProfile?,
        logs: List<ActivityLog>,
        ongoingLog: ActivityLog?,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): BabyNeedPrediction {
        if (ongoingLog != null) {
            val elapsedMinutes = TimeUnit.MILLISECONDS.toMinutes(currentTimeMillis - ongoingLog.startTimeMillis)
            return when (ongoingLog.activityType) {
                ActivityTypes.SLEEP -> BabyNeedPrediction(
                    primaryNeedTitle = "Baby is Currently Sleeping 💤",
                    confidencePercentage = 95,
                    reasoning = "Nap in progress for ${elapsedMinutes}m. Keep room quiet and dark.",
                    urgencyLevel = UrgencyLevel.LOW_ALL_GOOD,
                    recommendedAction = "Monitor Nap",
                    timeRemainingMinutes = 0,
                    suggestedActivityType = ActivityTypes.SLEEP
                )
                ActivityTypes.BREASTFEEDING, ActivityTypes.BOTTLE -> BabyNeedPrediction(
                    primaryNeedTitle = "Feeding Session in Progress 🍼",
                    confidencePercentage = 98,
                    reasoning = "Active feeding timer running for ${elapsedMinutes}m.",
                    urgencyLevel = UrgencyLevel.LOW_ALL_GOOD,
                    recommendedAction = "Finish & Burp Baby",
                    timeRemainingMinutes = 0,
                    suggestedActivityType = ActivityTypes.BOTTLE
                )
                ActivityTypes.TUMMY_TIME -> BabyNeedPrediction(
                    primaryNeedTitle = "Tummy Time in Progress 👶",
                    confidencePercentage = 90,
                    reasoning = "Baby is exercising neck and back muscles (${elapsedMinutes}m elapsed).",
                    urgencyLevel = UrgencyLevel.LOW_ALL_GOOD,
                    recommendedAction = "Praise & Engage",
                    timeRemainingMinutes = 0,
                    suggestedActivityType = ActivityTypes.TUMMY_TIME
                )
                else -> BabyNeedPrediction(
                    primaryNeedTitle = "Active Activity: ${ongoingLog.activityType}",
                    confidencePercentage = 90,
                    reasoning = "Caregiver is logged as performing ${ongoingLog.activityType}.",
                    urgencyLevel = UrgencyLevel.LOW_ALL_GOOD,
                    recommendedAction = "Complete Activity",
                    timeRemainingMinutes = 0,
                    suggestedActivityType = ongoingLog.activityType
                )
            }
        }

        val completedLogs = logs.filter { it.endTimeMillis != null || it.activityType == ActivityTypes.DIAPER || it.activityType == ActivityTypes.MEDICINE || it.activityType == ActivityTypes.TEMPERATURE }

        // Find last feeding
        val lastFeed = completedLogs.firstOrNull { it.activityType == ActivityTypes.BREASTFEEDING || it.activityType == ActivityTypes.BOTTLE }
        val lastDiaper = completedLogs.firstOrNull { it.activityType == ActivityTypes.DIAPER }
        val lastSleep = completedLogs.firstOrNull { it.activityType == ActivityTypes.SLEEP }

        val targetFeedInterval = profile?.targetFeedingIntervalMinutes ?: 180
        val targetSleepInterval = profile?.targetNapIntervalMinutes ?: 150

        val minutesSinceFeed = if (lastFeed != null) {
            val feedEnd = lastFeed.endTimeMillis ?: lastFeed.startTimeMillis
            TimeUnit.MILLISECONDS.toMinutes(currentTimeMillis - feedEnd)
        } else 240L

        val minutesSinceDiaper = if (lastDiaper != null) {
            val diaperTime = lastDiaper.startTimeMillis
            TimeUnit.MILLISECONDS.toMinutes(currentTimeMillis - diaperTime)
        } else 180L

        val minutesSinceSleep = if (lastSleep != null) {
            val sleepEnd = lastSleep.endTimeMillis ?: lastSleep.startTimeMillis
            TimeUnit.MILLISECONDS.toMinutes(currentTimeMillis - sleepEnd)
        } else 150L

        val name = profile?.name ?: "Baby"

        // Rule 1: Feeding overdue or due very soon
        if (minutesSinceFeed >= targetFeedInterval) {
            val overdueMin = minutesSinceFeed - targetFeedInterval
            return BabyNeedPrediction(
                primaryNeedTitle = "Feeding Due Now! 🍼",
                confidencePercentage = 92,
                reasoning = "Last fed ${formatMinutes(minutesSinceFeed)} ago. Target interval is ${formatMinutes(targetFeedInterval.toLong())}.",
                urgencyLevel = UrgencyLevel.HIGH_URGENT,
                recommendedAction = "Prepare Bottle / Breastfeed",
                timeRemainingMinutes = -overdueMin.toInt(),
                suggestedActivityType = ActivityTypes.BOTTLE
            )
        } else if (targetFeedInterval - minutesSinceFeed <= 20) {
            val remainingMin = (targetFeedInterval - minutesSinceFeed).toInt()
            return BabyNeedPrediction(
                primaryNeedTitle = "Feeding Predicted in ~${remainingMin} mins 🍼",
                confidencePercentage = 85,
                reasoning = "Last fed ${formatMinutes(minutesSinceFeed)} ago. Baby usually shows hunger cues soon.",
                urgencyLevel = UrgencyLevel.MEDIUM_RECOMMENDED,
                recommendedAction = "Warm Breast Milk / Formula",
                timeRemainingMinutes = remainingMin,
                suggestedActivityType = ActivityTypes.BOTTLE
            )
        }

        // Rule 2: Diaper check overdue
        if (minutesSinceDiaper >= DIAPER_INTERVAL_MINUTES) {
            return BabyNeedPrediction(
                primaryNeedTitle = "Diaper Check Recommended 👶",
                confidencePercentage = 88,
                reasoning = "Last diaper change was ${formatMinutes(minutesSinceDiaper)} ago.",
                urgencyLevel = UrgencyLevel.MEDIUM_RECOMMENDED,
                recommendedAction = "Check Diaper",
                timeRemainingMinutes = 0,
                suggestedActivityType = ActivityTypes.DIAPER
            )
        }

        // Rule 3: Nap / Sleep window opening up (awake window reached) or unconfirmed gap
        if (minutesSinceSleep >= targetSleepInterval) {
            val awakeMin = minutesSinceSleep
            if (awakeMin > targetSleepInterval + 45) {
                return BabyNeedPrediction(
                    primaryNeedTitle = "Unconfirmed Awake Gap (${formatMinutes(awakeMin)}) 💤",
                    confidencePercentage = 80,
                    reasoning = "Last sleep logged was ${formatMinutes(awakeMin)} ago. Did $name take a nap in between?",
                    urgencyLevel = UrgencyLevel.MEDIUM_RECOMMENDED,
                    recommendedAction = "Confirm or Quick-Log Nap",
                    timeRemainingMinutes = 0,
                    suggestedActivityType = ActivityTypes.SLEEP
                )
            } else {
                return BabyNeedPrediction(
                    primaryNeedTitle = "$name Might Be Showing Tired Cues 😴",
                    confidencePercentage = 84,
                    reasoning = "$name has been awake for ${formatMinutes(awakeMin)}. Look for eye rubbing or yawning.",
                    urgencyLevel = UrgencyLevel.MEDIUM_RECOMMENDED,
                    recommendedAction = "Start Nap Routine",
                    timeRemainingMinutes = 0,
                    suggestedActivityType = ActivityTypes.SLEEP
                )
            }
        }

        // Default: Baby is comfortable
        val nextFeedMin = (targetFeedInterval - minutesSinceFeed).toInt()
        return BabyNeedPrediction(
            primaryNeedTitle = "$name is Content & Happy ✨",
            confidencePercentage = 95,
            reasoning = "All routines are up to date! Next feeding predicted in ~${nextFeedMin} mins.",
            urgencyLevel = UrgencyLevel.LOW_ALL_GOOD,
            recommendedAction = "Playtime / Tummy Time",
            timeRemainingMinutes = nextFeedMin,
            suggestedActivityType = ActivityTypes.TUMMY_TIME
        )
    }

    fun detectUnloggedSleepGap(
        profile: BabyProfile?,
        logs: List<ActivityLog>,
        ongoingLog: ActivityLog?,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): SmartSleepGapPrompt? {
        if (ongoingLog != null) return null

        val targetSleepInterval = profile?.targetNapIntervalMinutes ?: 150
        val completedLogs = logs.filter { it.endTimeMillis != null || it.activityType == ActivityTypes.DIAPER || it.activityType == ActivityTypes.MEDICINE || it.activityType == ActivityTypes.TEMPERATURE }

        val lastSleep = completedLogs.firstOrNull { it.activityType == ActivityTypes.SLEEP }
        val lastSleepEnd = lastSleep?.let { it.endTimeMillis ?: it.startTimeMillis } ?: (currentTimeMillis - (targetSleepInterval * 60_000L))
        val minutesSinceSleep = TimeUnit.MILLISECONDS.toMinutes(currentTimeMillis - lastSleepEnd)

        if (minutesSinceSleep > (targetSleepInterval + 30)) {
            val estimatedStart = lastSleepEnd + (targetSleepInterval * 60_000L)
            return SmartSleepGapPrompt(
                estimatedNapStartMillis = estimatedStart,
                minutesSinceLastActivity = minutesSinceSleep,
                suggestedDurationsMinutes = listOf(30, 45, 60, 90, 120)
            )
        }
        return null
    }

    fun computeTodaySummary(
        logs: List<ActivityLog>,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): TodaySummary {
        return computeDaySummary(logs, 0, currentTimeMillis)
    }

    fun computeDaySummary(
        logs: List<ActivityLog>,
        dayOffset: Int = 0,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): TodaySummary {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = currentTimeMillis
        cal.add(java.util.Calendar.DAY_OF_YEAR, dayOffset)

        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val startOfDayMillis = cal.timeInMillis

        cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        cal.set(java.util.Calendar.MINUTE, 59)
        cal.set(java.util.Calendar.SECOND, 59)
        cal.set(java.util.Calendar.MILLISECOND, 999)
        val endOfDayMillis = cal.timeInMillis

        val dayLogs = logs.filter { log ->
            if (dayOffset == 0) {
                log.startTimeMillis >= startOfDayMillis
            } else {
                log.startTimeMillis in startOfDayMillis..endOfDayMillis
            }
        }

        var feedVol = 0
        var feedCount = 0
        var sleepMinutes = 0L
        var napCount = 0
        var wetDiapers = 0
        var dirtyDiapers = 0
        var pumpedVol = 0

        val capTime = if (dayOffset == 0) currentTimeMillis else endOfDayMillis

        dayLogs.forEach { log ->
            when (log.activityType) {
                ActivityTypes.BOTTLE, ActivityTypes.BREASTFEEDING -> {
                    feedCount++
                    feedVol += log.volumeMl
                }
                ActivityTypes.SLEEP -> {
                    napCount++
                    val sleepEnd = log.endTimeMillis ?: capTime
                    val clampedStart = maxOf(log.startTimeMillis, startOfDayMillis)
                    val clampedEnd = minOf(sleepEnd, endOfDayMillis)
                    val duration = clampedEnd - clampedStart
                    if (duration > 0) {
                        sleepMinutes += TimeUnit.MILLISECONDS.toMinutes(duration)
                    }
                }
                ActivityTypes.DIAPER -> {
                    when (log.diaperStatus) {
                        "Wet" -> wetDiapers++
                        "Dirty" -> dirtyDiapers++
                        "Both", "Wet & Dirty" -> {
                            wetDiapers++
                            dirtyDiapers++
                        }
                        else -> wetDiapers++
                    }
                }
                ActivityTypes.PUMPING -> {
                    pumpedVol += log.volumeMl
                }
            }
        }

        val lastFeed = logs.firstOrNull { it.activityType == ActivityTypes.BREASTFEEDING || it.activityType == ActivityTypes.BOTTLE }
        val lastSleep = logs.firstOrNull { it.activityType == ActivityTypes.SLEEP }
        val lastDiaper = logs.firstOrNull { it.activityType == ActivityTypes.DIAPER }
        val lastPump = logs.firstOrNull { it.activityType == ActivityTypes.PUMPING }

        val lastFedMin = lastFeed?.let { TimeUnit.MILLISECONDS.toMinutes(currentTimeMillis - (it.endTimeMillis ?: it.startTimeMillis)) } ?: -1L
        val lastSleptMin = lastSleep?.let { TimeUnit.MILLISECONDS.toMinutes(currentTimeMillis - (it.endTimeMillis ?: it.startTimeMillis)) } ?: -1L
        val lastDiaperMin = lastDiaper?.let { TimeUnit.MILLISECONDS.toMinutes(currentTimeMillis - it.startTimeMillis) } ?: -1L
        val lastPumpedMin = lastPump?.let { TimeUnit.MILLISECONDS.toMinutes(currentTimeMillis - (it.endTimeMillis ?: it.startTimeMillis)) } ?: -1L

        val dateLabel = when (dayOffset) {
            0 -> "TODAY'S ROUTINE SUMMARY"
            -1 -> "YESTERDAY'S ROUTINE SUMMARY"
            else -> {
                val sdf = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.US)
                "ROUTINE SUMMARY • ${sdf.format(java.util.Date(startOfDayMillis)).uppercase()}"
            }
        }

        return TodaySummary(
            totalFeedVolumeMl = feedVol,
            feedCount = feedCount,
            lastFedMinutesAgo = lastFedMin,
            totalSleepMinutes = sleepMinutes,
            napCount = napCount,
            lastSleptMinutesAgo = lastSleptMin,
            wetDiaperCount = wetDiapers,
            dirtyDiaperCount = dirtyDiapers,
            lastDiaperMinutesAgo = lastDiaperMin,
            totalPumpedVolumeMl = pumpedVol,
            lastPumpedMinutesAgo = lastPumpedMin,
            dayOffset = dayOffset,
            dateLabel = dateLabel
        )
    }

    private fun getStartOfDayMillis(timeMillis: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timeMillis
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun formatMinutes(minutes: Long): String {
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
