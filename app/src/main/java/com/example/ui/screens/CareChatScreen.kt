package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.BluetoothCareEngine
import com.example.engine.BluetoothConnectionState
import com.example.engine.PingPresets
import com.example.ui.components.CareChatBubble

@Composable
fun CareChatScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val connectionState by BluetoothCareEngine.connectionState.collectAsStateWithLifecycle()
    val connectedDeviceName by BluetoothCareEngine.connectedDeviceName.collectAsStateWithLifecycle()
    val messages by BluetoothCareEngine.messages.collectAsStateWithLifecycle()
    val statusText by BluetoothCareEngine.statusText.collectAsStateWithLifecycle()

    var chatInputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        BluetoothCareEngine.setChatScreenVisible(true)
        onDispose { BluetoothCareEngine.setChatScreenVisible(false) }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val peerTitle = connectedDeviceName?.takeIf { it.isNotBlank() } ?: "Caregiver"
    val statusDotColor = when (connectionState) {
        BluetoothConnectionState.CONNECTED -> MaterialTheme.colorScheme.tertiary
        BluetoothConnectionState.CONNECTING,
        BluetoothConnectionState.HOSTING_SERVER -> MaterialTheme.colorScheme.secondary
        BluetoothConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val canSend = connectionState == BluetoothConnectionState.CONNECTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("care_chat_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.testTag("care_chat_back_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = peerTitle,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Text(
                    text = if (canSend) "Connected · messages sync live" else statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (canSend) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(PingPresets) { preset ->
                    Card(
                        onClick = { BluetoothCareEngine.sendPing(context, preset) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(preset.icon, fontSize = 20.sp)
                            Text(
                                preset.title,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Icon(
                                Icons.Default.Vibration,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        text = if (canSend) {
                            "Say hi — messages appear here for both caregivers."
                        } else {
                            "Connect via Care Sync to message nearby caregivers."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
            items(messages, key = { it.id }) { msg ->
                CareChatBubble(message = msg)
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .navigationBarsPadding()
                .imePadding()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = chatInputText,
                onValueChange = { chatInputText = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("care_chat_input"),
                placeholder = {
                    Text(if (canSend) "Message caregiver…" else "Not connected")
                },
                enabled = canSend,
                singleLine = true,
                shape = RoundedCornerShape(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    BluetoothCareEngine.sendChatMessage(context, chatInputText)
                    chatInputText = ""
                },
                enabled = canSend && chatInputText.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend && chatInputText.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .testTag("send_chat_btn")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend && chatInputText.isNotBlank()) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
