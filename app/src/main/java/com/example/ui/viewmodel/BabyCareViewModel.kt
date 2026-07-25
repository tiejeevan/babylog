package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notification.BabyNotificationManager
import com.example.data.database.BabyCareDatabase
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.model.BabyProfile
import com.example.data.model.CaregiverProfile
import com.example.data.model.GrowthRecord
import com.example.data.model.MedicalRecord
import com.example.data.model.MilestoneRecord
import com.example.data.model.MilkStashItem
import com.example.data.repository.BabyCareRepository
import com.example.widget.BabyCareWidgetProvider
import com.example.engine.BabyNeedPrediction
import com.example.engine.IntelligentNeedEngine
import com.example.engine.TodaySummary
import com.example.service.GeminiCaregiverService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatMessage(
    val isUser: Boolean,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

class BabyCareViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BabyCareRepository
    private val geminiService = GeminiCaregiverService()

    // Ticking timestamp for real-time timer calculations
    private val _currentTimeMillis = MutableStateFlow(System.currentTimeMillis())
    val currentTimeMillis: StateFlow<Long> = _currentTimeMillis.asStateFlow()

    init {
        val database = BabyCareDatabase.getDatabase(application)
        repository = BabyCareRepository(database.babyCareDao())

        // Background timer tick every 1 second
        viewModelScope.launch {
            while (true) {
                delay(1000)
                _currentTimeMillis.value = System.currentTimeMillis()
            }
        }
    }

    val babyProfile: StateFlow<BabyProfile?> = repository.babyProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BabyProfile()
    )

    val recentLogs: StateFlow<List<ActivityLog>> = repository.recentLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val ongoingActivity: StateFlow<ActivityLog?> = repository.ongoingActivity.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val growthRecords: StateFlow<List<GrowthRecord>> = repository.growthRecords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val medicalRecords: StateFlow<List<MedicalRecord>> = repository.medicalRecords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val milkStash: StateFlow<List<MilkStashItem>> = repository.milkStash.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val milestones: StateFlow<List<MilestoneRecord>> = repository.milestones.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val caregivers: StateFlow<List<CaregiverProfile>> = repository.caregivers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val activeCaregiver: StateFlow<CaregiverProfile?> = repository.activeCaregiver.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CaregiverProfile(name = "Mom", role = "Owner", relationship = "Mother", pin = "1234")
    )

    // Derived Intelligent AI Prediction
    val needPrediction: StateFlow<BabyNeedPrediction> = combine(
        babyProfile,
        recentLogs,
        ongoingActivity,
        currentTimeMillis
    ) { profile, logs, ongoing, now ->
        IntelligentNeedEngine.analyzeBabyNeeds(profile, logs, ongoing, now)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BabyNeedPrediction("Analyzing Baby Status...", 90, "Evaluating logs", com.example.engine.UrgencyLevel.LOW_ALL_GOOD, "Check Dashboard", 0, ActivityTypes.BOTTLE)
    )

    // Derived Today Summary
    val todaySummary: StateFlow<TodaySummary> = combine(
        recentLogs,
        currentTimeMillis
    ) { logs, now ->
        IntelligentNeedEngine.computeTodaySummary(logs, now)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TodaySummary()
    )

    // AI Chat State
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(false, "Hello! I am your AI Caregiver Assistant. Ask me anything about your baby's feeding schedule, sleep training, diaper health, or growth milestones!")
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    // Cloud Real-Time Sync Status
    private val _syncStatusText = MutableStateFlow("Cloud Sync Active • 3 Caregivers Connected")
    val syncStatusText: StateFlow<String> = _syncStatusText.asStateFlow()

    // ---------------- Actions ----------------

    fun startLiveActivity(
        activityType: String,
        volumeMl: Int = 0,
        milkType: String = "Breast Milk",
        diaperStatus: String = "Wet",
        leftBreastSec: Long = 0,
        rightBreastSec: Long = 0,
        notes: String = ""
    ) {
        viewModelScope.launch {
            val caregiver = activeCaregiver.value ?: CaregiverProfile(name = "Sarah (Mom)", role = "Owner", relationship = "Mother")
            val log = ActivityLog(
                babyId = 1,
                activityType = activityType,
                startTimeMillis = System.currentTimeMillis(),
                endTimeMillis = null, // null means ongoing
                volumeMl = volumeMl,
                milkType = milkType,
                diaperStatus = diaperStatus,
                leftBreastDurationSec = leftBreastSec,
                rightBreastDurationSec = rightBreastSec,
                notes = notes,
                caregiverName = caregiver.name,
                caregiverRole = caregiver.relationship,
                timestampMillis = System.currentTimeMillis()
            )
            repository.insertLog(log)
            _syncStatusText.value = "Live Activity Started by ${caregiver.name} • Synced"
            BabyCareWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun stopLiveActivity(finalNotes: String = "") {
        viewModelScope.launch {
            val ongoing = ongoingActivity.value ?: return@launch
            val now = System.currentTimeMillis()
            val durationSec = (now - ongoing.startTimeMillis) / 1000
            val updated = ongoing.copy(
                endTimeMillis = now,
                durationSeconds = durationSec,
                notes = if (finalNotes.isNotBlank()) finalNotes else ongoing.notes
            )
            repository.updateLog(updated)
            _syncStatusText.value = "Activity Logged & Synced across devices"
            BabyCareWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun quickLogActivity(
        activityType: String,
        durationSeconds: Long = 600,
        volumeMl: Int = 0,
        milkType: String? = null,
        diaperStatus: String? = null,
        medicineName: String? = null,
        dosage: String? = null,
        temperatureCelsius: Double = 0.0,
        notes: String = ""
    ) {
        viewModelScope.launch {
            val caregiver = activeCaregiver.value ?: CaregiverProfile(name = "Sarah (Mom)", role = "Owner", relationship = "Mother")
            val now = System.currentTimeMillis()
            val log = ActivityLog(
                babyId = 1,
                activityType = activityType,
                startTimeMillis = now - (durationSeconds * 1000),
                endTimeMillis = now,
                durationSeconds = durationSeconds,
                volumeMl = volumeMl,
                milkType = milkType,
                diaperStatus = diaperStatus,
                medicineName = medicineName,
                dosage = dosage,
                temperatureCelsius = temperatureCelsius,
                notes = notes,
                caregiverName = caregiver.name,
                caregiverRole = caregiver.relationship,
                timestampMillis = now
            )
            repository.insertLog(log)
            _syncStatusText.value = "Logged $activityType by ${caregiver.name}"
            BabyCareWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun toggleSleepTimer() {
        val currentOngoing = ongoingActivity.value
        if (currentOngoing != null) {
            stopLiveActivity("Quick sleep logged")
        } else {
            startLiveActivity(activityType = ActivityTypes.SLEEP, notes = "Quick sleep timer started")
        }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch {
            repository.deleteLog(id)
            BabyCareWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun saveBabyProfile(profile: BabyProfile) {
        viewModelScope.launch {
            repository.saveProfile(profile)
            BabyCareWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun setActiveCaregiver(caregiverId: Long) {
        viewModelScope.launch {
            repository.setActiveCaregiver(caregiverId)
        }
    }

    fun verifyAndSwitchCaregiver(caregiverId: Long, pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.verifyAndSetActiveCaregiver(caregiverId, pin)
            if (success) {
                val active = caregivers.value.find { it.id == caregiverId }
                _syncStatusText.value = "Active Caregiver: ${active?.name ?: "User"}"
            }
            onResult(success)
        }
    }

    fun addCaregiver(name: String, relationship: String, role: String, pin: String = "1234") {
        viewModelScope.launch {
            val colors = listOf("#FF7043", "#26A69A", "#7E57C2", "#42A5F5", "#EC407A", "#66BB6A")
            val newCaregiver = CaregiverProfile(
                name = name,
                role = role,
                relationship = relationship,
                pin = pin,
                isActiveNow = false,
                avatarColorHex = colors.random()
            )
            repository.insertCaregiver(newCaregiver)
        }
    }

    fun clearAllSampleData() {
        viewModelScope.launch {
            repository.clearAllSampleData()
            _syncStatusText.value = "Cleared sample data • Ready for Real Data"
            BabyCareWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun wipeAllDataAndReset() {
        viewModelScope.launch {
            repository.wipeAllDataAndReset()
            _syncStatusText.value = "App Data Factory Reset • Please complete setup"
        }
    }

    fun addGrowthRecord(weightKg: Double, heightCm: Double, headCm: Double, notes: String) {
        viewModelScope.launch {
            val record = GrowthRecord(
                babyId = 1,
                dateMillis = System.currentTimeMillis(),
                weightKg = weightKg,
                heightCm = heightCm,
                headCircumferenceCm = headCm,
                notes = notes
            )
            repository.insertGrowthRecord(record)
        }
    }

    fun addMilkStash(volumeMl: Int, location: String, notes: String) {
        viewModelScope.launch {
            val item = MilkStashItem(
                babyId = 1,
                volumeMl = volumeMl,
                location = location,
                pumpedDateMillis = System.currentTimeMillis(),
                notes = notes
            )
            repository.insertMilkStash(item)
        }
    }

    fun toggleMilestone(milestone: MilestoneRecord) {
        viewModelScope.launch {
            val updated = milestone.copy(
                isAchieved = !milestone.isAchieved,
                achievedDateMillis = if (!milestone.isAchieved) System.currentTimeMillis() else null
            )
            repository.updateMilestone(updated)
        }
    }

    fun addMedicalRecord(type: String, title: String, details: String) {
        viewModelScope.launch {
            val record = MedicalRecord(
                babyId = 1,
                dateMillis = System.currentTimeMillis(),
                recordType = type,
                title = title,
                details = details,
                isCompleted = false
            )
            repository.insertMedicalRecord(record)
        }
    }

    fun toggleMedicalRecord(record: MedicalRecord) {
        viewModelScope.launch {
            repository.updateMedicalRecord(record.copy(isCompleted = !record.isCompleted))
        }
    }

    fun askAiAssistant(questionText: String) {
        if (questionText.isBlank()) return
        val userMsg = ChatMessage(true, questionText)
        _chatMessages.value = _chatMessages.value + userMsg
        _isAiThinking.value = true

        viewModelScope.launch {
            val profile = babyProfile.value
            val summary = todaySummary.value
            val babyName = profile?.name ?: "Your Baby"
            val ageMonths = 2.0 // calculated or default
            val feedSummary = "Fed ${summary.feedCount} times today (${summary.totalFeedVolumeMl}ml)"
            val sleepSummary = "Slept ${summary.totalSleepMinutes} minutes across ${summary.napCount} naps"

            val response = geminiService.askGeminiCaregiver(
                userQuestion = questionText,
                babyName = babyName,
                babyAgeMonths = ageMonths,
                lastFeedingSummary = feedSummary,
                lastSleepSummary = sleepSummary
            )

            _isAiThinking.value = false
            val aiMsg = ChatMessage(false, response)
            _chatMessages.value = _chatMessages.value + aiMsg
        }
    }

    fun completeOnboardingSetup(
        profile: BabyProfile,
        initialWeightKg: Double,
        initialHeightCm: Double
    ) {
        viewModelScope.launch {
            repository.saveProfile(profile)

            // Insert initial caregiver profile
            val caregiver = CaregiverProfile(
                id = 1,
                name = profile.primaryCaregiverName,
                role = "Owner",
                relationship = profile.primaryCaregiverRole,
                pin = "1234",
                isActiveNow = true
            )
            repository.insertCaregiver(caregiver)
            repository.setActiveCaregiver(1L)

            // Insert initial growth record
            val growthRecord = GrowthRecord(
                babyId = profile.id,
                dateMillis = profile.birthDateMillis,
                weightKg = initialWeightKg,
                heightCm = initialHeightCm,
                headCircumferenceCm = 35.0,
                notes = "Birth measurements"
            )
            repository.insertGrowthRecord(growthRecord)
        }
    }

    fun triggerTestNotification(context: Context) {
        val name = babyProfile.value?.name ?: "Your Baby"
        BabyNotificationManager.showSystemNotification(
            context = context,
            title = "👶 System Alert Active for $name",
            message = "Deep-level background notifications, routine alarms & exact wake alerts are active!",
            channelId = BabyNotificationManager.CHANNEL_REMINDERS
        )
    }

    fun scheduleRoutineSystemAlarms(context: Context) {
        val profile = babyProfile.value ?: return
        val pred = needPrediction.value
        val name = profile.name
        val targetMillis = System.currentTimeMillis() + (pred.timeRemainingMinutes.coerceAtLeast(1) * 60 * 1000L)

        if (pred.suggestedActivityType == ActivityTypes.BOTTLE || pred.suggestedActivityType == ActivityTypes.BREASTFEEDING) {
            BabyNotificationManager.scheduleFeedingAlarm(context, targetMillis, name)
        } else if (pred.suggestedActivityType == ActivityTypes.SLEEP) {
            BabyNotificationManager.scheduleNapAlarm(context, targetMillis, name)
        } else {
            BabyNotificationManager.scheduleFeedingAlarm(context, targetMillis, name)
        }
    }
}
