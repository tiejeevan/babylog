package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.data.model.ActivityTypes
import com.example.data.model.BabyProfile
import com.example.ui.components.ActivityLogCard
import com.example.ui.components.IntelligentNeedCard
import com.example.ui.components.LiveActiveTimerCard
import com.example.ui.components.QuickActionGrid
import com.example.ui.components.TodaySummaryBar
import com.example.ui.components.TopBabyHeader
import com.example.ui.dialogs.AddGrowthDialog
import com.example.ui.dialogs.LogBottleDialog
import com.example.ui.dialogs.LogDiaperDialog
import com.example.ui.dialogs.LogMedicineDialog
import com.example.ui.dialogs.LogTemperatureDialog
import com.example.ui.dialogs.OnboardingSetupDialog
import com.example.ui.viewmodel.BabyCareViewModel

@Composable
fun DashboardScreen(
    viewModel: BabyCareViewModel,
    onNavigateToTimeline: () -> Unit,
    onNavigateToFamily: () -> Unit,
    onNavigateToSleepSound: () -> Unit = {},
    onNavigateToBluetooth: () -> Unit = {},
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
    val currentTimeMillis by viewModel.currentTimeMillis.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // Dialog state
    var activeActionDialog by remember { mutableStateOf<String?>(null) }
    var showSetupProfileDialog by remember { mutableStateOf(false) }

    LaunchedEffect(quickAction) {
        if (quickAction != null) {
            when (quickAction) {
                "LOG_FEED" -> activeActionDialog = ActivityTypes.BOTTLE
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
        ActivityTypes.BOTTLE -> {
            LogBottleDialog(
                onDismiss = { activeActionDialog = null },
                onConfirm = { vol, milkType, notes ->
                    viewModel.quickLogActivity(
                        activityType = ActivityTypes.BOTTLE,
                        volumeMl = vol,
                        milkType = milkType,
                        notes = notes
                    )
                    activeActionDialog = null
                }
            )
        }
        ActivityTypes.DIAPER -> {
            LogDiaperDialog(
                onDismiss = { activeActionDialog = null },
                onConfirm = { status, notes ->
                    viewModel.quickLogActivity(
                        activityType = ActivityTypes.DIAPER,
                        diaperStatus = status,
                        notes = notes
                    )
                    activeActionDialog = null
                }
            )
        }
        ActivityTypes.MEDICINE -> {
            LogMedicineDialog(
                onDismiss = { activeActionDialog = null },
                onConfirm = { name, dosage, notes ->
                    viewModel.quickLogActivity(
                        activityType = ActivityTypes.MEDICINE,
                        medicineName = name,
                        dosage = dosage,
                        notes = notes
                    )
                    activeActionDialog = null
                }
            )
        }
        ActivityTypes.TEMPERATURE -> {
            LogTemperatureDialog(
                onDismiss = { activeActionDialog = null },
                onConfirm = { temp, notes ->
                    viewModel.quickLogActivity(
                        activityType = ActivityTypes.TEMPERATURE,
                        temperatureCelsius = temp,
                        notes = notes
                    )
                    activeActionDialog = null
                }
            )
        }
        ActivityTypes.GROWTH -> {
            AddGrowthDialog(
                onDismiss = { activeActionDialog = null },
                onConfirm = { w, h, head, notes ->
                    viewModel.addGrowthRecord(w, h, head, notes)
                    viewModel.quickLogActivity(
                        activityType = ActivityTypes.GROWTH,
                        notes = "Logged $w kg, $h cm"
                    )
                    activeActionDialog = null
                }
            )
        }
    }

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
                onSwitchCaregiverClick = onNavigateToFamily,
                onProfileClick = { showSetupProfileDialog = true }
            )
        }

        // Main Live Timer if activity running
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (ongoingActivity != null) {
                    LiveActiveTimerCard(
                        ongoingActivity = ongoingActivity!!,
                        currentTimeMillis = currentTimeMillis,
                        onStopClick = { viewModel.stopLiveActivity() }
                    )
                } else {
                    IntelligentNeedCard(
                        prediction = prediction,
                        onActionClick = { type ->
                            when (type) {
                                ActivityTypes.SLEEP, ActivityTypes.BREASTFEEDING, ActivityTypes.TUMMY_TIME, ActivityTypes.PUMPING -> {
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
        }

        // Quick Actions Grid
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                QuickActionGrid(
                    onActionSelected = { type ->
                        when (type) {
                            ActivityTypes.SLEEP, ActivityTypes.BREASTFEEDING, ActivityTypes.TUMMY_TIME, ActivityTypes.PUMPING, ActivityTypes.BATH -> {
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
                TodaySummaryBar(summary = todaySummary)
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

        // Recent Logs List
        val displayLogs = recentLogs.take(8)
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
                        onDeleteClick = { viewModel.deleteLog(log.id) }
                    )
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

        // Bluetooth Peer Sync & Ping Quick Access Card
        item {
            Card(
                onClick = onNavigateToBluetooth,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("card_bluetooth_sync_launcher"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
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
                            text = "📡 Bluetooth Spouse Ping & Walkie-Talkie",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Direct offline phone-to-phone connection for urgent vibration pings & caregiver chat",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onNavigateToBluetooth,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Sync 📳", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
