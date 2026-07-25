package com.example.ui.screens

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.data.model.BabyProfile
import com.example.data.model.CaregiverProfile
import com.example.ui.dialogs.AddCaregiverDialog
import com.example.ui.dialogs.EnterPinDialog
import com.example.ui.dialogs.OnboardingSetupDialog
import com.example.ui.viewmodel.BabyCareViewModel
import com.example.ui.theme.parseHexColor
import android.widget.Toast

@Composable
fun FamilyCaregiversScreen(
    viewModel: BabyCareViewModel,
    onNavigateToBluetooth: () -> Unit = {}
) {
    val caregivers by viewModel.caregivers.collectAsStateWithLifecycle()
    val activeCaregiver by viewModel.activeCaregiver.collectAsStateWithLifecycle()
    val profile by viewModel.babyProfile.collectAsStateWithLifecycle()

    var showAddCaregiverDialog by remember { mutableStateOf(false) }
    var selectedCaregiverToSwitch by remember { mutableStateOf<CaregiverProfile?>(null) }
    var showResetConfirmationDialog by remember { mutableStateOf(false) }
    var showSetupDialog by remember { mutableStateOf(false) }
    var babyNameText by remember(profile?.name) { mutableStateOf(profile?.name ?: "Your Baby") }
    var feedIntervalText by remember { mutableStateOf((profile?.targetFeedingIntervalMinutes ?: 180).toString()) }
    var napIntervalText by remember { mutableStateOf((profile?.targetNapIntervalMinutes ?: 150).toString()) }

    var showWidgetLogDialog by remember { mutableStateOf(false) }
    var widgetLogs by remember { mutableStateOf(emptyList<String>()) }
    var widgetDiagnosticReport by remember { mutableStateOf("") }

    val context = LocalContext.current
    val familyCode = "BABY-LIVE-8829-FAMILY"

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

        // Family Invitation Code Card
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
                            text = "Family Share Code & QR Sync",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Share this code with co-parents or babysitters to connect their phone to ${profile?.name ?: "your baby"}'s live operating system in real time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = familyCode,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Button(
                                onClick = {
                                    val sendIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_TEXT, "Join ${profile?.name ?: "our baby"}'s BabyCare Live family profile using invite code: $familyCode")
                                        type = "text/plain"
                                    }
                                    context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Invitation Code"))
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share Invite")
                            }
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
                                text = "Bluetooth Caregiver Ping & Walkie-Talkie",
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
                        text = "Connect directly with spouse phone using Bluetooth. Trigger instant vibration alerts ('Need urgent help!', 'Your turn for feed!') & offline caregiver chat.",
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
                        Text("Open Bluetooth Sync & Ping 📳", fontWeight = FontWeight.Bold)
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

        // System Level Background Access & Alerts (Moved from Dashboard)
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
                            text = "SYSTEM ALERTS & SETUP CONFIGURATION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = { showSetupDialog = true },
                            modifier = Modifier.testTag("rerun_setup_wizard_btn")
                        ) {
                            Text("Re-run Setup Wizard ⚙️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Test real system notification bar alerts, schedule exact alarm background triggers, or review system permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.triggerTestNotification(context)
                                Toast.makeText(context, "System Notification Sent! Check status bar 🔔", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_system_alert_btn")
                        ) {
                            Text("Test Alert 🔔", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.scheduleRoutineSystemAlarms(context)
                                Toast.makeText(context, "System Alarm Scheduled for next routine window ⏰", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("schedule_system_alarms_btn")
                        ) {
                            Text("Schedule Alarm ⏰", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

                            val activeInstances = try {
                                appWidgetManager?.getAppWidgetIds(myProvider)?.size ?: 0
                            } catch (e: Exception) { -1 }

                            com.example.widget.WidgetLogger.log(context, "User clicked 'Add Home Screen Widget'")
                            com.example.widget.WidgetLogger.log(context, "SDK: ${android.os.Build.VERSION.SDK_INT} | AppWidgetManager: ${appWidgetManager != null} | PinSupported: $isPinSupported | ActiveInstances: $activeInstances")

                            var pinResultMsg = ""
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                if (appWidgetManager != null && isPinSupported) {
                                    try {
                                        val pinnedWidgetCallbackIntent = android.content.Intent(context, com.example.widget.BabyCareWidgetProvider::class.java)
                                        val successCallback = android.app.PendingIntent.getBroadcast(
                                            context,
                                            0,
                                            pinnedWidgetCallbackIntent,
                                            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                                        )
                                        val success = appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
                                        com.example.widget.WidgetLogger.log(context, "requestPinAppWidget executed. Return value: $success")
                                        pinResultMsg = "Pin request sent to device launcher (Result: $success)."
                                    } catch (e: Exception) {
                                        com.example.widget.WidgetLogger.log(context, "Exception calling requestPinAppWidget", isError = true, throwable = e)
                                        pinResultMsg = "Pin failed with exception: ${e.localizedMessage ?: e.message}"
                                    }
                                } else {
                                    val errReason = if (appWidgetManager == null) "AppWidgetManager is NULL on this device/emulator" else "Launcher does NOT support pin (isRequestPinAppWidgetSupported = false)"
                                    com.example.widget.WidgetLogger.log(context, "Cannot pin widget: $errReason", isError = true)
                                    pinResultMsg = "Pinning unavailable: $errReason.\n\n👉 Manual Workaround: Long-press your device Home Screen -> tap 'Widgets' -> locate 'BabyCare Live' -> drag it to your screen."
                                }
                            } else {
                                com.example.widget.WidgetLogger.log(context, "Android OS version ${android.os.Build.VERSION.SDK_INT} lacks requestPinAppWidget support", isError = true)
                                pinResultMsg = "Android version below Oreo (API 26) does not support pin widget requests."
                            }

                            // Trigger widget refresh
                            com.example.widget.BabyCareWidgetProvider.updateAllWidgets(context)

                            val logs = com.example.widget.WidgetLogger.getLogs(context)
                            widgetLogs = logs
                            widgetDiagnosticReport = "📱 WIDGET DIAGNOSTIC & PIN REPORT\n\n" +
                                    "• OS Version: Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n" +
                                    "• AppWidgetManager Service: ${if (appWidgetManager != null) "Available" else "NULL"}\n" +
                                    "• Programmatic Pinning Supported: $isPinSupported\n" +
                                    "• Active Placed Widgets on Home Screen: $activeInstances\n\n" +
                                    "RESULT:\n$pinResultMsg"

                            showWidgetLogDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_widget_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Add Home Screen Widget 📱", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        text = "All logs, growth stats, medical records, and milk stash are saved permanently in Room DB. You can wipe all data or clear sample logs below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.clearAllSampleData()
                                Toast.makeText(context, "Sample data cleared. Ready for real logs!", Toast.LENGTH_LONG).show()
                            },
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

    if (showSetupDialog) {
        OnboardingSetupDialog(
            initialProfile = profile,
            onDismiss = { showSetupDialog = false },
            onCompleteSetup = { updatedProfile, initialWeightKg, initialHeightCm ->
                viewModel.completeOnboardingSetup(
                    profile = updatedProfile,
                    initialWeightKg = initialWeightKg,
                    initialHeightCm = initialHeightCm
                )
                showSetupDialog = false
                Toast.makeText(context, "Setup completed for ${updatedProfile.name}!", Toast.LENGTH_SHORT).show()
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
