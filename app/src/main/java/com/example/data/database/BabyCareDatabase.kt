package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.BabyCareDao
import com.example.data.model.ActivityLog
import com.example.data.model.BabyBirthDefaults
import com.example.data.model.BabyProfile
import com.example.data.model.CareCheckSettings
import com.example.data.model.CaregiverProfile
import com.example.data.model.DutySession
import com.example.data.model.GrowthRecord
import com.example.data.model.MedicalRecord
import com.example.data.model.MedicineAlarm
import com.example.data.model.MemoryItem
import com.example.data.model.MilestoneRecord
import com.example.data.model.MilkStashItem
import com.example.data.model.PeerChatMessage
import com.example.data.model.SharedList
import com.example.data.model.SharedListItem
import com.example.data.model.SharedNote
import com.example.data.model.SyncOutboxItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        BabyProfile::class,
        ActivityLog::class,
        GrowthRecord::class,
        MedicalRecord::class,
        MilkStashItem::class,
        MilestoneRecord::class,
        CaregiverProfile::class,
        PeerChatMessage::class,
        DutySession::class,
        SyncOutboxItem::class,
        MemoryItem::class,
        SharedNote::class,
        SharedList::class,
        SharedListItem::class,
        MedicineAlarm::class,
        CareCheckSettings::class
    ],
    version = 10,
    exportSchema = true
)
abstract class BabyCareDatabase : RoomDatabase() {
    abstract fun babyCareDao(): BabyCareDao

    companion object {
        const val DATABASE_NAME = "babycare_live_db"

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS medicine_alarms (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        subject TEXT NOT NULL,
                        name TEXT NOT NULL,
                        doseNote TEXT NOT NULL,
                        intervalMinutes INTEGER NOT NULL,
                        pilotTimeMillis INTEGER NOT NULL,
                        enabled INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS care_check_settings (
                        id INTEGER PRIMARY KEY NOT NULL,
                        notificationsEnabled INTEGER NOT NULL,
                        systemAlarmsEnabled INTEGER NOT NULL,
                        feedEnabled INTEGER NOT NULL,
                        diaperEnabled INTEGER NOT NULL,
                        sleepEnabled INTEGER NOT NULL,
                        babyCheckEnabled INTEGER NOT NULL,
                        diaperIntervalMinutes INTEGER NOT NULL,
                        babyCheckIntervalMinutes INTEGER NOT NULL,
                        babyCheckPilotMillis INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                val now = System.currentTimeMillis()
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO care_check_settings (
                        id, notificationsEnabled, systemAlarmsEnabled,
                        feedEnabled, diaperEnabled, sleepEnabled, babyCheckEnabled,
                        diaperIntervalMinutes, babyCheckIntervalMinutes, babyCheckPilotMillis,
                        updatedAtMillis
                    ) VALUES (1, 1, 1, 1, 1, 1, 0, 180, 120, 0, $now)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE peer_chat_messages
                    ADD COLUMN deliveryStatus TEXT NOT NULL DEFAULT 'PENDING'
                    """.trimIndent()
                )
                // Existing history was already seen locally; avoid unread badges / pending ticks.
                db.execSQL(
                    "UPDATE peer_chat_messages SET deliveryStatus = 'READ' WHERE isFromMe = 0"
                )
                db.execSQL(
                    "UPDATE peer_chat_messages SET deliveryStatus = 'DELIVERED' WHERE isFromMe = 1"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rebuild care_check_settings with per-check reminder/alarm + App/Custom timing.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS care_check_settings_new (
                        id INTEGER PRIMARY KEY NOT NULL,
                        notificationsEnabled INTEGER NOT NULL,
                        systemAlarmsEnabled INTEGER NOT NULL,
                        feedReminderEnabled INTEGER NOT NULL,
                        feedAlarmEnabled INTEGER NOT NULL,
                        diaperReminderEnabled INTEGER NOT NULL,
                        diaperAlarmEnabled INTEGER NOT NULL,
                        babyCheckReminderEnabled INTEGER NOT NULL,
                        babyCheckAlarmEnabled INTEGER NOT NULL,
                        sleepEnabled INTEGER NOT NULL,
                        feedUseAppTiming INTEGER NOT NULL,
                        diaperUseAppTiming INTEGER NOT NULL,
                        babyCheckUseAppTiming INTEGER NOT NULL,
                        feedCustomIntervalMinutes INTEGER NOT NULL,
                        diaperIntervalMinutes INTEGER NOT NULL,
                        babyCheckIntervalMinutes INTEGER NOT NULL,
                        babyCheckPilotMillis INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                val now = System.currentTimeMillis()
                db.execSQL(
                    """
                    INSERT INTO care_check_settings_new (
                        id, notificationsEnabled, systemAlarmsEnabled,
                        feedReminderEnabled, feedAlarmEnabled,
                        diaperReminderEnabled, diaperAlarmEnabled,
                        babyCheckReminderEnabled, babyCheckAlarmEnabled,
                        sleepEnabled,
                        feedUseAppTiming, diaperUseAppTiming, babyCheckUseAppTiming,
                        feedCustomIntervalMinutes, diaperIntervalMinutes,
                        babyCheckIntervalMinutes, babyCheckPilotMillis, updatedAtMillis
                    )
                    SELECT
                        id,
                        notificationsEnabled,
                        systemAlarmsEnabled,
                        CASE WHEN feedEnabled = 1 AND (notificationsEnabled = 1 OR systemAlarmsEnabled = 1) THEN 1 ELSE 0 END,
                        CASE WHEN feedEnabled = 1 AND (notificationsEnabled = 1 OR systemAlarmsEnabled = 1) THEN 1 ELSE 0 END,
                        CASE WHEN diaperEnabled = 1 AND (notificationsEnabled = 1 OR systemAlarmsEnabled = 1) THEN 1 ELSE 0 END,
                        CASE WHEN diaperEnabled = 1 AND (notificationsEnabled = 1 OR systemAlarmsEnabled = 1) THEN 1 ELSE 0 END,
                        CASE WHEN babyCheckEnabled = 1 AND (notificationsEnabled = 1 OR systemAlarmsEnabled = 1) THEN 1 ELSE 0 END,
                        CASE WHEN babyCheckEnabled = 1 AND (notificationsEnabled = 1 OR systemAlarmsEnabled = 1) THEN 1 ELSE 0 END,
                        0,
                        1,
                        1,
                        0,
                        180,
                        diaperIntervalMinutes,
                        babyCheckIntervalMinutes,
                        babyCheckPilotMillis,
                        $now
                    FROM care_check_settings
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE care_check_settings")
                db.execSQL("ALTER TABLE care_check_settings_new RENAME TO care_check_settings")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO care_check_settings (
                        id, notificationsEnabled, systemAlarmsEnabled,
                        feedReminderEnabled, feedAlarmEnabled,
                        diaperReminderEnabled, diaperAlarmEnabled,
                        babyCheckReminderEnabled, babyCheckAlarmEnabled,
                        sleepEnabled,
                        feedUseAppTiming, diaperUseAppTiming, babyCheckUseAppTiming,
                        feedCustomIntervalMinutes, diaperIntervalMinutes,
                        babyCheckIntervalMinutes, babyCheckPilotMillis, updatedAtMillis
                    ) VALUES (1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 1, 1, 0, 180, 180, 120, 0, $now)
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: BabyCareDatabase? = null

        fun getDatabase(context: Context): BabyCareDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BabyCareDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .addCallback(DatabaseCallback())
                    // Never use fallbackToDestructiveMigration — add explicit Migration(N, N+1)
                    // when bumping [version] so app updates retain user data.
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /** Closes the singleton DB. Used by full restore before replacing DB files. */
        fun closeAndClearInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialData(database.babyCareDao())
                    }
                }
            }

