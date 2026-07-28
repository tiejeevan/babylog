package com.example

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.data.database.BabyCareDatabase
import com.example.data.repository.BabyCareRepository
import com.example.engine.BabySoundSynthesizer
import com.example.engine.BluetoothCareEngine
import com.example.engine.ReminderEngine
import com.example.notification.BabyNotificationManager
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
            }

            override fun onStop(owner: LifecycleOwner) {
                BluetoothCareEngine.setAppInForeground(false)
            }
        })
        CoroutineScope(Dispatchers.IO).launch {
            val dao = BabyCareDatabase.getDatabase(this@BabyCareApplication).babyCareDao()
            val repository = BabyCareRepository(dao)
            ReminderEngine.rescheduleAll(this@BabyCareApplication, repository)
        }
    }
}
