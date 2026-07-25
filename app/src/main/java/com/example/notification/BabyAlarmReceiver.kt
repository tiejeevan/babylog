package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BabyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "BabyCare Alert 👶"
        val message = intent.getStringExtra("message") ?: "Time to check on your baby's routine."
        val channelId = intent.getStringExtra("channel_id") ?: BabyNotificationManager.CHANNEL_REMINDERS

        BabyNotificationManager.showSystemNotification(
            context = context,
            title = title,
            message = message,
            channelId = channelId
        )
    }
}
