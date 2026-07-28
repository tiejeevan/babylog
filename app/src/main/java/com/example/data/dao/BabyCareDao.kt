package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ActivityLog
import com.example.data.model.BabyProfile
import com.example.data.model.CareCheckSettings
import com.example.data.model.CaregiverProfile
import com.example.data.model.DutySession
import com.example.data.model.GrowthRecord
import com.example.data.model.MedicalRecord
import com.example.data.model.MedicineAlarm
import com.example.data.model.MilestoneRecord
import com.example.data.model.MilkStashItem
import com.example.data.model.MemoryItem
import com.example.data.model.PeerChatMessage
import com.example.data.model.SharedList
import com.example.data.model.SharedListItem
import com.example.data.model.SharedNote
import com.example.data.model.SyncOutboxItem
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

    // Activity Logs (exclude tombstones from UI flows)
    @Query("SELECT * FROM activity_logs WHERE isDeleted = 0 ORDER BY startTimeMillis DESC")
    fun getAllLogsFlow(): Flow<List<ActivityLog>>

    @Query("SELECT * FROM activity_logs WHERE isDeleted = 0 ORDER BY startTimeMillis DESC LIMIT :limit")
    fun getRecentLogsFlow(limit: Int = 30): Flow<List<ActivityLog>>

    @Query("SELECT * FROM activity_logs WHERE isDeleted = 0 ORDER BY startTimeMillis DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 30): List<ActivityLog>

    @Query("SELECT * FROM activity_logs WHERE isDeleted = 0 AND startTimeMillis >= :startMillis AND startTimeMillis <= :endMillis ORDER BY startTimeMillis DESC")
    fun getLogsByDateRangeFlow(startMillis: Long, endMillis: Long): Flow<List<ActivityLog>>

    @Query("SELECT * FROM activity_logs WHERE isDeleted = 0 AND endTimeMillis IS NULL ORDER BY startTimeMillis DESC LIMIT 1")
    fun getOngoingActivityFlow(): Flow<ActivityLog?>

    @Query("SELECT * FROM activity_logs WHERE isDeleted = 0 AND endTimeMillis IS NULL ORDER BY startTimeMillis DESC LIMIT 1")
    suspend fun getOngoingActivity(): ActivityLog?

    @Query("SELECT * FROM activity_logs WHERE isDeleted = 0 AND startTimeMillis >= :startMillis ORDER BY startTimeMillis DESC")
    suspend fun getTodayLogs(startMillis: Long): List<ActivityLog>

    @Query("SELECT * FROM activity_logs WHERE syncId = :syncId LIMIT 1")
    suspend fun getLogBySyncId(syncId: String): ActivityLog?

    @Query("SELECT * FROM activity_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: Long): ActivityLog?

    @Query(
        """
        SELECT * FROM activity_logs
        WHERE startTimeMillis >= :sinceMillis OR endTimeMillis IS NULL OR isDeleted = 1
        ORDER BY updatedAtMillis ASC
        """
    )
    suspend fun getLogsForSync(sinceMillis: Long): List<ActivityLog>

    @Query("SELECT MAX(updatedAtMillis) FROM activity_logs")
    suspend fun getLatestLogUpdatedAt(): Long?

    @Query("SELECT COUNT(*) FROM activity_logs")
    suspend fun getLogCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActivityLog): Long

    @Update
    suspend fun updateLog(log: ActivityLog)

    @Query("DELETE FROM activity_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    // Peer chat
    @Query("SELECT * FROM peer_chat_messages ORDER BY timestampMillis ASC")
    fun getPeerChatMessagesFlow(): Flow<List<PeerChatMessage>>

    @Query("SELECT * FROM peer_chat_messages ORDER BY timestampMillis ASC")
    suspend fun getPeerChatMessages(): List<PeerChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeerChatMessage(message: PeerChatMessage)

    @Query("UPDATE peer_chat_messages SET deliveryStatus = :status WHERE syncId = :syncId")
    suspend fun updatePeerChatDeliveryStatus(syncId: String, status: String)

    @Query(
        """
        SELECT COUNT(*) FROM peer_chat_messages
        WHERE isFromMe = 0 AND deliveryStatus != 'READ'
        """
    )
    fun getUnreadIncomingCountFlow(): Flow<Int>

    @Query(
        """
        SELECT * FROM peer_chat_messages
        WHERE isFromMe = 0 AND deliveryStatus != 'READ'
        ORDER BY timestampMillis ASC
        """
    )
    suspend fun getUnreadIncomingMessages(): List<PeerChatMessage>

    @Query("DELETE FROM peer_chat_messages")
    suspend fun clearPeerChatMessages()

    // Growth
    @Query("SELECT * FROM growth_records WHERE isDeleted = 0 ORDER BY dateMillis ASC")
    fun getGrowthRecordsFlow(): Flow<List<GrowthRecord>>

    @Query("SELECT * FROM growth_records ORDER BY updatedAtMillis ASC")
    suspend fun getGrowthRecordsForSync(): List<GrowthRecord>

    @Query("SELECT * FROM growth_records WHERE syncId = :syncId LIMIT 1")
    suspend fun getGrowthBySyncId(syncId: String): GrowthRecord?

    @Query("SELECT * FROM growth_records WHERE id = :id LIMIT 1")
    suspend fun getGrowthById(id: Long): GrowthRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrowthRecord(record: GrowthRecord): Long

    @Update
    suspend fun updateGrowthRecord(record: GrowthRecord)

    @Query("DELETE FROM growth_records WHERE id = :id")
    suspend fun deleteGrowthRecord(id: Long)

    // Medical
    @Query("SELECT * FROM medical_records WHERE isDeleted = 0 ORDER BY dateMillis DESC")
    fun getMedicalRecordsFlow(): Flow<List<MedicalRecord>>

    @Query("SELECT * FROM medical_records ORDER BY updatedAtMillis ASC")
    suspend fun getMedicalRecordsForSync(): List<MedicalRecord>

    @Query("SELECT * FROM medical_records WHERE syncId = :syncId LIMIT 1")
    suspend fun getMedicalBySyncId(syncId: String): MedicalRecord?

    @Query("SELECT * FROM medical_records WHERE id = :id LIMIT 1")
    suspend fun getMedicalById(id: Long): MedicalRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicalRecord(record: MedicalRecord): Long

    @Update
    suspend fun updateMedicalRecord(record: MedicalRecord)

    // Milk Stash
    @Query("SELECT * FROM milk_stash WHERE isDeleted = 0 ORDER BY pumpedDateMillis DESC")
    fun getMilkStashFlow(): Flow<List<MilkStashItem>>

    @Query("SELECT * FROM milk_stash ORDER BY updatedAtMillis ASC")
    suspend fun getMilkStashForSync(): List<MilkStashItem>

    @Query("SELECT * FROM milk_stash WHERE syncId = :syncId LIMIT 1")
    suspend fun getMilkBySyncId(syncId: String): MilkStashItem?

    @Query("SELECT * FROM milk_stash WHERE id = :id LIMIT 1")
    suspend fun getMilkById(id: Long): MilkStashItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilkStash(item: MilkStashItem): Long

    @Update
    suspend fun updateMilkStash(item: MilkStashItem)

    // Milestones
    @Query("SELECT * FROM milestone_records WHERE isDeleted = 0 ORDER BY id ASC")
    fun getMilestonesFlow(): Flow<List<MilestoneRecord>>

    @Query("SELECT * FROM milestone_records ORDER BY updatedAtMillis ASC")
    suspend fun getMilestonesForSync(): List<MilestoneRecord>

    @Query("SELECT * FROM milestone_records WHERE syncId = :syncId LIMIT 1")
    suspend fun getMilestoneBySyncId(syncId: String): MilestoneRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilestones(milestones: List<MilestoneRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilestone(milestone: MilestoneRecord): Long

    @Update
    suspend fun updateMilestone(milestone: MilestoneRecord)

    // Duty sessions
    @Query("SELECT * FROM duty_sessions WHERE isActive = 1 ORDER BY updatedAtMillis DESC LIMIT 1")
    fun getActiveDutyFlow(): Flow<DutySession?>

    @Query("SELECT * FROM duty_sessions WHERE isActive = 1 ORDER BY updatedAtMillis DESC LIMIT 1")
    suspend fun getActiveDuty(): DutySession?

    @Query("SELECT * FROM duty_sessions ORDER BY updatedAtMillis ASC")
    suspend fun getDutySessionsForSync(): List<DutySession>

    @Query("SELECT * FROM duty_sessions WHERE syncId = :syncId LIMIT 1")
    suspend fun getDutyBySyncId(syncId: String): DutySession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDutySession(session: DutySession): Long

    @Update
    suspend fun updateDutySession(session: DutySession)

    @Query("UPDATE duty_sessions SET isActive = 0, updatedAtMillis = :updatedAt WHERE isActive = 1")
    suspend fun deactivateAllDutySessions(updatedAt: Long)

    // Sync outbox
    @Query("SELECT * FROM sync_outbox ORDER BY createdAtMillis ASC")
    suspend fun getOutboxOrdered(): List<SyncOutboxItem>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun getOutboxCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutboxItem(item: SyncOutboxItem): Long

    @Query("DELETE FROM sync_outbox WHERE dedupeKey = :dedupeKey")
    suspend fun deleteOutboxByDedupeKey(dedupeKey: String)

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun deleteOutboxById(id: Long)

    @Query("DELETE FROM sync_outbox")
    suspend fun clearOutbox()

    @Query("UPDATE sync_outbox SET attempts = attempts + 1 WHERE id = :id")
    suspend fun incrementOutboxAttempts(id: Long)

    // Clear Real/Sample Data Queries
    @Query("DELETE FROM activity_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM growth_records")
    suspend fun clearAllGrowthRecords()

    @Query("DELETE FROM milk_stash")
    suspend fun clearAllMilkStash()

    @Query("DELETE FROM medical_records")
    suspend fun clearAllMedicalRecords()

    @Query("DELETE FROM duty_sessions")
    suspend fun clearAllDutySessions()

    // Caregivers
    @Query("SELECT * FROM caregiver_profiles")
    fun getCaregiversFlow(): Flow<List<CaregiverProfile>>

    @Query("SELECT * FROM caregiver_profiles WHERE isActiveNow = 1 LIMIT 1")
    fun getActiveCaregiverFlow(): Flow<CaregiverProfile?>

    @Query("SELECT * FROM caregiver_profiles WHERE id = :id LIMIT 1")
    suspend fun getCaregiverById(id: Long): CaregiverProfile?

    @Query("SELECT * FROM caregiver_profiles WHERE name = :name LIMIT 1")
    suspend fun getCaregiverByName(name: String): CaregiverProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCaregiver(caregiver: CaregiverProfile)

    @Query("UPDATE caregiver_profiles SET isActiveNow = CASE WHEN id = :activeId THEN 1 ELSE 0 END")
    suspend fun setActiveCaregiver(activeId: Long)

    // Memories
    @Query("SELECT * FROM memory_items WHERE isDeleted = 0 ORDER BY capturedAtMillis DESC")
    fun getMemoriesFlow(): Flow<List<MemoryItem>>

    @Query("SELECT * FROM memory_items WHERE isDeleted = 0 AND capturedAtMillis >= :startMillis AND capturedAtMillis <= :endMillis ORDER BY capturedAtMillis DESC")
    fun getMemoriesByDateRangeFlow(startMillis: Long, endMillis: Long): Flow<List<MemoryItem>>

    @Query("SELECT * FROM memory_items ORDER BY updatedAtMillis ASC")
    suspend fun getMemoriesForSync(): List<MemoryItem>

    @Query("SELECT * FROM memory_items WHERE syncId = :syncId LIMIT 1")
    suspend fun getMemoryBySyncId(syncId: String): MemoryItem?

    @Query("SELECT * FROM memory_items WHERE id = :id LIMIT 1")
    suspend fun getMemoryById(id: Long): MemoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(item: MemoryItem): Long

    @Update
    suspend fun updateMemory(item: MemoryItem)

    // Shared notes
    @Query("SELECT * FROM shared_notes WHERE isDeleted = 0 ORDER BY updatedAtMillis DESC")
    fun getNotesFlow(): Flow<List<SharedNote>>

    @Query("SELECT * FROM shared_notes ORDER BY updatedAtMillis ASC")
    suspend fun getNotesForSync(): List<SharedNote>

    @Query("SELECT * FROM shared_notes WHERE syncId = :syncId LIMIT 1")
    suspend fun getNoteBySyncId(syncId: String): SharedNote?

    @Query("SELECT * FROM shared_notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): SharedNote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: SharedNote): Long

    @Update
    suspend fun updateNote(note: SharedNote)

    // Shared lists
    @Query("SELECT * FROM shared_lists WHERE isDeleted = 0 ORDER BY updatedAtMillis DESC")
    fun getListsFlow(): Flow<List<SharedList>>

    @Query("SELECT * FROM shared_lists ORDER BY updatedAtMillis ASC")
    suspend fun getListsForSync(): List<SharedList>

    @Query("SELECT * FROM shared_lists WHERE syncId = :syncId LIMIT 1")
    suspend fun getListBySyncId(syncId: String): SharedList?

    @Query("SELECT * FROM shared_lists WHERE id = :id LIMIT 1")
    suspend fun getListById(id: Long): SharedList?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: SharedList): Long

    @Update
    suspend fun updateList(list: SharedList)

    @Query("SELECT * FROM shared_list_items WHERE isDeleted = 0 AND listSyncId = :listSyncId ORDER BY sortOrder ASC")
    fun getListItemsFlow(listSyncId: String): Flow<List<SharedListItem>>

    @Query("SELECT * FROM shared_list_items WHERE isDeleted = 0 AND listSyncId = :listSyncId ORDER BY sortOrder ASC")
    suspend fun getListItems(listSyncId: String): List<SharedListItem>

    @Query("SELECT * FROM shared_list_items ORDER BY updatedAtMillis ASC")
    suspend fun getListItemsForSync(): List<SharedListItem>

    @Query("SELECT * FROM shared_list_items WHERE syncId = :syncId LIMIT 1")
    suspend fun getListItemBySyncId(syncId: String): SharedListItem?

    @Query("SELECT * FROM shared_list_items WHERE id = :id LIMIT 1")
    suspend fun getListItemById(id: Long): SharedListItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListItem(item: SharedListItem): Long

    @Update
    suspend fun updateListItem(item: SharedListItem)

    // Care check settings (single row)
    @Query("SELECT * FROM care_check_settings WHERE id = 1 LIMIT 1")
    fun getCareCheckSettingsFlow(): Flow<CareCheckSettings?>

    @Query("SELECT * FROM care_check_settings WHERE id = 1 LIMIT 1")
    suspend fun getCareCheckSettings(): CareCheckSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCareCheckSettings(settings: CareCheckSettings)

    // Medicine alarms
    @Query("SELECT * FROM medicine_alarms ORDER BY subject ASC, name ASC")
    fun getMedicineAlarmsFlow(): Flow<List<MedicineAlarm>>

    @Query("SELECT * FROM medicine_alarms ORDER BY subject ASC, name ASC")
    suspend fun getMedicineAlarms(): List<MedicineAlarm>

    @Query("SELECT * FROM medicine_alarms WHERE enabled = 1 ORDER BY id ASC")
    suspend fun getEnabledMedicineAlarms(): List<MedicineAlarm>

    @Query("SELECT * FROM medicine_alarms WHERE id = :id LIMIT 1")
    suspend fun getMedicineAlarmById(id: Long): MedicineAlarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicineAlarm(alarm: MedicineAlarm): Long

    @Update
    suspend fun updateMedicineAlarm(alarm: MedicineAlarm)

    @Query("DELETE FROM medicine_alarms WHERE id = :id")
    suspend fun deleteMedicineAlarmById(id: Long)
}
