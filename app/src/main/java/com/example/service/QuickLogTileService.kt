package com.example.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.data.database.BabyCareDatabase
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.repository.BabyCareRepository
import com.example.engine.BluetoothCareEngine
import com.example.widget.BabyCareWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.N)
class QuickLogTileService : TileService() {

    private fun getRepository(): BabyCareRepository {
        val dao = BabyCareDatabase.getDatabase(applicationContext).babyCareDao()
        return BabyCareRepository(dao)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        CoroutineScope(Dispatchers.IO).launch {
            val repository = getRepository()
            val ongoing = repository.getOngoingActivityDirect()

            if (ongoing != null) {
                // Ongoing activity exists -> Stop and save it
                OngoingTimerService.stopAndSave(applicationContext)
            } else {
                // No ongoing activity -> Quick log 120ml bottle
                val now = System.currentTimeMillis()
                val caregiver = repository.getActiveCaregiverDirect()
                val log = ActivityLog(
                    babyId = 1,
                    activityType = ActivityTypes.BOTTLE,
                    startTimeMillis = now - 600_000L,
                    endTimeMillis = now,
                    durationSeconds = 600L,
                    volumeMl = 120,
                    milkType = "Formula",
                    notes = "1-Tap Quick Log from Status Bar Tile",
                    caregiverName = caregiver?.name ?: "Caregiver",
                    caregiverRole = caregiver?.relationship ?: "Parent",
                    timestampMillis = now
                )
                val saved = repository.insertLog(log)
                BluetoothCareEngine.broadcastLogUpsert(saved)
                BabyCareWidgetProvider.updateAllWidgets(applicationContext)
            }
            updateTileState()
        }
    }

    private fun updateTileState() {
        CoroutineScope(Dispatchers.IO).launch {
            val repository = getRepository()
            val ongoing = repository.getOngoingActivityDirect()

            withContext(Dispatchers.Main) {
                val tile = qsTile ?: return@withContext
                if (ongoing != null) {
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = "Stop ${ongoing.activityType}"
                    tile.contentDescription = "Active ${ongoing.activityType} timer running. Tap to stop."
                } else {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = "Quick Log Bottle"
                    tile.contentDescription = "Tap to log 120ml Bottle feeding."
                }
                tile.updateTile()
            }
        }
    }
}
