package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.model.BabyProfile
import com.example.engine.BluetoothCareEngine
import com.example.engine.BluetoothConnectionState
import com.example.engine.CustomLogRouter
import com.example.engine.IntelligentNeedEngine
import com.example.ui.components.ActivityLogCard
import com.example.ui.components.BabyStatusBoard
import com.example.ui.components.DashboardPatternHighlightCard
import com.example.ui.components.IntelligentNeedCard
import com.example.ui.components.LiveActiveTimerCard
import com.example.ui.components.QuickActionGrid
import com.example.ui.components.SmartSleepGapPromptCard
import com.example.ui.components.TodaySummaryBar
import com.example.ui.components.TopBabyHeader
import com.example.ui.dialogs.AddGrowthDialog
import com.example.ui.dialogs.EditActivityLogDialog
import com.example.ui.dialogs.LogBottleDialog
import com.example.ui.dialogs.LogCustomActionDialog
import com.example.ui.dialogs.LogDiaperDialog
import com.example.ui.dialogs.LogMedicineDialog
import com.example.ui.dialogs.LogNurseDialog
import com.example.ui.dialogs.LogSleepDialog
import com.example.ui.dialogs.LogTemperatureDialog
import com.example.ui.dialogs.NearbyCareSyncBottomSheet
import com.example.ui.dialogs.OnboardingSetupDialog
import com.example.ui.viewmodel.BabyCareViewModel
import kotlin.math.max

