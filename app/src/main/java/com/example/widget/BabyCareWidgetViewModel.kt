package com.example.widget

import android.content.Context
import com.example.data.database.BabyCareDatabase
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.model.BabyProfile
import com.example.engine.BluetoothCareEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

data class WidgetUiState(
    val babyName: String = "Your Baby",
    val ongoingActivity: ActivityLog? = null,
    val feedCount: Int = 0,
    val feedVolumeMl: Int = 0,
    val sleepHours: Long = 0,
    val sleepMinutes: Long = 0,
    val napCount: Int = 0,
    val diaperCount: Int = 0,
    val lastFeedText: String = "Never",
    val lastDiaperText: String = "Never",
    val lastUpdatedMillis: Long = System.currentTimeMillis()
)

object BabyCareWidgetViewModel {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isAutoSyncInitialized = false
    private var liveTickerJob: Job? = null

    private val _widgetState = MutableStateFlow(WidgetUiState())
    val widgetState: StateFlow<WidgetUiState> = _widgetState.asStateFlow()

    /**
     * Initializes reactive Room database observation.
     * Whenever Room emits changes in baby profile or activity logs,
     * this ViewModel-like layer automatically recalculates the widget state
     * and pushes layout updates to all placed home screen widgets.
     */
    fun initAutoSync(context: Context) {
        val appContext = context.applicationContext
        if (isAutoSyncInitialized) return
        isAutoSyncInitialized = true

        WidgetLogger.log(appContext, "Initializing BabyCareWidgetViewModel reactive Room listener")

        scope.launch {
            try {
                val dao = BabyCareDatabase.getDatabase(appContext).babyCareDao()

                combine(
                    dao.getBabyProfileFlow(),
                    dao.getOngoingActivityFlow(),
                    dao.getAllLogsFlow()
                ) { profile, ongoing, allLogs ->
                    computeUiState(profile, ongoing, allLogs)
                }.collect { newState ->
                    _widgetState.value = newState
                    WidgetLogger.log(appContext, "Room database changed reactively -> Updating widgets (Ongoing: ${newState.ongoingActivity?.activityType ?: "None"})")
                    
                    // Manage live ticker loop for ongoing activities
                    manageLiveTicker(appContext, newState.ongoingActivity)
                    
                    BabyCareWidgetProvider.updateAllWidgets(appContext)
                }
            } catch (e: Exception) {
                isAutoSyncInitialized = false
                WidgetLogger.log(appContext, "Error in BabyCareWidgetViewModel reactive stream", isError = true, throwable = e)
            }
        }
    }

    private fun manageLiveTicker(appContext: Context, ongoing: ActivityLog?) {
        liveTickerJob?.cancel()
        if (ongoing != null) {
            liveTickerJob = scope.launch {
                WidgetLogger.log(appContext, "Started 1-second live ticker loop for widget")
                while (true) {
                    delay(1000)
                    BabyCareWidgetProvider.updateAllWidgets(appContext)
                }
            }
        }
    }

    /**
     * Computes or returns current widget UI state.
     */
    suspend fun getWidgetState(context: Context): WidgetUiState = withContext(Dispatchers.IO) {
        try {
            val dao = BabyCareDatabase.getDatabase(context).babyCareDao()
            val profile = dao.getBabyProfile()
            val ongoing = dao.getOngoingActivity()
            val allLogs = dao.getRecentLogs(100)

            val state = computeUiState(profile, ongoing, allLogs)
            _widgetState.value = state
            state
        } catch (e: Exception) {
            WidgetLogger.log(context, "Error fetching widget state in BabyCareWidgetViewModel", isError = true, throwable = e)
            _widgetState.value
        }
    }

