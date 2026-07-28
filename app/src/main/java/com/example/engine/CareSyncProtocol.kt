package com.example.engine

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CareEnvelope(
    val v: Int = 1,
    val type: String,
    val payloadJson: String
)

@JsonClass(generateAdapter = true)
data class HelloPayload(
    val pin: String,
    val caregiverName: String,
    val caregiverRole: String = "",
    val babyName: String = "",
    val deviceId: String
)

@JsonClass(generateAdapter = true)
data class HelloAckPayload(
    val ok: Boolean,
    val caregiverName: String = "",
    val reason: String = ""
)

@JsonClass(generateAdapter = true)
data class ChatPayload(
    val syncId: String,
    val senderName: String,
    val text: String,
    val timestampMillis: Long
)

@JsonClass(generateAdapter = true)
data class PingPayload(
    val syncId: String,
    val presetId: String,
    val senderName: String,
    val text: String,
    val pingIcon: String? = null,
    val timestampMillis: Long
)

@JsonClass(generateAdapter = true)
data class ChatAckPayload(
    val syncId: String
)

@JsonClass(generateAdapter = true)
data class ChatReadPayload(
    val syncIds: List<String>
)

@JsonClass(generateAdapter = true)
data class SyncOfferPayload(
    val latestUpdatedAt: Long,
    val logCount: Int
)

