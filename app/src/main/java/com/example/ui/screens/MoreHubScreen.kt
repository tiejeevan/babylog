package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.BabyCareViewModel

import androidx.compose.material.icons.filled.Settings

enum class MoreDestination {
    HUB,
    CALENDAR,
    MEMORIES,
    NOTES_LISTS,
    REMINDERS,
    VOICE_COMMANDS,
    SYSTEM_SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreHubScreen(
    viewModel: BabyCareViewModel,
    destination: MoreDestination,
    onDestinationChange: (MoreDestination) -> Unit,
    onNavigateToBluetooth: () -> Unit,
    onNavigateToCareChat: () -> Unit = {},
    onNavigateToSleepSound: () -> Unit,
    onDismiss: () -> Unit = {}
) {
    when (destination) {
        MoreDestination.HUB -> MoreHubHome(
            onOpen = onDestinationChange,
            onNavigateToBluetooth = onNavigateToBluetooth,
            onNavigateToCareChat = onNavigateToCareChat,
            onNavigateToSleepSound = onNavigateToSleepSound,
            onDismiss = onDismiss
        )
        MoreDestination.CALENDAR -> CalendarScreen(
            viewModel = viewModel,
            onNavigateBack = { onDestinationChange(MoreDestination.HUB) }
        )
        MoreDestination.MEMORIES -> MemoriesScreen(
            viewModel = viewModel,
            onNavigateBack = { onDestinationChange(MoreDestination.HUB) }
        )
        MoreDestination.NOTES_LISTS -> NotesListsScreen(
            viewModel = viewModel,
            onNavigateBack = { onDestinationChange(MoreDestination.HUB) }
        )
        MoreDestination.REMINDERS -> RemindersAlarmsScreen(
            viewModel = viewModel,
            onNavigateBack = { onDestinationChange(MoreDestination.HUB) }
        )
        MoreDestination.VOICE_COMMANDS -> VoiceCommandsScreen(
            onNavigateBack = { onDestinationChange(MoreDestination.HUB) }
        )
        MoreDestination.SYSTEM_SETTINGS -> SystemSettingsScreen(
            viewModel = viewModel,
            onNavigateBack = { onDestinationChange(MoreDestination.HUB) }
        )
    }
}

@Composable
private fun MoreHubHome(
    onOpen: (MoreDestination) -> Unit,
    onNavigateToBluetooth: () -> Unit,
    onNavigateToCareChat: () -> Unit,
    onNavigateToSleepSound: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("more_hub_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "More",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Calendar, memories, notes, and family tools",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("more_hub_dismiss")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss"
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        MoreHubTile(
            title = "Calendar",
            subtitle = "Logs, memories, reminders by day",
            icon = Icons.Default.CalendarMonth,
            testTag = "more_calendar",
            onClick = { onOpen(MoreDestination.CALENDAR) }
        )
        MoreHubTile(
            title = "Memories",
            subtitle = "Photos & videos · syncs nearby",
            icon = Icons.Default.PhotoLibrary,
            testTag = "more_memories",
            onClick = { onOpen(MoreDestination.MEMORIES) }
        )
        MoreHubTile(
            title = "Notes & Lists",
            subtitle = "Sharable notes and shopping lists",
            icon = Icons.AutoMirrored.Filled.Notes,
            testTag = "more_notes",
            onClick = { onOpen(MoreDestination.NOTES_LISTS) }
        )
        MoreHubTile(
            title = "Reminders & Alarms",
            subtitle = "Care checks & medicine schedules",
            icon = Icons.Default.Alarm,
            testTag = "more_reminders",
            onClick = { onOpen(MoreDestination.REMINDERS) }
        )
        MoreHubTile(
            title = "Voice Commands",
            subtitle = "Hands-free diaper & feeding logs",
            icon = Icons.Default.Mic,
            testTag = "more_voice_commands",
            onClick = { onOpen(MoreDestination.VOICE_COMMANDS) }
        )

        MoreHubTile(
            title = "System Settings",
            subtitle = "Widget diagnostics, backups & data management",
            icon = Icons.Default.Settings,
            testTag = "more_system_settings",
            onClick = { onOpen(MoreDestination.SYSTEM_SETTINGS) }
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Shortcuts",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        MoreHubTile(
            title = "Messages",
            subtitle = "Caregiver chat & quick pings",
            icon = Icons.AutoMirrored.Filled.Chat,
            testTag = "more_care_chat",
            onClick = onNavigateToCareChat
        )
        MoreHubTile(
            title = "Sleep Sounds",
            subtitle = "White noise & lullabies",
            icon = Icons.Default.NightlightRound,
            testTag = "more_sleep_sound",
            onClick = onNavigateToSleepSound
        )
    }
}

@Composable
private fun MoreHubTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