@Composable
fun DashboardScreen(
    viewModel: BabyCareViewModel,
    onNavigateToTimeline: () -> Unit,
    onNavigateToFamily: () -> Unit = {},
    onNavigateToInsights: () -> Unit = {},
    onNavigateToSleepSound: () -> Unit = {},
    onNavigateToBluetooth: () -> Unit = {},
    onNavigateToCareChat: () -> Unit = {},
    quickAction: String? = null,
    onQuickActionHandled: () -> Unit = {}
) {
    val profile by viewModel.babyProfile.collectAsStateWithLifecycle()
    val activeCaregiver by viewModel.activeCaregiver.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatusText.collectAsStateWithLifecycle()
    val ongoingActivity by viewModel.ongoingActivity.collectAsStateWithLifecycle()
    val prediction by viewModel.needPrediction.collectAsStateWithLifecycle()
    val todaySummary by viewModel.todaySummary.collectAsStateWithLifecycle()
    val recentLogs by viewModel.recentLogs.collectAsStateWithLifecycle()
    val allLogs by viewModel.allLogs.collectAsStateWithLifecycle()
    val currentTimeMillis by viewModel.currentTimeMillis.collectAsStateWithLifecycle()
    val activeNursingSide by viewModel.activeNursingSide.collectAsStateWithLifecycle()
    val nursingSideStartedAtMillis by viewModel.nursingSideStartedAtMillis.collectAsStateWithLifecycle()
    val dashboardPattern by viewModel.dashboardPatternHighlight.collectAsStateWithLifecycle()
    val careSyncEnabled by BluetoothCareEngine.careSyncEnabled.collectAsStateWithLifecycle()
    val connectionState by BluetoothCareEngine.connectionState.collectAsStateWithLifecycle()
    val connectedDeviceName by BluetoothCareEngine.connectedDeviceName.collectAsStateWithLifecycle()
    val unreadCount by BluetoothCareEngine.unreadIncomingCount.collectAsStateWithLifecycle()
    val showPeerChatFab = careSyncEnabled &&
        connectionState == BluetoothConnectionState.CONNECTED

    val context = LocalContext.current

    // Dialog state
    var activeActionDialog by remember { mutableStateOf<String?>(null) }
    var showSetupProfileDialog by remember { mutableStateOf(false) }
    var showNearbySyncSheet by remember { mutableStateOf(false) }
    var editingLog by remember { mutableStateOf<ActivityLog?>(null) }
    var dismissedSleepPrompt by remember { mutableStateOf(false) }
    val sleepGapPrompt = remember(recentLogs, ongoingActivity, currentTimeMillis) {
        IntelligentNeedEngine.detectUnloggedSleepGap(profile, recentLogs, ongoingActivity, currentTimeMillis)
    }
    var timelinePage by remember { mutableIntStateOf(0) }
    val timelinePageSize = 10
    val timelinePageCount = max(1, (recentLogs.size + timelinePageSize - 1) / timelinePageSize)

    LaunchedEffect(timelinePageCount) {
        if (timelinePage >= timelinePageCount) {
            timelinePage = timelinePageCount - 1
        }
    }

    LaunchedEffect(quickAction) {
        if (quickAction != null) {
            when (quickAction) {
                "LOG_FEED" -> activeActionDialog = ActivityTypes.BOTTLE
                "LOG_NURSE" -> viewModel.startLiveActivity(activityType = ActivityTypes.BREASTFEEDING)
                "LOG_SLEEP" -> viewModel.toggleSleepTimer()
                "LOG_DIAPER" -> activeActionDialog = ActivityTypes.DIAPER
            }
            onQuickActionHandled()
        }
    }

    val needProfileSetup = profile != null && !profile!!.isInitialSetupDone
    if (needProfileSetup || showSetupProfileDialog) {
        OnboardingSetupDialog(
            initialProfile = profile,
            onDismiss = { showSetupProfileDialog = false },
            onCompleteSetup = { updatedProfile, initWeight, initHeight ->
                viewModel.completeOnboardingSetup(updatedProfile, initWeight, initHeight)
                showSetupProfileDialog = false
                Toast.makeText(context, "Welcome! ${updatedProfile.name}'s setup is complete 🎉", Toast.LENGTH_LONG).show()
            }
        )
    }

    // Dialog Handling
    when (activeActionDialog) {
        ActivityTypes.SLEEP -> {
            LogSleepDialog(
                onDismiss = { activeActionDialog = null },
                onStartLiveTimer = {
                    viewModel.startLiveActivity(activityType = ActivityTypes.SLEEP)
                    activeActionDialog = null
                },
                onConfirmQuickNap = { durMin ->
                    val now = System.currentTimeMillis()
                    val durSec = durMin * 60L
                    val start = now - (durSec * 1000L)
                    viewModel.quickLogActivity(
                        activityType = ActivityTypes.SLEEP,
                        durationSeconds = durSec,
                        startTimeMillis = start,
                        endTimeMillis = now,
                        notes = "1-Tap Quick Nap (${durMin}m)"
                    )
                    activeActionDialog = null
                    Toast.makeText(context, "Logged ${durMin}m nap 💤", Toast.LENGTH_SHORT).show()
                },
                onConfirmPastSleep = { durMin, notes, timestamp ->
                    val durSec = durMin * 60L
                    val end = timestamp
                    val start = end - (durSec * 1000L)
                    val fullNotes = if (notes.isBlank()) "Nap (${durMin}m)" else "Nap (${durMin}m) — $notes"
                    viewModel.quickLogActivity(
                        activityType = ActivityTypes.SLEEP,
                        durationSeconds = durSec,
                        startTimeMillis = start,
                        endTimeMillis = end,
                        notes = fullNotes
                    )
                    activeActionDialog = null
                    Toast.makeText(context, "Logged past ${durMin}m nap 💤", Toast.LENGTH_SHORT).show()
                }
            )
        }
        ActivityTypes.BREASTFEEDING -> {
            LogNurseDialog(
                onDismiss = { activeActionDialog = null },
                onStartLiveTimer = { initialSide ->
                    viewModel.setNursingSide(initialSide)
                    viewModel.startLiveActivity(activityType = ActivityTypes.BREASTFEEDING)
                    activeActionDialog = null
                },
                onConfirmQuickNurse = { durMin ->
                    val now = System.currentTimeMillis()
                    val durSec = durMin * 60L
                    val halfSec = durSec / 2
                    val start = now - (durSec * 1000L)
                    viewModel.quickLogActivity(
                        activityType = ActivityTypes.BREASTFEEDING,
                        durationSeconds = durSec,
                        startTimeMillis = start,
                        endTimeMillis = now,
                        leftBreastSec = halfSec,
                        rightBreastSec = halfSec,
                        notes = "1-Tap Quick Nursing (${durMin}m)"
                    )
                    activeActionDialog = null
                    Toast.makeText(context, "Logged ${durMin}m nursing session 🍼", Toast.LENGTH_SHORT).show()
                },
                onConfirmPastNurse = { leftMin, rightMin, notes, timestamp ->
                    val leftSec = leftMin * 60L
                    val rightSec = rightMin * 60L
                    val totalSec = leftSec + rightSec
                    val end = timestamp
                    val start = end - (totalSec * 1000L)
                    val sideNotes = "Nursing (L: ${leftMin}m, R: ${rightMin}m)"
                    val fullNotes = if (notes.isBlank()) sideNotes else "$sideNotes — $notes"
                    viewModel.quickLogActivity(
                        activityType = ActivityTypes.BREASTFEEDING,
                        durationSeconds = totalSec,
                        startTimeMillis = start,
                        endTimeMillis = end,
                        leftBreastSec = leftSec,
                        rightBreastSec = rightSec,
                        notes = fullNotes
                    )
                    activeActionDialog = null
                    Toast.makeText(context, "Logged past nursing session 🍼", Toast.LENGTH_SHORT).show()
                }
            )
        }
        ActivityTypes.BOTTLE -> {
            LogBottleDialog(
                onDismiss = { activeActionDialog = null },
                onConfirm = { vol, milkType, notes, timestamp ->
                    viewModel.quickLogActivity(
                        activityType = ActivityTypes.BOTTLE,
                        volumeMl = vol,
                        milkType = milkType,
                        notes = notes,
                        startTimeMillis = timestamp,
                        endTimeMillis = timestamp
                    )
                    activeActionDialog = null
                }
            )
        }
        ActivityTypes.DIAPER -> {
            LogDiaperDialog(
                onDismiss = { activeActionDialog = null },
                onConfirm = { status, notes, timestamp ->
                    viewModel.quickLogActivity(
                        activityType = ActivityTypes.DIAPER,
                        diaperStatus = status,
                        notes = notes,
                        startTimeMillis = timestamp,
                        endTimeMillis = timestamp
                    )
                    activeActionDialog = null
                }
            )
        }
        ActivityTypes.MEDICINE -> {
            LogMedicineDialog(
                onDismiss = { activeActionDialog = null },
                onConfirm = { name, dosage, notes, timestamp ->
                    viewModel.quickLogActivity(
                        activityType = ActivityTypes.MEDICINE,
                        medicineName = name,
                        dosage = dosage,
                        notes = notes,
                        startTimeMillis = timestamp,
                        endTimeMillis = timestamp
                    )
                    activeActionDialog = null
                }
            )
        }
        ActivityTypes.TEMPERATURE -> {
            LogTemperatureDialog(
                onDismiss = { activeActionDialog = null },
                onConfirm = { temp, notes, timestamp ->
                    viewModel.quickLogActivity(
                        activityType = ActivityTypes.TEMPERATURE,
                        temperatureCelsius = temp,
                        notes = notes,
                        startTimeMillis = timestamp,
                        endTimeMillis = timestamp
                    )
                    activeActionDialog = null
                }
            )
        }
        ActivityTypes.GROWTH -> {
            AddGrowthDialog(
                onDismiss = { activeActionDialog = null },
                onConfirm = { w, h, head, notes, timestamp ->
                    viewModel.addGrowthRecord(w, h, head, notes)
                    viewModel.quickLogActivity(
                        activityType = ActivityTypes.GROWTH,
                        notes = "Logged $w kg, $h cm",
                        startTimeMillis = timestamp,
                        endTimeMillis = timestamp
                    )
                    activeActionDialog = null
                }
            )
        }
        ActivityTypes.CUSTOM -> {
            LogCustomActionDialog(
                onDismiss = { activeActionDialog = null },
                onConfirm = { title, notes, timestamp ->
                    val combined = if (notes.isBlank()) title else "$title — $notes"
                    val routed = CustomLogRouter.route(title, notes)
                    viewModel.quickLogActivity(
                        activityType = routed.activityType,
                        diaperStatus = routed.diaperStatus,
                        volumeMl = routed.volumeMl,
                        durationSeconds = if (routed.durationSeconds > 0) routed.durationSeconds else 600,
                        notes = routed.notes,
                        startTimeMillis = timestamp,
                        endTimeMillis = timestamp
                    )
                    activeActionDialog = null
                    val toastMsg = if (routed.activityType != ActivityTypes.CUSTOM) {
                        val label = routed.activityType.lowercase().replaceFirstChar { it.uppercase() }
                        "Categorized as $label ✓"
                    } else {
                        "Logged: $title"
                    }
                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("dashboard_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Header
            item {
                TopBabyHeader(
                    profile = profile,
                    activeCaregiver = activeCaregiver,
                    syncStatus = syncStatus,
                    onSwitchCaregiverClick = { showNearbySyncSheet = true },
                    onProfileClick = { showSetupProfileDialog = true },
                    onOpenCareSyncClick = { showNearbySyncSheet = true },
                    careSyncEnabled = careSyncEnabled,
                    connectionState = connectionState,
                    connectedDeviceName = connectedDeviceName
                )
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    BabyStatusBoard(
                        logs = recentLogs,
                        ongoing = ongoingActivity,
                        currentTimeMillis = currentTimeMillis
                    )
                }
            }

            // Main Live Timer if activity running
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    if (ongoingActivity != null) {
                        LiveActiveTimerCard(
                            ongoingActivity = ongoingActivity!!,
                            currentTimeMillis = currentTimeMillis,
                            onStopClick = { viewModel.stopLiveActivity() },
                            activeNursingSide = activeNursingSide,
                            nursingSideStartedAtMillis = nursingSideStartedAtMillis,
                            onNursingSideChange = { viewModel.setNursingSide(it) }
                        )
                    } else {
                        IntelligentNeedCard(
                            prediction = prediction,
                            onActionClick = { type ->
                                when (type) {
                                    ActivityTypes.TUMMY_TIME, ActivityTypes.PUMPING -> {
                                        viewModel.startLiveActivity(activityType = type)
                                    }
                                    else -> {
                                        activeActionDialog = type
                                    }
                                }
                            }
                        )
                    }

                    if (sleepGapPrompt != null && !dismissedSleepPrompt) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SmartSleepGapPromptCard(
                            prompt = sleepGapPrompt,
                            babyName = profile?.name ?: "Baby",
                            onQuickLogNap = { durMin ->
                                val now = System.currentTimeMillis()
                                val start = sleepGapPrompt.estimatedNapStartMillis
                                val end = (start + (durMin * 60_000L)).coerceAtMost(now)
                                val durSec = ((end - start) / 1000L).coerceAtLeast(60L)
                                viewModel.quickLogActivity(
                                    activityType = ActivityTypes.SLEEP,
                                    durationSeconds = durSec,
                                    startTimeMillis = start,
                                    endTimeMillis = end,
                                    notes = "Logged in timeline gap (${durMin}m nap)"
                                )
                                dismissedSleepPrompt = true
                                Toast.makeText(context, "Logged ${durMin}m nap into timeline gap 💤", Toast.LENGTH_SHORT).show()
                            },
                            onDismiss = { dismissedSleepPrompt = true }
                        )
                    }
                }
            }

            // Quick Actions Grid
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    QuickActionGrid(
                        onActionSelected = { type ->
                            when (type) {
                                ActivityTypes.TUMMY_TIME, ActivityTypes.PUMPING, ActivityTypes.BATH -> {
                                    viewModel.startLiveActivity(activityType = type)
                                }
                                else -> {
                                    activeActionDialog = type
                                }
                            }
                        }
                    )
                }
            }

            // Today Summary Bar
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TodaySummaryBar(allLogs = allLogs, summary = todaySummary)
                }
            }

            // Patterns highlight (7-day)
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    DashboardPatternHighlightCard(
                        report = dashboardPattern,
                        onOpenInsights = onNavigateToInsights
                    )
                }
            }

            // Recent Activity Feed Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE ACTIVITY TIMELINE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp
                    )

                    TextButton(onClick = onNavigateToTimeline) {
                        Text("View Full History", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Recent Logs List (10 per page, no delete — delete lives on Timeline tab)
            val safeTimelinePage = timelinePage.coerceIn(0, timelinePageCount - 1)
            val displayLogs = recentLogs
                .drop(safeTimelinePage * timelinePageSize)
                .take(timelinePageSize)
            if (displayLogs.isEmpty()) {
                item {
                    Text(
                        text = "No activities logged yet today. Tap a quick action above to begin!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                items(displayLogs, key = { it.id }) { log ->
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ActivityLogCard(
                            log = log,
                            onEditClick = { editingLog = log }
                        )
                    }
                }
                if (timelinePageCount > 1) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { timelinePage = (safeTimelinePage - 1).coerceAtLeast(0) },
                                enabled = safeTimelinePage > 0,
                                modifier = Modifier.testTag("dashboard_timeline_prev")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous page"
                                )
                            }
                            Text(
                                text = "Page ${safeTimelinePage + 1} of $timelinePageCount",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = {
                                    timelinePage = (safeTimelinePage + 1).coerceAtMost(timelinePageCount - 1)
                                },
                                enabled = safeTimelinePage < timelinePageCount - 1,
                                modifier = Modifier.testTag("dashboard_timeline_next")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next page"
                                )
                            }
                        }
                    }
                }
            }

            // Sleep Sound & Night Light Mode Quick Access Card (Dashboard Bottom)
            item {
                Card(
                    onClick = onNavigateToSleepSound,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("card_sleep_sound_launcher"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🌙 Baby Sleep Sound Machine & Night Light",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Synthesized pink noise, womb heartbeat, rain & soothing glowing light palette",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = onNavigateToSleepSound,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Open 🎵", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (showNearbySyncSheet) {
            NearbyCareSyncBottomSheet(
                viewModel = viewModel,
                onDismiss = { showNearbySyncSheet = false }
            )
        }

        if (showPeerChatFab) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                FloatingActionButton(
                    onClick = onNavigateToCareChat,
                    modifier = Modifier.testTag("fab_peer_chat"),
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "Open caregiver messages"
                    )
                }
                if (unreadCount > 0) {
                    Text(
                        text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                        color = MaterialTheme.colorScheme.onError,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .background(
                                MaterialTheme.colorScheme.error,
                                CircleShape
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }

    editingLog?.let { log ->
        EditActivityLogDialog(
            log = log,
            onDismiss = { editingLog = null },
            onConfirm = { updated ->
                viewModel.editLog(updated)
                editingLog = null
            }
        )
    }
}
