package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ActivityLog
import com.example.data.model.BabyProfile
import com.example.data.model.CaregiverProfile
import com.example.data.model.GrowthRecord
import com.example.data.model.MedicalRecord
import com.example.data.model.MilestoneRecord
import com.example.data.model.MilkStashItem
import kotlinx.coroutines.flow.Flow

@Dao
interface BabyCareDao {
    // Baby Profile
    @Query("SELECT * FROM baby_profile WHERE id = 1 LIMIT 1")
    fun getBabyProfileFlow(): Flow<BabyProfile?>

    @Query("SELECT * FROM baby_profile WHERE id = 1 LIMIT 1")
    suspend fun getBabyProfile(): BabyProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: BabyProfile)

    // Activity Logs
    @Query("SELECT * FROM activity_logs ORDER BY startTimeMillis DESC")
    fun getAllLogsFlow(): Flow<List<ActivityLog>>

    @Query("SELECT * FROM activity_logs ORDER BY startTimeMillis DESC LIMIT :limit")
    fun getRecentLogsFlow(limit: Int = 30): Flow<List<ActivityLog>>

    @Query("SELECT * FROM activity_logs ORDER BY startTimeMillis DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 30): List<ActivityLog>

    @Query("SELECT * FROM activity_logs WHERE startTimeMillis >= :startMillis AND startTimeMillis <= :endMillis ORDER BY startTimeMillis DESC")
    fun getLogsByDateRangeFlow(startMillis: Long, endMillis: Long): Flow<List<ActivityLog>>

    @Query("SELECT * FROM activity_logs WHERE endTimeMillis IS NULL ORDER BY startTimeMillis DESC LIMIT 1")
    fun getOngoingActivityFlow(): Flow<ActivityLog?>

    @Query("SELECT * FROM activity_logs WHERE endTimeMillis IS NULL ORDER BY startTimeMillis DESC LIMIT 1")
    suspend fun getOngoingActivity(): ActivityLog?

    @Query("SELECT * FROM activity_logs WHERE startTimeMillis >= :startMillis ORDER BY startTimeMillis DESC")
    suspend fun getTodayLogs(startMillis: Long): List<ActivityLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActivityLog): Long

    @Update
    suspend fun updateLog(log: ActivityLog)

    @Query("DELETE FROM activity_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    // Growth
    @Query("SELECT * FROM growth_records ORDER BY dateMillis ASC")
    fun getGrowthRecordsFlow(): Flow<List<GrowthRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrowthRecord(record: GrowthRecord)

    @Query("DELETE FROM growth_records WHERE id = :id")
    suspend fun deleteGrowthRecord(id: Long)

    // Medical
    @Query("SELECT * FROM medical_records ORDER BY dateMillis DESC")
    fun getMedicalRecordsFlow(): Flow<List<MedicalRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicalRecord(record: MedicalRecord)

    @Update
    suspend fun updateMedicalRecord(record: MedicalRecord)

    // Milk Stash
    @Query("SELECT * FROM milk_stash ORDER BY pumpedDateMillis DESC")
    fun getMilkStashFlow(): Flow<List<MilkStashItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilkStash(item: MilkStashItem)

    @Update
    suspend fun updateMilkStash(item: MilkStashItem)

    // Milestones
    @Query("SELECT * FROM milestone_records ORDER BY id ASC")
    fun getMilestonesFlow(): Flow<List<MilestoneRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilestones(milestones: List<MilestoneRecord>)

    @Update
    suspend fun updateMilestone(milestone: MilestoneRecord)

    // Clear Real/Sample Data Queries
    @Query("DELETE FROM activity_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM growth_records")
    suspend fun clearAllGrowthRecords()

    @Query("DELETE FROM milk_stash")
    suspend fun clearAllMilkStash()

    @Query("DELETE FROM medical_records")
    suspend fun clearAllMedicalRecords()

    // Caregivers
    @Query("SELECT * FROM caregiver_profiles")
    fun getCaregiversFlow(): Flow<List<CaregiverProfile>>

    @Query("SELECT * FROM caregiver_profiles WHERE isActiveNow = 1 LIMIT 1")
    fun getActiveCaregiverFlow(): Flow<CaregiverProfile?>

    @Query("SELECT * FROM caregiver_profiles WHERE id = :id LIMIT 1")
    suspend fun getCaregiverById(id: Long): CaregiverProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCaregiver(caregiver: CaregiverProfile)

    @Query("UPDATE caregiver_profiles SET isActiveNow = CASE WHEN id = :activeId THEN 1 ELSE 0 END")
    suspend fun setActiveCaregiver(activeId: Long)
}
