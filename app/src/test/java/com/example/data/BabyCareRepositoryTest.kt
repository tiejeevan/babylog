package com.example.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.model.BabyProfile
import com.example.data.model.CaregiverProfile
import com.example.data.model.GrowthRecord
import com.example.data.repository.BabyCareRepository
import com.example.testing.TestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BabyCareRepositoryTest {

    private lateinit var db: com.example.data.database.BabyCareDatabase
    private lateinit var repository: BabyCareRepository

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val pair = TestDatabase.createRepository(app)
        db = pair.first
        repository = pair.second
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun saveAndReadBabyProfile() = runBlocking {
        val saved = repository.saveProfile(
            BabyProfile(name = "Luna", isInitialSetupDone = true)
        )
        val loaded = repository.getBabyProfileDirect()
        assertEquals("Luna", loaded?.name)
        assertTrue(loaded!!.isInitialSetupDone)
        assertTrue(saved.updatedAtMillis > 0)
    }

    @Test
    fun insertLogAppearsInRecentAndRangeQueries() = runBlocking {
        val now = System.currentTimeMillis()
        val log = repository.insertLog(
            ActivityLog(
                activityType = ActivityTypes.BOTTLE,
                startTimeMillis = now,
                endTimeMillis = now,
                durationSeconds = 300,
                volumeMl = 120,
                milkType = "Formula"
            )
        )
        assertTrue(log.id > 0)
        assertTrue(log.syncId.isNotBlank())

        val recent = repository.getRecentLogs(10)
        assertEquals(1, recent.size)
        assertEquals(ActivityTypes.BOTTLE, recent.first().activityType)
        assertEquals(120, recent.first().volumeMl)

        val ranged = repository.logsForRange(now - 1_000, now + 1_000).first()
        assertEquals(1, ranged.size)
    }

    @Test
    fun softDeleteHidesLogFromRecentButKeepsTombstone() = runBlocking {
        val inserted = repository.insertLog(
            ActivityLog(activityType = ActivityTypes.DIAPER, diaperStatus = "Wet")
        )
        val tombstone = repository.softDeleteLog(inserted.id)
        assertNotNull(tombstone)
        assertTrue(tombstone!!.isDeleted)

        assertTrue(repository.getRecentLogs(10).none { it.id == inserted.id })
        assertTrue(db.babyCareDao().getLogById(inserted.id)!!.isDeleted)
    }

    @Test
    fun ongoingActivityClearsWhenEnded() = runBlocking {
        repository.insertLog(
            ActivityLog(
                activityType = ActivityTypes.SLEEP,
                startTimeMillis = System.currentTimeMillis(),
                endTimeMillis = null
            )
        )
        assertNotNull(db.babyCareDao().getOngoingActivity())

        val ongoing = db.babyCareDao().getOngoingActivity()!!
        repository.updateLog(
            ongoing.copy(
                endTimeMillis = System.currentTimeMillis(),
                durationSeconds = 60
            )
        )
        assertNull(db.babyCareDao().getOngoingActivity())
    }

    @Test
    fun caregiverPinVerificationSwitchesActive() = runBlocking {
        repository.insertCaregiver(
            CaregiverProfile(
                id = 1,
                name = "Mom",
                role = "Owner",
                relationship = "Mother",
                pin = "1234",
                isActiveNow = true
            )
        )
        repository.insertCaregiver(
            CaregiverProfile(
                id = 2,
                name = "Dad",
                role = "Admin",
                relationship = "Father",
                pin = "5678",
                isActiveNow = false
            )
        )

        assertFalse(repository.verifyAndSetActiveCaregiver(2, "0000"))
        assertTrue(repository.verifyAndSetActiveCaregiver(2, "5678"))

        val active = repository.activeCaregiver.first()
        assertEquals("Dad", active?.name)
    }

    @Test
    fun growthRecordRoundTrip() = runBlocking {
        val record = repository.insertGrowthRecord(
            GrowthRecord(
                babyId = 1,
                weightKg = 4.2,
                heightCm = 54.0,
                headCircumferenceCm = 36.0,
                dateMillis = System.currentTimeMillis()
            )
        )
        assertTrue(record.id > 0)
        val all = repository.growthRecords.first()
        assertEquals(1, all.size)
        assertEquals(4.2, all.first().weightKg, 0.001)
    }

    @Test
    fun claimAndReleaseDuty() = runBlocking {
        val session = repository.claimDuty(
            caregiverName = "Mom",
            caregiverRole = "Mother",
            untilMillis = System.currentTimeMillis() + 3_600_000,
            deviceId = "device-a"
        )
        assertTrue(session.isActive)
        assertEquals("Mom", repository.activeDuty.first()?.caregiverName)

        val released = repository.releaseDuty("device-a")
        assertNotNull(released)
        assertFalse(released!!.isActive)
        assertNull(repository.activeDuty.first())
    }
}
