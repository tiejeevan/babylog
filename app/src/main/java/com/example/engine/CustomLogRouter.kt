package com.example.engine

import com.example.data.model.ActivityTypes
import java.util.Locale
import kotlin.math.roundToInt

data class RoutedActivity(
    val activityType: String,
    val diaperStatus: String? = null,
    val volumeMl: Int = 0,
    val durationSeconds: Long = 0,
    val notes: String = ""
)

object CustomLogRouter {

    private val DIAPER_DIRTY_KEYWORDS = listOf("poop", "pooped", "bm", "bowel", "stool", "dirty", "soiled", "crap")
    private val DIAPER_WET_KEYWORDS = listOf("pee", "peed", "wet", "urine", "piddle")
    private val DIAPER_GENERAL_KEYWORDS = listOf("diaper", "nappy", "change")

    private val FEEDING_BREAST_KEYWORDS = listOf("breastfeed", "breastfed", "nursed", "nursing", "latch", "latched")
    private val FEEDING_BOTTLE_KEYWORDS = listOf("bottle", "drank", "formula", "expressed milk", "fed bottle", "milk")

    private val SLEEP_KEYWORDS = listOf("slept", "nap", "napped", "asleep", "fell asleep", "woke up", "sleeping", "bedtime")
    private val MEDICINE_KEYWORDS = listOf("tylenol", "motrin", "advil", "calpol", "medicine", "meds", "dose", "vitamin", "drops", "vaccine", "shot")
    private val TEMP_KEYWORDS = listOf("fever", "temp", "temperature", "degree", "celsius", "fahrenheit", "°c", "°f")
    private val TUMMY_KEYWORDS = listOf("tummy time", "tummy")
    private val BATH_KEYWORDS = listOf("bath", "shower", "sponge bath", "wash")

    /**
     * Inspects [title] and [notes] of a custom log and routes it to the correct activity type.
     */
    fun route(title: String, notes: String = ""): RoutedActivity {
        val fullText = if (notes.isNotBlank() && title != notes) "$title — $notes" else title.ifBlank { notes }
        val lower = fullText.lowercase(Locale.ROOT)

        // 1. Check Diaper Intent
        val hasDirty = DIAPER_DIRTY_KEYWORDS.any { lower.contains(it) }
        val hasWet = DIAPER_WET_KEYWORDS.any { lower.contains(it) }
        val hasDiaperGen = DIAPER_GENERAL_KEYWORDS.any { lower.contains(it) }

        if (hasDirty || hasWet || hasDiaperGen) {
            val status = when {
                hasDirty && hasWet -> "Both"
                hasDirty -> "Dirty"
                hasWet -> "Wet"
                else -> "Dirty" // Default for generic "diaper change"
            }
            return RoutedActivity(
                activityType = ActivityTypes.DIAPER,
                diaperStatus = status,
                notes = fullText
            )
        }

        // 2. Check Breastfeeding Intent
        if (FEEDING_BREAST_KEYWORDS.any { lower.contains(it) }) {
            return RoutedActivity(
                activityType = ActivityTypes.BREASTFEEDING,
                notes = fullText
            )
        }

        // 3. Check Bottle Feeding Intent
        val hasBottle = FEEDING_BOTTLE_KEYWORDS.any { lower.contains(it) }
        if (hasBottle) {
            val volume = extractVolumeMl(lower)
            return RoutedActivity(
                activityType = ActivityTypes.BOTTLE,
                volumeMl = volume,
                notes = fullText
            )
        }

        // 4. Check Sleep Intent
        if (SLEEP_KEYWORDS.any { lower.contains(it) }) {
            val durationSec = extractDurationSeconds(lower)
            return RoutedActivity(
                activityType = ActivityTypes.SLEEP,
                durationSeconds = durationSec,
                notes = fullText
            )
        }

        // 5. Check Medicine Intent
        if (MEDICINE_KEYWORDS.any { lower.contains(it) }) {
            return RoutedActivity(
                activityType = ActivityTypes.MEDICINE,
                notes = fullText
            )
        }

        // 6. Check Temperature Intent
        if (TEMP_KEYWORDS.any { lower.contains(it) }) {
            return RoutedActivity(
                activityType = ActivityTypes.TEMPERATURE,
                notes = fullText
            )
        }

        // 7. Check Tummy Time Intent
        if (TUMMY_KEYWORDS.any { lower.contains(it) }) {
            return RoutedActivity(
                activityType = ActivityTypes.TUMMY_TIME,
                notes = fullText
            )
        }

        // 8. Check Bath Intent
        if (BATH_KEYWORDS.any { lower.contains(it) }) {
            return RoutedActivity(
                activityType = ActivityTypes.BATH,
                notes = fullText
            )
        }

        // Default: Pure Custom Log
        return RoutedActivity(
            activityType = ActivityTypes.CUSTOM,
            notes = fullText
        )
    }

    private fun extractVolumeMl(text: String): Int {
        // Match e.g. "150 ml", "150ml", "5 oz", "5oz"
        val ozRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:oz|ounces?)""")
        val ozMatch = ozRegex.find(text)
        if (ozMatch != null) {
            val ozVal = ozMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            return (ozVal * 29.5735).roundToInt()
        }

        val mlRegex = Regex("""(\d+)\s*(?:ml|milliliters?)""")
        val mlMatch = mlRegex.find(text)
        if (mlMatch != null) {
            return mlMatch.groupValues[1].toIntOrNull() ?: 0
        }

        return 0
    }

    private fun extractDurationSeconds(text: String): Long {
        var totalSec = 0L
        val hrRegex = Regex("""(\d+)\s*(?:h|hr|hrs|hours?)""")
        val hrMatch = hrRegex.find(text)
        if (hrMatch != null) {
            val hrs = hrMatch.groupValues[1].toLongOrNull() ?: 0L
            totalSec += hrs * 3600L
        }

        val minRegex = Regex("""(\d+)\s*(?:m|min|mins|minutes?)""")
        val minMatch = minRegex.find(text)
        if (minMatch != null) {
            val mins = minMatch.groupValues[1].toLongOrNull() ?: 0L
            totalSec += mins * 60L
        }

        return totalSec
    }
}
