package com.example.testing

import android.content.Context
import androidx.room.Room
import com.example.data.database.BabyCareDatabase
import com.example.data.model.BabyProfile
import com.example.data.model.CaregiverProfile
import com.example.data.repository.BabyCareRepository
import kotlinx.coroutines.runBlocking

object TestDatabase {
    fun createInMemory(context: Context): BabyCareDatabase =
        Room.inMemoryDatabaseBuilder(context, BabyCareDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    fun createRepository(context: Context): Pair<BabyCareDatabase, BabyCareRepository> {
        val db = createInMemory(context)
        return db to BabyCareRepository(db.babyCareDao())
    }

    suspend fun seedActiveCaregiver(
        repository: BabyCareRepository,
        name: String = "Mom",
        pin: String = "1234"
    ): CaregiverProfile {
        val caregiver = CaregiverProfile(
            id = 1,
            name = name,
            role = "Owner",
            relationship = "Mother",
            pin = pin,
            isActiveNow = true,
            avatarColorHex = "#FF7043"
        )
        repository.insertCaregiver(caregiver)
        return caregiver
    }

    suspend fun seedProfile(
        repository: BabyCareRepository,
        name: String = "Test Baby",
        setupDone: Boolean = true
    ): BabyProfile {
        val profile = BabyProfile(
            id = 1,
            name = name,
            isInitialSetupDone = setupDone,
            updatedAtMillis = System.currentTimeMillis()
        )
        return repository.saveProfile(profile)
    }

    fun seedDefaultsBlocking(repository: BabyCareRepository) = runBlocking {
        seedActiveCaregiver(repository)
        seedProfile(repository)
    }
}
