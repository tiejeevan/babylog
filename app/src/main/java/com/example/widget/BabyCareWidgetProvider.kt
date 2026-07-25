package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.model.ActivityTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class BabyCareWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_START_SLEEP = "com.example.ACTION_WIDGET_START_SLEEP"
        const val ACTION_WIDGET_START_FEED = "com.example.ACTION_WIDGET_START_FEED"
        const val ACTION_WIDGET_START_NURSING = "com.example.ACTION_WIDGET_START_NURSING"
        const val ACTION_WIDGET_STOP_ACTIVITY = "com.example.ACTION_WIDGET_STOP_ACTIVITY"
        const val ACTION_WIDGET_TOGGLE_PAUSE = "com.example.ACTION_WIDGET_TOGGLE_PAUSE"
        const val ACTION_WIDGET_REFRESH = "com.example.ACTION_WIDGET_REFRESH"

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                if (appWidgetManager == null) {
                    WidgetLogger.log(context, "AppWidgetManager.getInstance returned null", isError = true)
                    return
                }
                val componentName = ComponentName(context, BabyCareWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                WidgetLogger.log(context, "updateAllWidgets called. Active widget instances: ${appWidgetIds?.size ?: 0}")
                if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        for (appWidgetId in appWidgetIds) {
                            updateAppWidget(context, appWidgetManager, appWidgetId)
                        }
                    }
                } else {
                    WidgetLogger.log(context, "No widget instances placed on home screen yet.")
                }
            } catch (e: Exception) {
                WidgetLogger.log(context, "Error in updateAllWidgets", isError = true, throwable = e)
            }
        }

        suspend fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            WidgetLogger.log(context, "Updating widget instance ID: $appWidgetId via BabyCareWidgetViewModel")
            val views = RemoteViews(context.packageName, R.layout.baby_care_widget_layout)

            // 1. PendingIntents Setup
            // Open Main App
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val launchPendingIntent = PendingIntent.getActivity(
                context, 100, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, launchPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_baby_name, launchPendingIntent)

            // Action Start Feed
            val feedIntent = Intent(context, BabyCareWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_START_FEED
            }
            val feedPendingIntent = PendingIntent.getBroadcast(
                context, 101, feedIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_start_feed, feedPendingIntent)

            // Action Start Nursing
            val nursingIntent = Intent(context, BabyCareWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_START_NURSING
            }
            val nursingPendingIntent = PendingIntent.getBroadcast(
                context, 106, nursingIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_start_nursing, nursingPendingIntent)

            // Action Start Sleep
            val sleepIntent = Intent(context, BabyCareWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_START_SLEEP
            }
            val sleepPendingIntent = PendingIntent.getBroadcast(
                context, 102, sleepIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_start_sleep, sleepPendingIntent)

            // Action Log Diaper (Opens app directly to diaper log dialog)
            val diaperIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("quick_action", "LOG_DIAPER")
            }
            val diaperPendingIntent = PendingIntent.getActivity(
                context, 103, diaperIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_log_diaper, diaperPendingIntent)

            // Action Stop Live
            val stopIntent = Intent(context, BabyCareWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_STOP_ACTIVITY
            }
            val stopPendingIntent = PendingIntent.getBroadcast(
                context, 104, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_stop_live, stopPendingIntent)

            // Action Pause Live
            val pauseIntent = Intent(context, BabyCareWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_TOGGLE_PAUSE
            }
            val pausePendingIntent = PendingIntent.getBroadcast(
                context, 105, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_pause_live, pausePendingIntent)

            // 2. Fetch Widget State from BabyCareWidgetViewModel
            try {
                val state = BabyCareWidgetViewModel.getWidgetState(context)

                // Baby Profile Name
                views.setTextViewText(R.id.widget_baby_name, state.babyName)

                // Today's Feeds summary
                val feedText = if (state.feedVolumeMl > 0) {
                    "${state.feedCount} feeds (${state.feedVolumeMl}ml)"
                } else {
                    "${state.feedCount} feeds"
                }
                views.setTextViewText(R.id.widget_today_feeds, feedText)

                // Today's Sleep summary
                views.setTextViewText(
                    R.id.widget_today_sleep,
                    "${state.sleepHours}h ${state.sleepMinutes}m (${state.napCount} naps)"
                )

                // Today's Diaper summary
                views.setTextViewText(R.id.widget_today_diapers, "${state.diaperCount} changes")

                // Last Activities Status
                views.setTextViewText(
                    R.id.widget_last_activities_text,
                    "Last Feed: ${state.lastFeedText} • Last Diaper: ${state.lastDiaperText}"
                )

                // Ongoing Live Activity
                val ongoing = state.ongoingActivity
                if (ongoing != null) {
                    views.setViewVisibility(R.id.widget_live_container, View.VISIBLE)
                    val now = System.currentTimeMillis()
                    val elapsedSec = ((now - ongoing.startTimeMillis) / 1000).coerceAtLeast(0)
                    val hrs = elapsedSec / 3600
                    val mins = (elapsedSec % 3600) / 60
                    val secs = elapsedSec % 60
                    val timerStr = String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)

                    val typeDisplay = when (ongoing.activityType) {
                        ActivityTypes.SLEEP -> "SLEEPING 😴"
                        ActivityTypes.BREASTFEEDING -> "BREASTFEEDING 🤱"
                        ActivityTypes.BOTTLE -> "BOTTLE FEEDING 🍼"
                        else -> "${ongoing.activityType.uppercase()} ⏱️"
                    }

                    val isPaused = ongoing.notes.contains("[Paused]")
                    val statusPrefix = if (isPaused) "⏸️ PAUSED:" else "🔴 LIVE:"

                    views.setTextViewText(R.id.widget_live_badge, "$statusPrefix $typeDisplay")
                    views.setTextViewText(R.id.widget_live_timer, timerStr)

                    // Dynamic Pause / Resume Button Label
                    val pauseButtonText = if (isPaused) "Resume ▶️" else "Pause ⏸️"
                    views.setTextViewText(R.id.btn_widget_pause_live, pauseButtonText)

                    val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
                    val startTimeFormatted = timeFormatter.format(ongoing.startTimeMillis)
                    views.setTextViewText(R.id.widget_live_countdown, "Started at $startTimeFormatted • Tap buttons to control")
                } else {
                    views.setViewVisibility(R.id.widget_live_container, View.GONE)
                }

                WidgetLogger.log(
                    context,
                    "Widget ID $appWidgetId updated via ViewModel. Baby: ${state.babyName}, Feeds: ${state.feedCount}, Sleep: ${state.sleepHours}h ${state.sleepMinutes}m, Ongoing: ${ongoing?.activityType ?: "None"}"
                )

            } catch (e: Exception) {
                WidgetLogger.log(context, "Error fetching state from ViewModel for widget ID $appWidgetId", isError = true, throwable = e)
            }

            try {
                appWidgetManager.updateAppWidget(appWidgetId, views)
                WidgetLogger.log(context, "Successfully pushed RemoteViews update to appWidgetId $appWidgetId")
            } catch (e: Exception) {
                WidgetLogger.log(context, "Failed to call updateAppWidget on AppWidgetManager for ID $appWidgetId", isError = true, throwable = e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        WidgetLogger.log(context, "onReceive triggered with action: $action")

        // Ensure reactive Room listener is active
        BabyCareWidgetViewModel.initAutoSync(context)

        when (action) {
            ACTION_WIDGET_START_SLEEP -> {
                val pendingResult = goAsync()
                BabyCareWidgetViewModel.handleStartSleep(context) {
                    try { pendingResult.finish() } catch (_: Exception) {}
                }
            }

            ACTION_WIDGET_START_FEED -> {
                val pendingResult = goAsync()
                BabyCareWidgetViewModel.handleStartFeed(context) {
                    try { pendingResult.finish() } catch (_: Exception) {}
                }
            }

            ACTION_WIDGET_START_NURSING -> {
                val pendingResult = goAsync()
                BabyCareWidgetViewModel.handleStartNursing(context) {
                    try { pendingResult.finish() } catch (_: Exception) {}
                }
            }

            ACTION_WIDGET_STOP_ACTIVITY -> {
                val pendingResult = goAsync()
                BabyCareWidgetViewModel.handleStopActivity(context) {
                    try { pendingResult.finish() } catch (_: Exception) {}
                }
            }

            ACTION_WIDGET_TOGGLE_PAUSE -> {
                val pendingResult = goAsync()
                BabyCareWidgetViewModel.handleTogglePause(context) {
                    try { pendingResult.finish() } catch (_: Exception) {}
                }
            }

            ACTION_WIDGET_REFRESH -> {
                WidgetLogger.log(context, "Widget Refresh requested via button")
                updateAllWidgets(context)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetLogger.log(context, "onUpdate broadcast received for ${appWidgetIds.size} widget IDs: ${appWidgetIds.joinToString()}")
        BabyCareWidgetViewModel.initAutoSync(context)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } catch (e: Exception) {
                WidgetLogger.log(context, "Error in onUpdate", isError = true, throwable = e)
            } finally {
                try {
                    pendingResult.finish()
                } catch (_: Exception) {}
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetLogger.log(context, "onEnabled broadcast received (First widget instance added to home screen)")
        BabyCareWidgetViewModel.initAutoSync(context)
        updateAllWidgets(context)
    }
}
