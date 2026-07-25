package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.BabyCareDao
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.model.BabyProfile
import com.example.data.model.CaregiverProfile
import com.example.data.model.GrowthRecord
import com.example.data.model.MedicalRecord
import com.example.data.model.MilestoneRecord
import com.example.data.model.MilkStashItem
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
        CaregiverProfile::class
    ],
    version = 4,
    exportSchema = false
)
abstract class BabyCareDatabase : RoomDatabase() {
    abstract fun babyCareDao(): BabyCareDao

    companion object {
        @Volatile
        private var INSTANCE: BabyCareDatabase? = null

        fun getDatabase(context: Context): BabyCareDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BabyCareDatabase::class.java,
                    "babycare_live_db"
                )
                .addCallback(DatabaseCallback())
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
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
                // Default Profile
                val now = System.currentTimeMillis()
                val twoMonthsAgo = now - (60L * 24 * 3600 * 1000)
                dao.insertOrUpdateProfile(
                    BabyProfile(
                        id = 1,
                        name = "Your Baby",
                        birthDateMillis = twoMonthsAgo,
                        gender = "Girl",
                        targetFeedingIntervalMinutes = 180,
                        targetNapIntervalMinutes = 150,
                        isInitialSetupDone = false
                    )
                )

                // Default Caregivers with PINs
                val mom = CaregiverProfile(id = 1, name = "Mom", role = "Owner", relationship = "Mother", pin = "1234", isActiveNow = true, avatarColorHex = "#FF7043")
                val dad = CaregiverProfile(id = 2, name = "Dad", role = "Admin", relationship = "Father", pin = "5678", isActiveNow = false, avatarColorHex = "#26A69A")
                val grandma = CaregiverProfile(id = 3, name = "Grandma", role = "Caregiver", relationship = "Grandmother", pin = "0000", isActiveNow = false, avatarColorHex = "#7E57C2")
                dao.insertCaregiver(mom)
                dao.insertCaregiver(dad)
                dao.insertCaregiver(grandma)

                // Developmental Milestones Checklist (CDC guidelines reference)
                val milestones = listOf(
                    MilestoneRecord(babyId = 1, category = "Motor", title = "Lifts head when on belly (Tummy Time)", description = "Pushes up on arms during tummy time", achievedDateMillis = null, isAchieved = false),
                    MilestoneRecord(babyId = 1, category = "Social", title = "Social Smile", description = "Smiles at Mom and Dad when spoken to", achievedDateMillis = null, isAchieved = false),
                    MilestoneRecord(babyId = 1, category = "Language", title = "Coos and makes gurgling sounds", description = "Vocalizes responses when engaged", achievedDateMillis = null, isAchieved = false),
                    MilestoneRecord(babyId = 1, category = "Motor", title = "Rolls from tummy to back", description = "Pushes over onto back during playtime", achievedDateMillis = null, isAchieved = false),
                    MilestoneRecord(babyId = 1, category = "Cognitive", title = "Follows moving object with eyes", description = "Tracks colorful toys across visual field", achievedDateMillis = null, isAchieved = false)
                )
                dao.insertMilestones(milestones)
            }
        }
    }
}
