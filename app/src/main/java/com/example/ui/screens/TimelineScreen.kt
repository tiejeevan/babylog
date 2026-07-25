package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.data.model.ActivityTypes
import com.example.ui.components.ActivityLogCard
import com.example.ui.viewmodel.BabyCareViewModel

@Composable
fun TimelineScreen(viewModel: BabyCareViewModel) {
    val logs by viewModel.recentLogs.collectAsStateWithLifecycle()
    val profile by viewModel.babyProfile.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var showExportDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TIMELINE & ACTIVITY HISTORY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "${profile?.name ?: "Your Baby"}'s Routine Stream",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Button(
                    onClick = { showExportDialog = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export Report",
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("Export", fontWeight = FontWeight.Bold)
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
        } else {
            items(filteredLogs, key = { it.id }) { log ->
                ActivityLogCard(
                    log = log,
                    onDeleteClick = { viewModel.deleteLog(log.id) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showExportDialog) {
        val reportText = buildString {
            append("--- BabyCare Live Daily Report for ${profile?.name ?: "Your Baby"} ---\n")
            append("Generated on: ${java.util.Date()}\n\n")
            filteredLogs.take(20).forEach { log ->
                append("• [${java.text.SimpleDateFormat("h:mm a").format(java.util.Date(log.startTimeMillis))}] ")
                append("${log.activityType}: ${log.notes} (By ${log.caregiverName})\n")
            }
        }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Doctor & Pediatrician Summary", fontWeight = FontWeight.Bold) },
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
                androidx.compose.material3.TextButton(onClick = { showExportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
