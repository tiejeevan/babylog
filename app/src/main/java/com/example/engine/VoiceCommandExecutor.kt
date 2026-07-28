package com.example.engine

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.data.database.BabyCareDatabase
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.model.CaregiverProfile
import com.example.data.repository.BabyCareRepository
import com.example.notification.BabyNotificationManager
import com.example.widget.BabyCareWidgetProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VoiceCommandExecutor {

    data class Result(
        val success: Boolean,
        val confirmationTitle: String,
        val confirmationMessage: String
    )

    suspend fun execute(context: Context, command: VoiceCommand): Result {
        val app = context.applicationContext
        val dao = BabyCareDatabase.getDatabase(app).babyCareDao()
        val repository = BabyCareRepository(dao)
        val caregiver = repository.getActiveCaregiverDirect()
            ?: CaregiverProfile(name = "Sarah (Mom)", role = "Owner", relationship = "Mother")
        val now = System.currentTimeMillis()
        val timeLabel = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(now))

        val result = when (command) {
            VoiceCommand.CODE_BROWN -> logDiaper(
                app = app,
                repository = repository,
                caregiver = caregiver,
                status = "Dirty",
                notes = "Voice: code brown",
                now = now,
                confirmationTitle = "Diaper logged",
                confirmationMessage = "Dirty · $timeLabel"
            )
            VoiceCommand.CODE_YELLOW -> logDiaper(
                app = app,
                repository = repository,
                caregiver = caregiver,
                status = "Wet",
                notes = "Voice: code yellow",
                now = now,
                confirmationTitle = "Diaper logged",
                confirmationMessage = "Wet · $timeLabel"
            )
            VoiceCommand.FEEDING_BABY -> logBottle(
                app = app,
                repository = repository,
                caregiver = caregiver,
                notes = "Voice: feeding baby",
                now = now,
                confirmationTitle = "Bottle logged",
                confirmationMessage = "Edit volume when free · $timeLabel"
            )
            VoiceCommand.NURSE_BABY -> startNursing(
                app = app,
                repository = repository,
                caregiver = caregiver,
                notes = "Voice: nurse baby",
                now = now,
                confirmationTitle = "Nursing started",
                confirmationMessage = "Timer running · $timeLabel"
            )
        }

        if (result.success) {
            VoiceCommandPrefs.setLastFiredAt(app, command, now)
            BabyNotificationManager.showVoiceCommandConfirmation(
                app,
                result.confirmationTitle,
                result.confirmationMessage
            )
            lightVibrate(app)
        } else if (command == VoiceCommand.NURSE_BABY) {
            BabyNotificationManager.showVoiceCommandConfirmation(
                app,
                result.confirmationTitle,
                result.confirmationMessage
            )
        }

        return result
    }

    private suspend fun logDiaper(
        app: Context,
        repository: BabyCareRepository,
        caregiver: CaregiverProfile,
        status: String,
        notes: String,
        now: Long,
        confirmationTitle: String,
        confirmationMessage: String
    ): Result {
        val log = ActivityLog(
            babyId = 1,
            activityType = ActivityTypes.DIAPER,
            startTimeMillis = now,
            endTimeMillis = now,
            durationSeconds = 0,
            diaperStatus = status,
            notes = notes,
            caregiverName = caregiver.name,
            caregiverRole = caregiver.relationship,
            timestampMillis = now
        )
        return persistCompleted(app, repository, log, confirmationTitle, confirmationMessage)
    }

    private suspend fun logBottle(
        app: Context,
        repository: BabyCareRepository,
        caregiver: CaregiverProfile,
        notes: String,
        now: Long,
        confirmationTitle: String,
        confirmationMessage: String
    ): Result {
        val log = ActivityLog(
            babyId = 1,
            activityType = ActivityTypes.BOTTLE,
            startTimeMillis = now,
            endTimeMillis = now,
            durationSeconds = 0,
            volumeMl = 0,
            milkType = "Breast Milk",
            notes = notes,
            caregiverName = caregiver.name,
            caregiverRole = caregiver.relationship,
            timestampMillis = now
        )
        return persistCompleted(app, repository, log, confirmationTitle, confirmationMessage)
    }

    private suspend fun startNursing(
        app: Context,
        repository: BabyCareRepository,
        caregiver: CaregiverProfile,
        notes: String,
        now: Long,
        confirmationTitle: String,
        confirmationMessage: String
    ): Result {
        val ongoing = repository.getOngoingActivityDirect()
        if (ongoing?.activityType == ActivityTypes.BREASTFEEDING) {
            return Result(
                success = false,
                confirmationTitle = "Already nursing",
                confirmationMessage = "A breastfeeding session is already running"
            )
        }
        val log = ActivityLog(
            babyId = 1,
            activityType = ActivityTypes.BREASTFEEDING,
            startTimeMillis = now,
            endTimeMillis = null,
            volumeMl = 0,
            milkType = "Breast Milk",
            notes = notes,
            caregiverName = caregiver.name,
            caregiverRole = caregiver.relationship,
            timestampMillis = now
        )
        val saved = repository.insertLog(log)
        BluetoothCareEngine.broadcastLogUpsert(saved)
        BabyCareWidgetProvider.updateAllWidgets(app)
        return Result(true, confirmationTitle, confirmationMessage)
    }

    private suspend fun persistCompleted(
        app: Context,
        repository: BabyCareRepository,
        log: ActivityLog,
        confirmationTitle: String,
        confirmationMessage: String
    ): Result {
        val saved = repository.insertLog(log)
        BluetoothCareEngine.broadcastLogUpsert(saved)
        BabyCareWidgetProvider.updateAllWidgets(app)
        if (ReminderTiming.shouldRescheduleForActivity(saved.activityType)) {
            ReminderEngine.cancelAllCareSnoozes(app)
            ReminderEngine.rescheduleAll(app, repository)
        }
        return Result(true, confirmationTitle, confirmationMessage)
    }

    private fun lightVibrate(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            } ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(80)
            }
        } catch (_: Exception) {
        }
    }
}
