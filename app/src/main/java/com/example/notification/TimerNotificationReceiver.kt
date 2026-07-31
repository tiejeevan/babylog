package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.service.OngoingTimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimerNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    BabyNotificationManager.ACTION_PAUSE_TIMER -> {
                        OngoingTimerService.pauseTimerDirect(context)
                    }
                    BabyNotificationManager.ACTION_RESUME_TIMER -> {
                        OngoingTimerService.resumeTimerDirect(context)
                    }
                    BabyNotificationManager.ACTION_SWITCH_SIDE -> {
                        OngoingTimerService.switchSideDirect(context)
                    }
                    BabyNotificationManager.ACTION_STOP_TIMER -> {
                        OngoingTimerService.stopAndSaveDirect(context)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
