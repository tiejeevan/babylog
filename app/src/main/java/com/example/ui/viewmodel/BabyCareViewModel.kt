package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.FullBackupManager
import com.example.data.database.BabyCareDatabase
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.model.BabyProfile
import com.example.data.model.CareCheckSettings
import com.example.data.model.CaregiverProfile
import com.example.data.model.DutySession
import com.example.data.model.GrowthRecord
import com.example.data.model.MedicalRecord
import com.example.data.model.MedicineAlarm
import com.example.data.model.MilestoneRecord
import com.example.data.model.MilkStashItem
import com.example.data.model.MediaTypes
import com.example.data.model.MemoryItem
import com.example.data.model.SharedList
import com.example.data.model.SharedListItem
import com.example.data.model.SharedNote
import com.example.data.repository.BabyCareRepository
import com.example.widget.BabyCareWidgetProvider
import com.example.engine.ActiveAlarmTracker
import com.example.engine.BabyNeedPrediction
import com.example.engine.BluetoothCareEngine
import com.example.engine.CareCheckTriggers
import com.example.engine.IntelligentNeedEngine
import com.example.engine.MediaCompressor
import com.example.engine.PatternAnalyticsEngine
import com.example.engine.PatternRangeDays
import com.example.engine.PatternReport
import com.example.engine.ReminderEngine
import com.example.engine.ReminderTiming
import com.example.engine.RoutineTriggers
import com.example.engine.TodaySummary
import com.example.notification.AlarmSoundController
import com.example.notification.BabyNotificationManager
import com.example.service.GeminiCaregiverService
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

sealed class BackupUiState {
    data object Idle : BackupUiState()
    data object InProgress : BackupUiState()
    data class Success(val message: String) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}

