package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "baby_profile")
data class BabyProfile(
    @PrimaryKey val id: Long = 1,
    val name: String = "Your Baby",
    val birthDateMillis: Long = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000), // default 2 months ago
    val birthTimeFormatted: String = "08:00 AM",
    val initialWeightKg: Double = 3.5,
    val initialHeightCm: Double = 50.0,
    val gender: String = "Girl",
    val photoUri: String? = null,
    val targetFeedingIntervalMinutes: Int = 180, // 3 hrs default
    val targetNapIntervalMinutes: Int = 150, // 2.5 hrs default
    val primaryCaregiverName: String = "Mom",
    val primaryCaregiverRole: String = "Mom",
    val isInitialSetupDone: Boolean = false
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
}

@Entity(tableName = "activity_logs")
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
    val timestampMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "growth_records")
data class GrowthRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long = 1,
    val dateMillis: Long = System.currentTimeMillis(),
    val weightKg: Double,
    val heightCm: Double,
    val headCircumferenceCm: Double,
    val notes: String = ""
)

@Entity(tableName = "medical_records")
data class MedicalRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long = 1,
    val dateMillis: Long = System.currentTimeMillis(),
    val recordType: String, // Medication, Vaccine, Doctor Visit, Symptom
    val title: String,
    val details: String = "",
    val isCompleted: Boolean = false
)

@Entity(tableName = "milk_stash")
data class MilkStashItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long = 1,
    val volumeMl: Int,
    val location: String = "Freezer", // Fridge or Freezer
    val pumpedDateMillis: Long = System.currentTimeMillis(),
    val expirationDateMillis: Long = System.currentTimeMillis() + (90L * 24 * 3600 * 1000), // 3 months default
    val isUsed: Boolean = false,
    val notes: String = ""
)

@Entity(tableName = "milestone_records")
data class MilestoneRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long = 1,
    val category: String, // Motor, Social, Language, Cognitive
    val title: String,
    val description: String = "",
    val achievedDateMillis: Long? = null,
    val isAchieved: Boolean = false
)

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
