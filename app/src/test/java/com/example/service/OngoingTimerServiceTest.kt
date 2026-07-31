package com.example.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.BabyCareDatabase
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.repository.BabyCareRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OngoingTimerServiceTest {

    private lateinit var context: Context
    private lateinit var repository: BabyCareRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        val dao = BabyCareDatabase.getDatabase(context).babyCareDao()
        dao.clearAllLogs()
        repository = BabyCareRepository(dao)
        OngoingTimerService.resetState()
    }

    @Test
    fun `switchSide updates Room database log and flips active nursing side`() = runBlocking {
        val now = System.currentTimeMillis()
        val ongoingLog = ActivityLog(
            babyId = 1,
            activityType = ActivityTypes.BREASTFEEDING,
            startTimeMillis = now - 120_000L,
            endTimeMillis = null,
            caregiverName = "Mom",
            timestampMillis = now
        )
        repository.insertLog(ongoingLog)

        OngoingTimerService.nursingSideStartedAtMillis = now - 120_000L
        OngoingTimerService.activeNursingSide = "LEFT"

        // Perform side switch
        OngoingTimerService.switchSideDirect(context)

        val updatedOngoing = repository.getOngoingActivityDirect()
        assertNotNull(updatedOngoing)
        assertEquals("RIGHT", OngoingTimerService.activeNursingSide)
        assertTrue(updatedOngoing!!.leftBreastDurationSec >= 120L)
    }

    @Test
    fun `stopAndSave finalizes ongoing log in Room database and clears state`() = runBlocking {
        val now = System.currentTimeMillis()
        val ongoingLog = ActivityLog(
            babyId = 1,
            activityType = ActivityTypes.SLEEP,
            startTimeMillis = now - 300_000L,
            endTimeMillis = null,
            caregiverName = "Dad",
            timestampMillis = now
        )
        val savedLog = repository.insertLog(ongoingLog)

        // Stop and save
        OngoingTimerService.stopAndSaveDirect(context)

        // Verify ongoing activity is now null (completed)
        val ongoingAfterStop = repository.getOngoingActivityDirect()
        assertNull(ongoingAfterStop)

        // Fetch recent log from DB
        val logs = repository.getRecentLogs(10)
        val completedLog = logs.find { it.id == savedLog.id }
        assertNotNull(completedLog)
        assertNotNull(completedLog!!.endTimeMillis)
        assertTrue(completedLog.durationSeconds >= 300L)
    }
}
