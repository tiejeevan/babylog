package com.example.notification

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.BabyCareDatabase
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.model.MedicineSubjects
import com.example.data.repository.BabyCareRepository
import com.example.engine.ActiveAlarmTracker
import com.example.engine.ReminderEngine
import com.example.ui.theme.BabyCareTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Full-screen phone-style alarm UI shown when a care or medicine alarm fires.
 */
class AlarmRingActivity : ComponentActivity() {

    private var reminderKind: String = BabyNotificationManager.TYPE_FEED
    private var notificationId: Int = 0
    private var medicineAlarmId: Long = 0L
    private var title: String = "Alarm"
    private var message: String = ""
    private var channelId: String = BabyNotificationManager.CHANNEL_PHONE_ALARMS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnScreenOn()
        readExtras()
        AlarmSoundController.start(this)

        val isMedicine = reminderKind == BabyNotificationManager.TYPE_MEDICINE
        setContent {
            BabyCareTheme {
                AlarmRingScreen(
                    title = title,
                    message = message,
                    isMedicine = isMedicine,
                    onPrimary = {
                        if (isMedicine) tookMedicine() else dismissAlarm(logDose = false)
                    },
                    onSnooze = { snoozeAlarm() },
                    onSkip = { dismissAlarm(logDose = false) }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readExtras()
        AlarmSoundController.start(this)
    }

    override fun onDestroy() {
        // Keep ringing if activity is destroyed without dismiss (e.g. config change).
        // Sound is stopped explicitly on dismiss/snooze.
        super.onDestroy()
    }

    private fun readExtras() {
        reminderKind = BabyNotificationManager.normalizeKind(
            intent.getStringExtra(BabyNotificationManager.EXTRA_REMINDER_TYPE)
                ?: BabyNotificationManager.TYPE_FEED
        )
        notificationId = intent.getIntExtra(
            BabyNotificationManager.EXTRA_NOTIFICATION_ID,
            BabyNotificationManager.notificationIdForType(reminderKind)
        )
        medicineAlarmId = intent.getLongExtra(BabyNotificationManager.EXTRA_MEDICINE_ALARM_ID, 0L)
        title = intent.getStringExtra(BabyNotificationManager.EXTRA_TITLE) ?: "Alarm"
        message = intent.getStringExtra(BabyNotificationManager.EXTRA_MESSAGE).orEmpty()
        channelId = intent.getStringExtra(BabyNotificationManager.EXTRA_CHANNEL_ID)
            ?: BabyNotificationManager.CHANNEL_PHONE_ALARMS
    }

    private fun turnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguard.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun tookMedicine() {
        dismissAlarm(logDose = true)
    }

    private fun dismissAlarm(logDose: Boolean) {
        AlarmSoundController.stop()
        val kindKey = ActiveAlarmTracker.kindKey(reminderKind, medicineAlarmId)
        ActiveAlarmTracker.clear(this, kindKey)
        BabyNotificationManager.cancelStickyReminder(this, notificationId)
        ReminderEngine.cancelCareDelivery(this, reminderKind)

        val app = applicationContext
        val kind = reminderKind
        val medId = medicineAlarmId
        val shouldLog = logDose && kind == BabyNotificationManager.TYPE_MEDICINE && medId > 0L
        CoroutineScope(Dispatchers.IO).launch {
            val dao = BabyCareDatabase.getDatabase(app).babyCareDao()
            val repository = BabyCareRepository(dao)
            if (shouldLog) {
                val alarm = repository.getMedicineAlarmById(medId)
                if (alarm != null && alarm.subject == MedicineSubjects.BABY) {
                    val now = System.currentTimeMillis()
                    repository.insertLog(
                        ActivityLog(
                            activityType = ActivityTypes.MEDICINE,
                            startTimeMillis = now,
                            endTimeMillis = now,
                            medicineName = alarm.name,
                            dosage = alarm.doseNote.ifBlank { null },
                            notes = "Logged from medicine reminder"
                        )
                    )
                }
            }
            ReminderEngine.acknowledgeAndReschedule(
                context = app,
                repository = repository,
                reminderKind = kind,
                medicineAlarmId = medId
            )
        }
        finish()
    }

    private fun snoozeAlarm() {
        AlarmSoundController.stop()
        // Keep ActiveAlarmTracker set so rescheduleAll does not immediately
        // re-fire an overdue medicine/care check while the 10-minute snooze is pending.
        BabyNotificationManager.cancelStickyReminder(this, notificationId)

        val app = applicationContext
        val t = title
        val m = message
        val kind = reminderKind
        val ch = channelId
        val medId = medicineAlarmId
        CoroutineScope(Dispatchers.IO).launch {
            val dao = BabyCareDatabase.getDatabase(app).babyCareDao()
            val repository = BabyCareRepository(dao)
            val settings = ReminderEngine.ensureMigrated(app, repository)
            if (settings.deliveryEnabled()) {
                BabyNotificationManager.scheduleSnooze(
                    context = app,
                    reminderType = kind,
                    title = t,
                    message = m,
                    channelId = ch,
                    settings = settings,
                    medicineAlarmId = medId
                )
            }
            ReminderEngine.rescheduleAll(app, repository)
        }
        finish()
    }
}

@Composable
private fun AlarmRingScreen(
    title: String,
    message: String,
    isMedicine: Boolean,
    onPrimary: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isMedicine) "MEDICINE" else "ALARM",
            color = Color(0xFFFFB74D),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            textAlign = TextAlign.Center
        )
        if (message.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                color = Color(0xFFBDBDBD),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047))
        ) {
            Text(
                text = if (isMedicine) "Took it" else "Dismiss",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSnooze,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Snooze 10 minutes", fontSize = 16.sp)
        }
        if (isMedicine) {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onSkip) {
                Text(
                    text = "Skip (don’t log)",
                    color = Color(0xFF9E9E9E),
                    fontSize = 14.sp
                )
            }
        }
    }
}
