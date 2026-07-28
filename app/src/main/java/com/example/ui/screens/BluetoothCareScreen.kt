package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.BluetoothCareEngine
import com.example.engine.BluetoothConnectionState
import com.example.ui.viewmodel.BabyCareViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BluetoothCareScreen(
    viewModel: BabyCareViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToChat: () -> Unit = {}
) {
    val context = LocalContext.current
    val activeCaregiver by viewModel.activeCaregiver.collectAsStateWithLifecycle()

    val connectionState by BluetoothCareEngine.connectionState.collectAsStateWithLifecycle()
    val careSyncEnabled by BluetoothCareEngine.careSyncEnabled.collectAsStateWithLifecycle()
    val connectedDeviceName by BluetoothCareEngine.connectedDeviceName.collectAsStateWithLifecycle()
    val discoveredDevices by BluetoothCareEngine.discoveredDevices.collectAsStateWithLifecycle()
    val isScanning by BluetoothCareEngine.isScanning.collectAsStateWithLifecycle()
    val passcode by BluetoothCareEngine.passcode.collectAsStateWithLifecycle()
    val statusText by BluetoothCareEngine.statusText.collectAsStateWithLifecycle()
    val lastSyncedAt by BluetoothCareEngine.lastSyncedAt.collectAsStateWithLifecycle()
    val muteOffDuty by viewModel.muteNonUrgentWhenOffDuty.collectAsStateWithLifecycle()
    val vibrateOnReceive by viewModel.vibrateOnReceive.collectAsStateWithLifecycle()
    val outboxPending by viewModel.outboxPendingCount.collectAsStateWithLifecycle()

    var pinDraft by remember(passcode) { mutableStateOf(passcode) }
    var selectedRole by remember { mutableStateOf(activeCaregiver?.name ?: "Dad") }
    var permissionsGranted by remember { mutableStateOf(false) }

    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    LaunchedEffect(selectedRole) {
        BluetoothCareEngine.setMyCaregiverName(selectedRole)
    }

    LaunchedEffect(activeCaregiver?.name) {
        activeCaregiver?.name?.let { selectedRole = it }
    }

    fun requiredPermissions(): Array<String> {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed += listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed += Manifest.permission.NEARBY_WIFI_DEVICES
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        return needed.toTypedArray()
    }

    fun hasAllPermissions(): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        permissionsGranted = allGranted
        if (allGranted) {
            BluetoothCareEngine.setPasscode(pinDraft)
            BluetoothCareEngine.startCareSync(context)
            Toast.makeText(context, "Care Sync on — searching nearby phones", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Nearby / Bluetooth permissions are required for Care Sync.", Toast.LENGTH_LONG).show()
        }
    }

    fun enableCareSync() {
        BluetoothCareEngine.setPasscode(pinDraft)
        if (hasAllPermissions()) {
            permissionsGranted = true
            BluetoothCareEngine.startCareSync(context)
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    LaunchedEffect(Unit) {
        BluetoothCareEngine.initialize(context)
        permissionsGranted = hasAllPermissions()
        pinDraft = BluetoothCareEngine.passcode.value
    }

    val statusColor = when (connectionState) {
        BluetoothConnectionState.CONNECTED -> MaterialTheme.colorScheme.tertiary
        BluetoothConnectionState.CONNECTING -> MaterialTheme.colorScheme.secondary
        BluetoothConnectionState.HOSTING_SERVER -> MaterialTheme.colorScheme.primary
        BluetoothConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("bluetooth_care_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    "Care Sync",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
                if (outboxPending > 0) {
                    Text(
                        "$outboxPending change(s) queued for reconnect",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Icon(
                imageVector = when (connectionState) {
                    BluetoothConnectionState.CONNECTED -> Icons.Default.BluetoothConnected
                    BluetoothConnectionState.HOSTING_SERVER,
                    BluetoothConnectionState.CONNECTING -> Icons.Default.BluetoothSearching
                    else -> Icons.Default.Bluetooth
                },
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.padding(end = 4.dp)
            )
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.testTag("care_sync_dismiss_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss"
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Care Sync", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(
                                    "Advertise + discover nearby family phones. Same Family PIN required.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = careSyncEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) enableCareSync()
                                    else BluetoothCareEngine.stopCareSync(context)
                                },
                                modifier = Modifier.testTag("care_sync_toggle")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Alert settings",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Vibrate on incoming messages", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    "Phone vibrates when a chat or ping arrives from another caregiver.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = vibrateOnReceive,
                                onCheckedChange = { viewModel.setVibrateOnReceive(it) },
                                modifier = Modifier.testTag("vibrate_on_receive_toggle")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Mute non-urgent when off duty", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    "Off-duty phones skip FEEDING/SLEEP/DIAPER vibration; URGENT always alerts.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = muteOffDuty,
                                onCheckedChange = { viewModel.setMuteNonUrgentWhenOffDuty(it) },
                                modifier = Modifier.testTag("mute_off_duty_toggle")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = pinDraft,
                            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pinDraft = it },
                            label = { Text("Family PIN") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("Both phones must use the same PIN") }
                        )

                        if (pinDraft != passcode && pinDraft.length >= 4) {
                            TextButton(
                                onClick = {
                                    BluetoothCareEngine.setPasscode(pinDraft)
                                    Toast.makeText(context, "Family PIN saved", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Save PIN")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Your role on this phone", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val roles = listOf("Mom", "Dad", "Nanny", "Grandma", "Grandpa")
                            items(roles) { role ->
                                FilterChip(
                                    selected = selectedRole == role,
                                    onClick = { selectedRole = role },
                                    label = { Text(role) },
                                    leadingIcon = if (selectedRole == role) {
                                        { Icon(Icons.Default.People, null, Modifier.size(16.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors()
                                )
                            }
                        }
                    }
                }
            }

            if (careSyncEnabled) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (connectionState == BluetoothConnectionState.CONNECTED) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isScanning && connectionState != BluetoothConnectionState.CONNECTED) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    if (connectionState == BluetoothConnectionState.CONNECTED)
                                        "Connected to ${connectedDeviceName ?: "peer"}"
                                    else "Looking for nearby BabyCare phones…",
                                    fontWeight = FontWeight.Bold,
                                    color = if (connectionState == BluetoothConnectionState.CONNECTED) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                )
                            }
                            if (lastSyncedAt > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Last synced · ${timeFmt.format(Date(lastSyncedAt))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    BluetoothCareEngine.startActiveScan(context)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Refresh nearby")
                            }
                            if (connectionState == BluetoothConnectionState.CONNECTED) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = onNavigateToChat,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("open_messages_btn")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Messages")
                                }
                            }
                        }
                    }
                }

                if (discoveredDevices.isNotEmpty()) {
                    item {
                        Text("Nearby phones", fontWeight = FontWeight.Bold)
                    }
                    items(discoveredDevices, key = { it.address }) { device ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    BluetoothCareEngine.connectToDevice(context, device.address, device.name)
                                },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(device.name, fontWeight = FontWeight.SemiBold)
                                    Text("Tap to connect", style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(Icons.Default.BluetoothSearching, null)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.navigationBarsPadding().height(16.dp))
            }
        }
    }
}
