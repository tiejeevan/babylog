package com.example

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.data.database.BabyCareDatabase
import com.example.data.repository.BabyCareRepository
import com.example.engine.BabySoundSynthesizer
import com.example.engine.BluetoothCareEngine
import com.example.engine.ReminderEngine
import com.example.engine.VoiceCommandPrefs
import com.example.notification.BabyNotificationManager
import com.example.service.VoiceCommandForegroundService
import com.example.widget.BabyCareWidgetViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BabyCareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BabyNotificationManager.createNotificationChannels(this)
        BluetoothCareEngine.initialize(this)
        BabySoundSynthesizer.initialize(this)
        // Initialize reactive Room database sync for the Widget ViewModel
        BabyCareWidgetViewModel.initAutoSync(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                BluetoothCareEngine.setAppInForeground(true)
                // Microphone FGS may only start while the app is foreground-eligible.
                maybeRestartVoiceListening()
            }

            override fun onStop(owner: LifecycleOwner) {
                BluetoothCareEngine.setAppInForeground(false)
            }
        })
        CoroutineScope(Dispatchers.IO).launch {
            val dao = BabyCareDatabase.getDatabase(this@BabyCareApplication).babyCareDao()
            val repository = BabyCareRepository(dao)
            val ongoing = repository.getOngoingActivityDirect()
            if (ongoing != null) {
                com.example.service.OngoingTimerService.start(this@BabyCareApplication)
            }
            ReminderEngine.rescheduleAll(this@BabyCareApplication, repository)
        }
    }

    private fun maybeRestartVoiceListening() {
        if (!VoiceCommandPrefs.isEnabled(this)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        VoiceCommandForegroundService.start(this)
    }
}
