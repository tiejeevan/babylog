package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.ActivityLog
import com.example.data.model.MemoryItem
import com.example.data.model.SharedNote
import com.example.ui.viewmodel.BabyCareViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class DayMarkers(
    val hasLogs: Boolean = false,
    val hasMemories: Boolean = false,
    val hasNotes: Boolean = false,
    val hasReminder: Boolean = false
)

private sealed class DayEntry {
    abstract val timeMillis: Long
    data class Log(val log: ActivityLog) : DayEntry() {
        override val timeMillis: Long get() = log.startTimeMillis
    }
    data class Memory(val memory: MemoryItem) : DayEntry() {
        override val timeMillis: Long get() = memory.capturedAtMillis
    }
    data class Note(val note: SharedNote) : DayEntry() {
        override val timeMillis: Long get() = note.pinnedDateMillis ?: note.updatedAtMillis
    }
    data class Reminder(val label: String, override val timeMillis: Long) : DayEntry()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: BabyCareViewModel,
    onNavigateBack: () -> Unit
) {
    val logs by viewModel.allLogs.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()

    var monthAnchor by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedDay by remember { mutableLongStateOf(BabyCareViewModel.startOfDayMillis(System.currentTimeMillis())) }
    var showDaySheet by remember { mutableStateOf(false) }

    val (feedReminder, diaperReminder, napReminder) = remember(
        logs,
        viewModel.babyProfile.value,
        viewModel.careCheckSettings.value
    ) {
        viewModel.nextReminderTimes()
    }

    val monthCal = remember(monthAnchor) {
        Calendar.getInstance().apply {
            timeInMillis = monthAnchor
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    val monthTitle = remember(monthAnchor) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(monthAnchor))
    }

    val markersByDay = remember(logs, memories, notes, feedReminder, diaperReminder, napReminder, monthAnchor) {
        buildMonthMarkers(monthCal, logs, memories, notes, feedReminder, diaperReminder, napReminder)
    }

    val dayEntries = remember(selectedDay, logs, memories, notes, feedReminder, diaperReminder, napReminder) {
        buildDayEntries(selectedDay, logs, memories, notes, feedReminder, diaperReminder, napReminder)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar") },
                actions = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("calendar_dismiss")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .testTag("calendar_screen")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    val c = Calendar.getInstance().apply { timeInMillis = monthAnchor }
                    c.add(Calendar.MONTH, -1)
                    monthAnchor = c.timeInMillis
                }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
                }
                Text(monthTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    val c = Calendar.getInstance().apply { timeInMillis = monthAnchor }
                    c.add(Calendar.MONTH, 1)
                    monthAnchor = c.timeInMillis
                }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
                }
            }

            FilterChip(
                selected = false,
                onClick = {
                    val today = System.currentTimeMillis()
                    monthAnchor = today
                    selectedDay = BabyCareViewModel.startOfDayMillis(today)
                    showDaySheet = true
                },
                label = { Text("Today") },
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                    Text(
                        d,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            MonthGrid(
                monthCal = monthCal,
                selectedDay = selectedDay,
                markersByDay = markersByDay,
                onDayClick = { dayStart ->
                    selectedDay = dayStart
                    showDaySheet = true
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
            LegendRow()
        }
    }

    if (showDaySheet) {
        ModalBottomSheet(
            onDismissRequest = { showDaySheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            val dayLabel = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
                .format(Date(selectedDay))
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(dayLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                if (dayEntries.isEmpty()) {
                    Text(
                        "Nothing logged this day yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .padding(bottom = 24.dp)
                    ) {
                        items(dayEntries) { entry ->
                            DayEntryRow(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    monthCal: Calendar,
    selectedDay: Long,
    markersByDay: Map<Long, DayMarkers>,
    onDayClick: (Long) -> Unit
) {
    val firstDayOfWeek = monthCal.get(Calendar.DAY_OF_WEEK) // 1=Sun
    val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val todayStart = BabyCareViewModel.startOfDayMillis(System.currentTimeMillis())
    val cells = (firstDayOfWeek - 1) + daysInMonth
    val rows = (cells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var dayNum = 1
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { col ->
                    val cellIndex = row * 7 + col
                    val dayOffset = cellIndex - (firstDayOfWeek - 1)
                    if (dayOffset in 0 until daysInMonth) {
                        val dayStart = Calendar.getInstance().apply {
                            timeInMillis = monthCal.timeInMillis
                            set(Calendar.DAY_OF_MONTH, dayNum)
                        }.timeInMillis.let { BabyCareViewModel.startOfDayMillis(it) }
                        val markers = markersByDay[dayStart] ?: DayMarkers()
                        val isSelected = dayStart == selectedDay
                        val isToday = dayStart == todayStart
                        DayCell(
                            day = dayNum,
                            markers = markers,
                            isSelected = isSelected,
                            isToday = isToday,
                            onClick = { onDayClick(dayStart) },
                            modifier = Modifier.weight(1f)
                        )
                        dayNum++
                    } else {
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    markers: DayMarkers,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.tertiary
        else -> Color.Transparent
    }
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "$day",
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (markers.hasLogs) Dot(MaterialTheme.colorScheme.primary)
            if (markers.hasMemories) Dot(MaterialTheme.colorScheme.secondary)
            if (markers.hasNotes) Dot(MaterialTheme.colorScheme.tertiary)
            if (markers.hasReminder) Dot(MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun LegendRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LegendItem("Logs", MaterialTheme.colorScheme.primary)
        LegendItem("Memories", MaterialTheme.colorScheme.secondary)
        LegendItem("Notes", MaterialTheme.colorScheme.tertiary)
        LegendItem("Reminders", MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color)
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DayEntryRow(entry: DayEntry) {
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (entry) {
            is DayEntry.Log -> {
                Text(timeFmt.format(Date(entry.timeMillis)), style = MaterialTheme.typography.labelMedium)
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.log.activityType.replace('_', ' '), fontWeight = FontWeight.SemiBold)
                    if (entry.log.notes.isNotBlank()) {
                        Text(
                            entry.log.notes,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            is DayEntry.Memory -> {
                val path = entry.memory.thumbPath.ifBlank { entry.memory.localPath }
                if (path.isNotBlank() && File(path).exists()) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Memory", fontWeight = FontWeight.SemiBold)
                    Text(
                        entry.memory.caption.ifBlank { timeFmt.format(Date(entry.timeMillis)) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            is DayEntry.Note -> {
                Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.note.title.ifBlank { "Note" }, fontWeight = FontWeight.SemiBold)
                    Text(
                        entry.note.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            is DayEntry.Reminder -> {
                Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.label, fontWeight = FontWeight.SemiBold)
                    Text(timeFmt.format(Date(entry.timeMillis)), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun buildMonthMarkers(
    monthCal: Calendar,
    logs: List<ActivityLog>,
    memories: List<MemoryItem>,
    notes: List<SharedNote>,
    feedReminder: Long?,
    diaperReminder: Long?,
    napReminder: Long?
): Map<Long, DayMarkers> {
    val map = mutableMapOf<Long, DayMarkers>()
    fun touch(day: Long, transform: (DayMarkers) -> DayMarkers) {
        map[day] = transform(map[day] ?: DayMarkers())
    }
    logs.forEach {
        val day = BabyCareViewModel.startOfDayMillis(it.startTimeMillis)
        touch(day) { m -> m.copy(hasLogs = true) }
    }
    memories.forEach {
        val day = BabyCareViewModel.startOfDayMillis(it.capturedAtMillis)
        touch(day) { m -> m.copy(hasMemories = true) }
    }
    notes.forEach { note ->
        val pinned = note.pinnedDateMillis ?: return@forEach
        val day = BabyCareViewModel.startOfDayMillis(pinned)
        touch(day) { m -> m.copy(hasNotes = true) }
    }
    listOfNotNull(feedReminder, diaperReminder, napReminder).forEach { t ->
        touch(BabyCareViewModel.startOfDayMillis(t)) { m -> m.copy(hasReminder = true) }
    }
    // Keep monthCal referenced for future filtering if needed
    monthCal.timeInMillis
    return map
}

private fun buildDayEntries(
    dayStart: Long,
    logs: List<ActivityLog>,
    memories: List<MemoryItem>,
    notes: List<SharedNote>,
    feedReminder: Long?,
    diaperReminder: Long?,
    napReminder: Long?
): List<DayEntry> {
    val dayEnd = BabyCareViewModel.endOfDayMillis(dayStart)
    val entries = mutableListOf<DayEntry>()
    logs.filter { it.startTimeMillis in dayStart..dayEnd }.forEach { entries += DayEntry.Log(it) }
    memories.filter { it.capturedAtMillis in dayStart..dayEnd }.forEach { entries += DayEntry.Memory(it) }
    notes.filter { note ->
        val pinned = note.pinnedDateMillis
        pinned != null && pinned in dayStart..dayEnd
    }.forEach { entries += DayEntry.Note(it) }
    feedReminder?.takeIf { it in dayStart..dayEnd }?.let {
        entries += DayEntry.Reminder("Feed reminder", it)
    }
    diaperReminder?.takeIf { it in dayStart..dayEnd }?.let {
        entries += DayEntry.Reminder("Diaper reminder", it)
    }
    napReminder?.takeIf { it in dayStart..dayEnd }?.let {
        entries += DayEntry.Reminder("Nap reminder", it)
    }
    return entries.sortedBy { it.timeMillis }
}