@JsonClass(generateAdapter = true)
data class ActivityLogDto(
    val syncId: String,
    val babyId: Long = 1,
    val activityType: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long? = null,
    val durationSeconds: Long = 0,
    val volumeMl: Int = 0,
    val milkType: String? = null,
    val leftBreastDurationSec: Long = 0,
    val rightBreastDurationSec: Long = 0,
    val diaperStatus: String? = null,
    val medicineName: String? = null,
    val dosage: String? = null,
    val temperatureCelsius: Double = 0.0,
    val notes: String = "",
    val caregiverName: String = "",
    val caregiverRole: String = "",
    val timestampMillis: Long = 0,
    val updatedAtMillis: Long = 0,
    val isDeleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GrowthDto(
    val syncId: String,
    val babyId: Long = 1,
    val dateMillis: Long,
    val weightKg: Double,
    val heightCm: Double,
    val headCircumferenceCm: Double,
    val notes: String = "",
    val updatedAtMillis: Long = 0,
    val isDeleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class MedicalDto(
    val syncId: String,
    val babyId: Long = 1,
    val dateMillis: Long,
    val recordType: String,
    val title: String,
    val details: String = "",
    val isCompleted: Boolean = false,
    val updatedAtMillis: Long = 0,
    val isDeleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class MilkStashDto(
    val syncId: String,
    val babyId: Long = 1,
    val volumeMl: Int,
    val location: String = "Freezer",
    val pumpedDateMillis: Long,
    val expirationDateMillis: Long,
    val isUsed: Boolean = false,
    val notes: String = "",
    val updatedAtMillis: Long = 0,
    val isDeleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class MilestoneDto(
    val syncId: String,
    val babyId: Long = 1,
    val category: String,
    val title: String,
    val description: String = "",
    val achievedDateMillis: Long? = null,
    val isAchieved: Boolean = false,
    val updatedAtMillis: Long = 0,
    val isDeleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class BabyProfileDto(
    val name: String,
    val birthDateMillis: Long,
    val birthTimeFormatted: String = com.example.data.model.BabyBirthDefaults.BIRTH_TIME_FORMATTED,
    val initialWeightKg: Double = 3.5,
    val initialHeightCm: Double = 50.0,
    val gender: String = "Girl",
    val targetFeedingIntervalMinutes: Int = 180,
    val targetNapIntervalMinutes: Int = 150,
    val primaryCaregiverName: String = "Mom",
    val primaryCaregiverRole: String = "Mom",
    val isInitialSetupDone: Boolean = false,
    val updatedAtMillis: Long = 0
)

@JsonClass(generateAdapter = true)
data class DutyDto(
    val syncId: String,
    val caregiverName: String,
    val caregiverRole: String = "",
    val startedAtMillis: Long,
    val untilMillis: Long? = null,
    val isActive: Boolean = true,
    val updatedAtMillis: Long = 0,
    val deviceId: String = ""
)

@JsonClass(generateAdapter = true)
data class MemoryDto(
    val syncId: String,
    val babyId: Long = 1,
    val mediaType: String = "PHOTO",
    val capturedAtMillis: Long = 0,
    val caption: String = "",
    val caregiverName: String = "",
    val contentHash: String = "",
    val fileSizeBytes: Long = 0,
    val mimeType: String = "image/webp",
    val updatedAtMillis: Long = 0,
    val isDeleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class MemoryFileOfferPayload(
    val syncId: String,
    val contentHash: String,
    val mimeType: String = "image/webp",
    val fileSizeBytes: Long = 0
)

@JsonClass(generateAdapter = true)
data class MemoryFileRequestPayload(
    val syncId: String,
    val contentHash: String
)

@JsonClass(generateAdapter = true)
data class NoteDto(
    val syncId: String,
    val babyId: Long = 1,
    val title: String = "",
    val body: String = "",
    val pinnedDateMillis: Long? = null,
    val caregiverName: String = "",
    val updatedAtMillis: Long = 0,
    val isDeleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ListDto(
    val syncId: String,
    val babyId: Long = 1,
    val title: String = "",
    val caregiverName: String = "",
    val updatedAtMillis: Long = 0,
    val isDeleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ListItemDto(
    val syncId: String,
    val listSyncId: String,
    val text: String = "",
    val isChecked: Boolean = false,
    val sortOrder: Int = 0,
    val updatedAtMillis: Long = 0,
    val isDeleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class SyncBatchPayload(
    val batchId: String,
    val logs: List<ActivityLogDto> = emptyList(),
    val growth: List<GrowthDto> = emptyList(),
    val medical: List<MedicalDto> = emptyList(),
    val milk: List<MilkStashDto> = emptyList(),
    val milestones: List<MilestoneDto> = emptyList(),
    val profile: BabyProfileDto? = null,
    val duty: List<DutyDto> = emptyList(),
    val memories: List<MemoryDto> = emptyList(),
    val notes: List<NoteDto> = emptyList(),
    val lists: List<ListDto> = emptyList(),
    val listItems: List<ListItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SyncAckPayload(
    val batchId: String,
    val appliedCount: Int
)

object CareMessageTypes {
    const val HELLO = "HELLO"
    const val HELLO_ACK = "HELLO_ACK"
    const val CHAT = "CHAT"
    const val PING = "PING"
    const val CHAT_ACK = "CHAT_ACK"
    const val CHAT_READ = "CHAT_READ"
    const val SYNC_OFFER = "SYNC_OFFER"
    const val SYNC_BATCH = "SYNC_BATCH"
    const val SYNC_ACK = "SYNC_ACK"
    const val LOG_UPSERT = "LOG_UPSERT"
    const val LOG_DELETE = "LOG_DELETE"
    const val GROWTH_UPSERT = "GROWTH_UPSERT"
    const val GROWTH_DELETE = "GROWTH_DELETE"
    const val MEDICAL_UPSERT = "MEDICAL_UPSERT"
    const val MEDICAL_DELETE = "MEDICAL_DELETE"
    const val MILK_UPSERT = "MILK_UPSERT"
    const val MILK_DELETE = "MILK_DELETE"
    const val MILESTONE_UPSERT = "MILESTONE_UPSERT"
    const val MILESTONE_DELETE = "MILESTONE_DELETE"
    const val PROFILE_UPSERT = "PROFILE_UPSERT"
    const val DUTY_CLAIM = "DUTY_CLAIM"
    const val DUTY_RELEASE = "DUTY_RELEASE"
    const val MEMORY_UPSERT = "MEMORY_UPSERT"
    const val MEMORY_DELETE = "MEMORY_DELETE"
    const val MEMORY_FILE_OFFER = "MEMORY_FILE_OFFER"
    const val MEMORY_FILE_REQUEST = "MEMORY_FILE_REQUEST"
    const val NOTE_UPSERT = "NOTE_UPSERT"
    const val NOTE_DELETE = "NOTE_DELETE"
    const val LIST_UPSERT = "LIST_UPSERT"
    const val LIST_DELETE = "LIST_DELETE"
    const val LIST_ITEM_UPSERT = "LIST_ITEM_UPSERT"
    const val LIST_ITEM_DELETE = "LIST_ITEM_DELETE"
}
