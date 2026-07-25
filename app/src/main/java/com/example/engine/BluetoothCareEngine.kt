package com.example.engine

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

enum class BluetoothConnectionState {
    DISCONNECTED,
    HOSTING_SERVER,
    CONNECTING,
    CONNECTED
}

data class BluetoothCareMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderName: String,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val isPing: Boolean = false,
    val pingIcon: String? = null,
    val isFromMe: Boolean = false
)

data class PingPreset(
    val id: String,
    val title: String,
    val icon: String,
    val description: String,
    val vibratePattern: LongArray,
    val defaultMessage: String
)

val PingPresets = listOf(
    PingPreset(
        id = "URGENT",
        title = "Urgent Help Needed!",
        icon = "🚨",
        description = "Strong double pulse vibration",
        vibratePattern = longArrayOf(0, 500, 200, 500, 200, 500),
        defaultMessage = "Need help in the nursery immediately! 🚨"
    ),
    PingPreset(
        id = "FEEDING",
        title = "Your Turn for Feed",
        icon = "🍼",
        description = "Rhythmic bottle pulse",
        vibratePattern = longArrayOf(0, 300, 150, 300, 150, 300),
        defaultMessage = "Baby is hungry! Your turn for the bottle 🍼"
    ),
    PingPreset(
        id = "SLEEPING",
        title = "Baby Is Asleep",
        icon = "😴",
        description = "Gentle calming pulse",
        vibratePattern = longArrayOf(0, 150, 100, 150),
        defaultMessage = "Shh! Baby just fell asleep peacefully 😴"
    ),
    PingPreset(
        id = "DIAPER",
        title = "Diaper Change Required",
        icon = "🧷",
        description = "Triple quick tap",
        vibratePattern = longArrayOf(0, 100, 80, 100, 80, 100),
        defaultMessage = "Diaper alert! Can you handle a change? 🧷"
    ),
    PingPreset(
        id = "MEDICINE",
        title = "Medication / Drops",
        icon = "💊",
        description = "Long alert pulse",
        vibratePattern = longArrayOf(0, 400, 200, 400),
        defaultMessage = "Time for baby's routine vitamin/medicine drops 💊"
    )
)

data class DiscoveredBluetoothDevice(
    val name: String,
    val address: String,
    val isBonded: Boolean
)

object BluetoothCareEngine {

    private const val TAG = "BluetoothCareEngine"
    // Standard SPP UUID for maximum hardware & cross-device compatibility
    private val APP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val SERVICE_NAME = "BabyCareBluetoothSync"

    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverJob: Job? = null
    private var clientJob: Job? = null
    private var activeSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private val _connectionState = MutableStateFlow(BluetoothConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _messages = MutableStateFlow<List<BluetoothCareMessage>>(emptyList())
    val messages: StateFlow<List<BluetoothCareMessage>> = _messages.asStateFlow()

    private val _lastPingReceived = MutableStateFlow<BluetoothCareMessage?>(null)
    val lastPingReceived: StateFlow<BluetoothCareMessage?> = _lastPingReceived.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val pairedDevices: StateFlow<List<Pair<String, String>>> = _pairedDevices.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _passcode = MutableStateFlow("1234")
    val passcode: StateFlow<String> = _passcode.asStateFlow()

    private val _statusText = MutableStateFlow<String>("Ready to sync")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private var myCaregiverName: String = "Parent"
    private var scanReceiverRegistered = false

    fun setMyCaregiverName(name: String) {
        myCaregiverName = name
    }

    fun setPasscode(code: String) {
        if (code.isNotBlank()) {
            _passcode.value = code.trim()
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices(context: Context) {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && adapter.isEnabled) {
                val bonded = adapter.bondedDevices ?: emptySet()
                _pairedDevices.value = bonded.map { Pair(it.name ?: "Unknown Device", it.address) }
                
                // Initialize discovered devices with paired list
                val list = bonded.map { 
                    DiscoveredBluetoothDevice(name = it.name ?: "Paired Device", address = it.address, isBonded = true) 
                }
                _discoveredDevices.value = list
                
                if (bonded.isEmpty()) {
                    _statusText.value = "No paired Bluetooth devices found. Tap 'Scan Nearby Devices'."
                } else {
                    _statusText.value = "Found ${bonded.size} paired Bluetooth device(s)."
                }
            } else {
                _pairedDevices.value = emptyList()
                _discoveredDevices.value = emptyList()
                _statusText.value = "Bluetooth is turned OFF. Please turn ON Bluetooth."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting paired devices", e)
            _statusText.value = "Error reading devices: ${e.message}"
        }
    }

    private val scanReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        val name = device.name ?: "Nearby Phone / Bluetooth"
                        val address = device.address
                        val isBonded = device.bondState == BluetoothDevice.BOND_BONDED
                        val newItem = DiscoveredBluetoothDevice(name, address, isBonded)

                        val current = _discoveredDevices.value.toMutableList()
                        if (current.none { it.address == address }) {
                            current.add(newItem)
                            _discoveredDevices.value = current
                            Log.d(TAG, "Discovered device: $name ($address)")
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                    _statusText.value = "Scan complete. Found ${_discoveredDevices.value.size} total device(s)."
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startActiveScan(context: Context) {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled) {
                _statusText.value = "Bluetooth is turned OFF. Please enable Bluetooth."
                return
            }

            if (!scanReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                context.applicationContext.registerReceiver(scanReceiver, filter)
                scanReceiverRegistered = true
            }

            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }

            refreshPairedDevices(context)
            _isScanning.value = true
            _statusText.value = "Scanning for nearby Bluetooth devices & phones..."
            adapter.startDiscovery()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start active scan", e)
            _statusText.value = "Scan failed: ${e.message}"
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun makeDeviceDiscoverable(context: Context) {
        try {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(discoverableIntent)
            _statusText.value = "Device is now discoverable to nearby phones for 5 minutes."
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch discoverable intent", e)
            _statusText.value = "Could not set discoverable mode directly."
        }
    }

    @SuppressLint("MissingPermission")
    fun startHostingServer(context: Context) {
        stopConnection()
        _connectionState.value = BluetoothConnectionState.HOSTING_SERVER
        _statusText.value = "Listening for spouse phone connection (Passcode: ${_passcode.value})..."

        serverJob = scope.launch {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter == null || !adapter.isEnabled) {
                    _statusText.value = "Bluetooth is disabled. Please turn ON Bluetooth."
                    _connectionState.value = BluetoothConnectionState.DISCONNECTED
                    return@launch
                }

                val serverSocket: BluetoothServerSocket = adapter.listenUsingInsecureRfcommWithServiceRecord(
                    SERVICE_NAME,
                    APP_UUID
                )
                Log.d(TAG, "Insecure RFCOMM Server listening for peer...")
                val socket: BluetoothSocket = serverSocket.accept() // Blocks until client connects
                serverSocket.close()

                manageServerHandshake(context, socket)
            } catch (e: Exception) {
                Log.e(TAG, "Server listening failed", e)
                if (_connectionState.value == BluetoothConnectionState.HOSTING_SERVER) {
                    _statusText.value = "Server listening stopped: ${e.localizedMessage}"
                    _connectionState.value = BluetoothConnectionState.DISCONNECTED
                }
            }
        }
    }

