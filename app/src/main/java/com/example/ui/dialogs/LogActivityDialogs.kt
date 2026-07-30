package com.example.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.data.model.BabyProfile
import com.example.engine.QuickActionPrefs
import com.example.ui.viewmodel.NursingSide
import com.example.ui.theme.CustomActionColor
import com.example.ui.theme.DiaperColor
import com.example.ui.theme.FavoriteActionColor
import com.example.ui.theme.FeedingColor
import com.example.ui.theme.HealthColor
import com.example.ui.theme.MedicineColor
import com.example.ui.theme.PumpingColor
import com.example.ui.theme.SleepColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Modern Square UI Button / Chip Component
@Composable
fun SquareChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 42.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accentColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

// Modern Square UI Action Button
@Composable
fun SquareActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White,
    text: String
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = modifier
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

// Event Time Selector for Past / Retroactive Event Logging ("Already Happened")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventTimeSelector(
    selectedTimeMillis: Long,
    onTimeSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val diffMinutes = ((now - selectedTimeMillis) / 60000L).coerceAtLeast(0)

    val isJustNow = diffMinutes < 2
    val is15mAgo = diffMinutes in 13..17
    val is30mAgo = diffMinutes in 28..32
    val is1hAgo = diffMinutes in 55..65
    val isCustom = !isJustNow && !is15mAgo && !is30mAgo && !is1hAgo

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateTimeFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "When did this happen?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                color = accentColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isJustNow) "Just Now" else if (diffMinutes < 120) "${diffMinutes}m ago (${timeFormat.format(Date(selectedTimeMillis))})" else dateTimeFormat.format(Date(selectedTimeMillis)),
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SquareChoiceChip(
                selected = isJustNow,
                onClick = { onTimeSelected(System.currentTimeMillis()) },
                label = "Now",
                accentColor = accentColor,
                modifier = Modifier.weight(1f)
            )
            SquareChoiceChip(
                selected = is15mAgo,
                onClick = { onTimeSelected(System.currentTimeMillis() - 15 * 60 * 1000L) },
                label = "15m ago",
                accentColor = accentColor,
                modifier = Modifier.weight(1f)
            )
            SquareChoiceChip(
                selected = is30mAgo,
                onClick = { onTimeSelected(System.currentTimeMillis() - 30 * 60 * 1000L) },
                label = "30m ago",
                accentColor = accentColor,
                modifier = Modifier.weight(1f)
            )
            SquareChoiceChip(
                selected = is1hAgo,
                onClick = { onTimeSelected(System.currentTimeMillis() - 60 * 60 * 1000L) },
                label = "1h ago",
                accentColor = accentColor,
                modifier = Modifier.weight(1f)
            )
            SquareChoiceChip(
                selected = isCustom,
                onClick = { showTimePicker = true },
                label = "Set Time",
                icon = Icons.Default.AccessTime,
                accentColor = accentColor,
                modifier = Modifier.weight(1.2f)
            )
        }

        if (showDatePicker) {
            val dateState = rememberDatePickerState(initialSelectedDateMillis = selectedTimeMillis)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        dateState.selectedDateMillis?.let { pickedDate ->
                            onTimeSelected(mergeDateKeepingTime(pickedDate, selectedTimeMillis))
                        }
                        showDatePicker = false
                    }) { Text("OK", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = dateState)
            }
        }

        if (showTimePicker) {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedTimeMillis }
            val timeState = rememberTimePickerState(
                initialHour = cal.get(Calendar.HOUR_OF_DAY),
                initialMinute = cal.get(Calendar.MINUTE),
                is24Hour = false
            )
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val merged = mergeTimeKeepingDate(selectedTimeMillis, timeState.hour, timeState.minute)
                        onTimeSelected(merged)
                        showTimePicker = false
                    }) { Text("OK", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            showTimePicker = false
                            showDatePicker = true
                        }) { Text("Change Date") }
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                    }
                },
                title = { Text("Set Past Event Time", fontWeight = FontWeight.Bold) },
                text = { TimePicker(state = timeState) }
            )
        }
    }
}

// Stool Color Data & Innovative Pediatric Color Selector Slider
data class StoolColorInfo(
    val id: String,
    val name: String,
    val hexColor: Color,
    val description: String
)