            private suspend fun populateInitialData(dao: BabyCareDao) {
                val now = System.currentTimeMillis()
                dao.insertOrUpdateProfile(
                    BabyProfile(
                        id = 1,
                        name = "Your Baby",
                        birthDateMillis = BabyBirthDefaults.birthDateMillis,
                        birthTimeFormatted = BabyBirthDefaults.BIRTH_TIME_FORMATTED,
                        gender = "Girl",
                        targetFeedingIntervalMinutes = 180,
                        targetNapIntervalMinutes = 150,
                        isInitialSetupDone = false,
                        updatedAtMillis = now
                    )
                )

                dao.insertOrUpdateCareCheckSettings(CareCheckSettings(updatedAtMillis = now))

                val mom = CaregiverProfile(id = 1, name = "Mom", role = "Owner", relationship = "Mother", pin = "1234", isActiveNow = true, avatarColorHex = "#FF7043")
                val dad = CaregiverProfile(id = 2, name = "Dad", role = "Admin", relationship = "Father", pin = "5678", isActiveNow = false, avatarColorHex = "#26A69A")
                val grandma = CaregiverProfile(id = 3, name = "Grandma", role = "Caregiver", relationship = "Grandmother", pin = "0000", isActiveNow = false, avatarColorHex = "#7E57C2")
                dao.insertCaregiver(mom)
                dao.insertCaregiver(dad)
                dao.insertCaregiver(grandma)

                val milestones = listOf(
                    MilestoneRecord(
                        babyId = 1,
                        category = "Motor",
                        title = "Lifts head when on belly (Tummy Time)",
                        description = "Pushes up on arms during tummy time",
                        syncId = MilestoneRecord.seededSyncId("Motor", "Lifts head when on belly (Tummy Time)"),
                        updatedAtMillis = now
                    ),
                    MilestoneRecord(
                        babyId = 1,
                        category = "Social",
                        title = "Social Smile",
                        description = "Smiles at Mom and Dad when spoken to",
                        syncId = MilestoneRecord.seededSyncId("Social", "Social Smile"),
                        updatedAtMillis = now
                    ),
                    MilestoneRecord(
                        babyId = 1,
                        category = "Language",
                        title = "Coos and makes gurgling sounds",
                        description = "Vocalizes responses when engaged",
                        syncId = MilestoneRecord.seededSyncId("Language", "Coos and makes gurgling sounds"),
                        updatedAtMillis = now
                    ),
                    MilestoneRecord(
                        babyId = 1,
                        category = "Motor",
                        title = "Rolls from tummy to back",
                        description = "Pushes over onto back during playtime",
                        syncId = MilestoneRecord.seededSyncId("Motor", "Rolls from tummy to back"),
                        updatedAtMillis = now
                    ),
                    MilestoneRecord(
                        babyId = 1,
                        category = "Cognitive",
                        title = "Follows moving object with eyes",
                        description = "Tracks colorful toys across visual field",
                        syncId = MilestoneRecord.seededSyncId("Cognitive", "Follows moving object with eyes"),
                        updatedAtMillis = now
                    )
                )
                dao.insertMilestones(milestones)
            }
        }
    }
}
