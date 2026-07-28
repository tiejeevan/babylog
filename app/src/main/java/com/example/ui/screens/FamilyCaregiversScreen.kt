@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.backup.FullBackupManager
import com.example.data.model.BabyProfile
import com.example.data.model.CaregiverProfile
import com.example.engine.BluetoothCareEngine
import com.example.engine.CareSyncPrefs
import com.example.ui.dialogs.AddCaregiverDialog
import com.example.ui.dialogs.EnterPinDialog
import com.example.ui.viewmodel.BabyCareViewModel
import com.example.ui.viewmodel.BackupUiState
import com.example.ui.theme.parseHexColor
import android.widget.Toast
import java.util.Calendar
import java.util.Locale
@Composable
fun FamilyCaregiversScreen(
    viewModel: BabyCareViewModel,
    onNavigateToBluetooth: () -> Unit = {}
) {
    val caregivers by viewModel.caregivers.collectAsStateWithLifecycle()
    val activeCaregiver by viewModel.activeCaregiver.collectAsStateWithLifecycle()
    val profile by viewModel.babyProfile.collectAsStateWithLifecycle()
    val activeDuty by viewModel.activeDuty.collectAsStateWithLifecycle()
    val muteOffDuty by viewModel.muteNonUrgentWhenOffDuty.collectAsStateWithLifecycle()
    val vibrateOnReceive by viewModel.vibrateOnReceive.collectAsStateWithLifecycle()

    val context = LocalContext.current

    var showAddCaregiverDialog by remember { mutableStateOf(false) }
    var selectedCaregiverToSwitch by remember { mutableStateOf<CaregiverProfile?>(null) }
    var showResetConfirmationDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmationDialog by remember { mutableStateOf(false) }
    var showWidgetLogDialog by remember { mutableStateOf(false) }
    var babyNameText by remember(profile?.name) { mutableStateOf(profile?.name ?: "Your Baby") }
    var feedIntervalText by remember { mutableStateOf((profile?.targetFeedingIntervalMinutes ?: 180).toString()) }
    var napIntervalText by remember { mutableStateOf((profile?.targetNapIntervalMinutes ?: 150).toString()) }
    var widgetLogs by remember { mutableStateOf(emptyList<String>()) }
    var widgetDiagnosticReport by remember { mutableStateOf("") }

    val backupUiState by viewModel.backupUiState.collectAsStateWithLifecycle()
    val backupInProgress = backupUiState is BackupUiState.InProgress

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.backupToUri(uri)
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreFromUri(uri)
        }
    }

    LaunchedEffect(backupUiState) {
        when (val state = backupUiState) {
            is BackupUiState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.clearBackupUiState()
            }
            is BackupUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.clearBackupUiState()
            }
            else -> Unit
        }
    }

    val familyPin by BluetoothCareEngine.passcode.collectAsStateWithLifecycle()
    val careSyncStatus by BluetoothCareEngine.statusText.collectAsStateWithLifecycle()
    var familyPinDraft by remember(familyPin) { mutableStateOf(familyPin) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("family_caregivers_screen")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "FAMILY & CAREGIVER COORDINATION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "Co-Parents, Grandparents & Nannies",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        // Active Caregiver Selection
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "WHO IS CARING FOR BABY RIGHT NOW?",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select caregiver identity with 4-digit PIN verification",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    caregivers.forEach { caregiver ->
                        val isSelected = caregiver.id == (activeCaregiver?.id ?: 1L)
                        Surface(
                            onClick = {
                                if (!isSelected) {
                                    selectedCaregiverToSwitch = caregiver
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("caregiver_option_${caregiver.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        if (!isSelected) {
                                            selectedCaregiverToSwitch = caregiver
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(caregiver.avatarColorHex))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = caregiver.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "${caregiver.relationship} • Role: ${caregiver.role} • PIN Protection Active",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Active Now",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { showAddCaregiverDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_caregiver_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Connect New Caregiver / Family Member", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // On-duty hand-off
        item {
            val iAmOnDuty = activeDuty?.isActive == true &&
                activeDuty?.caregiverName.equals(activeCaregiver?.name, ignoreCase = true)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_duty_handoff"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "I'm on duty",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val dutyStatus = when {
                        activeDuty == null || activeDuty?.isActive != true -> "No one claimed primary caregiver"
                        activeDuty!!.untilMillis != null -> {
                            val fmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                            "${activeDuty!!.caregiverName} is on duty until ${fmt.format(java.util.Date(activeDuty!!.untilMillis!!))}"
                        }
                        else -> "${activeDuty!!.caregiverName} is on duty"
                    }
                    Text(
                        text = dutyStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (iAmOnDuty) {
                        OutlinedButton(
                            onClick = { viewModel.releaseDuty() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Release duty")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.claimDuty(null) },
                            modifier = Modifier.fillMaxWidth().testTag("family_claim_duty"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("I'm on duty", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.claimDutyForHours(1) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("1 hour") }
                            OutlinedButton(
                                onClick = { viewModel.claimDutyUntilHour(22) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Until 10pm") }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Alert settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vibrate on incoming messages", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Vibrate when chat or ping arrives from another caregiver.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = vibrateOnReceive,
                            onCheckedChange = { viewModel.setVibrateOnReceive(it) },
                            modifier = Modifier.testTag("family_vibrate_on_receive_toggle")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mute non-urgent pings when off duty", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Urgent pings still alert. Chat history is always saved.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = muteOffDuty,
                            onCheckedChange = { viewModel.setMuteNonUrgentWhenOffDuty(it) }
                        )
                    }
                }
            }
        }

        // Family PIN for Nearby Care Sync
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Family PIN (Nearby Sync)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Use the same PIN on Mom, Dad, and Nanny phones. Turn on Care Sync nearby to chat, ping, and sync all baby data (logs, growth, medical, milk, milestones, profile).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = careSyncStatus,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = familyPinDraft,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) familyPinDraft = it },
                        label = { Text("Family PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                BluetoothCareEngine.setPasscode(familyPinDraft)
                                CareSyncPrefs.setFamilyPin(context, familyPinDraft)
                                Toast.makeText(context, "Family PIN saved", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save PIN")
                        }
                        Button(
                            onClick = {
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(
                                        android.content.Intent.EXTRA_TEXT,
                                        "Join ${profile?.name ?: "our baby"}'s BabyCare Care Sync. Install the app, open Care Sync, and enter Family PIN: $familyPinDraft"
                                    )
                                    type = "text/plain"
                                }
                                context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Family PIN"))
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share PIN")
                        }
                    }
                }
            }
        }

        // Bluetooth Direct Peer Sync & Walkie-Talkie Deck
        item {
            Card(
                onClick = onNavigateToBluetooth,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_family_bluetooth_sync"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📡", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Nearby Care Sync & Walkie-Talkie",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "NEW",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Connect nearby family phones. Vibration pings, caregiver chat, and activity log sync while connected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onNavigateToBluetooth,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Care Sync 📳", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Baby Profile Settings Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "BABY PROFILE & ROUTINE TARGETS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = babyNameText,
                        onValueChange = { babyNameText = it },
                        label = { Text("Baby Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = feedIntervalText,
                        onValueChange = { feedIntervalText = it },
                        label = { Text("Target Feeding Interval (minutes, e.g. 180 = 3 hrs)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = napIntervalText,
                        onValueChange = { napIntervalText = it },
                        label = { Text("Target Nap Interval / Wake Window (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val feedInt = feedIntervalText.toIntOrNull() ?: 180
                            val napInt = napIntervalText.toIntOrNull() ?: 150
                            val current = profile ?: BabyProfile()
                            val updated = current.copy(
                                name = babyNameText,
                                targetFeedingIntervalMinutes = feedInt,
                                targetNapIntervalMinutes = napInt
                            )
                            viewModel.saveBabyProfile(updated)
                            Toast.makeText(context, "Baby profile saved!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Baby Profile Settings", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Widget Diagnostic Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HOME SCREEN WIDGET DIAGNOSTICS 🩺",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            val activeCount = try {
                                val mgr = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    context.getSystemService(android.appwidget.AppWidgetManager::class.java)
                                } else null
                                val comp = android.content.ComponentName(context, com.example.widget.BabyCareWidgetProvider::class.java)
                                mgr?.getAppWidgetIds(comp)?.size ?: 0
                            } catch (_: Exception) { 0 }

                            Text(
                                text = "Active: $activeCount",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "If your widget failed to show or update, view live error logs and test database syncing below.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val mgr = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    context.getSystemService(android.appwidget.AppWidgetManager::class.java)
                                } else null
                                val comp = android.content.ComponentName(context, com.example.widget.BabyCareWidgetProvider::class.java)
                                val isSupported = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    mgr?.isRequestPinAppWidgetSupported == true
                                } else false
                                val count = try { mgr?.getAppWidgetIds(comp)?.size ?: 0 } catch (_: Exception) { -1 }

                                widgetLogs = com.example.widget.WidgetLogger.getLogs(context)
                                widgetDiagnosticReport = "📱 SYSTEM WIDGET DIAGNOSTIC REPORT\n\n" +
                                        "• Device OS: Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n" +
                                        "• AppWidgetManager Service: ${if (mgr != null) "Ready" else "Unavailable"}\n" +
                                        "• Pin Supported: $isSupported\n" +
                                        "• Placed Widget Instances: $count\n" +
                                        "• Last Error Log: ${com.example.widget.WidgetLogger.getLastError(context) ?: "None recorded"}"
                                showWidgetLogDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("View Error Logs 🔍", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                com.example.widget.BabyCareWidgetProvider.updateAllWidgets(context)
                                widgetLogs = com.example.widget.WidgetLogger.getLogs(context)
                                Toast.makeText(context, "Triggered widget refresh! Checked database.", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Sync Widget 🔄", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val appWidgetManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                context.getSystemService(android.appwidget.AppWidgetManager::class.java)
                            } else null
                            val myProvider = android.content.ComponentName(context, com.example.widget.BabyCareWidgetProvider::class.java)
                            val isPinSupported = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                appWidgetManager?.isRequestPinAppWidgetSupported == true
                            } else false
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                                appWidgetManager != null && isPinSupported
                            ) {
                                try {
                                    val pinnedWidgetCallbackIntent = android.content.Intent(
                                        context,
                                        com.example.widget.BabyCareWidgetProvider::class.java
                                    )
                                    val successCallback = android.app.PendingIntent.getBroadcast(
                                        context,
                                        0,
                                        pinnedWidgetCallbackIntent,
                                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                                            android.app.PendingIntent.FLAG_IMMUTABLE
                                    )
                                    appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
                                    Toast.makeText(context, "Pin request sent to launcher", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Pin failed: ${e.localizedMessage ?: e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "Long-press Home → Widgets → BabyCare Live",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            com.example.widget.BabyCareWidgetProvider.updateAllWidgets(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_widget_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Add Home Screen Widget 📱", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    val recentError = com.example.widget.WidgetLogger.getLastError(context)
                    if (recentError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "⚠️ Last Widget Error:\n$recentError",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Factory Reset / Wipe Data Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "PERSISTENT MEMORY & DATA MANAGEMENT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Full backup saves all logs, growth, medical visits, notes, pics, and videos to a ZIP you keep (Drive, Files, email). App updates keep local data. After uninstall, restore from your ZIP.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (backupInProgress) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Working on backup/restore…",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                createBackupLauncher.launch(FullBackupManager.suggestedBackupFileName())
                            },
                            enabled = !backupInProgress,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("backup_all_data_btn")
                        ) {
                            Text("Backup all data", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showRestoreConfirmationDialog = true },
                            enabled = !backupInProgress,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("restore_backup_btn")
                        ) {
                            Text("Restore backup", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.clearAllSampleData()
                                Toast.makeText(context, "Sample data cleared. Ready for real logs!", Toast.LENGTH_LONG).show()
                            },
                            enabled = !backupInProgress,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("clear_sample_data_btn")
                        ) {
                            Text("Clear Sample Logs", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                showResetConfirmationDialog = true
                            },
                            enabled = !backupInProgress,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("factory_reset_btn")
                        ) {
                            Text("Wipe All Data 🚨", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showAddCaregiverDialog) {
        AddCaregiverDialog(
            onDismiss = { showAddCaregiverDialog = false },
            onConfirm = { name, rel, role, pin ->
                viewModel.addCaregiver(name, rel, role, pin)
                showAddCaregiverDialog = false
                Toast.makeText(context, "Caregiver $name added with PIN!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    selectedCaregiverToSwitch?.let { caregiver ->
        EnterPinDialog(
            caregiverName = caregiver.name,
            onDismiss = { selectedCaregiverToSwitch = null },
            onConfirm = { inputPin ->
                viewModel.verifyAndSwitchCaregiver(caregiver.id, inputPin) { success ->
                    if (success) {
                        Toast.makeText(context, "Switched caregiver to ${caregiver.name}", Toast.LENGTH_SHORT).show()
                        selectedCaregiverToSwitch = null
                    } else {
                        Toast.makeText(context, "Incorrect PIN for ${caregiver.name}. Default PINs: Mom=1234, Dad=5678, Grandma=0000", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (showRestoreConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmationDialog = false },
            title = { Text("Restore full backup?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This replaces all current logs, medical records, notes, photos, videos, and settings with the backup file. The app will restart when restore finishes."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmationDialog = false
                        openBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    }
                ) {
                    Text("Choose backup file", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmationDialog = false },
            title = { Text("Wipe All App Data & Reset?", fontWeight = FontWeight.Bold) },
            text = { Text("This action will permanently erase all activity logs, growth tracking, health records, and profile details from local storage. The setup wizard will restart.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.wipeAllDataAndReset()
                        showResetConfirmationDialog = false
                        Toast.makeText(context, "All app data wiped. Please complete setup.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Wipe Data", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showWidgetLogDialog) {
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showWidgetLogDialog = false },
            title = { Text("Widget Diagnostic & Error Logs 📱", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    if (widgetDiagnosticReport.isNotEmpty()) {
                        Text(
                            text = widgetDiagnosticReport,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(
                        text = "SYSTEM LOG MESSAGES (${widgetLogs.size}):",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (widgetLogs.isEmpty()) {
                        Text(
                            text = "No log messages recorded yet. Tap 'Sync Widget' or try adding the widget.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(widgetLogs) { logLine ->
                                val isErr = logLine.contains("ERROR:")
                                Text(
                                    text = logLine,
                                    fontSize = 10.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = if (isErr) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fullReport = "$widgetDiagnosticReport\n\nLOGS:\n" + widgetLogs.joinToString("\n")
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(fullReport))
                        Toast.makeText(context, "Copied report to clipboard 📋", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Copy Report 📋")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            com.example.widget.WidgetLogger.clearLogs(context)
                            widgetLogs = emptyList()
                            Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Clear Logs", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showWidgetLogDialog = false }) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

/** Clock time today, or tomorrow if that clock time has already passed. */
internal fun medicinePilotMillisForTodayOrTomorrow(hour: Int, minute: Int): Long {
    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (cal.timeInMillis <= now) {
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return cal.timeInMillis
}
