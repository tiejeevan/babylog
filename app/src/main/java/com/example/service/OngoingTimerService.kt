package com.example.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.example.data.database.BabyCareDatabase
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.repository.BabyCareRepository
import com.example.engine.BluetoothCareEngine
import com.example.engine.ReminderEngine
import com.example.notification.BabyNotificationManager
import com.example.widget.BabyCareWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OngoingTimerService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var repository: BabyCareRepository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val dao = BabyCareDatabase.getDatabase(applicationContext).babyCareDao()
        repository = BabyCareRepository(dao)

        serviceScope.launch {
            repository.ongoingActivity.collectLatest { ongoing ->
                if (ongoing == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    if (nursingSideStartedAtMillis <= 0L) {
                        nursingSideStartedAtMillis = ongoing.startTimeMillis
                    }
                    postOrUpdateNotification(ongoing)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pauseTimer(applicationContext)
            ACTION_RESUME -> resumeTimer(applicationContext)
            ACTION_SWITCH_SIDE -> switchSide(applicationContext)
            ACTION_STOP_AND_SAVE -> stopAndSave(applicationContext)
            ACTION_START -> {
                serviceScope.launch {
                    val ongoing = repository.getOngoingActivityDirect()
                    if (ongoing != null) {
                        postOrUpdateNotification(ongoing)
                    }
                }
            }
        }
        return START_STICKY
    }

    private suspend fun postOrUpdateNotification(ongoing: ActivityLog) {
        val now = System.currentTimeMillis()
        var leftSec = ongoing.leftBreastDurationSec
        var rightSec = ongoing.rightBreastDurationSec

        if (ongoing.activityType == ActivityTypes.BREASTFEEDING && !isPaused && nursingSideStartedAtMillis > 0) {
            val elapsedSec = ((now - nursingSideStartedAtMillis) / 1000).coerceAtLeast(0)
            if (activeNursingSide == "LEFT") {
                leftSec += elapsedSec
            } else {
                rightSec += elapsedSec
            }
        }

        val notification = BabyNotificationManager.buildOngoingTimerNotification(
            context = this@OngoingTimerService,
            ongoingLog = ongoing,
            isPaused = isPaused,
            activeNursingSide = activeNursingSide,
            elapsedLeftSec = leftSec,
            elapsedRightSec = rightSec
        )

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(
                this@OngoingTimerService,
                BabyNotificationManager.NOTIFICATION_ID_ONGOING_TIMER,
                notification,
                foregroundType
            )
        } catch (e: Exception) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(BabyNotificationManager.NOTIFICATION_ID_ONGOING_TIMER, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        const val ACTION_START = "com.example.service.ACTION_START_ONGOING_TIMER"
        const val ACTION_PAUSE = "com.example.service.ACTION_PAUSE_ONGOING_TIMER"
        const val ACTION_RESUME = "com.example.service.ACTION_RESUME_ONGOING_TIMER"
        const val ACTION_SWITCH_SIDE = "com.example.service.ACTION_SWITCH_SIDE_ONGOING_TIMER"
        const val ACTION_STOP_AND_SAVE = "com.example.service.ACTION_STOP_ONGOING_TIMER"

        @Volatile var isPaused: Boolean = false
        @Volatile var pauseStartedAtMillis: Long = 0L
        @Volatile var accumulatedPausedMs: Long = 0L
        @Volatile var activeNursingSide: String = "LEFT"
        @Volatile var nursingSideStartedAtMillis: Long = 0L

        fun start(context: Context) {
            val intent = Intent(context, OngoingTimerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        suspend fun pauseTimerDirect(context: Context) {
            withContext(Dispatchers.IO) {
                val dao = BabyCareDatabase.getDatabase(context.applicationContext).babyCareDao()
                val repository = BabyCareRepository(dao)
                val ongoing = repository.getOngoingActivityDirect()
                val now = System.currentTimeMillis()

                if (ongoing != null && ongoing.activityType == ActivityTypes.BREASTFEEDING && nursingSideStartedAtMillis > 0 && !isPaused) {
                    val elapsedSec = ((now - nursingSideStartedAtMillis) / 1000).coerceAtLeast(0)
                    var left = ongoing.leftBreastDurationSec
                    var right = ongoing.rightBreastDurationSec
                    if (activeNursingSide == "LEFT") left += elapsedSec else right += elapsedSec
                    repository.updateLog(ongoing.copy(leftBreastDurationSec = left, rightBreastDurationSec = right))
                }

                isPaused = true
                pauseStartedAtMillis = now
                triggerServiceUpdate(context)
            }
        }

        fun pauseTimer(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                pauseTimerDirect(context)
            }
        }

        suspend fun resumeTimerDirect(context: Context) {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                if (pauseStartedAtMillis > 0) {
                    accumulatedPausedMs += (now - pauseStartedAtMillis)
                    pauseStartedAtMillis = 0L
                }
                isPaused = false
                nursingSideStartedAtMillis = now
                triggerServiceUpdate(context)
            }
        }

        fun resumeTimer(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                resumeTimerDirect(context)
            }
        }

        suspend fun switchSideDirect(context: Context) {
            withContext(Dispatchers.IO) {
                val dao = BabyCareDatabase.getDatabase(context.applicationContext).babyCareDao()
                val repository = BabyCareRepository(dao)
                val ongoing = repository.getOngoingActivityDirect()
                val now = System.currentTimeMillis()

                if (ongoing != null && ongoing.activityType == ActivityTypes.BREASTFEEDING && nursingSideStartedAtMillis > 0 && !isPaused) {
                    val elapsedSec = ((now - nursingSideStartedAtMillis) / 1000).coerceAtLeast(0)
                    var left = ongoing.leftBreastDurationSec
                    var right = ongoing.rightBreastDurationSec
                    if (activeNursingSide == "LEFT") left += elapsedSec else right += elapsedSec
                    repository.updateLog(ongoing.copy(leftBreastDurationSec = left, rightBreastDurationSec = right))
                }

                activeNursingSide = if (activeNursingSide == "LEFT") "RIGHT" else "LEFT"
                nursingSideStartedAtMillis = now
                triggerServiceUpdate(context)
            }
        }

        fun switchSide(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                switchSideDirect(context)
            }
        }

        suspend fun stopAndSaveDirect(context: Context) {
            withContext(Dispatchers.IO) {
                val dao = BabyCareDatabase.getDatabase(context.applicationContext).babyCareDao()
                val repository = BabyCareRepository(dao)
                val ongoing = repository.getOngoingActivityDirect() ?: return@withContext

                val now = System.currentTimeMillis()
                var left = ongoing.leftBreastDurationSec
                var right = ongoing.rightBreastDurationSec

                if (ongoing.activityType == ActivityTypes.BREASTFEEDING &&
                    nursingSideStartedAtMillis > 0 && !isPaused
                ) {
                    val elapsedSec = ((now - nursingSideStartedAtMillis) / 1000).coerceAtLeast(0)
                    if (activeNursingSide == "LEFT") {
                        left += elapsedSec
                    } else {
                        right += elapsedSec
                    }
                }

                val totalPausedTime = accumulatedPausedMs + (if (isPaused && pauseStartedAtMillis > 0) now - pauseStartedAtMillis else 0L)
                val effectiveDurationSec = ((now - ongoing.startTimeMillis - totalPausedTime) / 1000).coerceAtLeast(0)

                val updated = ongoing.copy(
                    endTimeMillis = now,
                    durationSeconds = effectiveDurationSec,
                    leftBreastDurationSec = left,
                    rightBreastDurationSec = right
                )

                val saved = repository.updateLog(updated)
                resetState()

                ReminderEngine.clearActiveCareAlarmsForActivity(context.applicationContext, saved.activityType)
                ReminderEngine.cancelAllCareSnoozes(context.applicationContext)
                ReminderEngine.rescheduleAll(context.applicationContext, repository)

                BluetoothCareEngine.broadcastLogUpsert(saved)
                BabyCareWidgetProvider.updateAllWidgets(context.applicationContext)
            }
        }

        fun stopAndSave(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                stopAndSaveDirect(context)
            }
        }

        fun resetState() {
            isPaused = false
            pauseStartedAtMillis = 0L
            accumulatedPausedMs = 0L
            activeNursingSide = "LEFT"
            nursingSideStartedAtMillis = 0L
        }

        private fun triggerServiceUpdate(context: Context) {
            val intent = Intent(context, OngoingTimerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
