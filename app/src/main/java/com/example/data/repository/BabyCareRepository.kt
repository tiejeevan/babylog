package com.example.data.repository

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
import com.example.engine.SyncMergeRules
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class BabyCareRepository(private val dao: BabyCareDao) {
    val babyProfile: Flow<BabyProfile?> = dao.getBabyProfileFlow()
    val allLogs: Flow<List<ActivityLog>> = dao.getAllLogsFlow()
    val recentLogs: Flow<List<ActivityLog>> = dao.getRecentLogsFlow(50)
    val ongoingActivity: Flow<ActivityLog?> = dao.getOngoingActivityFlow()
    val careCheckSettings: Flow<CareCheckSettings?> = dao.getCareCheckSettingsFlow()
    val medicineAlarms: Flow<List<MedicineAlarm>> = dao.getMedicineAlarmsFlow()

    fun logsForRange(startMillis: Long, endMillis: Long): Flow<List<ActivityLog>> =
        dao.getLogsByDateRangeFlow(startMillis, endMillis)

    suspend fun getRecentLogs(limit: Int = 100): List<ActivityLog> = dao.getRecentLogs(limit)
    val growthRecords: Flow<List<GrowthRecord>> = dao.getGrowthRecordsFlow()
    val medicalRecords: Flow<List<MedicalRecord>> = dao.getMedicalRecordsFlow()
    val milkStash: Flow<List<MilkStashItem>> = dao.getMilkStashFlow()
    val milestones: Flow<List<MilestoneRecord>> = dao.getMilestonesFlow()
    val caregivers: Flow<List<CaregiverProfile>> = dao.getCaregiversFlow()
    val activeCaregiver: Flow<CaregiverProfile?> = dao.getActiveCaregiverFlow()
    val peerChatMessages: Flow<List<PeerChatMessage>> = dao.getPeerChatMessagesFlow()
    val activeDuty: Flow<DutySession?> = dao.getActiveDutyFlow()
    val outboxCount: Flow<Int> = dao.getOutboxCountFlow()
    val memories: Flow<List<MemoryItem>> = dao.getMemoriesFlow()
    val notes: Flow<List<SharedNote>> = dao.getNotesFlow()
    val lists: Flow<List<SharedList>> = dao.getListsFlow()

    fun memoriesForRange(startMillis: Long, endMillis: Long): Flow<List<MemoryItem>> =
        dao.getMemoriesByDateRangeFlow(startMillis, endMillis)

    fun listItems(listSyncId: String): Flow<List<SharedListItem>> =
        dao.getListItemsFlow(listSyncId)

    suspend fun getBabyProfileDirect(): BabyProfile? = dao.getBabyProfile()

    suspend fun saveProfile(profile: BabyProfile): BabyProfile {
        val prepared = profile.copy(
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.insertOrUpdateProfile(prepared)
        return prepared
    }

    /**
     * Merge remote profile by updatedAt (LWW). Preserves local photoUri.
     */
    suspend fun upsertSyncedProfile(incoming: BabyProfile): Boolean {
        val existing = dao.getBabyProfile()
        if (existing == null) {
            dao.insertOrUpdateProfile(incoming.copy(id = 1, photoUri = null))
            return true
        }
        if (incoming.updatedAtMillis > existing.updatedAtMillis) {
            dao.insertOrUpdateProfile(
                incoming.copy(id = 1, photoUri = existing.photoUri)
            )
            return true
        }
        return false
    }

    suspend fun insertLog(log: ActivityLog): ActivityLog {
        val now = System.currentTimeMillis()
        val prepared = log.copy(
            syncId = log.syncId.ifBlank { UUID.randomUUID().toString() },
            updatedAtMillis = if (log.updatedAtMillis > 0) log.updatedAtMillis else now,
            isDeleted = false
        )
        val rowId = dao.insertLog(prepared)
        return prepared.copy(id = rowId)
    }

    suspend fun updateLog(log: ActivityLog): ActivityLog {
        val updated = log.copy(
            syncId = log.syncId.ifBlank { UUID.randomUUID().toString() },
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.updateLog(updated)
        return updated
    }

    /** Soft-delete so peers can receive tombstones. */
    suspend fun softDeleteLog(id: Long): ActivityLog? {
        val existing = dao.getLogById(id) ?: return null
        val tombstone = existing.copy(
            isDeleted = true,
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.updateLog(tombstone)
        return tombstone
    }

    suspend fun deleteLog(id: Long) {
        softDeleteLog(id)
    }

    /**
     * Merge remote activity log by syncId.
     * Higher updatedAtMillis wins; ties prefer deleted tombstone.
     */
    suspend fun upsertSyncedLog(incoming: ActivityLog): Boolean {
        val existing = dao.getLogBySyncId(incoming.syncId)
        if (existing == null) {
            dao.insertLog(incoming.copy(id = 0))
            return true
        }
        val shouldReplace = SyncMergeRules.shouldReplace(
            existingUpdatedAt = existing.updatedAtMillis,
            existingDeleted = existing.isDeleted,
            incomingUpdatedAt = incoming.updatedAtMillis,
            incomingDeleted = incoming.isDeleted
        )
        if (shouldReplace) {
            dao.updateLog(incoming.copy(id = existing.id))
            return true
        }
        return false
    }

    suspend fun getLogsForSync(windowDays: Int = 30): List<ActivityLog> {
        val since = System.currentTimeMillis() - (windowDays * 24L * 3600L * 1000L)
        return dao.getLogsForSync(since)
    }

    suspend fun getLatestLogUpdatedAt(): Long = dao.getLatestLogUpdatedAt() ?: 0L

    suspend fun getLogCount(): Int = dao.getLogCount()

    suspend fun insertPeerChatMessage(message: PeerChatMessage) {
        dao.insertPeerChatMessage(message)
    }

    suspend fun getPeerChatMessages(): List<PeerChatMessage> = dao.getPeerChatMessages()

    suspend fun updatePeerChatDeliveryStatus(syncId: String, status: String) {
        dao.updatePeerChatDeliveryStatus(syncId, status)
    }

    fun getUnreadIncomingCountFlow() = dao.getUnreadIncomingCountFlow()

    suspend fun getUnreadIncomingMessages(): List<PeerChatMessage> =
        dao.getUnreadIncomingMessages()

    // ---- Growth ----

    suspend fun insertGrowthRecord(record: GrowthRecord): GrowthRecord {
        val now = System.currentTimeMillis()
        val prepared = record.copy(
            syncId = record.syncId.ifBlank { UUID.randomUUID().toString() },
            updatedAtMillis = if (record.updatedAtMillis > 0) record.updatedAtMillis else now,
            isDeleted = false
        )
        val rowId = dao.insertGrowthRecord(prepared)
        return prepared.copy(id = rowId)
    }

    suspend fun softDeleteGrowthRecord(id: Long): GrowthRecord? {
        val existing = dao.getGrowthById(id) ?: return null
        val tombstone = existing.copy(isDeleted = true, updatedAtMillis = System.currentTimeMillis())
        dao.updateGrowthRecord(tombstone)
        return tombstone
    }

    suspend fun deleteGrowthRecord(id: Long) {
        softDeleteGrowthRecord(id)
    }

    suspend fun upsertSyncedGrowth(incoming: GrowthRecord): Boolean {
        val existing = dao.getGrowthBySyncId(incoming.syncId)
        if (existing == null) {
            dao.insertGrowthRecord(incoming.copy(id = 0))
            return true
        }
        if (SyncMergeRules.shouldReplace(
                existing.updatedAtMillis, existing.isDeleted,
                incoming.updatedAtMillis, incoming.isDeleted
            )
        ) {
            dao.updateGrowthRecord(incoming.copy(id = existing.id))
            return true
        }
        return false
    }

    suspend fun getGrowthForSync(): List<GrowthRecord> = dao.getGrowthRecordsForSync()

    // ---- Medical ----

    suspend fun insertMedicalRecord(record: MedicalRecord): MedicalRecord {
        val now = System.currentTimeMillis()
        val prepared = record.copy(
            syncId = record.syncId.ifBlank { UUID.randomUUID().toString() },
            updatedAtMillis = if (record.updatedAtMillis > 0) record.updatedAtMillis else now,
            isDeleted = false
        )
        val rowId = dao.insertMedicalRecord(prepared)
        return prepared.copy(id = rowId)
    }

    suspend fun updateMedicalRecord(record: MedicalRecord): MedicalRecord {
        val updated = record.copy(
            syncId = record.syncId.ifBlank { UUID.randomUUID().toString() },
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.updateMedicalRecord(updated)
        return updated
    }

    suspend fun softDeleteMedicalRecord(id: Long): MedicalRecord? {
        val existing = dao.getMedicalById(id) ?: return null
        val tombstone = existing.copy(isDeleted = true, updatedAtMillis = System.currentTimeMillis())
        dao.updateMedicalRecord(tombstone)
        return tombstone
    }

    suspend fun upsertSyncedMedical(incoming: MedicalRecord): Boolean {
        val existing = dao.getMedicalBySyncId(incoming.syncId)
        if (existing == null) {
            dao.insertMedicalRecord(incoming.copy(id = 0))
            return true
        }
        if (SyncMergeRules.shouldReplace(
                existing.updatedAtMillis, existing.isDeleted,
                incoming.updatedAtMillis, incoming.isDeleted
            )
        ) {
            dao.updateMedicalRecord(incoming.copy(id = existing.id))
            return true
        }
        return false
    }

    suspend fun getMedicalForSync(): List<MedicalRecord> = dao.getMedicalRecordsForSync()

    // ---- Milk stash ----

    suspend fun insertMilkStash(item: MilkStashItem): MilkStashItem {
        val now = System.currentTimeMillis()
        val prepared = item.copy(
            syncId = item.syncId.ifBlank { UUID.randomUUID().toString() },
            updatedAtMillis = if (item.updatedAtMillis > 0) item.updatedAtMillis else now,
            isDeleted = false
        )
        val rowId = dao.insertMilkStash(prepared)
        return prepared.copy(id = rowId)
    }

    suspend fun updateMilkStash(item: MilkStashItem): MilkStashItem {
        val updated = item.copy(
            syncId = item.syncId.ifBlank { UUID.randomUUID().toString() },
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.updateMilkStash(updated)
        return updated
    }

    suspend fun softDeleteMilkStash(id: Long): MilkStashItem? {
        val existing = dao.getMilkById(id) ?: return null
        val tombstone = existing.copy(isDeleted = true, updatedAtMillis = System.currentTimeMillis())
        dao.updateMilkStash(tombstone)
        return tombstone
    }

    suspend fun upsertSyncedMilk(incoming: MilkStashItem): Boolean {
        val existing = dao.getMilkBySyncId(incoming.syncId)
        if (existing == null) {
            dao.insertMilkStash(incoming.copy(id = 0))
            return true
        }
        if (SyncMergeRules.shouldReplace(
                existing.updatedAtMillis, existing.isDeleted,
                incoming.updatedAtMillis, incoming.isDeleted
            )
        ) {
            dao.updateMilkStash(incoming.copy(id = existing.id))
            return true
        }
        return false
    }

    suspend fun getMilkForSync(): List<MilkStashItem> = dao.getMilkStashForSync()

    // ---- Milestones ----

    suspend fun updateMilestone(milestone: MilestoneRecord): MilestoneRecord {
        val updated = milestone.copy(
            syncId = milestone.syncId.ifBlank {
                MilestoneRecord.seededSyncId(milestone.category, milestone.title)
            },
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.updateMilestone(updated)
        return updated
    }

    suspend fun upsertSyncedMilestone(incoming: MilestoneRecord): Boolean {
        val existing = dao.getMilestoneBySyncId(incoming.syncId)
        if (existing == null) {
            dao.insertMilestone(incoming.copy(id = 0))
            return true
        }
        if (SyncMergeRules.shouldReplace(
                existing.updatedAtMillis, existing.isDeleted,
                incoming.updatedAtMillis, incoming.isDeleted
            )
        ) {
            dao.updateMilestone(incoming.copy(id = existing.id))
            return true
        }
        return false
    }

    suspend fun getMilestonesForSync(): List<MilestoneRecord> = dao.getMilestonesForSync()

    // ---- Duty ----

    suspend fun getActiveDutyDirect(): DutySession? = dao.getActiveDuty()

    suspend fun claimDuty(
        caregiverName: String,
        caregiverRole: String,
        untilMillis: Long?,
        deviceId: String
    ): DutySession {
        val now = System.currentTimeMillis()
        dao.deactivateAllDutySessions(now)
        val session = DutySession(
            syncId = UUID.randomUUID().toString(),
            caregiverName = caregiverName,
            caregiverRole = caregiverRole,
            startedAtMillis = now,
            untilMillis = untilMillis,
            isActive = true,
            updatedAtMillis = now,
            deviceId = deviceId
        )
        val rowId = dao.insertDutySession(session)
        // Match local caregiver switch when name matches
        dao.getCaregiverByName(caregiverName)?.let { dao.setActiveCaregiver(it.id) }
        return session.copy(id = rowId)
    }

    suspend fun releaseDuty(deviceId: String): DutySession? {
        val active = dao.getActiveDuty() ?: return null
        val now = System.currentTimeMillis()
        val released = active.copy(
            isActive = false,
            updatedAtMillis = now,
            deviceId = deviceId.ifBlank { active.deviceId }
        )
        dao.updateDutySession(released)
        return released
    }

    suspend fun expireDutyIfNeeded(nowMillis: Long = System.currentTimeMillis()): DutySession? {
        val active = dao.getActiveDuty() ?: return null
        val until = active.untilMillis ?: return null
        if (until > nowMillis) return null
        val released = active.copy(isActive = false, updatedAtMillis = nowMillis)
        dao.updateDutySession(released)
        return released
    }

    suspend fun upsertSyncedDuty(incoming: DutySession): Boolean {
        val existing = dao.getDutyBySyncId(incoming.syncId)
        if (existing == null) {
            if (incoming.isActive) {
                dao.deactivateAllDutySessions(incoming.updatedAtMillis)
            }
            dao.insertDutySession(incoming.copy(id = 0))
            return true
        }
        // For duty, newer updatedAt always wins (isActive acts like "deleted" when false for ties)
        val shouldReplace = SyncMergeRules.shouldReplace(
            existingUpdatedAt = existing.updatedAtMillis,
            existingDeleted = !existing.isActive,
            incomingUpdatedAt = incoming.updatedAtMillis,
            incomingDeleted = !incoming.isActive
        )
        if (shouldReplace) {
            if (incoming.isActive) {
                dao.deactivateAllDutySessions(incoming.updatedAtMillis)
            }
            dao.updateDutySession(incoming.copy(id = existing.id))
            return true
        }
        return false
    }

    suspend fun getDutyForSync(): List<DutySession> = dao.getDutySessionsForSync()

    // ---- Memories ----

    suspend fun insertMemory(item: MemoryItem): MemoryItem {
        val now = System.currentTimeMillis()
        val prepared = item.copy(
            syncId = item.syncId.ifBlank { UUID.randomUUID().toString() },
            updatedAtMillis = if (item.updatedAtMillis > 0) item.updatedAtMillis else now,
            isDeleted = false
        )
        val rowId = dao.insertMemory(prepared)
        return prepared.copy(id = rowId)
    }

    suspend fun updateMemory(item: MemoryItem): MemoryItem {
        val updated = item.copy(
            syncId = item.syncId.ifBlank { UUID.randomUUID().toString() },
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.updateMemory(updated)
        return updated
    }

    suspend fun softDeleteMemory(id: Long): MemoryItem? {
        val existing = dao.getMemoryById(id) ?: return null
        val tombstone = existing.copy(isDeleted = true, updatedAtMillis = System.currentTimeMillis())
        dao.updateMemory(tombstone)
        return tombstone
    }

    suspend fun upsertSyncedMemory(incoming: MemoryItem): Boolean {
        val existing = dao.getMemoryBySyncId(incoming.syncId)
        if (existing == null) {
            dao.insertMemory(incoming.copy(id = 0))
            return true
        }
        if (SyncMergeRules.shouldReplace(
                existing.updatedAtMillis, existing.isDeleted,
                incoming.updatedAtMillis, incoming.isDeleted
            )
        ) {
            // Keep local media paths if incoming has none (metadata-only sync).
            val merged = if (incoming.localPath.isBlank() && existing.localPath.isNotBlank()) {
                incoming.copy(
                    id = existing.id,
                    localPath = existing.localPath,
                    thumbPath = existing.thumbPath
                )
            } else {
                incoming.copy(id = existing.id)
            }
            dao.updateMemory(merged)
            return true
        }
        return false
    }

    suspend fun getMemoriesForSync(): List<MemoryItem> = dao.getMemoriesForSync()

    suspend fun getMemoryBySyncId(syncId: String): MemoryItem? = dao.getMemoryBySyncId(syncId)

    suspend fun attachMemoryFile(
        syncId: String,
        localPath: String,
        thumbPath: String,
        fileSizeBytes: Long,
        contentHash: String = ""
    ): MemoryItem? {
        val existing = dao.getMemoryBySyncId(syncId) ?: return null
        val updated = existing.copy(
            localPath = localPath,
            thumbPath = thumbPath.ifBlank { existing.thumbPath },
            fileSizeBytes = fileSizeBytes,
            contentHash = contentHash.ifBlank { existing.contentHash },
            mimeType = if (existing.mediaType == com.example.data.model.MediaTypes.VIDEO) {
                existing.mimeType
            } else {
                "image/jpeg"
            },
            updatedAtMillis = existing.updatedAtMillis
        )
        dao.updateMemory(updated)
        return updated
    }

    // ---- Notes ----

    suspend fun insertNote(note: SharedNote): SharedNote {
        val now = System.currentTimeMillis()
        val prepared = note.copy(
            syncId = note.syncId.ifBlank { UUID.randomUUID().toString() },
            updatedAtMillis = if (note.updatedAtMillis > 0) note.updatedAtMillis else now,
            isDeleted = false
        )
        val rowId = dao.insertNote(prepared)
        return prepared.copy(id = rowId)
    }

    suspend fun updateNote(note: SharedNote): SharedNote {
        val updated = note.copy(
            syncId = note.syncId.ifBlank { UUID.randomUUID().toString() },
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.updateNote(updated)
        return updated
    }

    suspend fun softDeleteNote(id: Long): SharedNote? {
        val existing = dao.getNoteById(id) ?: return null
        val tombstone = existing.copy(isDeleted = true, updatedAtMillis = System.currentTimeMillis())
        dao.updateNote(tombstone)
        return tombstone
    }

    suspend fun upsertSyncedNote(incoming: SharedNote): Boolean {
        val existing = dao.getNoteBySyncId(incoming.syncId)
        if (existing == null) {
            dao.insertNote(incoming.copy(id = 0))
            return true
        }
        if (SyncMergeRules.shouldReplace(
                existing.updatedAtMillis, existing.isDeleted,
                incoming.updatedAtMillis, incoming.isDeleted
            )
        ) {
            dao.updateNote(incoming.copy(id = existing.id))
            return true
        }
        return false
    }

    suspend fun getNotesForSync(): List<SharedNote> = dao.getNotesForSync()

    // ---- Lists ----

    suspend fun insertList(list: SharedList): SharedList {
        val now = System.currentTimeMillis()
        val prepared = list.copy(
            syncId = list.syncId.ifBlank { UUID.randomUUID().toString() },
            updatedAtMillis = if (list.updatedAtMillis > 0) list.updatedAtMillis else now,
            isDeleted = false
        )
        val rowId = dao.insertList(prepared)
        return prepared.copy(id = rowId)
    }

    suspend fun updateList(list: SharedList): SharedList {
        val updated = list.copy(
            syncId = list.syncId.ifBlank { UUID.randomUUID().toString() },
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.updateList(updated)
        return updated
    }

    suspend fun softDeleteList(id: Long): SharedList? {
        val existing = dao.getListById(id) ?: return null
        val tombstone = existing.copy(isDeleted = true, updatedAtMillis = System.currentTimeMillis())
        dao.updateList(tombstone)
        return tombstone
    }

    suspend fun upsertSyncedList(incoming: SharedList): Boolean {
        val existing = dao.getListBySyncId(incoming.syncId)
        if (existing == null) {
            dao.insertList(incoming.copy(id = 0))
            return true
        }
        if (SyncMergeRules.shouldReplace(
                existing.updatedAtMillis, existing.isDeleted,
                incoming.updatedAtMillis, incoming.isDeleted
            )
        ) {
            dao.updateList(incoming.copy(id = existing.id))
            return true
        }
        return false
    }

    suspend fun getListsForSync(): List<SharedList> = dao.getListsForSync()

    suspend fun insertListItem(item: SharedListItem): SharedListItem {
        val now = System.currentTimeMillis()
        val prepared = item.copy(
            syncId = item.syncId.ifBlank { UUID.randomUUID().toString() },
            updatedAtMillis = if (item.updatedAtMillis > 0) item.updatedAtMillis else now,
            isDeleted = false
        )
        val rowId = dao.insertListItem(prepared)
        return prepared.copy(id = rowId)
    }

    suspend fun updateListItem(item: SharedListItem): SharedListItem {
        val updated = item.copy(
            syncId = item.syncId.ifBlank { UUID.randomUUID().toString() },
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.updateListItem(updated)
        return updated
    }

    suspend fun softDeleteListItem(id: Long): SharedListItem? {
        val existing = dao.getListItemById(id) ?: return null
        val tombstone = existing.copy(isDeleted = true, updatedAtMillis = System.currentTimeMillis())
        dao.updateListItem(tombstone)
        return tombstone
    }

    suspend fun upsertSyncedListItem(incoming: SharedListItem): Boolean {
        val existing = dao.getListItemBySyncId(incoming.syncId)
        if (existing == null) {
            dao.insertListItem(incoming.copy(id = 0))
            return true
        }
        if (SyncMergeRules.shouldReplace(
                existing.updatedAtMillis, existing.isDeleted,
                incoming.updatedAtMillis, incoming.isDeleted
            )
        ) {
            dao.updateListItem(incoming.copy(id = existing.id))
            return true
        }
        return false
    }

    suspend fun getListItemsForSync(): List<SharedListItem> = dao.getListItemsForSync()

    suspend fun getListItems(listSyncId: String): List<SharedListItem> = dao.getListItems(listSyncId)

    // ---- Outbox ----

    suspend fun enqueueOutbox(messageType: String, payloadJson: String, dedupeKey: String) {
        dao.deleteOutboxByDedupeKey(dedupeKey)
        dao.insertOutboxItem(
            SyncOutboxItem(
                createdAtMillis = System.currentTimeMillis(),
                messageType = messageType,
                payloadJson = payloadJson,
                dedupeKey = dedupeKey,
                attempts = 0
            )
        )
    }

    suspend fun getOutboxOrdered(): List<SyncOutboxItem> = dao.getOutboxOrdered()

    suspend fun removeOutboxById(id: Long) = dao.deleteOutboxById(id)

    suspend fun removeOutboxByDedupeKey(dedupeKey: String) = dao.deleteOutboxByDedupeKey(dedupeKey)

    suspend fun incrementOutboxAttempts(id: Long) = dao.incrementOutboxAttempts(id)

    // ---- Caregivers ----

    suspend fun insertCaregiver(caregiver: CaregiverProfile) {
        dao.insertCaregiver(caregiver)
    }

    suspend fun setActiveCaregiver(activeId: Long) {
        dao.setActiveCaregiver(activeId)
    }

    suspend fun verifyAndSetActiveCaregiver(caregiverId: Long, pinInput: String): Boolean {
        val caregiver = dao.getCaregiverById(caregiverId) ?: return false
        if (caregiver.pin == pinInput) {
            dao.setActiveCaregiver(caregiverId)
            return true
        }
        return false
    }

    suspend fun clearAllSampleData() {
        dao.clearAllLogs()
        dao.clearAllGrowthRecords()
        dao.clearAllMilkStash()
        dao.clearAllMedicalRecords()
    }

    suspend fun wipeAllDataAndReset() {
        dao.clearAllLogs()
        dao.clearAllGrowthRecords()
        dao.clearAllMilkStash()
        dao.clearAllMedicalRecords()
        dao.clearPeerChatMessages()
        dao.clearAllDutySessions()
        dao.clearOutbox()
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
        // Clear medicine alarms on full wipe
        dao.getMedicineAlarms().forEach { dao.deleteMedicineAlarmById(it.id) }
    }

    suspend fun getCareCheckSettingsDirect(): CareCheckSettings =
        dao.getCareCheckSettings() ?: CareCheckSettings().also {
            dao.insertOrUpdateCareCheckSettings(it)
        }

    suspend fun saveCareCheckSettings(settings: CareCheckSettings): CareCheckSettings {
        val updated = settings.copy(
            id = 1,
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.insertOrUpdateCareCheckSettings(updated)
        return updated
    }

    suspend fun getMedicineAlarmsDirect(): List<MedicineAlarm> = dao.getMedicineAlarms()

    suspend fun getEnabledMedicineAlarms(): List<MedicineAlarm> = dao.getEnabledMedicineAlarms()

    suspend fun getMedicineAlarmById(id: Long): MedicineAlarm? = dao.getMedicineAlarmById(id)

    suspend fun upsertMedicineAlarm(alarm: MedicineAlarm): MedicineAlarm {
        val now = System.currentTimeMillis()
        val prepared = alarm.copy(
            updatedAtMillis = now,
            name = alarm.name.trim(),
            doseNote = alarm.doseNote.trim(),
            intervalMinutes = alarm.intervalMinutes.coerceAtLeast(1)
        )
        return if (prepared.id == 0L) {
            val rowId = dao.insertMedicineAlarm(prepared)
            prepared.copy(id = rowId)
        } else {
            dao.updateMedicineAlarm(prepared)
            prepared
        }
    }

    suspend fun deleteMedicineAlarm(id: Long) {
        dao.deleteMedicineAlarmById(id)
    }
}
