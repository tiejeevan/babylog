package com.example.engine

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.FullBackupManager
import com.example.data.database.BabyCareDatabase
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.model.BabyProfile
import com.example.data.repository.BabyCareRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class IntelligentNeedEngineTest {

    private val profile = BabyProfile(
        name = "Ava",
        targetFeedingIntervalMinutes = 180,
        targetNapIntervalMinutes = 150
    )

    @Test
    fun ongoingSleepOverridesOtherNeeds() {
        val now = 1_000_000L
        val prediction = IntelligentNeedEngine.analyzeBabyNeeds(
            profile = profile,
            logs = emptyList(),
            ongoingLog = ActivityLog(
                activityType = ActivityTypes.SLEEP,
                startTimeMillis = now - TimeUnit.MINUTES.toMillis(20)
            ),
            currentTimeMillis = now
        )
        assertEquals(UrgencyLevel.LOW_ALL_GOOD, prediction.urgencyLevel)
        assertTrue(prediction.primaryNeedTitle.contains("Sleeping"))
    }

    @Test
    fun feedingOverdueIsHighUrgency() {
        val now = 10_000_000L
        val lastFeedEnd = now - TimeUnit.MINUTES.toMillis(200)
        val prediction = IntelligentNeedEngine.analyzeBabyNeeds(
            profile = profile,
            logs = listOf(
                ActivityLog(
                    activityType = ActivityTypes.BOTTLE,
                    startTimeMillis = lastFeedEnd - 60_000,
                    endTimeMillis = lastFeedEnd,
                    volumeMl = 120
                )
            ),
            ongoingLog = null,
            currentTimeMillis = now
        )
        assertEquals(UrgencyLevel.HIGH_URGENT, prediction.urgencyLevel)
        assertEquals(ActivityTypes.BOTTLE, prediction.suggestedActivityType)
    }

    @Test
    fun todaySummaryCountsFeedsDiapersAndSleep() {
        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = dayStart + TimeUnit.HOURS.toMillis(12)

        val summary = IntelligentNeedEngine.computeTodaySummary(
            logs = listOf(
                ActivityLog(
                    activityType = ActivityTypes.BOTTLE,
                    startTimeMillis = dayStart + TimeUnit.HOURS.toMillis(8),
                    endTimeMillis = dayStart + TimeUnit.HOURS.toMillis(8) + 60_000,
                    volumeMl = 100
                ),
                ActivityLog(
                    activityType = ActivityTypes.DIAPER,
                    startTimeMillis = dayStart + TimeUnit.HOURS.toMillis(9),
                    diaperStatus = "Both"
                ),
                ActivityLog(
                    activityType = ActivityTypes.SLEEP,
                    startTimeMillis = dayStart + TimeUnit.HOURS.toMillis(10),
                    endTimeMillis = dayStart + TimeUnit.HOURS.toMillis(11)
                )
            ),
            currentTimeMillis = now
        )

        assertEquals(100, summary.totalFeedVolumeMl)
        assertEquals(1, summary.feedCount)
        assertEquals(1, summary.wetDiaperCount)
        assertEquals(1, summary.dirtyDiaperCount)
        assertEquals(60L, summary.totalSleepMinutes)
        assertEquals(1, summary.napCount)
    }

    @Test
    fun formatMinutesHandlesHoursAndMinutes() {
        assertEquals("2h 5m", IntelligentNeedEngine.formatMinutes(125))
        assertEquals("3h", IntelligentNeedEngine.formatMinutes(180))
        assertEquals("45m", IntelligentNeedEngine.formatMinutes(45))
        assertEquals("--", IntelligentNeedEngine.formatMinutes(-1))
    }

    @Test
    fun computeRoutineTriggersUsesProfileIntervalsAndDiaperRule() {
        val now = 10_000_000L
        val lastFeedEnd = now - TimeUnit.MINUTES.toMillis(60)
        val lastDiaper = now - TimeUnit.MINUTES.toMillis(30)
        val lastSleepEnd = now - TimeUnit.MINUTES.toMillis(45)
        val triggers = IntelligentNeedEngine.computeRoutineTriggers(
            profile = profile,
            logs = listOf(
                ActivityLog(
                    activityType = ActivityTypes.BOTTLE,
                    startTimeMillis = lastFeedEnd - 60_000,
                    endTimeMillis = lastFeedEnd,
                    volumeMl = 90
                ),
                ActivityLog(
                    activityType = ActivityTypes.DIAPER,
                    startTimeMillis = lastDiaper,
                    diaperStatus = "Wet"
                ),
                ActivityLog(
                    activityType = ActivityTypes.SLEEP,
                    startTimeMillis = lastSleepEnd - TimeUnit.MINUTES.toMillis(40),
                    endTimeMillis = lastSleepEnd
                )
            ),
            currentTimeMillis = now
        )
        assertEquals(lastFeedEnd + TimeUnit.MINUTES.toMillis(180), triggers.feedAtMillis)
        assertEquals(lastDiaper + TimeUnit.MINUTES.toMillis(180), triggers.diaperAtMillis)
        assertEquals(lastSleepEnd + TimeUnit.MINUTES.toMillis(150), triggers.napAtMillis)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SleepSoundPrefsTest {

    @Test
    fun roundTripsSoundVolumeAndTimer() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        SleepSoundPrefs.setSoundType(context, SoundType.HEARTBEAT)
        SleepSoundPrefs.setVolume(context, 0.42f)
        SleepSoundPrefs.setTimerMinutes(context, 45)
        SleepSoundPrefs.setKeepAwake(context, false)
        SleepSoundPrefs.setPulseEnabled(context, true)

        assertEquals(SoundType.HEARTBEAT, SleepSoundPrefs.getSoundType(context))
        assertEquals(0.42f, SleepSoundPrefs.getVolume(context), 0.001f)
        assertEquals(45, SleepSoundPrefs.getTimerMinutes(context))
        assertEquals(false, SleepSoundPrefs.isKeepAwake(context))
        assertEquals(true, SleepSoundPrefs.isPulseEnabled(context))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FullBackupManagerTest {

    @Test
    fun suggestedBackupFileNameIncludesPrefixAndZip() {
        val name = FullBackupManager.suggestedBackupFileName()
        assertTrue(name.startsWith("BabyCareLive_backup_"))
        assertTrue(name.endsWith(".zip"))
    }

    @Test
    fun restoreRejectsUnsupportedFormatVersion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val badZip = File(context.cacheDir, "bad_backup.zip")
        ZipOutputStream(badZip.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("""{"formatVersion":999}""".toByteArray())
            zip.closeEntry()
        }

        val result = FullBackupManager.restoreBackup(context, Uri.fromFile(badZip))
        assertTrue(result is FullBackupManager.BackupResult.Failure)
        assertTrue(
            (result as FullBackupManager.BackupResult.Failure).message.contains("Unsupported")
        )
    }

    @Test
    fun backupRoundTripPreservesProfileName() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        BabyCareDatabase.closeAndClearInstance()
        val db = BabyCareDatabase.getDatabase(context)
        val repository = BabyCareRepository(db.babyCareDao())
        // DatabaseCallback seeds "Your Baby" asynchronously on first create — wait then overwrite.
        var confirmed = false
        repeat(10) {
            kotlinx.coroutines.delay(100)
            repository.saveProfile(
                BabyProfile(name = "BackupBaby", isInitialSetupDone = true)
            )
            if (repository.getBabyProfileDirect()?.name == "BackupBaby") {
                confirmed = true
                return@repeat
            }
        }
        assertTrue("Could not persist BackupBaby over async seed", confirmed)

        val outFile = File(context.cacheDir, "roundtrip_backup.zip")
        if (outFile.exists()) outFile.delete()
        val create = FullBackupManager.createBackup(context, Uri.fromFile(outFile))
        assertTrue(
            "create failed: ${(create as? FullBackupManager.BackupResult.Failure)?.message}",
            create is FullBackupManager.BackupResult.Success
        )
        assertTrue(outFile.exists() && outFile.length() > 0)

        repository.saveProfile(
            BabyProfile(name = "Changed", isInitialSetupDone = true)
        )
        assertEquals("Changed", repository.getBabyProfileDirect()?.name)
        BabyCareDatabase.closeAndClearInstance()

        val restore = FullBackupManager.restoreBackup(context, Uri.fromFile(outFile))
        assertTrue(
            "restore failed: ${(restore as? FullBackupManager.BackupResult.Failure)?.message}",
            restore is FullBackupManager.BackupResult.Success
        )

        BabyCareDatabase.closeAndClearInstance()
        val reopened = BabyCareDatabase.getDatabase(context)
        val restoredRepo = BabyCareRepository(reopened.babyCareDao())
        // Avoid racing a fresh-create seed if restore somehow missed the file.
        kotlinx.coroutines.delay(300)
        val restoredName = restoredRepo.getBabyProfileDirect()?.name
        assertEquals("BackupBaby", restoredName)
        reopened.close()
        BabyCareDatabase.closeAndClearInstance()
    }
}

class OutboxOrderingEdgeCasesTest {

    @Test
    fun stableOrderWhenCreatedAtEqual() {
        val items = listOf(
            OutboxOrdering.Item(5, 100, "A"),
            OutboxOrdering.Item(2, 100, "B"),
            OutboxOrdering.Item(9, 100, "C")
        )
        assertEquals(listOf(2L, 5L, 9L), OutboxOrdering.drainOrder(items).map { it.id })
    }

    @Test
    fun dedupeReplacesEarlierAndKeepsOthers() {
        val pending = listOf(
            OutboxOrdering.Item(1, 10, "LOG:a"),
            OutboxOrdering.Item(2, 20, "CHAT:b")
        )
        val result = OutboxOrdering.applyDedupe(
            pending,
            OutboxOrdering.Item(3, 30, "LOG:a")
        )
        assertEquals(2, result.size)
        assertEquals(3L, result.first { it.dedupeKey == "LOG:a" }.id)
        assertEquals(2L, result.first { it.dedupeKey == "CHAT:b" }.id)
    }
}