val stoolColorPalette = listOf(
    StoolColorInfo("yellow", "Mustard Yellow", Color(0xFFD4A017), "Normal (Breastfed / Early)"),
    StoolColorInfo("green", "Olive Green", Color(0xFF556B2F), "Normal (Formula / Transition)"),
    StoolColorInfo("brown", "Warm Brown", Color(0xFF795548), "Normal (Solids Introduced)"),
    StoolColorInfo("orange", "Dark Amber", Color(0xFFD2691E), "Concentrated Intake"),
    StoolColorInfo("clay", "Dark / Clay", Color(0xFF3E2723), "Special Pediatrician Note")
)

@Composable
fun PoopColorSlider(
    selectedColorId: String,
    onColorSelected: (StoolColorInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember(selectedColorId) {
        val idx = stoolColorPalette.indexOfFirst { it.id == selectedColorId }
        mutableIntStateOf(if (idx >= 0) idx else 0)
    }

    val currentColor = stoolColorPalette[selectedIndex]

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(currentColor.hexColor)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Stool Color & Pediatric Guide:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = currentColor.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = currentColor.hexColor
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = currentColor.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Square Color Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            stoolColorPalette.forEachIndexed { index, colorInfo ->
                val isSelected = (index == selectedIndex)
                Surface(
                    onClick = {
                        selectedIndex = index
                        onColorSelected(colorInfo)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = colorInfo.hexColor.copy(alpha = if (isSelected) 1.0f else 0.35f),
                    border = BorderStroke(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Smooth Color Slider
        Slider(
            value = selectedIndex.toFloat(),
            onValueChange = {
                val idx = it.roundToInt().coerceIn(0, stoolColorPalette.size - 1)
                if (idx != selectedIndex) {
                    selectedIndex = idx
                    onColorSelected(stoolColorPalette[idx])
                }
            },
            valueRange = 0f..(stoolColorPalette.size - 1).toFloat(),
            steps = stoolColorPalette.size - 2,
            colors = SliderDefaults.colors(
                thumbColor = currentColor.hexColor,
                activeTrackColor = currentColor.hexColor.copy(alpha = 0.7f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Interactive Volume Slider with Presets
@Composable
fun VolumeSliderWithPresets(
    volumeMl: Int,
    onVolumeChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(60, 90, 120, 150, 180, 240)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Volume (ml):",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Surface(
                color = FeedingColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "$volumeMl ml (${(volumeMl / 29.5735).roundToInt()} oz)",
                    style = MaterialTheme.typography.titleMedium,
                    color = FeedingColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Preset Square Choice Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.forEach { preset ->
                val isSelected = (volumeMl == preset)
                SquareChoiceChip(
                    selected = isSelected,
                    onClick = { onVolumeChanged(preset) },
                    label = "${preset}ml",
                    accentColor = FeedingColor,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chip_volume_$preset")
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = volumeMl.toFloat(),
            onValueChange = { onVolumeChanged(it.roundToInt()) },
            valueRange = 0f..360f,
            steps = 71,
            colors = SliderDefaults.colors(
                thumbColor = FeedingColor,
                activeTrackColor = FeedingColor
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Interactive Temperature Slider with Gauge Status
@Composable
fun TemperatureSliderWithGauge(
    tempCelsius: Double,
    onTempChanged: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(36.5, 36.8, 37.2, 37.5, 38.0, 38.5)

    val (statusLabel, statusColor) = when {
        tempCelsius < 36.0 -> "Low" to Color(0xFF00838F)
        tempCelsius <= 37.5 -> "Normal" to Color(0xFF2E7D32)
        tempCelsius <= 38.0 -> "Elevated" to Color(0xFFEF6C00)
        else -> "Fever" to Color(0xFFC62828)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Temperature:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Surface(
                color = statusColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f°C / %.1f°F", tempCelsius, (tempCelsius * 9 / 5) + 32),
                        style = MaterialTheme.typography.titleMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "($statusLabel)",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Square Preset Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            presets.forEach { preset ->
                val isSelected = (Math.abs(tempCelsius - preset) < 0.05)
                SquareChoiceChip(
                    selected = isSelected,
                    onClick = { onTempChanged(preset) },
                    label = String.format(Locale.US, "%.1f°", preset),
                    accentColor = HealthColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = tempCelsius.toFloat(),
            onValueChange = { onTempChanged((Math.round(it * 10.0) / 10.0)) },
            valueRange = 35.0f..41.0f,
            steps = 59,
            colors = SliderDefaults.colors(
                thumbColor = HealthColor,
                activeTrackColor = HealthColor
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun LogSleepDialog(
    onDismiss: () -> Unit,
    onStartLiveTimer: () -> Unit,
    onConfirmQuickNap: (durationMinutes: Int) -> Unit,
    onConfirmPastSleep: (durationMinutes: Int, notes: String, timestampMillis: Long) -> Unit
) {
    var selectedDurationMinutes by remember { mutableIntStateOf(60) }
    var notesText by remember { mutableStateOf("") }
    var eventTimestampMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Log Sleep & Naps 💤",
                fontWeight = FontWeight.Bold,
                color = SleepColor
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Live Sleep Timer Button
                Surface(
                    onClick = {
                        onStartLiveTimer()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SleepColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.5.dp, SleepColor)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NightlightRound,
                            contentDescription = null,
                            tint = SleepColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Start Live Sleep Timer ⏱️", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("Track active sleep with live duration timer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1-Tap Quick Nap (Just Woke Up)
                Text(
                    text = "Just Woke Up? 1-Tap Quick Log:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(30, 45, 60, 90, 120).forEach { dur ->
                        val label = if (dur < 60) "${dur}m" else if (dur == 60) "1h" else "${dur / 60.0}h"
                        SquareChoiceChip(
                            selected = false,
                            onClick = {
                                onConfirmQuickNap(dur)
                                onDismiss()
                            },
                            label = label,
                            accentColor = SleepColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Historical / Past Sleep Event
                Text(
                    text = "Or Log Past Sleep Session:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                EventTimeSelector(
                    selectedTimeMillis = eventTimestampMillis,
                    onTimeSelected = { eventTimestampMillis = it },
                    accentColor = SleepColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "Nap Duration:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(30, 60, 90, 120, 180).forEach { dur ->
                        val label = if (dur < 60) "${dur}m" else "${dur / 60}h"
                        SquareChoiceChip(
                            selected = (selectedDurationMinutes == dur),
                            onClick = { selectedDurationMinutes = dur },
                            label = label,
                            accentColor = SleepColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes (e.g. crib, stroller, car)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            SquareActionButton(
                onClick = {
                    onConfirmPastSleep(selectedDurationMinutes, notesText, eventTimestampMillis)
                    onDismiss()
                },
                containerColor = SleepColor,
                text = "Save Past Sleep Log"
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LogNurseDialog(
    onDismiss: () -> Unit,
    onStartLiveTimer: (initialSide: NursingSide) -> Unit,
    onConfirmQuickNurse: (durationMinutes: Int) -> Unit,
    onConfirmPastNurse: (leftMin: Int, rightMin: Int, notes: String, timestampMillis: Long) -> Unit
) {
    var initialLiveSide by remember { mutableStateOf(NursingSide.LEFT) }
    var leftMinText by remember { mutableStateOf("10") }
    var rightMinText by remember { mutableStateOf("10") }
    var notesText by remember { mutableStateOf("") }
    var eventTimestampMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Log Nursing & Breastfeeding 🍼",
                fontWeight = FontWeight.Bold,
                color = FeedingColor
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Option A: Start Live Breastfeeding Timer
                Text("Start Live Timer:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SquareChoiceChip(
                        selected = (initialLiveSide == NursingSide.LEFT),
                        onClick = { initialLiveSide = NursingSide.LEFT },
                        label = "Left First ⬅️",
                        accentColor = FeedingColor,
                        modifier = Modifier.weight(1f)
                    )
                    SquareChoiceChip(
                        selected = (initialLiveSide == NursingSide.RIGHT),
                        onClick = { initialLiveSide = NursingSide.RIGHT },
                        label = "Right First ➡️",
                        accentColor = FeedingColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                SquareActionButton(
                    onClick = {
                        onStartLiveTimer(initialLiveSide)
                        onDismiss()
                    },
                    containerColor = FeedingColor,
                    text = "Start Live Nursing Timer ⏱️",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Option B: 1-Tap Quick Log (Just Finished)
                Text(
                    text = "Just Finished? 1-Tap Quick Log:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(10, 15, 20, 30, 45).forEach { dur ->
                        SquareChoiceChip(
                            selected = false,
                            onClick = {
                                onConfirmQuickNurse(dur)
                                onDismiss()
                            },
                            label = "${dur}m",
                            accentColor = FeedingColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Option C: Historical / Past Nursing Event
                Text(
                    text = "Or Log Past Nursing Session:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                EventTimeSelector(
                    selectedTimeMillis = eventTimestampMillis,
                    onTimeSelected = { eventTimestampMillis = it },
                    accentColor = FeedingColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = leftMinText,
                        onValueChange = { leftMinText = it },
                        label = { Text("Left (min)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = rightMinText,
                        onValueChange = { rightMinText = it },
                        label = { Text("Right (min)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            SquareActionButton(
                onClick = {
                    val lMin = leftMinText.toIntOrNull() ?: 10
                    val rMin = rightMinText.toIntOrNull() ?: 10
                    onConfirmPastNurse(lMin, rMin, notesText, eventTimestampMillis)
                    onDismiss()
                },
                containerColor = FeedingColor,
                text = "Save Past Nursing Log"
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LogBottleDialog(
    onDismiss: () -> Unit,
    onConfirm: (volumeMl: Int, milkType: String, notes: String, timestampMillis: Long) -> Unit
) {
    var volumeMl by remember { mutableIntStateOf(120) }
    var selectedMilkType by remember { mutableStateOf("Breast Milk") }
    var notesText by remember { mutableStateOf("") }
    var eventTimestampMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Log Bottle Feeding 🍼",
                fontWeight = FontWeight.Bold,
                color = FeedingColor
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                EventTimeSelector(
                    selectedTimeMillis = eventTimestampMillis,
                    onTimeSelected = { eventTimestampMillis = it },
                    accentColor = FeedingColor
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(text = "Milk Type:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Breast Milk", "Formula", "Water").forEach { type ->
                        SquareChoiceChip(
                            selected = (selectedMilkType == type),
                            onClick = { selectedMilkType = type },
                            label = type,
                            accentColor = FeedingColor,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chip_milk_$type")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                VolumeSliderWithPresets(
                    volumeMl = volumeMl,
                    onVolumeChanged = { volumeMl = it }
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            SquareActionButton(
                onClick = {
                    onConfirm(volumeMl, selectedMilkType, notesText, eventTimestampMillis)
                },
                containerColor = FeedingColor,
                text = "Log Feeding",
                modifier = Modifier.testTag("confirm_bottle_log")
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LogDiaperDialog(
    onDismiss: () -> Unit,
    onConfirm: (status: String, notes: String, timestampMillis: Long) -> Unit
) {
    var selectedStatus by remember { mutableStateOf("Wet") }
    var selectedStoolColor by remember { mutableStateOf(stoolColorPalette[0]) }
    var notesText by remember { mutableStateOf("") }
    var eventTimestampMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Log Diaper Change 👶",
                fontWeight = FontWeight.Bold,
                color = DiaperColor
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                EventTimeSelector(
                    selectedTimeMillis = eventTimestampMillis,
                    onTimeSelected = { eventTimestampMillis = it },
                    accentColor = DiaperColor
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(text = "Diaper Status:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Wet", "Dirty", "Both", "Dry").forEach { status ->
                        SquareChoiceChip(
                            selected = (selectedStatus == status),
                            onClick = { selectedStatus = status },
                            label = status,
                            accentColor = DiaperColor,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chip_diaper_$status")
                        )
                    }
                }

                if (selectedStatus == "Dirty" || selectedStatus == "Both") {
                    Spacer(modifier = Modifier.height(14.dp))

                    PoopColorSlider(
                        selectedColorId = selectedStoolColor.id,
                        onColorSelected = { selectedStoolColor = it }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes (e.g. rash cream applied)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            SquareActionButton(
                onClick = {
                    val fullNotes = if (selectedStatus == "Dirty" || selectedStatus == "Both") {
                        val stoolInfo = "Stool Color: ${selectedStoolColor.name}"
                        if (notesText.isBlank()) stoolInfo else "$stoolInfo | $notesText"
                    } else {
                        notesText
                    }
                    onConfirm(selectedStatus, fullNotes, eventTimestampMillis)
                },
                containerColor = DiaperColor,
                text = "Log Diaper",
                modifier = Modifier.testTag("confirm_diaper_log")
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LogMedicineDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, dosage: String, notes: String, timestampMillis: Long) -> Unit
) {
    var nameText by remember { mutableStateOf("Infant Tylenol") }
    var dosageText by remember { mutableStateOf("1.25 ml") }
    var notesText by remember { mutableStateOf("") }
    var eventTimestampMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Log Medicine 💊",
                fontWeight = FontWeight.Bold,
                color = MedicineColor
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                EventTimeSelector(
                    selectedTimeMillis = eventTimestampMillis,
                    onTimeSelected = { eventTimestampMillis = it },
                    accentColor = MedicineColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Saves a dose to the timeline. For repeating reminders, use More → Reminders & Alarms.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Medicine Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = dosageText,
                    onValueChange = { dosageText = it },
                    label = { Text("Dosage") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            SquareActionButton(
                onClick = { onConfirm(nameText, dosageText, notesText, eventTimestampMillis) },
                containerColor = MedicineColor,
                text = "Save Medicine Log"
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private val customActionSuggestions = listOf(
    "Crying",
    "Outside / sun",
    "Playtime",
    "Fussy",
    "Car ride",
    "Visitor"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogCustomActionDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, notes: String, timestampMillis: Long) -> Unit
) {
    var titleText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var eventTimestampMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Log custom action ✨",
                fontWeight = FontWeight.Bold,
                color = CustomActionColor
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                EventTimeSelector(
                    selectedTimeMillis = eventTimestampMillis,
                    onTimeSelected = { eventTimestampMillis = it },
                    accentColor = CustomActionColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Anything misc — crying, outdoors, play…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    customActionSuggestions.forEach { suggestion ->
                        SquareChoiceChip(
                            selected = titleText.equals(suggestion, ignoreCase = true),
                            onClick = { titleText = suggestion },
                            label = suggestion,
                            accentColor = CustomActionColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("What happened?") },
                    placeholder = { Text("e.g. Went outside for sun") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_action_title"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            SquareActionButton(
                onClick = {
                    val title = titleText.trim()
                    if (title.isNotEmpty()) onConfirm(title, notesText.trim(), eventTimestampMillis)
                },
                enabled = titleText.trim().isNotEmpty(),
                containerColor = CustomActionColor,
                text = "Save",
                modifier = Modifier.testTag("custom_action_save")
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SetFavoriteActionDialog(
    currentType: String?,
    onDismiss: () -> Unit,
    onConfirm: (type: String, label: String) -> Unit,
    onClear: () -> Unit
) {
    val options = listOf(
        ActivityTypes.BOTTLE to "Bottle",
        ActivityTypes.BREASTFEEDING to "Nurse",
        ActivityTypes.SLEEP to "Sleep",
        ActivityTypes.DIAPER to "Diaper",
        ActivityTypes.PUMPING to "Pumping",
        ActivityTypes.MEDICINE to "Medicine",
        ActivityTypes.TEMPERATURE to "Temp",
        ActivityTypes.GROWTH to "Growth",
        ActivityTypes.BATH to "Bath",
        ActivityTypes.TUMMY_TIME to "Tummy Time",
        ActivityTypes.MILESTONE to "Milestone"
    )
    var selectedType by remember { mutableStateOf(currentType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Choose favorite action",
                fontWeight = FontWeight.Bold,
                color = FavoriteActionColor
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "This shows as a big quick-action button on the dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                options.forEach { (type, label) ->
                    SquareChoiceChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = label,
                        accentColor = FavoriteActionColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            SquareActionButton(
                onClick = {
                    val type = selectedType ?: return@SquareActionButton
                    val label = options.firstOrNull { it.first == type }?.second
                        ?: QuickActionPrefs.defaultLabelForType(type)
                    onConfirm(type, label)
                },
                enabled = selectedType != null,
                containerColor = FavoriteActionColor,
                text = "Save",
                modifier = Modifier.testTag("favorite_action_save")
            )
        },
        dismissButton = {
            Row {
                if (currentType != null) {
                    TextButton(onClick = onClear) {
                        Text("Clear")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun LogTemperatureDialog(
    onDismiss: () -> Unit,
    onConfirm: (tempCelsius: Double, notes: String, timestampMillis: Long) -> Unit
) {
    var tempCelsius by remember { mutableDoubleStateOf(36.8) }
    var notesText by remember { mutableStateOf("") }
    var eventTimestampMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Log Temperature 🌡️",
                fontWeight = FontWeight.Bold,
                color = HealthColor
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                EventTimeSelector(
                    selectedTimeMillis = eventTimestampMillis,
                    onTimeSelected = { eventTimestampMillis = it },
                    accentColor = HealthColor
                )

                Spacer(modifier = Modifier.height(14.dp))

                TemperatureSliderWithGauge(
                    tempCelsius = tempCelsius,
                    onTempChanged = { tempCelsius = it }
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes (Temporal, Axillary, Rectal)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            SquareActionButton(
                onClick = {
                    onConfirm(tempCelsius, notesText, eventTimestampMillis)
                },
                containerColor = HealthColor,
                text = "Log Temp"
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddGrowthDialog(
    onDismiss: () -> Unit,
    onConfirm: (weightKg: Double, heightCm: Double, headCm: Double, notes: String, timestampMillis: Long) -> Unit
) {
    var weightText by remember { mutableStateOf("5.8") }
    var heightText by remember { mutableStateOf("60.0") }
    var headText by remember { mutableStateOf("39.5") }
    var notesText by remember { mutableStateOf("Pediatrician checkup") }
    var eventTimestampMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Record Growth Measure 📈",
                fontWeight = FontWeight.Bold,
                color = HealthColor
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                EventTimeSelector(
                    selectedTimeMillis = eventTimestampMillis,
                    onTimeSelected = { eventTimestampMillis = it },
                    accentColor = HealthColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Weight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it },
                    label = { Text("Height (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = headText,
                    onValueChange = { headText = it },
                    label = { Text("Head Circumference (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            SquareActionButton(
                onClick = {
                    val w = weightText.toDoubleOrNull() ?: 5.8
                    val h = heightText.toDoubleOrNull() ?: 60.0
                    val head = headText.toDoubleOrNull() ?: 39.5
                    onConfirm(w, h, head, notesText, eventTimestampMillis)
                },
                containerColor = HealthColor,
                text = "Save Measurement"
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@Composable
fun AddCaregiverDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, relationship: String, role: String, pin: String) -> Unit
) {
    var nameText by remember { mutableStateOf("") }
    var relationshipText by remember { mutableStateOf("Father") }
    var selectedRole by remember { mutableStateOf("Caregiver") }
    var pinText by remember { mutableStateOf("1234") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Connect Family Caregiver 👨‍👩‍👧",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Caregiver Name (e.g. Alex, Sarah, Grandma)") },
                    modifier = Modifier.fillMaxWidth().testTag("add_caregiver_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = relationshipText,
                    onValueChange = { relationshipText = it },
                    label = { Text("Relationship (Mom, Dad, Babysitter, Pediatrician)") },
                    modifier = Modifier.fillMaxWidth().testTag("add_caregiver_rel_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pinText,
                    onValueChange = { if (it.length <= 4) pinText = it },
                    label = { Text("4-Digit Security PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().testTag("add_caregiver_pin_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Permission Role:", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Admin", "Caregiver", "Viewer").forEach { role ->
                        FilterChip(
                            selected = (selectedRole == role),
                            onClick = { selectedRole = role },
                            label = { Text(role) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameText.isNotBlank()) {
                        val pin = if (pinText.length == 4) pinText else "1234"
                        onConfirm(nameText, relationshipText, selectedRole, pin)
                    }
                },
                modifier = Modifier.testTag("confirm_add_caregiver_btn")
            ) {
                Text("Add Caregiver", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EnterPinDialog(
    caregiverName: String,
    onDismiss: () -> Unit,
    onConfirm: (pin: String) -> Unit
) {
    var pinText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Enter Security PIN 🔒",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Specify identity: Verify 4-digit PIN for $caregiverName to activate real-time caregiver logging",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = pinText,
                    onValueChange = {
                        if (it.length <= 4) {
                            pinText = it
                            errorMessage = null
                        }
                    },
                    label = { Text("4-Digit PIN") },
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().testTag("pin_entry_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pinText.length == 4) {
                        onConfirm(pinText)
                    } else {
                        errorMessage = "Please enter 4 digits"
                    }
                },
                modifier = Modifier.testTag("submit_pin_btn")
            ) {
                Text("Verify & Switch", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SetupBabyProfileDialog(
    initialProfile: BabyProfile?,
    onDismiss: () -> Unit,
    onSaveProfile: (profile: BabyProfile) -> Unit
) {
    var nameText by remember { mutableStateOf(if (initialProfile?.name == "Your Baby" || initialProfile?.name == "Emma") "" else (initialProfile?.name ?: "")) }
    var selectedGender by remember { mutableStateOf(initialProfile?.gender ?: "Boy") }
    var feedIntervalHoursText by remember { mutableStateOf(((initialProfile?.targetFeedingIntervalMinutes ?: 180) / 60.0).toString()) }
    var napIntervalHoursText by remember { mutableStateOf(((initialProfile?.targetNapIntervalMinutes ?: 150) / 60.0).toString()) }
    var caregiverNameText by remember { mutableStateOf(initialProfile?.primaryCaregiverName ?: "Mom") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (initialProfile?.isInitialSetupDone == true) "Edit Baby Profile 👶" else "Welcome! Set Up Your Baby's Profile 👶",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    text = "Personalize your baby's name, routine intervals, and system alerts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Baby's Name (e.g. Liam, Maya, Noah)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_baby_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Gender",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Boy", "Girl", "Other").forEach { gender ->
                        FilterChip(
                            selected = selectedGender == gender,
                            onClick = { selectedGender = gender },
                            label = { Text(gender) },
                            modifier = Modifier.testTag("gender_chip_$gender")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = feedIntervalHoursText,
                    onValueChange = { feedIntervalHoursText = it },
                    label = { Text("Target Feeding Interval (Hours, e.g. 3.0)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_feed_interval_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = napIntervalHoursText,
                    onValueChange = { napIntervalHoursText = it },
                    label = { Text("Target Wake Window / Nap Interval (Hours, e.g. 2.5)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_nap_interval_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = caregiverNameText,
                    onValueChange = { caregiverNameText = it },
                    label = { Text("Primary Parent / Logger Name (e.g. Mom, Dad)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_caregiver_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = nameText.ifBlank { "Your Baby" }
                    val feedHours = feedIntervalHoursText.toDoubleOrNull() ?: 3.0
                    val napHours = napIntervalHoursText.toDoubleOrNull() ?: 2.5
                    val feedMin = (feedHours * 60).toInt().coerceIn(30, 480)
                    val napMin = (napHours * 60).toInt().coerceIn(30, 480)

                    val newProfile = (initialProfile ?: BabyProfile()).copy(
                        name = finalName,
                        gender = selectedGender,
                        targetFeedingIntervalMinutes = feedMin,
                        targetNapIntervalMinutes = napMin,
                        primaryCaregiverName = caregiverNameText.ifBlank { "Mom" },
                        isInitialSetupDone = true
                    )
                    onSaveProfile(newProfile)
                },
                modifier = Modifier.testTag("save_profile_setup_btn")
            ) {
                Text("Save Profile & Launch", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (initialProfile?.isInitialSetupDone == true) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditActivityLogDialog(
    log: ActivityLog,
    onDismiss: () -> Unit,
    onConfirm: (ActivityLog) -> Unit
) {
    var notesText by remember { mutableStateOf(log.notes) }
    var volumeText by remember { mutableStateOf(if (log.volumeMl > 0) log.volumeMl.toString() else "") }
    var milkType by remember { mutableStateOf(log.milkType ?: "Breast Milk") }
    var diaperStatus by remember { mutableStateOf(log.diaperStatus ?: "Wet") }
    var medicineName by remember { mutableStateOf(log.medicineName ?: "") }
    var dosage by remember { mutableStateOf(log.dosage ?: "") }
    var tempText by remember { mutableStateOf(if (log.temperatureCelsius > 0) log.temperatureCelsius.toString() else "") }
    var leftMinText by remember {
        mutableStateOf(if (log.leftBreastDurationSec > 0) (log.leftBreastDurationSec / 60).toString() else "0")
    }
    var rightMinText by remember {
        mutableStateOf(if (log.rightBreastDurationSec > 0) (log.rightBreastDurationSec / 60).toString() else "0")
    }

    var startMillis by remember { mutableStateOf(log.startTimeMillis) }
    var endMillis by remember { mutableStateOf(log.endTimeMillis) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit ${log.activityType.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) }}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Start", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = false,
                        onClick = { showStartDatePicker = true },
                        label = { Text(dateFormat.format(Date(startMillis))) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { showStartTimePicker = true },
                        label = { Text(timeFormat.format(Date(startMillis))) }
                    )
                }

                if (endMillis != null) {
                    Text("End", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = false,
                            onClick = { showEndDatePicker = true },
                            label = { Text(dateFormat.format(Date(endMillis!!))) }
                        )
                        FilterChip(
                            selected = false,
                            onClick = { showEndTimePicker = true },
                            label = { Text(timeFormat.format(Date(endMillis!!))) }
                        )
                    }
                }

                when (log.activityType) {
                    ActivityTypes.BOTTLE, ActivityTypes.PUMPING -> {
                        OutlinedTextField(
                            value = volumeText,
                            onValueChange = { volumeText = it },
                            label = { Text("Volume (ml)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        if (log.activityType == ActivityTypes.BOTTLE) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Breast Milk", "Formula", "Water").forEach { type ->
                                    FilterChip(
                                        selected = milkType == type,
                                        onClick = { milkType = type },
                                        label = { Text(type) }
                                    )
                                }
                            }
                        }
                    }
                    ActivityTypes.DIAPER -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Wet", "Dirty", "Both", "Dry").forEach { status ->
                                FilterChip(
                                    selected = diaperStatus == status,
                                    onClick = { diaperStatus = status },
                                    label = { Text(status) }
                                )
                            }
                        }
                    }
                    ActivityTypes.MEDICINE -> {
                        OutlinedTextField(
                            value = medicineName,
                            onValueChange = { medicineName = it },
                            label = { Text("Medicine name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = dosage,
                            onValueChange = { dosage = it },
                            label = { Text("Dosage") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    ActivityTypes.TEMPERATURE -> {
                        OutlinedTextField(
                            value = tempText,
                            onValueChange = { tempText = it },
                            label = { Text("Temperature (°C)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    ActivityTypes.BREASTFEEDING -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = leftMinText,
                                onValueChange = { leftMinText = it },
                                label = { Text("Left (min)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = rightMinText,
                                onValueChange = { rightMinText = it },
                                label = { Text("Right (min)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val end = endMillis
                    val safeEnd = if (end != null && end < startMillis) startMillis else end
                    val leftSec = (leftMinText.toLongOrNull() ?: 0L) * 60L
                    val rightSec = (rightMinText.toLongOrNull() ?: 0L) * 60L
                    onConfirm(
                        log.copy(
                            startTimeMillis = startMillis,
                            endTimeMillis = safeEnd,
                            notes = notesText,
                            volumeMl = volumeText.toIntOrNull() ?: log.volumeMl,
                            milkType = milkType,
                            diaperStatus = diaperStatus,
                            medicineName = medicineName.ifBlank { null },
                            dosage = dosage.ifBlank { null },
                            temperatureCelsius = tempText.toDoubleOrNull() ?: log.temperatureCelsius,
                            leftBreastDurationSec = leftSec,
                            rightBreastDurationSec = rightSec
                        )
                    )
                }
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showStartDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = startMillis)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked ->
                        startMillis = mergeDateKeepingTime(picked, startMillis)
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showStartTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = startMillis }
        val state = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startMillis = mergeTimeKeepingDate(startMillis, state.hour, state.minute)
                    showStartTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) }
        )
    }

    if (showEndDatePicker && endMillis != null) {
        val state = rememberDatePickerState(initialSelectedDateMillis = endMillis)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked ->
                        endMillis = mergeDateKeepingTime(picked, endMillis!!)
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showEndTimePicker && endMillis != null) {
        val cal = Calendar.getInstance().apply { timeInMillis = endMillis!! }
        val state = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endMillis = mergeTimeKeepingDate(endMillis!!, state.hour, state.minute)
                    showEndTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) }
        )
    }
}

private fun mergeDateKeepingTime(dateMillis: Long, timeSourceMillis: Long): Long {
    val dateCal = Calendar.getInstance().apply { timeInMillis = dateMillis }
    val timeCal = Calendar.getInstance().apply { timeInMillis = timeSourceMillis }
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, dateCal.get(Calendar.YEAR))
        set(Calendar.MONTH, dateCal.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
        set(Calendar.SECOND, timeCal.get(Calendar.SECOND))
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun mergeTimeKeepingDate(dateSourceMillis: Long, hour: Int, minute: Int): Long {
    return Calendar.getInstance().apply {
        timeInMillis = dateSourceMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
