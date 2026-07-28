package com.example.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ActivityTypes
import com.example.data.model.BabyProfile
import com.example.data.model.CaregiverProfile
import com.example.data.repository.BabyCareRepository
import com.example.testing.MainDispatcherRule
import com.example.testing.TestDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BabyCareViewModelFlowTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: com.example.data.database.BabyCareDatabase
    private lateinit var repository: BabyCareRepository
    private lateinit var viewModel: BabyCareViewModel

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val pair = TestDatabase.createRepository(app)
        db = pair.first
        repository = pair.second
        kotlinx.coroutines.runBlocking {
            TestDatabase.seedActiveCaregiver(repository)
            TestDatabase.seedProfile(repository, name = "Flow Baby", setupDone = true)
        }
        viewModel = BabyCareViewModel(
            application = app,
            repositoryOverride = repository,
            runtimeOptions = BabyCareViewModelOptions.ForTests
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun startAndStopLiveSleepTimer() = runTest {
        viewModel.startLiveActivity(activityType = ActivityTypes.SLEEP)
        advanceUntilIdle()

        val ongoing = viewModel.ongoingActivity.first { it != null }
        assertEquals(ActivityTypes.SLEEP, ongoing!!.activityType)
        assertNull(ongoing.endTimeMillis)

        viewModel.stopLiveActivity(finalNotes = "Nap done")
        advanceUntilIdle()

        assertNull(viewModel.ongoingActivity.first { it == null })
        val logs = repository.getRecentLogs(10)
        assertEquals(1, logs.size)
        assertNotNull(logs.first().endTimeMillis)
        assertEquals("Nap done", logs.first().notes)
    }

    @Test
    fun quickLogBottlePersistsVolumeAndMilkType() = runTest {
        viewModel.quickLogActivity(
            activityType = ActivityTypes.BOTTLE,
            volumeMl = 150,
            milkType = "Formula",
            durationSeconds = 0
        )
        advanceUntilIdle()

        val logs = viewModel.recentLogs.first { it.isNotEmpty() }
        assertEquals(ActivityTypes.BOTTLE, logs.first().activityType)
        assertEquals(150, logs.first().volumeMl)
        assertEquals("Formula", logs.first().milkType)
    }

    @Test
    fun saveBabyProfileCompletesOnboarding() = runTest {
        viewModel.saveBabyProfile(
            BabyProfile(
                name = "Nora",
                isInitialSetupDone = true,
                targetFeedingIntervalMinutes = 150
            )
        )
        advanceUntilIdle()

        val profile = viewModel.babyProfile.first { it?.name == "Nora" }
        assertTrue(profile!!.isInitialSetupDone)
        assertEquals(150, profile.targetFeedingIntervalMinutes)
    }

    @Test
    fun verifyAndSwitchCaregiverWithPin() = runTest {
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
        viewModel.caregivers.first { list -> list.any { it.id == 2L } }

        val wrongPin = kotlinx.coroutines.CompletableDeferred<Boolean>()
        viewModel.verifyAndSwitchCaregiver(2, "9999") { wrongPin.complete(it) }
        assertFalse(wrongPin.await())

        val okPin = kotlinx.coroutines.CompletableDeferred<Boolean>()
        viewModel.verifyAndSwitchCaregiver(2, "5678") { okPin.complete(it) }
        assertTrue(okPin.await())
        assertEquals("Dad", viewModel.activeCaregiver.first { it?.name == "Dad" }?.name)
        assertTrue(viewModel.syncStatusText.value.contains("Dad"))
    }

    @Test
    fun claimDutyCreatesActiveSession() = runTest {
        viewModel.claimDutyForHours(2)
        advanceUntilIdle()

        val duty = viewModel.activeDuty.first { it != null }
        assertEquals("Mom", duty!!.caregiverName)
        assertTrue(duty.isActive)
        assertNotNull(duty.untilMillis)

        viewModel.releaseDuty()
        advanceUntilIdle()
        assertNull(viewModel.activeDuty.first { it == null })
    }
}
