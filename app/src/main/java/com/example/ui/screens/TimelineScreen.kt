package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ActivityLog
import com.example.data.model.ActivityTypes
import com.example.ui.components.ActivityLogCard
import com.example.ui.dialogs.EditActivityLogDialog
import com.example.ui.viewmodel.BabyCareViewModel
import com.example.ui.viewmodel.TimelineRangeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimelineScreen(viewModel: BabyCareViewModel) {
    val logs by viewModel.timelineLogs.collectAsStateWithLifecycle()
    val profile by viewModel.babyProfile.collectAsStateWithLifecycle()
    val mode by viewModel.timelineMode.collectAsStateWithLifecycle()
    val range by viewModel.timelineRange.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var showExportDialog by remember { mutableStateOf(false) }
    var editingLog by remember { mutableStateOf<ActivityLog?>(null) }
    var pendingDeleteLog by remember { mutableStateOf<ActivityLog?>(null) }

    val context = LocalContext.current
    val rangeLabel = remember(mode, range) { formatTimelineRangeLabel(mode, range.first, range.second) }

    val filteredLogs = logs.filter { log ->
        val matchesFilter = when (selectedFilter) {
            "Feeding" -> log.activityType == ActivityTypes.BREASTFEEDING || log.activityType == ActivityTypes.BOTTLE
            "Sleep" -> log.activityType == ActivityTypes.SLEEP
            "Diaper" -> log.activityType == ActivityTypes.DIAPER
            "Medicine" -> log.activityType == ActivityTypes.MEDICINE || log.activityType == ActivityTypes.TEMPERATURE
            "Pumping" -> log.activityType == ActivityTypes.PUMPING
            else -> true
        }
        val matchesSearch = log.notes.contains(searchQuery, ignoreCase = true) ||
                log.caregiverName.contains(searchQuery, ignoreCase = true) ||
                log.activityType.contains(searchQuery, ignoreCase = true) ||
                (log.medicineName ?: "").contains(searchQuery, ignoreCase = true)

        matchesFilter && matchesSearch
    }

    val weekSections = remember(filteredLogs, mode) {
        if (mode != TimelineRangeMode.WEEK) emptyList()
        else groupLogsByDay(filteredLogs)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("timeline_screen")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TIMELINE & ACTIVITY HISTORY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${profile?.name ?: "Your Baby"}'s Routine Stream",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = { showExportDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export Report",
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Export", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = mode == TimelineRangeMode.DAY,
                    onClick = { viewModel.setTimelineMode(TimelineRangeMode.DAY) },
                    label = { Text("Day") },
                    modifier = Modifier.testTag("timeline_mode_day")
                )
                FilterChip(
                    selected = mode == TimelineRangeMode.WEEK,
                    onClick = { viewModel.setTimelineMode(TimelineRangeMode.WEEK) },
                    label = { Text("Week") },
                    modifier = Modifier.testTag("timeline_mode_week")
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.jumpTimelineToToday() }) {
                    Text("Today")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { viewModel.shiftTimeline(forward = false) },
                    modifier = Modifier.testTag("timeline_prev")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous"
                    )
                }
                Text(
                    text = rangeLabel,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = { viewModel.shiftTimeline(forward = true) },
                    modifier = Modifier.testTag("timeline_next")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next"
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search logs, notes, caregivers...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf("All", "Feeding", "Sleep", "Diaper", "Medicine", "Pumping")
                items(filters) { filter ->
                    FilterChip(
                        selected = (selectedFilter == filter),
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        modifier = Modifier.testTag("timeline_filter_$filter")
                    )
                }
            }
        }

        if (filteredLogs.isEmpty()) {
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "No activity logs match your filter criteria.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else if (mode == TimelineRangeMode.WEEK) {
            weekSections.forEach { section ->
                item(key = "header_${section.dayStartMillis}") {
                    Text(
                        text = section.label,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(section.logs, key = { it.id }) { log ->
                    ActivityLogCard(
                        log = log,
                        onEditClick = { editingLog = log },
                        onDeleteClick = { pendingDeleteLog = log }
                    )
                }
            }
        } else {
            items(filteredLogs, key = { it.id }) { log ->
                ActivityLogCard(
                    log = log,
                    onEditClick = { editingLog = log },
                    onDeleteClick = { pendingDeleteLog = log }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
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

    pendingDeleteLog?.let { log ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDeleteLog = null },
            title = { Text("Delete activity?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will remove the ${log.activityType.lowercase().replace('_', ' ')} entry from your timeline. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLog(log.id)
                        pendingDeleteLog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_log")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteLog = null },
                    modifier = Modifier.testTag("cancel_delete_log")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExportDialog) {
        val reportText = buildExportReport(
            babyName = profile?.name ?: "Your Baby",
            rangeLabel = rangeLabel,
            logs = filteredLogs
        )

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Activity Report", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Share this report with co-parents, pediatrician, or nanny:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = reportText,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val sendIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, reportText)
                        type = "text/plain"
                    }
                    context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Baby Report"))
                    showExportDialog = false
                }) {
                    Text("Share / Copy Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

private data class DaySection(
    val dayStartMillis: Long,
    val label: String,
    val logs: List<ActivityLog>
)

private fun groupLogsByDay(logs: List<ActivityLog>): List<DaySection> {
    val dayFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    return logs
        .groupBy { BabyCareViewModel.startOfDayMillis(it.startTimeMillis) }
        .toSortedMap(compareByDescending { it })
        .map { (dayStart, dayLogs) ->
            DaySection(
                dayStartMillis = dayStart,
                label = dayFormat.format(Date(dayStart)),
                logs = dayLogs
            )
        }
}

private fun formatTimelineRangeLabel(mode: TimelineRangeMode, start: Long, end: Long): String {
    return when (mode) {
        TimelineRangeMode.DAY -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(start))
        TimelineRangeMode.WEEK -> {
            val startFmt = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(start))
            val endFmt = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(end))
            "$startFmt – $endFmt"
        }
    }
}

private fun buildExportReport(babyName: String, rangeLabel: String, logs: List<ActivityLog>): String {
    val stamp = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
    val rowStamp = SimpleDateFormat("yyyy-MM-dd h:mm a", Locale.getDefault())
    return buildString {
        append("--- BabyCare Live Report for $babyName ---\n")
        append("Range: $rangeLabel\n")
        append("Generated: ${stamp.format(Date())}\n")
        append("Entries: ${logs.size}\n\n")
        append("date_time,type,duration_min,volume_ml,diaper,left_min,right_min,caregiver,notes\n")
        logs.forEach { log ->
            val durationMin = if (log.durationSeconds > 0) (log.durationSeconds / 60).toString() else ""
            val volume = if (log.volumeMl > 0) log.volumeMl.toString() else ""
            val diaper = log.diaperStatus?.replace(",", " ") ?: ""
            val left = if (log.leftBreastDurationSec > 0) (log.leftBreastDurationSec / 60).toString() else ""
            val right = if (log.rightBreastDurationSec > 0) (log.rightBreastDurationSec / 60).toString() else ""
            val notes = log.notes.replace(",", " ").replace("\n", " ")
            append(rowStamp.format(Date(log.startTimeMillis)))
            append(",")
            append(log.activityType)
            append(",")
            append(durationMin)
            append(",")
            append(volume)
            append(",")
            append(diaper)
            append(",")
            append(left)
            append(",")
            append(right)
            append(",")
            append(log.caregiverName.replace(",", " "))
            append(",")
            append(notes)
            append("\n")
        }
    }
}