data class ChatMessage(
    val isUser: Boolean,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

enum class TimelineRangeMode { DAY, WEEK }

enum class NursingSide { LEFT, RIGHT }

/**
 * Runtime switches so unit/UI tests can inject a repository and skip Care Sync,
 * background timer, and alarm side effects.
 */
data class BabyCareViewModelOptions(
    val initializeCareSync: Boolean = true,
    val runBackgroundTimer: Boolean = true,
    val rescheduleRemindersOnStart: Boolean = true,
    val mirrorCareSyncStatus: Boolean = true
) {
    companion object {
        val Production = BabyCareViewModelOptions()
        val ForTests = BabyCareViewModelOptions(
            initializeCareSync = false,
            runBackgroundTimer = false,
            rescheduleRemindersOnStart = false,
            mirrorCareSyncStatus = false
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BabyCareViewModel @JvmOverloads constructor(
    application: Application,
    repositoryOverride: BabyCareRepository? = null,
    private val runtimeOptions: BabyCareViewModelOptions = BabyCareViewModelOptions.Production
) : AndroidViewModel(application) {

    private val repository: BabyCareRepository =
        repositoryOverride
            ?: BabyCareRepository(BabyCareDatabase.getDatabase(application).babyCareDao())
    private val geminiService = GeminiCaregiverService()

    // Ticking timestamp for real-time timer calculations
    private val _currentTimeMillis = MutableStateFlow(System.currentTimeMillis())
    val currentTimeMillis: StateFlow<Long> = _currentTimeMillis.asStateFlow()

    // Nearby Care Sync status (must be initialized before init collectors run)
    private val _syncStatusText = MutableStateFlow("Care Sync off")
    val syncStatusText: StateFlow<String> = _syncStatusText.asStateFlow()

    private val _backupUiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupUiState: StateFlow<BackupUiState> = _backupUiState.asStateFlow()

    private val _activeNursingSide = MutableStateFlow(NursingSide.LEFT)
    val activeNursingSide: StateFlow<NursingSide> = _activeNursingSide.asStateFlow()

    private val _nursingSideStartedAtMillis = MutableStateFlow(0L)
    val nursingSideStartedAtMillis: StateFlow<Long> = _nursingSideStartedAtMillis.asStateFlow()

    val careCheckSettings: StateFlow<CareCheckSettings> = repository.careCheckSettings
        .map { it ?: CareCheckSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CareCheckSettings())

    val medicineAlarms: StateFlow<List<MedicineAlarm>> = repository.medicineAlarms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reminderNotificationsEnabled: StateFlow<Boolean> = careCheckSettings
        .map { it.notificationsEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val systemAlarmsEnabled: StateFlow<Boolean> = careCheckSettings
        .map { it.systemAlarmsEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    init {
        if (runtimeOptions.initializeCareSync) {
            BluetoothCareEngine.initialize(application)
        }

        if (runtimeOptions.runBackgroundTimer) {
            // Background timer tick every 1 second
            viewModelScope.launch {
                while (true) {
                    delay(1000)
                    val now = System.currentTimeMillis()
                    _currentTimeMillis.value = now
                    val expired = repository.expireDutyIfNeeded(now)
                    if (expired != null) {
                        BluetoothCareEngine.broadcastDutyRelease(expired)
                    }
                }
            }
        }

        if (runtimeOptions.mirrorCareSyncStatus) {
            // Mirror real Nearby Care Sync status into the dashboard banner
            viewModelScope.launch {
                BluetoothCareEngine.statusText.collect { status ->
                    _syncStatusText.value = status
                }
            }
        }

        if (runtimeOptions.rescheduleRemindersOnStart) {
            viewModelScope.launch {
                ReminderEngine.rescheduleAll(getApplication(), repository)
            }
        }

        // Resume nursing side tracking if a breastfeeding session is already ongoing
        viewModelScope.launch {
            repository.ongoingActivity.collect { ongoing ->
                if (ongoing?.activityType == ActivityTypes.BREASTFEEDING &&
                    _nursingSideStartedAtMillis.value == 0L
                ) {
                    _nursingSideStartedAtMillis.value = System.currentTimeMillis()
                }
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

    val allLogs: StateFlow<List<ActivityLog>> = repository.allLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _timelineMode = MutableStateFlow(TimelineRangeMode.DAY)
    val timelineMode: StateFlow<TimelineRangeMode> = _timelineMode.asStateFlow()

    private val _timelineAnchorMillis = MutableStateFlow(startOfDayMillis(System.currentTimeMillis()))
    val timelineAnchorMillis: StateFlow<Long> = _timelineAnchorMillis.asStateFlow()

    val timelineRange: StateFlow<Pair<Long, Long>> = combine(
        _timelineMode,
        _timelineAnchorMillis
    ) { mode, anchor ->
        computeTimelineRange(mode, anchor)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = computeTimelineRange(TimelineRangeMode.DAY, startOfDayMillis(System.currentTimeMillis()))
    )

    val timelineLogs: StateFlow<List<ActivityLog>> = timelineRange
        .flatMapLatest { (start, end) -> repository.logsForRange(start, end) }
        .stateIn(
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

    val activeDuty: StateFlow<DutySession?> = repository.activeDuty.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val muteNonUrgentWhenOffDuty: StateFlow<Boolean> = BluetoothCareEngine.muteNonUrgentWhenOffDuty

    val vibrateOnReceive: StateFlow<Boolean> = BluetoothCareEngine.vibrateOnReceive

    val outboxPendingCount: StateFlow<Int> = BluetoothCareEngine.outboxPendingCount

    val memories: StateFlow<List<MemoryItem>> = repository.memories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val notes: StateFlow<List<SharedNote>> = repository.notes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val lists: StateFlow<List<SharedList>> = repository.lists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedListSyncId = MutableStateFlow<String?>(null)
    val selectedListSyncId: StateFlow<String?> = _selectedListSyncId.asStateFlow()

    val selectedListItems: StateFlow<List<SharedListItem>> = _selectedListSyncId
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                repository.listItems(id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _mediaBusy = MutableStateFlow(false)
    val mediaBusy: StateFlow<Boolean> = _mediaBusy.asStateFlow()

    private val _mediaError = MutableStateFlow<String?>(null)
    val mediaError: StateFlow<String?> = _mediaError.asStateFlow()

    init {
        viewModelScope.launch {
            combine(activeCaregiver, babyProfile) { caregiver, profile ->
                caregiver to profile
            }.collect { (caregiver, profile) ->
                caregiver?.let {
                    BluetoothCareEngine.setMyCaregiverName(it.name)
                    BluetoothCareEngine.setMyCaregiverRole(it.relationship)
                }
                profile?.let {
                    BluetoothCareEngine.setBabyName(it.name)
                }
            }
        }
    }

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

    // Patterns & Habits analytics
    private val _patternRangeDays = MutableStateFlow(PatternRangeDays.SEVEN)
    val patternRangeDays: StateFlow<PatternRangeDays> = _patternRangeDays.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val patternLogs: StateFlow<List<ActivityLog>> = combine(
        _patternRangeDays,
        currentTimeMillis
    ) { range, now ->
        val startOfToday = startOfDayMillis(now)
        val rangeStart = startOfToday - TimeUnit.DAYS.toMillis((range.days - 1).toLong())
        rangeStart to now
    }.flatMapLatest { (start, end) ->
        repository.logsForRange(start, end)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val patternReport: StateFlow<PatternReport> = combine(
        patternLogs,
        babyProfile,
        _patternRangeDays,
        currentTimeMillis
    ) { logs, profile, range, now ->
        PatternAnalyticsEngine.analyze(logs, profile, range, now)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PatternAnalyticsEngine.analyze(emptyList(), null, PatternRangeDays.SEVEN)
    )

    /** Fixed 7-day highlight for Dashboard (independent of Insights range chips). */
    val dashboardPatternHighlight: StateFlow<PatternReport> = combine(
        allLogs,
        babyProfile,
        currentTimeMillis
    ) { logs, profile, now ->
        PatternAnalyticsEngine.analyze(logs, profile, PatternRangeDays.SEVEN, now)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PatternAnalyticsEngine.analyze(emptyList(), null, PatternRangeDays.SEVEN)
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

    // ---------------- Actions ----------------

    fun setPatternRangeDays(range: PatternRangeDays) {
        _patternRangeDays.value = range
    }

    fun setTimelineMode(mode: TimelineRangeMode) {
        _timelineMode.value = mode
        if (mode == TimelineRangeMode.WEEK) {
            _timelineAnchorMillis.value = startOfWeekMillis(_timelineAnchorMillis.value)
        } else {
            _timelineAnchorMillis.value = startOfDayMillis(_timelineAnchorMillis.value)
        }
    }

    fun shiftTimeline(forward: Boolean) {
        val cal = Calendar.getInstance().apply { timeInMillis = _timelineAnchorMillis.value }
        val amount = if (_timelineMode.value == TimelineRangeMode.WEEK) 7 else 1
        cal.add(Calendar.DAY_OF_YEAR, if (forward) amount else -amount)
        _timelineAnchorMillis.value = if (_timelineMode.value == TimelineRangeMode.WEEK) {
            startOfWeekMillis(cal.timeInMillis)
        } else {
            startOfDayMillis(cal.timeInMillis)
        }
    }

    fun jumpTimelineToToday() {
        val today = startOfDayMillis(System.currentTimeMillis())
        _timelineAnchorMillis.value = if (_timelineMode.value == TimelineRangeMode.WEEK) {
            startOfWeekMillis(today)
        } else {
            today
        }
    }

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
            val now = System.currentTimeMillis()
            val log = ActivityLog(
                babyId = 1,
                activityType = activityType,
                startTimeMillis = now,
                endTimeMillis = null, // null means ongoing
                volumeMl = volumeMl,
                milkType = milkType,
                diaperStatus = diaperStatus,
                leftBreastDurationSec = leftBreastSec,
                rightBreastDurationSec = rightBreastSec,
                notes = notes,
                caregiverName = caregiver.name,
                caregiverRole = caregiver.relationship,
                timestampMillis = now
            )
            val saved = repository.insertLog(log)
            if (activityType == ActivityTypes.BREASTFEEDING) {
                _activeNursingSide.value = NursingSide.LEFT
                _nursingSideStartedAtMillis.value = now
            } else {
                clearNursingSession()
            }
            com.example.engine.BluetoothCareEngine.broadcastLogUpsert(saved)
            BabyCareWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun setNursingSide(side: NursingSide) {
        viewModelScope.launch {
            val ongoing = ongoingActivity.value ?: return@launch
            if (ongoing.activityType != ActivityTypes.BREASTFEEDING) return@launch
            if (side == _activeNursingSide.value) return@launch

            val now = System.currentTimeMillis()
            val sideStarted = _nursingSideStartedAtMillis.value.takeIf { it > 0 }
                ?: ongoing.startTimeMillis
            val elapsedSec = ((now - sideStarted) / 1000).coerceAtLeast(0)
            var left = ongoing.leftBreastDurationSec
            var right = ongoing.rightBreastDurationSec
            when (_activeNursingSide.value) {
                NursingSide.LEFT -> left += elapsedSec
                NursingSide.RIGHT -> right += elapsedSec
            }
            val saved = repository.updateLog(
                ongoing.copy(
                    leftBreastDurationSec = left,
                    rightBreastDurationSec = right
                )
            )
            com.example.engine.BluetoothCareEngine.broadcastLogUpsert(saved)
            _activeNursingSide.value = side
            _nursingSideStartedAtMillis.value = now
        }
    }

    fun stopLiveActivity(finalNotes: String = "") {
        viewModelScope.launch {
            val ongoing = ongoingActivity.value ?: return@launch
            val now = System.currentTimeMillis()
            var left = ongoing.leftBreastDurationSec
            var right = ongoing.rightBreastDurationSec
            if (ongoing.activityType == ActivityTypes.BREASTFEEDING &&
                _nursingSideStartedAtMillis.value > 0
            ) {
                val sideStarted = _nursingSideStartedAtMillis.value
                val elapsedSec = ((now - sideStarted) / 1000).coerceAtLeast(0)
                when (_activeNursingSide.value) {
                    NursingSide.LEFT -> left += elapsedSec
                    NursingSide.RIGHT -> right += elapsedSec
                }
            }
            val durationSec = ((now - ongoing.startTimeMillis) / 1000).coerceAtLeast(0)
            val updated = ongoing.copy(
                endTimeMillis = now,
                durationSeconds = durationSec,
                leftBreastDurationSec = left,
                rightBreastDurationSec = right,
                notes = if (finalNotes.isNotBlank()) finalNotes else ongoing.notes
            )
            val saved = repository.updateLog(updated)
            clearNursingSession()
            com.example.engine.BluetoothCareEngine.broadcastLogUpsert(saved)
            BabyCareWidgetProvider.updateAllWidgets(getApplication())
            if (ReminderTiming.shouldRescheduleForActivity(saved.activityType)) {
                ReminderEngine.cancelAllCareSnoozes(getApplication())
                clearActiveCareAlarmsForActivity(saved.activityType)
                rescheduleReminders()
            }
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
        notes: String = "",
        startTimeMillis: Long? = null,
        endTimeMillis: Long? = null,
        leftBreastSec: Long = 0,
        rightBreastSec: Long = 0
    ) {
        viewModelScope.launch {
            val caregiver = activeCaregiver.value ?: CaregiverProfile(name = "Sarah (Mom)", role = "Owner", relationship = "Mother")
            val now = System.currentTimeMillis()
            val end = endTimeMillis ?: now
            val start = startTimeMillis ?: (end - (durationSeconds * 1000))
            val duration = if (startTimeMillis != null || endTimeMillis != null) {
                ((end - start) / 1000).coerceAtLeast(0)
            } else {
                durationSeconds
            }
            val log = ActivityLog(
                babyId = 1,
                activityType = activityType,
                startTimeMillis = start,
                endTimeMillis = end,
                durationSeconds = duration,
                volumeMl = volumeMl,
                milkType = milkType,
                diaperStatus = diaperStatus,
                medicineName = medicineName,
                dosage = dosage,
                temperatureCelsius = temperatureCelsius,
                leftBreastDurationSec = leftBreastSec,
                rightBreastDurationSec = rightBreastSec,
                notes = notes,
                caregiverName = caregiver.name,
                caregiverRole = caregiver.relationship,
                timestampMillis = start
            )
            val saved = repository.insertLog(log)
            com.example.engine.BluetoothCareEngine.broadcastLogUpsert(saved)
            BabyCareWidgetProvider.updateAllWidgets(getApplication())
            if (ReminderTiming.shouldRescheduleForActivity(saved.activityType)) {
                ReminderEngine.cancelAllCareSnoozes(getApplication())
                clearActiveCareAlarmsForActivity(saved.activityType)
                rescheduleReminders()
            }
        }
    }

    fun editLog(log: ActivityLog) {
        viewModelScope.launch {
            val end = log.endTimeMillis
            val duration = if (end != null && end >= log.startTimeMillis) {
                ((end - log.startTimeMillis) / 1000).coerceAtLeast(0)
            } else {
                log.durationSeconds
            }
            val prepared = log.copy(
                durationSeconds = duration,
                timestampMillis = log.startTimeMillis
            )
            val saved = repository.updateLog(prepared)
            com.example.engine.BluetoothCareEngine.broadcastLogUpsert(saved)
            BabyCareWidgetProvider.updateAllWidgets(getApplication())
            if (ReminderTiming.shouldRescheduleForActivity(saved.activityType)) {
                ReminderEngine.cancelAllCareSnoozes(getApplication())
                clearActiveCareAlarmsForActivity(saved.activityType)
                rescheduleReminders()
            }
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
            val tombstone = repository.softDeleteLog(id)
            if (tombstone != null) {
                com.example.engine.BluetoothCareEngine.broadcastLogDelete(tombstone)
                if (ReminderTiming.shouldRescheduleForActivity(tombstone.activityType)) {
                    ReminderEngine.cancelAllCareSnoozes(getApplication())
                    clearActiveCareAlarmsForActivity(tombstone.activityType)
                    rescheduleReminders()
                }
            }
            BabyCareWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun saveBabyProfile(profile: BabyProfile) {
        viewModelScope.launch {
            val saved = repository.saveProfile(profile)
            BluetoothCareEngine.broadcastProfileUpsert(saved)
            BabyCareWidgetProvider.updateAllWidgets(getApplication())
            rescheduleReminders()
        }
    }

    fun claimDuty(untilMillis: Long?) {
        viewModelScope.launch {
            val caregiver = activeCaregiver.value
                ?: CaregiverProfile(name = "Mom", role = "Owner", relationship = "Mother")
            val session = repository.claimDuty(
                caregiverName = caregiver.name,
                caregiverRole = caregiver.relationship,
                untilMillis = untilMillis,
                deviceId = BluetoothCareEngine.getDeviceId()
            )
            BluetoothCareEngine.broadcastDutyClaim(session)
        }
    }

    /** Claim until a clock hour today (or tomorrow if that hour already passed). */
    fun claimDutyUntilHour(hourOfDay: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        claimDuty(cal.timeInMillis)
    }

    fun claimDutyForHours(hours: Int) {
        claimDuty(System.currentTimeMillis() + hours * 3600_000L)
    }

    fun releaseDuty() {
        viewModelScope.launch {
            val released = repository.releaseDuty(BluetoothCareEngine.getDeviceId()) ?: return@launch
            BluetoothCareEngine.broadcastDutyRelease(released)
        }
    }

    fun setMuteNonUrgentWhenOffDuty(mute: Boolean) {
        BluetoothCareEngine.setMuteNonUrgentWhenOffDuty(mute)
    }

    fun setVibrateOnReceive(enabled: Boolean) {
        BluetoothCareEngine.setVibrateOnReceive(enabled)
    }

    fun setReminderNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.getCareCheckSettingsDirect()
            repository.saveCareCheckSettings(current.copy(notificationsEnabled = enabled))
            rescheduleReminders()
        }
    }

    fun setSystemAlarmsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.getCareCheckSettingsDirect()
            repository.saveCareCheckSettings(current.copy(systemAlarmsEnabled = enabled))
            rescheduleReminders()
        }
    }

    fun updateCareCheckSettings(transform: (CareCheckSettings) -> CareCheckSettings) {
        viewModelScope.launch {
            val current = repository.getCareCheckSettingsDirect()
            val updated = transform(current).copy(sleepEnabled = false)
            // Baby check requires a pilot time before enabling Custom (or App will suggest).
            val babyOn = updated.babyCheckActive()
            val needsPilot = babyOn &&
                !updated.babyCheckUseAppTiming &&
                updated.babyCheckPilotMillis <= 0L
            val sanitized = if (needsPilot) {
                updated.copy(
                    babyCheckReminderEnabled = false,
                    babyCheckAlarmEnabled = false
                )
            } else if (babyOn && updated.babyCheckUseAppTiming && updated.babyCheckPilotMillis <= 0L) {
                updated.copy(
                    babyCheckPilotMillis = ReminderTiming.nextRoundHourMillis(System.currentTimeMillis()),
                    babyCheckIntervalMinutes = ReminderTiming.APP_BABY_CHECK_INTERVAL_MINUTES
                )
            } else {
                updated
            }
            repository.saveCareCheckSettings(sanitized)
            rescheduleReminders()
        }
    }

    fun upsertMedicineAlarm(alarm: MedicineAlarm) {
        viewModelScope.launch {
            if (alarm.enabled && alarm.pilotTimeMillis <= 0L) return@launch
            if (alarm.name.isBlank()) return@launch
            repository.upsertMedicineAlarm(alarm)
            rescheduleReminders()
        }
    }

    fun deleteMedicineAlarm(id: Long) {
        viewModelScope.launch {
            repository.deleteMedicineAlarm(id)
            rescheduleReminders()
        }
    }

    fun setMedicineAlarmEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            val existing = repository.getMedicineAlarmById(id) ?: return@launch
            if (enabled && existing.pilotTimeMillis <= 0L) return@launch
            repository.upsertMedicineAlarm(existing.copy(enabled = enabled))
            rescheduleReminders()
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

    fun clearBackupUiState() {
        _backupUiState.value = BackupUiState.Idle
    }

    fun backupToUri(uri: Uri) {
        viewModelScope.launch {
            _backupUiState.value = BackupUiState.InProgress
            when (val result = FullBackupManager.createBackup(getApplication(), uri)) {
                is FullBackupManager.BackupResult.Success -> {
                    _backupUiState.value = BackupUiState.Success(result.message)
                }
                is FullBackupManager.BackupResult.Failure -> {
                    _backupUiState.value = BackupUiState.Error(result.message)
                }
            }
        }
    }

    fun restoreFromUri(uri: Uri) {
        viewModelScope.launch {
            _backupUiState.value = BackupUiState.InProgress
            BluetoothCareEngine.stopCareSync(getApplication())
            when (val result = FullBackupManager.restoreBackup(getApplication(), uri)) {
                is FullBackupManager.BackupResult.Success -> {
                    _backupUiState.value = BackupUiState.Success(result.message)
                    if (result.requiresAppRestart) {
                        restartAppProcess()
                    }
                }
                is FullBackupManager.BackupResult.Failure -> {
                    _backupUiState.value = BackupUiState.Error(result.message)
                }
            }
        }
    }

    private fun restartAppProcess() {
        val app = getApplication<Application>()
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (launch != null) {
            app.startActivity(launch)
        }
        exitProcess(0)
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
            val saved = repository.insertGrowthRecord(record)
            BluetoothCareEngine.broadcastGrowthUpsert(saved)
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
            val saved = repository.insertMilkStash(item)
            BluetoothCareEngine.broadcastMilkUpsert(saved)
        }
    }

    fun toggleMilestone(milestone: MilestoneRecord) {
        viewModelScope.launch {
            val updated = milestone.copy(
                isAchieved = !milestone.isAchieved,
                achievedDateMillis = if (!milestone.isAchieved) System.currentTimeMillis() else null
            )
            val saved = repository.updateMilestone(updated)
            BluetoothCareEngine.broadcastMilestoneUpsert(saved)
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
            val saved = repository.insertMedicalRecord(record)
            BluetoothCareEngine.broadcastMedicalUpsert(saved)
        }
    }

    fun toggleMedicalRecord(record: MedicalRecord) {
        viewModelScope.launch {
            val saved = repository.updateMedicalRecord(record.copy(isCompleted = !record.isCompleted))
            BluetoothCareEngine.broadcastMedicalUpsert(saved)
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
            val patterns = patternReport.value
            val babyName = profile?.name ?: "Your Baby"
            val ageMonths = babyAgeMonths(profile)
            val feedSummary = "Fed ${summary.feedCount} times today (${summary.totalFeedVolumeMl}ml)"
            val sleepSummary = "Slept ${summary.totalSleepMinutes} minutes across ${summary.napCount} naps"
            val patternSummary = patterns.toAiContextSummary()

            val response = geminiService.askGeminiCaregiver(
                userQuestion = questionText,
                babyName = babyName,
                babyAgeMonths = ageMonths,
                lastFeedingSummary = feedSummary,
                lastSleepSummary = sleepSummary,
                patternSummary = patternSummary
            )

            _isAiThinking.value = false
            val aiMsg = ChatMessage(false, response)
            _chatMessages.value = _chatMessages.value + aiMsg
        }
    }

    private fun babyAgeMonths(profile: BabyProfile?, nowMillis: Long = System.currentTimeMillis()): Double {
        if (profile == null) return 0.0
        val days = ((nowMillis - profile.birthDateMillis).coerceAtLeast(0) / (1000.0 * 60 * 60 * 24))
        return days / 30.4375
    }

    fun completeOnboardingSetup(
        profile: BabyProfile,
        initialWeightKg: Double,
        initialHeightCm: Double
    ) {
        viewModelScope.launch {
            val savedProfile = repository.saveProfile(profile)
            BluetoothCareEngine.broadcastProfileUpsert(savedProfile)

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
            val savedGrowth = repository.insertGrowthRecord(growthRecord)
            BluetoothCareEngine.broadcastGrowthUpsert(savedGrowth)
        }
    }

    private suspend fun rescheduleReminders(context: Context = getApplication()) {
        ReminderEngine.rescheduleAll(context, repository)
    }

    private fun clearActiveCareAlarmsForActivity(activityType: String) {
        val app = getApplication<Application>()
        val kind = when (activityType) {
            ActivityTypes.BREASTFEEDING, ActivityTypes.BOTTLE ->
                BabyNotificationManager.TYPE_FEED
            ActivityTypes.DIAPER -> BabyNotificationManager.TYPE_DIAPER
            ActivityTypes.SLEEP -> BabyNotificationManager.TYPE_SLEEP
            else -> return
        }
        ReminderEngine.cancelCareDelivery(app, kind)
        ActiveAlarmTracker.clear(app, ActiveAlarmTracker.kindKey(kind))
        AlarmSoundController.stop()
        BabyNotificationManager.cancelStickyReminder(
            app,
            BabyNotificationManager.notificationIdForType(kind)
        )
    }

    private fun clearNursingSession() {
        _nursingSideStartedAtMillis.value = 0L
        _activeNursingSide.value = NursingSide.LEFT
    }

    // ---- Memories ----

    fun clearMediaError() {
        _mediaError.value = null
    }

    fun addMemoryFromUri(
        uri: Uri,
        isVideo: Boolean,
        caption: String = "",
        capturedAtMillis: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            _mediaBusy.value = true
            _mediaError.value = null
            try {
                val ctx = getApplication<Application>()
                val caregiver = activeCaregiver.value?.name.orEmpty()
                val result = withContext(Dispatchers.IO) {
                    if (isVideo) {
                        MediaCompressor.importVideoFromUri(ctx, uri)
                    } else {
                        MediaCompressor.compressPhotoFromUri(ctx, uri)
                    }
                }
                if (result == null) {
                    _mediaError.value = if (isVideo) {
                        "Video too large or failed (max ${MediaCompressor.MAX_VIDEO_BYTES / (1024 * 1024)}MB)"
                    } else {
                        "Could not compress photo"
                    }
                    return@launch
                }
                val item = MemoryItem(
                    mediaType = result.mediaType,
                    localPath = result.localPath,
                    thumbPath = result.thumbPath,
                    capturedAtMillis = capturedAtMillis,
                    caption = caption,
                    caregiverName = caregiver,
                    contentHash = result.contentHash,
                    fileSizeBytes = result.fileSizeBytes,
                    mimeType = result.mimeType,
                    syncId = result.syncId
                )
                val saved = repository.insertMemory(item)
                BluetoothCareEngine.broadcastMemoryUpsert(saved)
            } finally {
                _mediaBusy.value = false
            }
        }
    }

    fun addMemoryFromCameraFile(filePath: String, caption: String = "") {
        viewModelScope.launch {
            _mediaBusy.value = true
            _mediaError.value = null
            try {
                val ctx = getApplication<Application>()
                val caregiver = activeCaregiver.value?.name.orEmpty()
                val result = withContext(Dispatchers.IO) {
                    MediaCompressor.compressPhotoFromFile(ctx, java.io.File(filePath))
                }
                if (result == null) {
                    _mediaError.value = "Could not compress photo"
                    return@launch
                }
                val item = MemoryItem(
                    mediaType = result.mediaType,
                    localPath = result.localPath,
                    thumbPath = result.thumbPath,
                    capturedAtMillis = System.currentTimeMillis(),
                    caption = caption,
                    caregiverName = caregiver,
                    contentHash = result.contentHash,
                    fileSizeBytes = result.fileSizeBytes,
                    mimeType = result.mimeType,
                    syncId = result.syncId
                )
                val saved = repository.insertMemory(item)
                BluetoothCareEngine.broadcastMemoryUpsert(saved)
            } finally {
                _mediaBusy.value = false
            }
        }
    }

    fun updateMemoryCaption(memory: MemoryItem, caption: String) {
        viewModelScope.launch {
            val updated = repository.updateMemory(memory.copy(caption = caption))
            BluetoothCareEngine.broadcastMemoryUpsert(updated)
        }
    }

    fun deleteMemory(memory: MemoryItem) {
        viewModelScope.launch {
            val tombstone = repository.softDeleteMemory(memory.id) ?: return@launch
            BluetoothCareEngine.broadcastMemoryUpsert(tombstone)
        }
    }

    // ---- Notes ----

    fun saveNote(
        existing: SharedNote? = null,
        title: String,
        body: String,
        pinnedDateMillis: Long? = null
    ) {
        viewModelScope.launch {
            val caregiver = activeCaregiver.value?.name.orEmpty()
            val saved = if (existing == null) {
                repository.insertNote(
                    SharedNote(
                        title = title,
                        body = body,
                        pinnedDateMillis = pinnedDateMillis,
                        caregiverName = caregiver
                    )
                )
            } else {
                repository.updateNote(
                    existing.copy(
                        title = title,
                        body = body,
                        pinnedDateMillis = pinnedDateMillis,
                        caregiverName = caregiver
                    )
                )
            }
            BluetoothCareEngine.broadcastNoteUpsert(saved)
        }
    }

    fun deleteNote(note: SharedNote) {
        viewModelScope.launch {
            val tombstone = repository.softDeleteNote(note.id) ?: return@launch
            BluetoothCareEngine.broadcastNoteUpsert(tombstone)
        }
    }

    // ---- Lists ----

    fun selectList(syncId: String?) {
        _selectedListSyncId.value = syncId
    }

    fun createList(title: String) {
        viewModelScope.launch {
            val caregiver = activeCaregiver.value?.name.orEmpty()
            val saved = repository.insertList(
                SharedList(title = title, caregiverName = caregiver)
            )
            BluetoothCareEngine.broadcastListUpsert(saved)
            _selectedListSyncId.value = saved.syncId
        }
    }

    fun deleteList(list: SharedList) {
        viewModelScope.launch {
            val tombstone = repository.softDeleteList(list.id) ?: return@launch
            BluetoothCareEngine.broadcastListUpsert(tombstone)
            if (_selectedListSyncId.value == list.syncId) {
                _selectedListSyncId.value = null
            }
        }
    }

    fun addListItem(listSyncId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val order = selectedListItems.value.size
            val saved = repository.insertListItem(
                SharedListItem(listSyncId = listSyncId, text = text.trim(), sortOrder = order)
            )
            BluetoothCareEngine.broadcastListItemUpsert(saved)
        }
    }

    fun toggleListItem(item: SharedListItem) {
        viewModelScope.launch {
            val updated = repository.updateListItem(item.copy(isChecked = !item.isChecked))
            BluetoothCareEngine.broadcastListItemUpsert(updated)
        }
    }

    fun deleteListItem(item: SharedListItem) {
        viewModelScope.launch {
            val tombstone = repository.softDeleteListItem(item.id) ?: return@launch
            BluetoothCareEngine.broadcastListItemUpsert(tombstone)
        }
    }

    /**
     * Next scheduled feed / diaper / nap reminder times for calendar (does not reschedule alarms).
     */
    fun nextReminderTimes(): Triple<Long?, Long?, Long?> {
        val triggers = nextCareCheckTriggers() ?: return Triple(null, null, null)
        return Triple(triggers.feedAtMillis, triggers.diaperAtMillis, triggers.sleepAtMillis)
    }

    fun nextCareCheckTriggers(): CareCheckTriggers? {
        val profile = babyProfile.value ?: return null
        val settings = careCheckSettings.value
        return ReminderTiming.computeCareCheckTriggers(
            profile = profile,
            settings = settings,
            logs = allLogs.value,
            nowMillis = System.currentTimeMillis()
        )
    }

    fun nextRoutineTriggers(): RoutineTriggers? {
        val care = nextCareCheckTriggers() ?: return null
        return RoutineTriggers(
            feedAtMillis = care.feedAtMillis ?: 0L,
            diaperAtMillis = care.diaperAtMillis ?: 0L,
            napAtMillis = care.sleepAtMillis ?: 0L
        )
    }

    companion object {
        fun startOfDayMillis(millis: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = millis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        fun startOfWeekMillis(millis: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = startOfDayMillis(millis)
                val first = firstDayOfWeek
                set(Calendar.DAY_OF_WEEK, first)
                // If calendar jumped forward past the anchor day, go back one week
                if (timeInMillis > startOfDayMillis(millis)) {
                    add(Calendar.WEEK_OF_YEAR, -1)
                }
            }
            return cal.timeInMillis
        }

        fun endOfDayMillis(millis: Long): Long =
            startOfDayMillis(millis) + (24L * 60L * 60L * 1000L) - 1L

        fun computeTimelineRange(mode: TimelineRangeMode, anchorMillis: Long): Pair<Long, Long> {
            return when (mode) {
                TimelineRangeMode.DAY -> {
                    val start = startOfDayMillis(anchorMillis)
                    start to endOfDayMillis(anchorMillis)
                }
                TimelineRangeMode.WEEK -> {
                    val start = startOfWeekMillis(anchorMillis)
                    val end = start + (7L * 24L * 60L * 60L * 1000L) - 1L
                    start to end
                }
            }
        }
    }
}
