package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.database.BabyCareDatabase
import com.example.data.repository.BabyCareRepository
import com.example.engine.ReminderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores care-check and medicine alarms after reboot, package replace, or clock changes.
 *
 * Voice listening is not started here: microphone foreground services require a
 * foreground-eligible process. [VoiceCommandPrefs] remains enabled across reboot and
 * [com.example.BabyCareApplication] restarts listening when the user next opens the app.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val dao = BabyCareDatabase.getDatabase(appContext).babyCareDao()
                val repository = BabyCareRepository(dao)
                ReminderEngine.rescheduleAll(appContext, repository)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
