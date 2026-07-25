package com.example.data.repository

import com.example.data.dao.BabyCareDao
import com.example.data.model.ActivityLog
import com.example.data.model.BabyProfile
import com.example.data.model.CaregiverProfile
import com.example.data.model.GrowthRecord
import com.example.data.model.MedicalRecord
import com.example.data.model.MilestoneRecord
import com.example.data.model.MilkStashItem
import kotlinx.coroutines.flow.Flow

class BabyCareRepository(private val dao: BabyCareDao) {
    val babyProfile: Flow<BabyProfile?> = dao.getBabyProfileFlow()
    val allLogs: Flow<List<ActivityLog>> = dao.getAllLogsFlow()
    val recentLogs: Flow<List<ActivityLog>> = dao.getRecentLogsFlow(50)
    val ongoingActivity: Flow<ActivityLog?> = dao.getOngoingActivityFlow()
    val growthRecords: Flow<List<GrowthRecord>> = dao.getGrowthRecordsFlow()
    val medicalRecords: Flow<List<MedicalRecord>> = dao.getMedicalRecordsFlow()
    val milkStash: Flow<List<MilkStashItem>> = dao.getMilkStashFlow()
    val milestones: Flow<List<MilestoneRecord>> = dao.getMilestonesFlow()
    val caregivers: Flow<List<CaregiverProfile>> = dao.getCaregiversFlow()
    val activeCaregiver: Flow<CaregiverProfile?> = dao.getActiveCaregiverFlow()

    suspend fun getBabyProfileDirect(): BabyProfile? = dao.getBabyProfile()

    suspend fun saveProfile(profile: BabyProfile) {
        dao.insertOrUpdateProfile(profile)
    }

    suspend fun insertLog(log: ActivityLog): Long {
        return dao.insertLog(log)
    }

    suspend fun updateLog(log: ActivityLog) {
        dao.updateLog(log)
    }

    suspend fun deleteLog(id: Long) {
        dao.deleteLogById(id)
    }

    suspend fun insertGrowthRecord(record: GrowthRecord) {
        dao.insertGrowthRecord(record)
    }

    suspend fun deleteGrowthRecord(id: Long) {
        dao.deleteGrowthRecord(id)
    }

    suspend fun insertMedicalRecord(record: MedicalRecord) {
        dao.insertMedicalRecord(record)
    }

    suspend fun updateMedicalRecord(record: MedicalRecord) {
        dao.updateMedicalRecord(record)
    }

    suspend fun insertMilkStash(item: MilkStashItem) {
        dao.insertMilkStash(item)
    }

    suspend fun updateMilkStash(item: MilkStashItem) {
        dao.updateMilkStash(item)
    }

    suspend fun updateMilestone(milestone: MilestoneRecord) {
        dao.updateMilestone(milestone)
    }

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
    }
}