    private fun computeUiState(
        profile: BabyProfile?,
        ongoing: ActivityLog?,
        allLogs: List<ActivityLog>
    ): WidgetUiState {
        val babyName = profile?.name ?: "Your Baby"

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStartMillis = cal.timeInMillis

        // Logs starting today or ongoing
        val todayLogs = allLogs.filter { it.startTimeMillis >= todayStartMillis || it.endTimeMillis == null }

        // Feeds
        val feedLogs = todayLogs.filter { it.activityType in listOf(ActivityTypes.BOTTLE, ActivityTypes.BREASTFEEDING, ActivityTypes.PUMPING) }
        val feedCount = feedLogs.size
        val totalVolume = feedLogs.sumOf { it.volumeMl }

        // Sleep
        val now = System.currentTimeMillis()
        val sleepLogs = todayLogs.filter { it.activityType == ActivityTypes.SLEEP }
        var totalSleepSec = 0L
        for (s in sleepLogs) {
            val end = s.endTimeMillis ?: now
            val start = s.startTimeMillis.coerceAtLeast(todayStartMillis)
            totalSleepSec += ((end - start) / 1000).coerceAtLeast(0)
        }
        val sleepHours = totalSleepSec / 3600
        val sleepMins = (totalSleepSec % 3600) / 60

        // Diapers
        val diaperCount = todayLogs.count { it.activityType == ActivityTypes.DIAPER }

        // Last Feed & Last Diaper calculation
        val lastFeedLog = allLogs.filter { it.activityType in listOf(ActivityTypes.BOTTLE, ActivityTypes.BREASTFEEDING, ActivityTypes.PUMPING) }
            .maxByOrNull { it.startTimeMillis }
        val lastDiaperLog = allLogs.filter { it.activityType == ActivityTypes.DIAPER }
            .maxByOrNull { it.startTimeMillis }

        val lastFeedText = if (lastFeedLog != null) formatTimeAgo(now - lastFeedLog.startTimeMillis) else "Never"
        val lastDiaperText = if (lastDiaperLog != null) formatTimeAgo(now - lastDiaperLog.startTimeMillis) else "Never"

        return WidgetUiState(
            babyName = babyName,
            ongoingActivity = ongoing,
            feedCount = feedCount,
            feedVolumeMl = totalVolume,
            sleepHours = sleepHours,
            sleepMinutes = sleepMins,
            napCount = sleepLogs.size,
            diaperCount = diaperCount,
            lastFeedText = lastFeedText,
            lastDiaperText = lastDiaperText,
            lastUpdatedMillis = now
        )
    }

