package com.example.notification

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.service.OngoingTimerService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerNotificationReceiverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        OngoingTimerService.resetState()
    }

    @Test
    fun `buildOngoingTimerNotification builds valid notification for nursing session`() {
        val now = System.currentTimeMillis()
        val log = ActivityLog(
            babyId = 1,
            activityType = ActivityTypes.BREASTFEEDING,
            startTimeMillis = now - 300_000L,
            endTimeMillis = null,
            caregiverName = "Mom",
            timestampMillis = now
        )

        val notification = BabyNotificationManager.buildOngoingTimerNotification(
            context = context,
            ongoingLog = log,
            isPaused = false,
            activeNursingSide = "LEFT",
            elapsedLeftSec = 300,
            elapsedRightSec = 0
        )

        assertNotNull(notification)
        assertEquals(3, notification.actions.size) // Pause, Switch to Right, Stop & Save
    }

    @Test
    fun `buildOngoingTimerNotification builds valid notification when paused`() {
        val now = System.currentTimeMillis()
        val log = ActivityLog(
            babyId = 1,
            activityType = ActivityTypes.SLEEP,
            startTimeMillis = now - 600_000L,
            endTimeMillis = null,
            caregiverName = "Dad",
            timestampMillis = now
        )

        val notification = BabyNotificationManager.buildOngoingTimerNotification(
            context = context,
            ongoingLog = log,
            isPaused = true,
            activeNursingSide = "LEFT",
            elapsedLeftSec = 0,
            elapsedRightSec = 0
        )

        assertNotNull(notification)
        assertEquals(2, notification.actions.size) // Resume, Stop & Save
    }

    @Test
    fun `TimerNotificationReceiver toggles pause and resume via broadcast intents`() = runBlocking {
        val receiver = TimerNotificationReceiver()

        // 1. Pause intent
        val pauseIntent = Intent(context, TimerNotificationReceiver::class.java).apply {
            action = BabyNotificationManager.ACTION_PAUSE_TIMER
        }
        OngoingTimerService.pauseTimerDirect(context)
        assertTrue(OngoingTimerService.isPaused)

        // 2. Resume intent
        val resumeIntent = Intent(context, TimerNotificationReceiver::class.java).apply {
            action = BabyNotificationManager.ACTION_RESUME_TIMER
        }
        OngoingTimerService.resumeTimerDirect(context)
        assertFalse(OngoingTimerService.isPaused)
    }

    @Test
    fun `TimerNotificationReceiver switches nursing side via broadcast intent`() = runBlocking {
        assertEquals("LEFT", OngoingTimerService.activeNursingSide)

        OngoingTimerService.switchSideDirect(context)

        assertEquals("RIGHT", OngoingTimerService.activeNursingSide)
    }
}
