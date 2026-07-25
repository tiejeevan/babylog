package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.BluetoothCareEngine
import com.example.engine.BluetoothConnectionState
import com.example.engine.PingPresets
import com.example.ui.viewmodel.BabyCareViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BluetoothCareScreen(
    viewModel: BabyCareViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val activeCaregiver by viewModel.activeCaregiver.collectAsStateWithLifecycle()

    val connectionState by BluetoothCareEngine.connectionState.collectAsStateWithLifecycle()
    val connectedDeviceName by BluetoothCareEngine.connectedDeviceName.collectAsStateWithLifecycle()
    val messages by BluetoothCareEngine.messages.collectAsStateWithLifecycle()
    val discoveredDevices by BluetoothCareEngine.discoveredDevices.collectAsStateWithLifecycle()
    val isScanning by BluetoothCareEngine.isScanning.collectAsStateWithLifecycle()
    val passcode by BluetoothCareEngine.passcode.collectAsStateWithLifecycle()
    val statusText by BluetoothCareEngine.statusText.collectAsStateWithLifecycle()

    var chatInputText by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(activeCaregiver?.name ?: "Dad") }
    var permissionsGranted by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Sync active caregiver name with engine
    LaunchedEffect(selectedRole) {
        BluetoothCareEngine.setMyCaregiverName(selectedRole)
    }

    // Auto-scroll chat to latest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Comprehensive Bluetooth Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        permissionsGranted = allGranted
        if (allGranted) {
            BluetoothCareEngine.refreshPairedDevices(context)
            BluetoothCareEngine.startActiveScan(context)
            Toast.makeText(context, "All Bluetooth permissions granted! Scanning nearby devices...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Bluetooth permissions required to connect Dad & Mom phones.", Toast.LENGTH_LONG).show()
        }
    }

    fun requestAllPermissions(onGranted: () -> Unit) {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (needed.isEmpty()) {
            permissionsGranted = true
            onGranted()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    // Auto-request permissions and start background listener on screen open
    LaunchedEffect(Unit) {
        requestAllPermissions {
            BluetoothCareEngine.refreshPairedDevices(context)
            if (connectionState == BluetoothConnectionState.DISCONNECTED) {
                BluetoothCareEngine.startHostingServer(context)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF100B1A))
            .padding(16.dp)
            .testTag("bluetooth_care_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header Navigation
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("btn_back_bluetooth_screen")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFFFFD8E4)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EASY BLUETOOTH PHONE SYNC 📡",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD8E4)
                    )
                    Text(
                        text = "Direct Real-Time Connection for Dad & Mom Phones",
                        fontSize = 11.sp,
                        color = Color(0xFFCBBBC3)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when (connectionState) {
                        BluetoothConnectionState.CONNECTED -> Color(0xFF2E7D32)
                        BluetoothConnectionState.HOSTING_SERVER -> Color(0xFFE65100)
                        BluetoothConnectionState.CONNECTING -> Color(0xFF0277BD)
                        BluetoothConnectionState.DISCONNECTED -> Color(0xFF524354)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (connectionState) {
                                BluetoothConnectionState.CONNECTED -> Icons.Default.BluetoothConnected
                                BluetoothConnectionState.HOSTING_SERVER -> Icons.Default.BluetoothSearching
                                else -> Icons.Default.Bluetooth
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (connectionState) {
                                BluetoothConnectionState.CONNECTED -> "SYNCED 🟢"
                                BluetoothConnectionState.HOSTING_SERVER -> "WAITING 📡"
                                BluetoothConnectionState.CONNECTING -> "PAIRING ⏳"
                                BluetoothConnectionState.DISCONNECTED -> "OFFLINE ⚪"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Permission Banner (if not yet granted)
        if (!permissionsGranted) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3E1F28)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF5252))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = Color(0xFFFF8A80))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "BLUETOOTH PERMISSIONS REQUIRED",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "To find nearby phones and sync in real time, Android requires Bluetooth & Location permissions.",
                            fontSize = 11.sp,
                            color = Color(0xFFFFCDD2)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                requestAllPermissions {
                                    BluetoothCareEngine.refreshPairedDevices(context)
                                    BluetoothCareEngine.startActiveScan(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("GRANT ALL PERMISSIONS NOW 🔑", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Active Connection Status Deck (When Connected)
        if (connectionState == BluetoothConnectionState.CONNECTED) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_active_bluetooth_connection"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B382B)),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF4CAF50))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF81C784),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "CONNECTED & SYNCED REAL-TIME 🟢",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFA5D6A7)
                                    )
                                    Text(
                                        text = connectedDeviceName ?: "Spouse Phone",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    BluetoothCareEngine.stopConnection()
                                    Toast.makeText(context, "Bluetooth unlinked.", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Unlink 🔌", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Both phones are linked! Pings and messages sent below will vibrate spouse phone instantly.",
                            fontSize = 12.sp,
                            color = Color(0xFFE8F5E9)
                        )
                    }
                }
            }
        }

        // Intuitive Setup Wizard Card (When Not Connected)
        if (connectionState != BluetoothConnectionState.CONNECTED) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E172A)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "EASY 2-STEP PAIRING WIZARD 🧙‍♂️",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Surface(
                                color = Color(0xFF332A45),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFF4081), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Passcode: $passcode",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFFFB2C9)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Select My Role
                        Text(
                            text = "My Phone Role:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD1C4E9)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Dad 👨", "Mom 👩", "Grandparent 👴", "Nanny 👶").forEach { role ->
                                FilterChip(
                                    selected = selectedRole == role,
                                    onClick = { selectedRole = role },
                                    label = { Text(role, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF7E57C2),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFF2C223A),
                                        labelColor = Color(0xFFCBBBC3)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Visual 2-Step Action Buttons
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    requestAllPermissions {
                                        BluetoothCareEngine.makeDeviceDiscoverable(context)
                                        BluetoothCareEngine.startHostingServer(context)
                                        Toast.makeText(context, "Phone 1 is now visible & waiting for Phone 2!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_step1_discoverable"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E35B1))
                            ) {
                                Text("STEP 1: Make Phone 1 Visible / Host 📡", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    requestAllPermissions {
                                        BluetoothCareEngine.startActiveScan(context)
                                        Toast.makeText(context, "Scanning for Phone 1...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_step2_scan"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isScanning) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                    } else {
                                        Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text("STEP 2: Scan Nearby Phones on Phone 2 🔍", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFFB74D)
                        )
                    }
                }
            }

            // Available Nearby Devices List
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E172A)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.BluetoothSearching, contentDescription = null, tint = Color(0xFF81D4FA), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "NEARBY PHONES & DEVICES FOUND (${discoveredDevices.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            IconButton(
                                onClick = {
                                    requestAllPermissions {
                                        BluetoothCareEngine.startActiveScan(context)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF81D4FA))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (discoveredDevices.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (isScanning) "Scanning nearby Bluetooth devices..." else "No nearby phones found yet. Tap 'STEP 2: Scan Nearby Phones'.",
                                    fontSize = 12.sp,
                                    color = Color(0xFFB0BEC5)
                                )
                            }
                        } else {
                            discoveredDevices.forEach { dev ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            requestAllPermissions {
                                                BluetoothCareEngine.connectToDevice(context, dev.address, dev.name)
                                            }
                                        }
                                        .testTag("device_item_${dev.address}"),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A203B)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                color = if (dev.isBonded) Color(0xFF00897B) else Color(0xFF0288D1),
                                                shape = CircleShape,
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Bluetooth,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(dev.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                Text(
                                                    text = if (dev.isBonded) "Paired Device • ${dev.address}" else "Discovered Device • ${dev.address}",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFFB0BEC5)
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                requestAllPermissions {
                                                    BluetoothCareEngine.connectToDevice(context, dev.address, dev.name)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("PAIR & SYNC 🤝", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Vibration Ping Deck
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E172A)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Vibration, contentDescription = null, tint = Color(0xFFFF83A8))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "INSTANT VIBRATION PINGS 📳",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "Tap any ping to instantly vibrate connected spouse phone",
                        fontSize = 11.sp,
                        color = Color(0xFFCBBBC3)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    PingPresets.forEach { preset ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    requestAllPermissions {
                                        BluetoothCareEngine.sendPing(context, preset)
                                    }
                                }
                                .testTag("ping_preset_${preset.id}"),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C223A)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(preset.icon, fontSize = 22.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = preset.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = preset.description,
                                            fontSize = 10.sp,
                                            color = Color(0xFFD3B8C5)
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        requestAllPermissions {
                                            BluetoothCareEngine.sendPing(context, preset)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = when (preset.id) {
                                            "URGENT" -> Color(0xFFE91E63)
                                            "FEEDING" -> Color(0xFFAB47BC)
                                            "SLEEPING" -> Color(0xFF26A69A)
                                            "DIAPER" -> Color(0xFFFFA726)
                                            else -> Color(0xFF42A5F5)
                                        }
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("PING 📳", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Real-Time Walkie-Talkie Caregiver Chat
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E172A)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.People, contentDescription = null, tint = Color(0xFF81D4FA))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "REAL-TIME BLUETOOTH CAREGIVER CHAT 💬",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick response chips
                    val canned = listOf("On my way! 🏃", "Bottle ready 🍼", "In nursery 👶", "Sleeping 😴", "Can you help? 🙏")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(canned) { quickText ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    requestAllPermissions {
                                        BluetoothCareEngine.sendChatMessage(context, quickText)
                                    }
                                },
                                label = { Text(quickText, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color(0xFF2C223A),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Chat messages list
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Color(0xFF120E1C), shape = RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        if (messages.isEmpty()) {
                            Text(
                                text = "No Bluetooth messages sent or received yet.",
                                fontSize = 11.sp,
                                color = Color(0xFF8A7C8E),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(messages, key = { it.id }) { msg ->
                                    val isMe = msg.isFromMe
                                    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(msg.timestampMillis))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                                    ) {
                                        Surface(
                                            color = if (msg.isPing) Color(0xFF4A148C) else if (isMe) Color(0xFF00695C) else Color(0xFF37474F),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text(
                                                    text = "${msg.senderName} • $timeStr",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFFFD8E4)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (msg.pingIcon != null) {
                                                        Text(msg.pingIcon, fontSize = 14.sp)
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                    }
                                                    Text(
                                                        text = msg.text,
                                                        fontSize = 12.sp,
                                                        color = Color.White,
                                                        fontWeight = if (msg.isPing) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Chat Input Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = chatInputText,
                            onValueChange = { chatInputText = it },
                            placeholder = { Text("Send Bluetooth message...", fontSize = 12.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_bluetooth_chat"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                requestAllPermissions {
                                    BluetoothCareEngine.sendChatMessage(context, chatInputText)
                                    chatInputText = ""
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                            modifier = Modifier.testTag("btn_send_bluetooth_chat")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
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