    private suspend fun manageServerHandshake(context: Context, socket: BluetoothSocket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
            val writer = socket.outputStream

            // Await AUTH payload from client: AUTH|<passcode>|<clientName>
            val authLine = reader.readLine()
            if (authLine != null && authLine.startsWith("AUTH|")) {
                val parts = authLine.split("|")
                val clientPasscode = parts.getOrNull(1) ?: ""
                val clientName = parts.getOrNull(2) ?: "Spouse"

                if (clientPasscode == _passcode.value) {
                    // Send AUTH_OK|<myCaregiverName>
                    val response = "AUTH_OK|$myCaregiverName\n"
                    writer.write(response.toByteArray(Charsets.UTF_8))
                    writer.flush()

                    activeSocket = socket
                    outputStream = socket.outputStream
                    _connectionState.value = BluetoothConnectionState.CONNECTED
                    _connectedDeviceName.value = clientName
                    _statusText.value = "Connected to $clientName real-time over Bluetooth!"

                    // Start reading messages loop
                    readLoop(context, reader, clientName)
                } else {
                    val failMsg = "AUTH_FAIL|Incorrect passcode\n"
                    writer.write(failMsg.toByteArray(Charsets.UTF_8))
                    writer.flush()
                    socket.close()
                    _statusText.value = "Connection rejected: Invalid passcode entered by remote device."
                    _connectionState.value = BluetoothConnectionState.DISCONNECTED
                }
            } else {
                socket.close()
                _connectionState.value = BluetoothConnectionState.DISCONNECTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Handshake failed on server", e)
            socket.close()
            _statusText.value = "Handshake failed: ${e.localizedMessage}"
            _connectionState.value = BluetoothConnectionState.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(context: Context, deviceAddress: String, deviceName: String) {
        stopConnection()
        _connectionState.value = BluetoothConnectionState.CONNECTING
        _statusText.value = "Connecting to $deviceName..."

        clientJob = scope.launch {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter == null || !adapter.isEnabled) {
                    _statusText.value = "Bluetooth is disabled. Please turn ON Bluetooth."
                    _connectionState.value = BluetoothConnectionState.DISCONNECTED
                    return@launch
                }

                val device: BluetoothDevice = adapter.getRemoteDevice(deviceAddress)
                adapter.cancelDiscovery()

                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    try {
                        device.createBond()
                    } catch (e: Exception) {
                        Log.w(TAG, "Bonding failed or skipped, proceeding with insecure RFCOMM socket", e)
                    }
                }

                val socket = device.createInsecureRfcommSocketToServiceRecord(APP_UUID)
                socket.connect()

                manageClientHandshake(context, socket, deviceName)
            } catch (e: Exception) {
                Log.e(TAG, "Client connection failed to $deviceName", e)
                _statusText.value = "Connection failed to $deviceName: ${e.localizedMessage}. Ensure app is open & listening on $deviceName."
                _connectionState.value = BluetoothConnectionState.DISCONNECTED
            }
        }
    }

    private suspend fun manageClientHandshake(context: Context, socket: BluetoothSocket, defaultDeviceName: String) {
        try {
            val writer = socket.outputStream
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))

            // Send AUTH|<passcode>|<myCaregiverName>
            val authMsg = "AUTH|${_passcode.value}|$myCaregiverName\n"
            writer.write(authMsg.toByteArray(Charsets.UTF_8))
            writer.flush()

            // Read response
            val responseLine = reader.readLine()
            if (responseLine != null && responseLine.startsWith("AUTH_OK|")) {
                val remoteName = responseLine.split("|").getOrNull(1) ?: defaultDeviceName

                activeSocket = socket
                outputStream = socket.outputStream
                _connectionState.value = BluetoothConnectionState.CONNECTED
                _connectedDeviceName.value = remoteName
                _statusText.value = "Connected to $remoteName real-time over Bluetooth!"

                readLoop(context, reader, remoteName)
            } else {
                _statusText.value = "Passcode authentication failed. Make sure both phones use passcode '${_passcode.value}'."
                socket.close()
                _connectionState.value = BluetoothConnectionState.DISCONNECTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client handshake error", e)
            socket.close()
            _statusText.value = "Authentication error: ${e.localizedMessage}"
            _connectionState.value = BluetoothConnectionState.DISCONNECTED
        }
    }

    private fun readLoop(context: Context, reader: BufferedReader, remoteName: String) {
        try {
            while (scope.isActive && activeSocket?.isConnected == true) {
                val line = reader.readLine() ?: break
                handleIncomingPayload(context, line)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Socket stream read error or connection lost", e)
        } finally {
            _connectionState.value = BluetoothConnectionState.DISCONNECTED
            _connectedDeviceName.value = null
            _statusText.value = "Bluetooth connection closed."
        }
    }

    private fun handleIncomingPayload(context: Context, rawData: String) {
        val parts = rawData.split("|")
        if (parts.isEmpty()) return

        when (parts[0]) {
            "PING" -> {
                val presetId = parts.getOrNull(1) ?: "URGENT"
                val sender = parts.getOrNull(2) ?: "Caregiver"
                val text = parts.getOrNull(3) ?: "Ping alert!"

                val preset = PingPresets.find { it.id == presetId } ?: PingPresets[0]
                val pingMsg = BluetoothCareMessage(
                    senderName = sender,
                    text = text,
                    isPing = true,
                    pingIcon = preset.icon,
                    isFromMe = false
                )

                _lastPingReceived.value = pingMsg
                _messages.value = _messages.value + pingMsg

                // Real-time vibration trigger on physical phone
                vibrateDevice(context, preset.vibratePattern)
            }
            "CHAT" -> {
                val sender = parts.getOrNull(1) ?: "Caregiver"
                val text = parts.getOrNull(2) ?: ""

                val chatMsg = BluetoothCareMessage(
                    senderName = sender,
                    text = text,
                    isPing = false,
                    isFromMe = false
                )
                _messages.value = _messages.value + chatMsg

                // Light vibration notification on message received
                vibrateDevice(context, longArrayOf(0, 150))
            }
        }
    }

    fun sendPing(context: Context, preset: PingPreset, customMessage: String? = null) {
        val text = customMessage ?: preset.defaultMessage
        val payload = "PING|${preset.id}|$myCaregiverName|$text"

        val msg = BluetoothCareMessage(
            senderName = myCaregiverName,
            text = text,
            isPing = true,
            pingIcon = preset.icon,
            isFromMe = true
        )
        _messages.value = _messages.value + msg

        // Trigger local vibration as immediate tactile feedback for the sender
        vibrateDevice(context, preset.vibratePattern)

        sendPayloadOverSocket(payload)
    }

    fun sendChatMessage(context: Context, text: String) {
        if (text.isBlank()) return
        val payload = "CHAT|$myCaregiverName|$text"

        val msg = BluetoothCareMessage(
            senderName = myCaregiverName,
            text = text,
            isPing = false,
            isFromMe = true
        )
        _messages.value = _messages.value + msg

        sendPayloadOverSocket(payload)
    }

    private fun sendPayloadOverSocket(payload: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val stream = outputStream
                if (stream != null) {
                    val line = payload + "\n"
                    stream.write(line.toByteArray(Charsets.UTF_8))
                    stream.flush()
                } else {
                    Log.w(TAG, "Cannot send message: socket output stream is null")
                    _statusText.value = "Cannot send: Bluetooth is disconnected."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send payload over Bluetooth socket", e)
                _statusText.value = "Send failed: Bluetooth connection lost."
                _connectionState.value = BluetoothConnectionState.DISCONNECTED
            }
        }
    }

    fun vibrateDevice(context: Context, pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    fun stopConnection() {
        serverJob?.cancel()
        serverJob = null
        clientJob?.cancel()
        clientJob = null
        try {
            outputStream?.close()
            activeSocket?.close()
        } catch (_: Exception) {}
        activeSocket = null
        outputStream = null
        _connectionState.value = BluetoothConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
        _statusText.value = "Disconnected"
    }
}
