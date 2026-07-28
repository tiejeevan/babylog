package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Calendar
import java.util.TimeZone

/** Shared first-run birth defaults: 23 Jul 2026, 11:12 AM Cleveland (America/New_York). */
object BabyBirthDefaults {
    const val BIRTH_TIME_FORMATTED = "11:12 AM"
    private const val ZONE_ID = "America/New_York"

    val birthDateMillis: Long by lazy {
        Calendar.getInstance(TimeZone.getTimeZone(ZONE_ID)).apply {
            set(2026, Calendar.JULY, 23, 11, 12, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun birthCalendar(): Calendar =
        Calendar.getInstance().apply { timeInMillis = birthDateMillis }
}

@Entity(tableName = "baby_profile")
data class BabyProfile(
    @PrimaryKey val id: Long = 1,
    val name: String = "Your Baby",
    val birthDateMillis: Long = BabyBirthDefaults.birthDateMillis,
    val birthTimeFormatted: String = BabyBirthDefaults.BIRTH_TIME_FORMATTED,
    val initialWeightKg: Double = 3.5,
    val initialHeightCm: Double = 50.0,
    val gender: String = "Girl",
    val photoUri: String? = null,
    val targetFeedingIntervalMinutes: Int = 180, // 3 hrs default
    val targetNapIntervalMinutes: Int = 150, // 2.5 hrs default
    val primaryCaregiverName: String = "Mom",
    val primaryCaregiverRole: String = "Mom",
    val isInitialSetupDone: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun getFormattedAge(nowMillis: Long = System.currentTimeMillis()): String {
        val diffMillis = (nowMillis - birthDateMillis).coerceAtLeast(0)
        val totalDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

        return when {
            totalDays < 1 -> "Newborn"
            totalDays < 14 -> "$totalDays ${if (totalDays == 1) "day" else "days"}"
            totalDays < 60 -> {
                val weeks = totalDays / 7
                val daysRem = totalDays % 7
                if (daysRem == 0) "$weeks ${if (weeks == 1) "wk" else "wks"}"
                else "$weeks ${if (weeks == 1) "wk" else "wks"} $daysRem d"
            }
            totalDays < 730 -> {
                val months = (totalDays / 30.4375).toInt().coerceAtLeast(1)
                val daysRem = (totalDays % 30.4375).toInt()
                if (daysRem < 5) "$months ${if (months == 1) "month" else "months"}"
                else "$months mos ${daysRem}d"
            }
            else -> {
                val years = totalDays / 365
                val monthsRem = ((totalDays % 365) / 30.4375).toInt()
                if (monthsRem == 0) "$years ${if (years == 1) "yr" else "yrs"}"
                else "$years yrs $monthsRem mos"
            }
        }
    }

    /** Calendar-accurate age broken down to the second for live display. */
    fun getExactAge(nowMillis: Long = System.currentTimeMillis()): ExactAge {
        val safeNow = nowMillis.coerceAtLeast(birthDateMillis)
        val birth = Calendar.getInstance().apply { timeInMillis = birthDateMillis }
        val now = Calendar.getInstance().apply { timeInMillis = safeNow }

        var years = now.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
        var months = now.get(Calendar.MONTH) - birth.get(Calendar.MONTH)
        var days = now.get(Calendar.DAY_OF_MONTH) - birth.get(Calendar.DAY_OF_MONTH)
        var hours = now.get(Calendar.HOUR_OF_DAY) - birth.get(Calendar.HOUR_OF_DAY)
        var minutes = now.get(Calendar.MINUTE) - birth.get(Calendar.MINUTE)
        var seconds = now.get(Calendar.SECOND) - birth.get(Calendar.SECOND)

        if (seconds < 0) {
            seconds += 60
            minutes--
        }
        if (minutes < 0) {
            minutes += 60
            hours--
        }
        if (hours < 0) {
            hours += 24
            days--
        }
        if (days < 0) {
            val prevMonth = (now.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
            days += prevMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
            months--
        }
        if (months < 0) {
            months += 12
            years--
        }

        val totalMillis = safeNow - birthDateMillis
        return ExactAge(
            years = years.coerceAtLeast(0),
            months = months.coerceAtLeast(0),
            days = days.coerceAtLeast(0),
            hours = hours.coerceAtLeast(0),
            minutes = minutes.coerceAtLeast(0),
            seconds = seconds.coerceAtLeast(0),
            totalDays = totalMillis / (1000L * 60 * 60 * 24),
            totalSeconds = totalMillis / 1000L
        )
    }
}

data class ExactAge(
    val years: Int,
    val months: Int,
    val days: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val totalDays: Long,
    val totalSeconds: Long
) {
    fun liveClockLabel(): String =
        "%02d:%02d:%02d".format(hours, minutes, seconds)

    fun breakdownLabel(): String = buildString {
        if (years > 0) append("${years}y ")
        if (months > 0 || years > 0) append("${months}mo ")
        append("${days}d")
    }
}

object ActivityTypes {
    const val BREASTFEEDING = "BREASTFEEDING"
    const val BOTTLE = "BOTTLE"
    const val PUMPING = "PUMPING"
    const val DIAPER = "DIAPER"
    const val SLEEP = "SLEEP"
    const val MEDICINE = "MEDICINE"
    const val TEMPERATURE = "TEMPERATURE"
    const val BATH = "BATH"
    const val TUMMY_TIME = "TUMMY_TIME"
    const val MILESTONE = "MILESTONE"
    const val GROWTH = "GROWTH"
    /** Free-form misc event (crying, outdoors, etc.). Title lives in notes. */
    const val CUSTOM = "CUSTOM"
}

@Entity(
    tableName = "activity_logs",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class ActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long = 1,
    val activityType: String,
    val startTimeMillis: Long = System.currentTimeMillis(),
    val endTimeMillis: Long? = null,
    val durationSeconds: Long = 0,
    val volumeMl: Int = 0,
    val milkType: String? = "Breast Milk",
    val leftBreastDurationSec: Long = 0,
    val rightBreastDurationSec: Long = 0,
    val diaperStatus: String? = "Wet", // Wet, Dirty, Both, Dry
    val medicineName: String? = null,
    val dosage: String? = null,
    val temperatureCelsius: Double = 0.0,
    val notes: String = "",
    val caregiverName: String = "Mom (Sarah)",
    val caregiverRole: String = "Mother",
    val timestampMillis: Long = System.currentTimeMillis(),
    /** Stable identity across devices for peer merge. */
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

enum class MessageDeliveryStatus {
    PENDING,
    DELIVERED,
    READ
}

@Entity(tableName = "peer_chat_messages")
data class PeerChatMessage(
    @PrimaryKey val syncId: String = java.util.UUID.randomUUID().toString(),
    val senderName: String,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val isPing: Boolean = false,
    val pingIcon: String? = null,
    val isFromMe: Boolean = false,
    /** Meaningful for outgoing (`isFromMe`); incoming defaults to READ when viewed. */
    val deliveryStatus: String = MessageDeliveryStatus.PENDING.name
)

@Entity(
    tableName = "growth_records",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class GrowthRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long = 1,
    val dateMillis: Long = System.currentTimeMillis(),
    val weightKg: Double,
    val heightCm: Double,
    val headCircumferenceCm: Double,
    val notes: String = "",
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "medical_records",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class MedicalRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long = 1,
    val dateMillis: Long = System.currentTimeMillis(),
    val recordType: String, // Medication, Vaccine, Doctor Visit, Symptom
    val title: String,
    val details: String = "",
    val isCompleted: Boolean = false,
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "milk_stash",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class MilkStashItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long = 1,
    val volumeMl: Int,
    val location: String = "Freezer", // Fridge or Freezer
    val pumpedDateMillis: Long = System.currentTimeMillis(),
    val expirationDateMillis: Long = System.currentTimeMillis() + (90L * 24 * 3600 * 1000), // 3 months default
    val isUsed: Boolean = false,
    val notes: String = "",
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "milestone_records",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class MilestoneRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long = 1,
    val category: String, // Motor, Social, Language, Cognitive
    val title: String,
    val description: String = "",
    val achievedDateMillis: Long? = null,
    val isAchieved: Boolean = false,
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
) {
    companion object {
        /** Deterministic sync id so seeded CDC checklist merges across peers. */
        fun seededSyncId(category: String, title: String): String = "ms:$category:$title"
    }
}

@Entity(tableName = "caregiver_profiles")
data class CaregiverProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: String, // Owner, Admin, Caregiver, Viewer
    val relationship: String, // Mother, Father, Grandmother, Babysitter, Pediatrician
    val pin: String = "1234",
    val isActiveNow: Boolean = false,
    val avatarColorHex: String = "#FF7043"
)

@Entity(
    tableName = "duty_sessions",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class DutySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val caregiverName: String,
    val caregiverRole: String = "",
    val startedAtMillis: Long = System.currentTimeMillis(),
    /** Null means on duty until the next claim/release. */
    val untilMillis: Long? = null,
    val isActive: Boolean = true,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val deviceId: String = ""
)

@Entity(
    tableName = "sync_outbox",
    indices = [Index(value = ["dedupeKey"], unique = true)]
)
data class SyncOutboxItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val messageType: String,
    val payloadJson: String,
    /** e.g. "LOG_UPSERT:uuid" — newer write with same key replaces older pending send. */
    val dedupeKey: String,
    val attempts: Int = 0
)

object MediaTypes {
    const val PHOTO = "PHOTO"
    const val VIDEO = "VIDEO"
}

@Entity(
    tableName = "memory_items",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class MemoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long = 1,
    val mediaType: String = MediaTypes.PHOTO,
    val localPath: String = "",
    val thumbPath: String = "",
    val capturedAtMillis: Long = System.currentTimeMillis(),
    val caption: String = "",
    val caregiverName: String = "",
    val contentHash: String = "",
    val fileSizeBytes: Long = 0,
    val mimeType: String = "image/webp",
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "shared_notes",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class SharedNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long = 1,
    val title: String = "",
    val body: String = "",
    /** Optional day this note is pinned to on the calendar. */
    val pinnedDateMillis: Long? = null,
    val caregiverName: String = "",
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "shared_lists",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class SharedList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long = 1,
    val title: String = "",
    val caregiverName: String = "",
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "shared_list_items",
    indices = [
        Index(value = ["syncId"], unique = true),
        Index(value = ["listSyncId"])
    ]
)
data class SharedListItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listSyncId: String,
    val text: String = "",
    val isChecked: Boolean = false,
    val sortOrder: Int = 0,
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

object MedicineSubjects {
    const val BABY = "BABY"
    const val MOM = "MOM"
}

@Entity(tableName = "medicine_alarms")
data class MedicineAlarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [MedicineSubjects.BABY] or [MedicineSubjects.MOM]. */
    val subject: String = MedicineSubjects.BABY,
    val name: String = "",
    val doseNote: String = "",
    val intervalMinutes: Int = 360,
    /** Anchor for the repeating interval. Updated when a dose is taken so the next
     *  reminder is counted from that moment. Must be > 0 when enabled. */
    val pilotTimeMillis: Long = 0L,
    val enabled: Boolean = true,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

/**
 * Single-row care reminder settings (`id = 1`).
 * Replaces legacy SharedPreferences reminder toggles.
 */
@Entity(tableName = "care_check_settings")
data class CareCheckSettings(
    @PrimaryKey val id: Long = 1,
    /** Master gates for medicine + care delivery. */
    val notificationsEnabled: Boolean = true,
    val systemAlarmsEnabled: Boolean = true,
    /** Per-check soft reminder / ringing alarm (either on = check active). */
    val feedReminderEnabled: Boolean = true,
    val feedAlarmEnabled: Boolean = true,
    val diaperReminderEnabled: Boolean = true,
    val diaperAlarmEnabled: Boolean = true,
    val babyCheckReminderEnabled: Boolean = false,
    val babyCheckAlarmEnabled: Boolean = false,
    /** Sleep check removed from UI; kept off and unschedulable. */
    val sleepEnabled: Boolean = false,
    /** true = App timing, false = Custom. */
    val feedUseAppTiming: Boolean = true,
    val diaperUseAppTiming: Boolean = true,
    val babyCheckUseAppTiming: Boolean = false,
    val feedCustomIntervalMinutes: Int = 180,
    val diaperIntervalMinutes: Int = 180,
    val babyCheckIntervalMinutes: Int = 120,
    /** Anchor for baby-check grid; 0 means unset (Custom enable requires set). */
    val babyCheckPilotMillis: Long = 0L,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun deliveryEnabled(): Boolean = notificationsEnabled || systemAlarmsEnabled

    fun feedActive(): Boolean = feedReminderEnabled || feedAlarmEnabled
    fun diaperActive(): Boolean = diaperReminderEnabled || diaperAlarmEnabled
    fun babyCheckActive(): Boolean = babyCheckReminderEnabled || babyCheckAlarmEnabled
}