    private fun formatTimeAgo(diffMillis: Long): String {
        val minutes = (diffMillis / (1000 * 60)).coerceAtLeast(0)
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            else -> {
                val hrs = minutes / 60
                val remainingMins = minutes % 60
                if (remainingMins == 0L) "${hrs}h ago" else "${hrs}h ${remainingMins}m ago"
            }
        }
    }

    // Widget Action Handlers
    fun handleStartSleep(context: Context, onComplete: (() -> Unit)? = null) {
        scope.launch {
            try {
                val dao = BabyCareDatabase.getDatabase(context).babyCareDao()
                val ongoing = dao.getOngoingActivity()
                val now = System.currentTimeMillis()
                if (ongoing != null) {
                    val durationSec = (now - ongoing.startTimeMillis) / 1000
                    val stopped = ongoing.copy(
                        endTimeMillis = now,
                        durationSeconds = durationSec,
                        updatedAtMillis = now
                    )
                    dao.updateLog(stopped)
                    BluetoothCareEngine.broadcastLogUpsert(stopped)
                }
                val newLog = ActivityLog(
                    babyId = 1,
                    activityType = ActivityTypes.SLEEP,
                    startTimeMillis = now,
                    endTimeMillis = null,
                    caregiverName = "Widget Quick Care",
                    caregiverRole = "Caregiver",
                    timestampMillis = now,
                    notes = "Logged via Widget Shortcut",
                    syncId = UUID.randomUUID().toString(),
                    updatedAtMillis = now
                )
                val rowId = dao.insertLog(newLog)
                BluetoothCareEngine.broadcastLogUpsert(newLog.copy(id = rowId))
                WidgetLogger.log(context, "Started Sleep via BabyCareWidgetViewModel")
            } catch (e: Exception) {
                WidgetLogger.log(context, "Error starting sleep in ViewModel", isError = true, throwable = e)
            } finally {
                onComplete?.invoke()
            }
        }
    }

    fun handleStartNursing(context: Context, onComplete: (() -> Unit)? = null) {
        scope.launch {
            try {
                val dao = BabyCareDatabase.getDatabase(context).babyCareDao()
                val ongoing = dao.getOngoingActivity()
                val now = System.currentTimeMillis()
                if (ongoing != null) {
                    val durationSec = (now - ongoing.startTimeMillis) / 1000
                    val stopped = ongoing.copy(
                        endTimeMillis = now,
                        durationSeconds = durationSec,
                        updatedAtMillis = now
                    )
                    dao.updateLog(stopped)
                    BluetoothCareEngine.broadcastLogUpsert(stopped)
                }
                val newLog = ActivityLog(
                    babyId = 1,
                    activityType = ActivityTypes.BREASTFEEDING,
                    startTimeMillis = now,
                    endTimeMillis = null,
                    caregiverName = "Widget Quick Care",
                    caregiverRole = "Caregiver",
                    timestampMillis = now,
                    notes = "Logged via Widget Shortcut",
                    syncId = UUID.randomUUID().toString(),
                    updatedAtMillis = now
                )
                val rowId = dao.insertLog(newLog)
                BluetoothCareEngine.broadcastLogUpsert(newLog.copy(id = rowId))
                WidgetLogger.log(context, "Started Breastfeeding via BabyCareWidgetViewModel")
            } catch (e: Exception) {
                WidgetLogger.log(context, "Error starting breastfeeding in ViewModel", isError = true, throwable = e)
            } finally {
                onComplete?.invoke()
            }
        }
    }

    fun handleStartFeed(context: Context, onComplete: (() -> Unit)? = null) {
        scope.launch {
            try {
                val dao = BabyCareDatabase.getDatabase(context).babyCareDao()
                val ongoing = dao.getOngoingActivity()
                val now = System.currentTimeMillis()
                if (ongoing != null) {
                    val durationSec = (now - ongoing.startTimeMillis) / 1000
                    val stopped = ongoing.copy(
                        endTimeMillis = now,
                        durationSeconds = durationSec,
                        updatedAtMillis = now
                    )
                    dao.updateLog(stopped)
                    BluetoothCareEngine.broadcastLogUpsert(stopped)
                }
                val newLog = ActivityLog(
                    babyId = 1,
                    activityType = ActivityTypes.BOTTLE,
                    startTimeMillis = now,
                    endTimeMillis = null,
                    volumeMl = 120,
                    milkType = "Formula",
                    caregiverName = "Widget Quick Care",
                    caregiverRole = "Caregiver",
                    timestampMillis = now,
                    notes = "Logged via Widget Shortcut",
                    syncId = UUID.randomUUID().toString(),
                    updatedAtMillis = now
                )
                val rowId = dao.insertLog(newLog)
                BluetoothCareEngine.broadcastLogUpsert(newLog.copy(id = rowId))
                WidgetLogger.log(context, "Started Bottle Feed via BabyCareWidgetViewModel")
            } catch (e: Exception) {
                WidgetLogger.log(context, "Error starting feed in ViewModel", isError = true, throwable = e)
            } finally {
                onComplete?.invoke()
            }
        }
    }

    fun handleStopActivity(context: Context, onComplete: (() -> Unit)? = null) {
        scope.launch {
            try {
                val dao = BabyCareDatabase.getDatabase(context).babyCareDao()
                val ongoing = dao.getOngoingActivity()
                if (ongoing != null) {
                    val now = System.currentTimeMillis()
                    val durationSec = (now - ongoing.startTimeMillis) / 1000
                    val stopped = ongoing.copy(
                        endTimeMillis = now,
                        durationSeconds = durationSec,
                        updatedAtMillis = now
                    )
                    dao.updateLog(stopped)
                    BluetoothCareEngine.broadcastLogUpsert(stopped)
                    WidgetLogger.log(context, "Stopped live activity ${ongoing.activityType} via ViewModel")
                }
            } catch (e: Exception) {
                WidgetLogger.log(context, "Error stopping activity in ViewModel", isError = true, throwable = e)
            } finally {
                onComplete?.invoke()
            }
        }
    }

    fun handleTogglePause(context: Context, onComplete: (() -> Unit)? = null) {
        scope.launch {
            try {
                val dao = BabyCareDatabase.getDatabase(context).babyCareDao()
                val ongoing = dao.getOngoingActivity()
                if (ongoing != null) {
                    val currentNotes = ongoing.notes
                    val updatedNotes = if (currentNotes.contains("[Paused]")) {
                        currentNotes.replace(" [Paused]", "")
                    } else {
                        "$currentNotes [Paused]"
                    }
                    val updated = ongoing.copy(
                        notes = updatedNotes,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                    dao.updateLog(updated)
                    BluetoothCareEngine.broadcastLogUpsert(updated)
                    WidgetLogger.log(context, "Toggled pause status on activity ${ongoing.activityType}")
                }
            } catch (e: Exception) {
                WidgetLogger.log(context, "Error toggling pause in ViewModel", isError = true, throwable = e)
            } finally {
                onComplete?.invoke()
            }
        }
    }
}
