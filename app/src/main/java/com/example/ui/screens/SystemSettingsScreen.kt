package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.example.data.backup.FullBackupManager
import com.example.ui.viewmodel.BabyCareViewModel
import com.example.ui.viewmodel.BackupUiState

import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemSettingsScreen(
    viewModel: BabyCareViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    val backupUiState by viewModel.backupUiState.collectAsStateWithLifecycle()
    val backupInProgress = backupUiState is BackupUiState.InProgress

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

    var showResetConfirmationDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmationDialog by remember { mutableStateOf(false) }
    var showWidgetLogDialog by remember { mutableStateOf(false) }
    var widgetLogs by remember { mutableStateOf(emptyList<String>()) }
    var widgetDiagnosticReport by remember { mutableStateOf("") }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("System Settings", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("system_settings_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Widget Diagnostics Card
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

            // Persistent Memory & Data Management Card
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
    }

    if (showWidgetLogDialog) {
        AlertDialog(
            onDismissRequest = { showWidgetLogDialog = false },
            title = { Text("Widget Diagnostics & Logs") },
            text = {
                Column {
                    Text(
                        text = widgetDiagnosticReport,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Recent Logs (${widgetLogs.size}):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(modifier = Modifier.height(140.dp)) {
                        items(widgetLogs.size) { index ->
                            Text(
                                text = widgetLogs[index],
                                fontSize = 10.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWidgetLogDialog = false }) {
                    Text("Close")
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
}
