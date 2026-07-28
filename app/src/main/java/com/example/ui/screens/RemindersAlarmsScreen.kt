package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MedicineAlarm
import com.example.data.model.MedicineSubjects
import com.example.engine.CareCheckDeliveryTimes
import com.example.engine.ReminderTiming
import com.example.ui.viewmodel.BabyCareViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersAlarmsScreen(
    viewModel: BabyCareViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val profile by viewModel.babyProfile.collectAsStateWithLifecycle()
    val settings by viewModel.careCheckSettings.collectAsStateWithLifecycle()
    val medicines by viewModel.medicineAlarms.collectAsStateWithLifecycle()
    val logs by viewModel.allLogs.collectAsStateWithLifecycle()
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val triggers = remember(profile, settings, logs) {
        ReminderTiming.computeCareCheckTriggers(
            profile = profile,
            settings = settings,
            logs = logs
        )
    }

    var editingMedicine by remember { mutableStateOf<MedicineAlarm?>(null) }
    var showAddMedicine by remember { mutableStateOf(false) }
    var showBabyCheckPilotPicker by remember { mutableStateOf(false) }
    var pendingEnableBabyCheckReminder by remember { mutableStateOf(false) }
    var pendingEnableBabyCheckAlarm by remember { mutableStateOf(false) }
    var feedIntervalText by remember(settings.feedCustomIntervalMinutes) {
        mutableStateOf(settings.feedCustomIntervalMinutes.toString())
    }
    var diaperIntervalText by remember(settings.diaperIntervalMinutes) {
        mutableStateOf(settings.diaperIntervalMinutes.toString())
    }
    var babyCheckIntervalText by remember(settings.babyCheckIntervalMinutes) {
        mutableStateOf(settings.babyCheckIntervalMinutes.toString())
    }

    val powerManager = remember {
        context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    }
    val alarmManager = remember {
        context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
    }
    var ignoringBatteryOpts by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true
            }
        )
    }
    var canExactAlarms by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
        )
    }

    DisposableEffect(Unit) {
        ignoringBatteryOpts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
        canExactAlarms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        onDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminders & Alarms") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("reminders_close_btn")) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Text(
                    text = "DELIVERY",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                )
                Text(
                    text = "Sticky status-bar reminders and Clock-style exact alarms. Master switch for medicine; care checks also have per-check Reminder / Alarm toggles below.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                DeliverySwitchRow(
                    title = "Reminder notifications",
                    subtitle = "Sticky status-bar alerts with Done / Snooze",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { viewModel.setReminderNotificationsEnabled(it) },
                    testTag = "reminder_notifications_switch",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                DeliverySwitchRow(
                    title = "System alarms",
                    subtitle = "Exact AlarmClock wakeups (shows in Clock app)",
                    checked = settings.systemAlarmsEnabled,
                    onCheckedChange = { viewModel.setSystemAlarmsEnabled(it) },
                    testTag = "system_alarms_switch",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                if (!canExactAlarms || !ignoringBatteryOpts) {
                    Text(
                        text = "Background permissions",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
                    )
                    if (!canExactAlarms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .testTag("allow_exact_alarms_btn")
                        ) {
                            Text("Allow exact alarms")
                        }
                    }
                    if (!ignoringBatteryOpts && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .testTag("disable_battery_opt_btn")
                        ) {
                            Text("Disable battery optimization")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Text(
                    text = "CARE CHECKS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                )
                Text(
                    text = "Diaper, feed, and baby check. Reminder and Alarm can be on together (alarm rings 30s after reminder) or either alone.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                CareCheckSection(
                    title = "Feed check",
                    appSubtitle = "Based on last feed · profile ${profile?.targetFeedingIntervalMinutes ?: 180}m",
                    useAppTiming = settings.feedUseAppTiming,
                    onTimingModeChange = { appMode ->
                        viewModel.updateCareCheckSettings { it.copy(feedUseAppTiming = appMode) }
                    },
                    reminderEnabled = settings.feedReminderEnabled,
                    alarmEnabled = settings.feedAlarmEnabled,
                    onReminderChange = { on ->
                        viewModel.updateCareCheckSettings { it.copy(feedReminderEnabled = on) }
                    },
                    onAlarmChange = { on ->
                        viewModel.updateCareCheckSettings { it.copy(feedAlarmEnabled = on) }
                    },
                    delivery = triggers.feed,
                    timeFormat = timeFormat,
                    customIntervalText = feedIntervalText,
                    onCustomIntervalChange = { feedIntervalText = it.filter { c -> c.isDigit() }.take(4) },
                    onSaveCustomInterval = {
                        val mins = feedIntervalText.toIntOrNull()?.coerceIn(30, 720) ?: return@CareCheckSection
                        feedIntervalText = mins.toString()
                        viewModel.updateCareCheckSettings {
                            it.copy(feedCustomIntervalMinutes = mins, feedUseAppTiming = false)
                        }
                    },
                    customIntervalLabel = "Feed interval (min)",
                    reminderTestTag = "feed_reminder_switch",
                    alarmTestTag = "feed_alarm_switch",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                CareCheckSection(
                    title = "Diaper check",
                    appSubtitle = "Based on last diaper · every ${ReminderTiming.APP_DIAPER_INTERVAL_MINUTES}m",
                    useAppTiming = settings.diaperUseAppTiming,
                    onTimingModeChange = { appMode ->
                        viewModel.updateCareCheckSettings { it.copy(diaperUseAppTiming = appMode) }
                    },
                    reminderEnabled = settings.diaperReminderEnabled,
                    alarmEnabled = settings.diaperAlarmEnabled,
                    onReminderChange = { on ->
                        viewModel.updateCareCheckSettings { it.copy(diaperReminderEnabled = on) }
                    },
                    onAlarmChange = { on ->
                        viewModel.updateCareCheckSettings { it.copy(diaperAlarmEnabled = on) }
                    },
                    delivery = triggers.diaper,
                    timeFormat = timeFormat,
                    customIntervalText = diaperIntervalText,
                    onCustomIntervalChange = { diaperIntervalText = it.filter { c -> c.isDigit() }.take(4) },
                    onSaveCustomInterval = {
                        val mins = diaperIntervalText.toIntOrNull()?.coerceIn(30, 720) ?: return@CareCheckSection
                        diaperIntervalText = mins.toString()
                        viewModel.updateCareCheckSettings {
                            it.copy(diaperIntervalMinutes = mins, diaperUseAppTiming = false)
                        }
                    },
                    customIntervalLabel = "Diaper interval (min)",
                    reminderTestTag = "diaper_reminder_switch",
                    alarmTestTag = "diaper_alarm_switch",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                CareCheckSection(
                    title = "Baby check",
                    appSubtitle = "Every ${ReminderTiming.APP_BABY_CHECK_INTERVAL_MINUTES}m from next round hour",
                    useAppTiming = settings.babyCheckUseAppTiming,
                    onTimingModeChange = { appMode ->
                        viewModel.updateCareCheckSettings { current ->
                            if (appMode) {
                                current.copy(
                                    babyCheckUseAppTiming = true,
                                    babyCheckIntervalMinutes = ReminderTiming.APP_BABY_CHECK_INTERVAL_MINUTES,
                                    babyCheckPilotMillis = if (current.babyCheckPilotMillis <= 0L) {
                                        ReminderTiming.nextRoundHourMillis(System.currentTimeMillis())
                                    } else {
                                        current.babyCheckPilotMillis
                                    }
                                )
                            } else {
                                current.copy(babyCheckUseAppTiming = false)
                            }
                        }
                    },
                    reminderEnabled = settings.babyCheckReminderEnabled,
                    alarmEnabled = settings.babyCheckAlarmEnabled,
                    onReminderChange = { on ->
                        if (on && !settings.babyCheckUseAppTiming && settings.babyCheckPilotMillis <= 0L) {
                            pendingEnableBabyCheckReminder = true
                            pendingEnableBabyCheckAlarm = false
                            showBabyCheckPilotPicker = true
                        } else {
                            viewModel.updateCareCheckSettings { it.copy(babyCheckReminderEnabled = on) }
                        }
                    },
                    onAlarmChange = { on ->
                        if (on && !settings.babyCheckUseAppTiming && settings.babyCheckPilotMillis <= 0L) {
                            pendingEnableBabyCheckAlarm = true
                            pendingEnableBabyCheckReminder = false
                            showBabyCheckPilotPicker = true
                        } else {
                            viewModel.updateCareCheckSettings { it.copy(babyCheckAlarmEnabled = on) }
                        }
                    },
                    delivery = triggers.babyCheck,
                    timeFormat = timeFormat,
                    customIntervalText = babyCheckIntervalText,
                    onCustomIntervalChange = { babyCheckIntervalText = it.filter { c -> c.isDigit() }.take(4) },
                    onSaveCustomInterval = {
                        val mins = babyCheckIntervalText.toIntOrNull()?.coerceIn(30, 720) ?: return@CareCheckSection
                        babyCheckIntervalText = mins.toString()
                        viewModel.updateCareCheckSettings {
                            it.copy(babyCheckIntervalMinutes = mins, babyCheckUseAppTiming = false)
                        }
                    },
                    customIntervalLabel = "Baby check interval (min)",
                    reminderTestTag = "baby_check_reminder_switch",
                    alarmTestTag = "baby_check_alarm_switch",
                    extraCustomContent = {
                        OutlinedButton(
                            onClick = {
                                pendingEnableBabyCheckReminder = false
                                pendingEnableBabyCheckAlarm = false
                                showBabyCheckPilotPicker = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val label = if (settings.babyCheckPilotMillis > 0L) {
                                "Start time: ${timeFormat.format(Date(settings.babyCheckPilotMillis))}"
                            } else {
                                "Set baby check start time"
                            }
                            Text(label)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MEDICINE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = { showAddMedicine = true },
                        modifier = Modifier.testTag("add_medicine_alarm_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add medicine")
                    }
                }
                Text(
                    text = "Remind every few hours. Tap Took it when it rings.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
                if (!settings.deliveryEnabled()) {
                    Text(
                        text = "Turn on notifications or system alarms above first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                }
                if (medicines.isEmpty()) {
                    Text(
                        text = "Nothing yet — tap +",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    medicines.forEach { alarm ->
                        MedicineAlarmRow(
                            alarm = alarm,
                            timeFormat = timeFormat,
                            onToggle = { enabled -> viewModel.setMedicineAlarmEnabled(alarm.id, enabled) },
                            onEdit = { editingMedicine = alarm },
                            onDelete = { viewModel.deleteMedicineAlarm(alarm.id) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showBabyCheckPilotPicker) {
        val cal = Calendar.getInstance().apply {
            if (settings.babyCheckPilotMillis > 0L) timeInMillis = settings.babyCheckPilotMillis
        }
        val pickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = {
                showBabyCheckPilotPicker = false
                pendingEnableBabyCheckReminder = false
                pendingEnableBabyCheckAlarm = false
            },
            title = { Text("Baby check start time") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pilot = medicinePilotMillisForTodayOrTomorrow(
                            pickerState.hour,
                            pickerState.minute
                        )
                        viewModel.updateCareCheckSettings {
                            it.copy(
                                babyCheckPilotMillis = pilot,
                                babyCheckUseAppTiming = false,
                                babyCheckReminderEnabled = if (pendingEnableBabyCheckReminder) {
                                    true
                                } else {
                                    it.babyCheckReminderEnabled
                                },
                                babyCheckAlarmEnabled = if (pendingEnableBabyCheckAlarm) {
                                    true
                                } else {
                                    it.babyCheckAlarmEnabled
                                }
                            )
                        }
                        showBabyCheckPilotPicker = false
                        pendingEnableBabyCheckReminder = false
                        pendingEnableBabyCheckAlarm = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBabyCheckPilotPicker = false
                        pendingEnableBabyCheckReminder = false
                        pendingEnableBabyCheckAlarm = false
                    }
                ) { Text("Cancel") }
            }
        )
    }

    if (showAddMedicine) {
        MedicineAlarmEditorDialog(
            initial = MedicineAlarm(subject = MedicineSubjects.BABY, intervalMinutes = 360),
            onDismiss = { showAddMedicine = false },
            onSave = { alarm ->
                viewModel.upsertMedicineAlarm(alarm.copy(id = 0, enabled = true))
                showAddMedicine = false
            }
        )
    }

    editingMedicine?.let { existing ->
        MedicineAlarmEditorDialog(
            initial = existing,
            onDismiss = { editingMedicine = null },
            onSave = { alarm ->
                viewModel.upsertMedicineAlarm(alarm)
                editingMedicine = null
            }
        )
    }
}

@Composable
private fun CareCheckSection(
    title: String,
    appSubtitle: String,
    useAppTiming: Boolean,
    onTimingModeChange: (Boolean) -> Unit,
    reminderEnabled: Boolean,
    alarmEnabled: Boolean,
    onReminderChange: (Boolean) -> Unit,
    onAlarmChange: (Boolean) -> Unit,
    delivery: CareCheckDeliveryTimes,
    timeFormat: SimpleDateFormat,
    customIntervalText: String,
    onCustomIntervalChange: (String) -> Unit,
    onSaveCustomInterval: () -> Unit,
    customIntervalLabel: String,
    reminderTestTag: String,
    alarmTestTag: String,
    modifier: Modifier = Modifier,
    extraCustomContent: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = title, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = useAppTiming,
                onClick = { onTimingModeChange(true) },
                label = { Text("App") }
            )
            FilterChip(
                selected = !useAppTiming,
                onClick = { onTimingModeChange(false) },
                label = { Text("Custom") }
            )
        }
        if (useAppTiming) {
            Text(
                text = appSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customIntervalText,
                    onValueChange = onCustomIntervalChange,
                    label = { Text(customIntervalLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onSaveCustomInterval) { Text("Save") }
            }
            extraCustomContent?.invoke()
        }
        DeliverySwitchRow(
            title = "Reminder",
            subtitle = nextChannelLabel(delivery.reminderAtMillis, reminderEnabled, timeFormat),
            checked = reminderEnabled,
            onCheckedChange = onReminderChange,
            testTag = reminderTestTag
        )
        DeliverySwitchRow(
            title = "Alarm",
            subtitle = nextChannelLabel(delivery.alarmAtMillis, alarmEnabled, timeFormat),
            checked = alarmEnabled,
            onCheckedChange = onAlarmChange,
            testTag = alarmTestTag
        )
    }
}

private fun nextChannelLabel(
    atMillis: Long?,
    enabled: Boolean,
    timeFormat: SimpleDateFormat
): String = when {
    !enabled -> "Off"
    atMillis != null && atMillis > 0L -> "Next: ${timeFormat.format(Date(atMillis))}"
    else -> "Next: calculating…"
}

@Composable
private fun DeliverySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun MedicineAlarmRow(
    alarm: MedicineAlarm,
    timeFormat: SimpleDateFormat,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val next = ReminderTiming.nextMedicineTrigger(alarm)
    val subjectLabel = if (alarm.subject == MedicineSubjects.MOM) "Mom" else "Baby"
    val nextLabel = if (alarm.enabled && next > 0L) {
        " · Next ${timeFormat.format(Date(next))}"
    } else {
        ""
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "$subjectLabel · ${alarm.name}",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatMedicineInterval(alarm.intervalMinutes) + nextLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

private fun formatMedicineInterval(minutes: Int): String = when {
    minutes < 60 -> "Every ${minutes}m"
    minutes % 60 == 0 -> {
        val hours = minutes / 60
        if (hours == 1) "Every 1h" else "Every ${hours}h"
    }
    else -> "Every ${minutes}m"
}

/** Formats stored minutes for the custom interval field (e.g. 90 → "90m", 120 → "2h"). */
private fun formatIntervalInput(minutes: Int): String = when {
    minutes <= 0 -> ""
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes}m"
}

/**
 * Parses custom interval text: `2h`, `30m`, `90s`, or a bare number (minutes).
 * Returns minutes in 1..1440, or null if invalid.
 */
private fun parseIntervalToMinutes(raw: String): Int? {
    val text = raw.trim().lowercase()
    if (text.isEmpty()) return null
    val match = Regex("""^(\d+(?:\.\d+)?)\s*([hms])?$""").matchEntire(text) ?: return null
    val value = match.groupValues[1].toDoubleOrNull() ?: return null
    if (value <= 0.0) return null
    val unit = match.groupValues[2].ifEmpty { "m" }
    val minutes = when (unit) {
        "h" -> (value * 60).toInt()
        "m" -> value.toInt().coerceAtLeast(if (value > 0) 1 else 0)
        "s" -> kotlin.math.ceil(value / 60.0).toInt().coerceAtLeast(1)
        else -> return null
    }
    return minutes.coerceIn(1, 24 * 60)
}

private val medicineIntervalPresetsHours = listOf(4, 6, 8, 12)

@Composable
private fun MedicineAlarmEditorDialog(
    initial: MedicineAlarm,
    onDismiss: () -> Unit,
    onSave: (MedicineAlarm) -> Unit
) {
    val presetMinutes = medicineIntervalPresetsHours.map { it * 60 }
    val initialIsPreset = initial.intervalMinutes in presetMinutes

    var subject by remember(initial.id) { mutableStateOf(initial.subject) }
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var selectedPresetHours by remember(initial.id) {
        mutableStateOf(
            if (initialIsPreset) initial.intervalMinutes / 60 else null
        )
    }
    var customIntervalText by remember(initial.id) {
        mutableStateOf(
            if (initialIsPreset || initial.intervalMinutes <= 0) {
                ""
            } else {
                formatIntervalInput(initial.intervalMinutes)
            }
        )
    }

    val resolvedMinutes = when {
        customIntervalText.isNotBlank() -> parseIntervalToMinutes(customIntervalText)
        selectedPresetHours != null -> selectedPresetHours!! * 60
        else -> null
    }
    val canSave = name.isNotBlank() && resolvedMinutes != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Add medicine" else "Edit medicine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = subject == MedicineSubjects.BABY,
                        onClick = { subject = MedicineSubjects.BABY },
                        label = { Text("Baby") }
                    )
                    FilterChip(
                        selected = subject == MedicineSubjects.MOM,
                        onClick = { subject = MedicineSubjects.MOM },
                        label = { Text("Mom") }
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Tylenol") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(text = "Every", style = MaterialTheme.typography.bodySmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customIntervalText,
                        onValueChange = { raw ->
                            customIntervalText = raw.filter { c ->
                                c.isDigit() || c == '.' || c.lowercaseChar() in setOf('h', 'm', 's', ' ')
                            }.take(8)
                            selectedPresetHours = null
                        },
                        placeholder = { Text("30m") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.width(88.dp)
                    )
                    medicineIntervalPresetsHours.forEach { hours ->
                        FilterChip(
                            selected = customIntervalText.isBlank() && selectedPresetHours == hours,
                            onClick = {
                                selectedPresetHours = hours
                                customIntervalText = ""
                            },
                            label = { Text("${hours}h") }
                        )
                    }
                }
                Text(
                    text = "m minutes · h hours · s seconds",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val minutes = resolvedMinutes ?: return@TextButton
                    val now = System.currentTimeMillis()
                    onSave(
                        initial.copy(
                            subject = subject,
                            name = name.trim(),
                            doseNote = initial.doseNote,
                            intervalMinutes = minutes,
                            pilotTimeMillis = if (initial.id == 0L || initial.pilotTimeMillis <= 0L) {
                                now
                            } else {
                                initial.pilotTimeMillis
                            },
                            enabled = if (initial.id == 0L) true else initial.enabled
                        )
                    )
                },
                enabled = canSave
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
