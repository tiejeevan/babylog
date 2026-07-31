package com.example.engine

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.data.database.BabyCareDatabase
import com.example.data.model.ActivityLog
import com.example.data.model.BabyProfile
import com.example.data.model.DutySession
import com.example.data.model.GrowthRecord
import com.example.data.model.MedicalRecord
import com.example.data.model.MemoryItem
import com.example.data.model.MilestoneRecord
import com.example.data.model.MilkStashItem
import com.example.data.model.MessageDeliveryStatus
import com.example.data.model.PeerChatMessage
import com.example.data.model.SharedList
import com.example.data.model.SharedListItem
import com.example.data.model.SharedNote
import com.example.data.repository.BabyCareRepository
import com.example.notification.BabyNotificationManager
import com.example.service.CareSyncForegroundService
import com.example.widget.BabyCareWidgetProvider
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
enum class BluetoothConnectionState {
    DISCONNECTED,
    HOSTING_SERVER, // advertising / discovering (Care Sync on)
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
    val isFromMe: Boolean = false,
    val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.PENDING
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
    val isBonded: Boolean = false
)

/**
 * Nearby Connections backed Care Sync engine.
 * Keeps BluetoothCareEngine name for UI compatibility.
 */
object BluetoothCareEngine {

    private const val TAG = "BluetoothCareEngine"
    private const val SERVICE_ID = "com.aistudio.babycarelive.care"
    private val STRATEGY = Strategy.P2P_CLUSTER

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val envelopeAdapter = moshi.adapter(CareEnvelope::class.java)
    private val helloAdapter = moshi.adapter(HelloPayload::class.java)
    private val helloAckAdapter = moshi.adapter(HelloAckPayload::class.java)
    private val chatAdapter = moshi.adapter(ChatPayload::class.java)
    private val pingAdapter = moshi.adapter(PingPayload::class.java)
    private val chatAckAdapter = moshi.adapter(ChatAckPayload::class.java)
    private val chatReadAdapter = moshi.adapter(ChatReadPayload::class.java)
    private val syncOfferAdapter = moshi.adapter(SyncOfferPayload::class.java)
    private val syncBatchAdapter = moshi.adapter(SyncBatchPayload::class.java)
    private val syncAckAdapter = moshi.adapter(SyncAckPayload::class.java)
    private val logDtoAdapter = moshi.adapter(ActivityLogDto::class.java)
    private val growthDtoAdapter = moshi.adapter(GrowthDto::class.java)
    private val medicalDtoAdapter = moshi.adapter(MedicalDto::class.java)
    private val milkDtoAdapter = moshi.adapter(MilkStashDto::class.java)
    private val milestoneDtoAdapter = moshi.adapter(MilestoneDto::class.java)
    private val profileDtoAdapter = moshi.adapter(BabyProfileDto::class.java)
    private val dutyDtoAdapter = moshi.adapter(DutyDto::class.java)
    private val memoryDtoAdapter = moshi.adapter(MemoryDto::class.java)
    private val memoryFileOfferAdapter = moshi.adapter(MemoryFileOfferPayload::class.java)
    private val memoryFileRequestAdapter = moshi.adapter(MemoryFileRequestPayload::class.java)
    private val noteDtoAdapter = moshi.adapter(NoteDto::class.java)
    private val listDtoAdapter = moshi.adapter(ListDto::class.java)
    private val listItemDtoAdapter = moshi.adapter(ListItemDto::class.java)

    private var appContext: Context? = null
    private var connectionsClient: ConnectionsClient? = null
    private var repository: BabyCareRepository? = null

    private var myCaregiverName: String = "Parent"
    private var myCaregiverRole: String = ""
    private var babyName: String = ""
    private var deviceId: String = ""
    private var localEndpointName: String = "Caregiver"

    private val connectedEndpoints = ConcurrentHashMap<String, String>() // endpointId -> peer name
    private val pendingAuth = ConcurrentHashMap<String, Boolean>() // endpointId -> awaiting HELLO_ACK
    private val discoveredEndpoints = ConcurrentHashMap<String, DiscoveredBluetoothDevice>()
    /** Incoming FILE payloadId -> memory syncId */
    private val pendingIncomingFiles = ConcurrentHashMap<Long, String>()
    /** Incoming FILE payloadId -> Payload (for asFile after SUCCESS) */
    private val pendingFilePayloads = ConcurrentHashMap<Long, Payload>()
    /** endpointId -> syncId expecting next FILE */
    private val expectedFileByEndpoint = ConcurrentHashMap<String, String>()

    private val _connectionState = MutableStateFlow(BluetoothConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()

    private val _careSyncEnabled = MutableStateFlow(false)
    val careSyncEnabled: StateFlow<Boolean> = _careSyncEnabled.asStateFlow()

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

    private val _statusText = MutableStateFlow("Care Sync off")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _lastSyncedAt = MutableStateFlow(0L)
    val lastSyncedAt: StateFlow<Long> = _lastSyncedAt.asStateFlow()

    private val _muteNonUrgentWhenOffDuty = MutableStateFlow(true)
    val muteNonUrgentWhenOffDuty: StateFlow<Boolean> = _muteNonUrgentWhenOffDuty.asStateFlow()

    private val _vibrateOnReceive = MutableStateFlow(true)
    val vibrateOnReceive: StateFlow<Boolean> = _vibrateOnReceive.asStateFlow()

    private val _outboxPendingCount = MutableStateFlow(0)
    val outboxPendingCount: StateFlow<Int> = _outboxPendingCount.asStateFlow()

    private val _unreadIncomingCount = MutableStateFlow(0)
    val unreadIncomingCount: StateFlow<Int> = _unreadIncomingCount.asStateFlow()

    /** True while CareChatScreen is visible — suppresses notifications and drives read receipts. */
    private val chatScreenVisible = AtomicBoolean(false)

    /** Process-level foreground flag (updated via ProcessLifecycleOwner). */
    private val appInForeground = AtomicBoolean(true)

    /** Cached for mute checks without blocking the payload thread. */
    @Volatile
    private var cachedActiveDutyName: String? = null

    private var scanTimeoutJob: kotlinx.coroutines.Job? = null

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with ${connectionInfo.endpointName}")
            _statusText.value = "Connecting to ${connectionInfo.endpointName}..."
            _connectionState.value = BluetoothConnectionState.CONNECTING
            connectionsClient?.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connected to $endpointId — sending HELLO")
                    pendingAuth[endpointId] = true
                    sendHello(endpointId)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    _statusText.value = "Connection rejected"
                    refreshConnectionState()
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    _statusText.value = "Connection error"
                    refreshConnectionState()
                }
                else -> refreshConnectionState()
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            connectedEndpoints.remove(endpointId)
            pendingAuth.remove(endpointId)
            refreshConnectionState()
            val ctx = appContext
            if (connectedEndpoints.isEmpty() && ctx != null) {
                CareSyncForegroundService.stop(ctx)
                if (_careSyncEnabled.value) {
                    _statusText.value = "Peer left · searching again..."
                    startDiscovery()
                }
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Found endpoint ${info.endpointName} ($endpointId)")
            // Connected peers stay out of the discovered list
            if (connectedEndpoints.containsKey(endpointId)) {
                return
            }
            discoveredEndpoints[endpointId] = DiscoveredBluetoothDevice(
                name = info.endpointName,
                address = endpointId,
                isBonded = false
            )
            publishDiscovered()

            val ctx = appContext
            val forgottenSet = if (ctx != null) CareSyncPrefs.getForgottenDevices(ctx) else emptySet()
            val rememberedSet = if (ctx != null) CareSyncPrefs.getRememberedDevices(ctx) else emptySet()
            if (CareSyncDeviceLists.shouldAutoConnect(
                    endpointName = info.endpointName,
                    forgottenNames = forgottenSet,
                    rememberedNames = rememberedSet,
                    localEndpointName = localEndpointName,
                    localDeviceId = deviceId,
                    endpointId = endpointId,
                    alreadyConnectedOrPending = pendingAuth.containsKey(endpointId)
                )
            ) {
                requestConnection(endpointId, info.endpointName)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            discoveredEndpoints.remove(endpointId)
            publishDiscovered()
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    val raw = String(bytes, Charsets.UTF_8)
                    scope.launch { handleEnvelope(endpointId, raw) }
                }
                Payload.Type.FILE -> {
                    val syncId = expectedFileByEndpoint.remove(endpointId)
                    if (syncId != null) {
                        pendingIncomingFiles[payload.id] = syncId
                        pendingFilePayloads[payload.id] = payload
                    }
                }
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status != PayloadTransferUpdate.Status.SUCCESS) {
                if (update.status == PayloadTransferUpdate.Status.FAILURE ||
                    update.status == PayloadTransferUpdate.Status.CANCELED
                ) {
                    pendingIncomingFiles.remove(update.payloadId)
                    pendingFilePayloads.remove(update.payloadId)
                }
                return
            }
            val syncId = pendingIncomingFiles.remove(update.payloadId) ?: return
            val filePayload = pendingFilePayloads.remove(update.payloadId)
            scope.launch { finalizeIncomingMemoryFile(filePayload, syncId) }
        }
    }

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        connectionsClient = Nearby.getConnectionsClient(context.applicationContext)
        repository = BabyCareRepository(
            BabyCareDatabase.getDatabase(context.applicationContext).babyCareDao()
        )
        deviceId = CareSyncPrefs.getOrCreateDeviceId(context)
        _passcode.value = CareSyncPrefs.getFamilyPin(context)
        _careSyncEnabled.value = CareSyncPrefs.isCareSyncEnabled(context)
        _muteNonUrgentWhenOffDuty.value = CareSyncPrefs.isMuteNonUrgentWhenOffDuty(context)
        _vibrateOnReceive.value = CareSyncPrefs.isVibrateOnReceive(context)
        myCaregiverName = CareSyncPrefs.getCaregiverName(context)
        myCaregiverRole = CareSyncPrefs.getCaregiverRole(context)
        localEndpointName = myCaregiverName
        scope.launch {
            loadChatHistory()
            refreshOutboxCount()
            refreshUnreadCount()
            cachedActiveDutyName = repository?.getActiveDutyDirect()?.takeIf { it.isActive }?.caregiverName
        }
        if (_careSyncEnabled.value) {
            startCareSync(context.applicationContext)
        } else {
            updateStatusSummary()
        }
    }

    fun setMyCaregiverName(context: Context, name: String) {
        appContext = context.applicationContext
        myCaregiverName = name.ifBlank { "Parent" }
        localEndpointName = myCaregiverName
        CareSyncPrefs.setCaregiverName(context.applicationContext, myCaregiverName)
    }

    fun setMyCaregiverName(name: String) {
        myCaregiverName = name.ifBlank { "Parent" }
        localEndpointName = myCaregiverName
        appContext?.let { CareSyncPrefs.setCaregiverName(it, myCaregiverName) }
    }

    fun setMyCaregiverRole(context: Context, role: String) {
        appContext = context.applicationContext
        myCaregiverRole = role
        CareSyncPrefs.setCaregiverRole(context.applicationContext, role)
    }

    fun setMyCaregiverRole(role: String) {
        myCaregiverRole = role
        appContext?.let { CareSyncPrefs.setCaregiverRole(it, role) }
    }

    fun getMyCaregiverName(context: Context? = null): String {
        if (context != null) {
            val saved = CareSyncPrefs.getCaregiverName(context)
            if (saved.isNotBlank()) myCaregiverName = saved
        }
        return myCaregiverName
    }

    fun getMyCaregiverRole(context: Context? = null): String {
        if (context != null) {
            val saved = CareSyncPrefs.getCaregiverRole(context)
            if (saved.isNotBlank()) myCaregiverRole = saved
        }
        return myCaregiverRole
    }

    fun disconnectEndpoint(endpointId: String) {
        val client = connectionsClient ?: return
        client.disconnectFromEndpoint(endpointId)
        connectedEndpoints.remove(endpointId)
        pendingAuth.remove(endpointId)
        refreshConnectionState()
    }

    fun forgetDevice(context: Context, deviceNameOrAddress: String) {
        CareSyncPrefs.addForgottenDevice(context, deviceNameOrAddress)
        CareSyncPrefs.removeRememberedDevice(context, deviceNameOrAddress)
        val targetEndpoint = connectedEndpoints.entries.firstOrNull { it.value == deviceNameOrAddress }?.key
        if (targetEndpoint != null) {
            // Connected peer leaves discovered after disconnect; drop discovery entry too
            discoveredEndpoints.remove(targetEndpoint)
            disconnectEndpoint(targetEndpoint)
        }
        // Non-connected forgotten peers stay in discoveredEndpoints so the UI can
        // show them under "Forgotten Devices" while they remain nearby.
        publishDiscovered()
    }

    /**
     * Drop the active peer without turning Care Sync off.
     * Advertising stays up; discovery restarts so nearby devices can be found again.
     */
    fun disconnectConnectedPeer(context: Context) {
        initialize(context)
        val endpointIds = connectedEndpoints.keys.toList()
        if (endpointIds.isEmpty()) {
            if (_careSyncEnabled.value) {
                startDiscovery()
                _statusText.value = "Searching for nearby caregivers..."
            }
            return
        }
        endpointIds.forEach { disconnectEndpoint(it) }
        appContext?.let { CareSyncForegroundService.stop(it) }
        if (_careSyncEnabled.value) {
            _statusText.value = "Disconnected · searching again..."
            startDiscovery()
        }
    }

    fun setBabyName(name: String) {
        babyName = name
    }

    fun setPasscode(code: String) {
        if (code.isNotBlank()) {
            _passcode.value = code.trim()
            appContext?.let { CareSyncPrefs.setFamilyPin(it, code.trim()) }
        }
    }

    fun setMuteNonUrgentWhenOffDuty(mute: Boolean) {
        _muteNonUrgentWhenOffDuty.value = mute
        appContext?.let { CareSyncPrefs.setMuteNonUrgentWhenOffDuty(it, mute) }
    }

    fun setVibrateOnReceive(enabled: Boolean) {
        _vibrateOnReceive.value = enabled
        appContext?.let { CareSyncPrefs.setVibrateOnReceive(it, enabled) }
    }

    fun setAppInForeground(inForeground: Boolean) {
        appInForeground.set(inForeground)
    }

    fun setChatScreenVisible(visible: Boolean) {
        chatScreenVisible.set(visible)
        if (visible) {
            markChatVisible()
            appContext?.let { BabyNotificationManager.dismissPeerChatNotification(it) }
        }
    }

    /** Mark all unread incoming messages as READ and notify the peer. */
    fun markChatVisible() {
        scope.launch {
            val repo = repository ?: return@launch
            val unread = repo.getUnreadIncomingMessages()
            if (unread.isEmpty()) return@launch
            val syncIds = unread.map { it.syncId }
            for (id in syncIds) {
                updateLocalDeliveryStatus(id, MessageDeliveryStatus.READ)
            }
            if (connectedEndpoints.isNotEmpty()) {
                broadcast(
                    CareMessageTypes.CHAT_READ,
                    chatReadAdapter.toJson(ChatReadPayload(syncIds))
                )
            }
            refreshUnreadCount()
        }
    }

    fun isConnected(): Boolean = connectedEndpoints.isNotEmpty()

    fun getDeviceId(): String = deviceId

    fun startCareSync(context: Context) {
        initialize(context)
        val ctx = context.applicationContext
        CareSyncPrefs.setCareSyncEnabled(ctx, true)
        _careSyncEnabled.value = true
        localEndpointName = myCaregiverName.ifBlank { "Caregiver" }
        startAdvertising()
        startDiscovery()
        _connectionState.value = if (connectedEndpoints.isEmpty()) {
            BluetoothConnectionState.HOSTING_SERVER
        } else {
            BluetoothConnectionState.CONNECTED
        }
        _statusText.value = "Searching for nearby caregivers..."
        _isScanning.value = true
    }

    fun stopCareSync(context: Context) {
        CareSyncPrefs.setCareSyncEnabled(context.applicationContext, false)
        _careSyncEnabled.value = false
        stopAdvertisingAndDiscovery()
        disconnectAll()
        CareSyncForegroundService.stop(context.applicationContext)
        _connectionState.value = BluetoothConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
        _isScanning.value = false
        discoveredEndpoints.clear()
        publishDiscovered()
        _statusText.value = "Care Sync off"
    }

    fun startHostingServer(context: Context) = startCareSync(context)

    fun startActiveScan(context: Context) {
        initialize(context)
        if (!_careSyncEnabled.value) {
            startCareSync(context)
            return
        }
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        connectionsClient?.stopDiscovery()
        clearDiscoveredExceptConnected()
        publishDiscovered()
        _isScanning.value = true
        _statusText.value = "Scanning for nearby caregivers..."
        startDiscovery()
    }

    private fun clearDiscoveredExceptConnected() {
        CareSyncDeviceLists.clearDiscoveredExceptConnected(
            discoveredEndpoints,
            connectedEndpoints.keys.toSet()
        )
    }

    fun refreshPairedDevices(context: Context) {
        initialize(context)
        publishDiscovered()
    }

    fun makeDeviceDiscoverable(context: Context) {
        startCareSync(context)
    }

    fun connectToDevice(context: Context, deviceAddress: String, deviceName: String) {
        initialize(context)
        if (!_careSyncEnabled.value) startCareSync(context)
        requestConnection(deviceAddress, deviceName)
    }

    fun stopConnection() {
        disconnectAll()
        if (_careSyncEnabled.value) {
            _connectionState.value = BluetoothConnectionState.HOSTING_SERVER
            _statusText.value = "Disconnected · still searching..."
        } else {
            _connectionState.value = BluetoothConnectionState.DISCONNECTED
            _statusText.value = "Care Sync off"
        }
        _connectedDeviceName.value = null
        appContext?.let { CareSyncForegroundService.stop(it) }
    }

    fun sendPing(context: Context, preset: PingPreset, customMessage: String? = null) {
        val text = customMessage ?: preset.defaultMessage
        val syncId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val msg = BluetoothCareMessage(
            id = syncId,
            senderName = myCaregiverName,
            text = text,
            timestampMillis = now,
            isPing = true,
            pingIcon = preset.icon,
            isFromMe = true,
            deliveryStatus = MessageDeliveryStatus.PENDING
        )
        appendMessage(msg)
        persistChat(msg)
        vibrateDevice(context, preset.vibratePattern)
        val payload = PingPayload(
            syncId = syncId,
            presetId = preset.id,
            senderName = myCaregiverName,
            text = text,
            pingIcon = preset.icon,
            timestampMillis = now
        )
        enqueueAndMaybeSend(CareMessageTypes.PING, pingAdapter.toJson(payload), "PING:$syncId")
    }

    fun sendChatMessage(context: Context, text: String) {
        if (text.isBlank()) return
        val syncId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val msg = BluetoothCareMessage(
            id = syncId,
            senderName = myCaregiverName,
            text = text,
            timestampMillis = now,
            isPing = false,
            isFromMe = true,
            deliveryStatus = MessageDeliveryStatus.PENDING
        )
        appendMessage(msg)
        persistChat(msg)
        val payload = ChatPayload(
            syncId = syncId,
            senderName = myCaregiverName,
            text = text,
            timestampMillis = now
        )
        enqueueAndMaybeSend(CareMessageTypes.CHAT, chatAdapter.toJson(payload), "CHAT:$syncId")
    }

    fun broadcastLogUpsert(log: ActivityLog) {
        val dto = log.toDto()
        enqueueAndMaybeSend(
            CareMessageTypes.LOG_UPSERT,
            logDtoAdapter.toJson(dto),
            "LOG_UPSERT:${log.syncId}"
        )
    }

    fun broadcastLogDelete(log: ActivityLog) {
        val dto = log.copy(isDeleted = true).toDto()
        enqueueAndMaybeSend(
            CareMessageTypes.LOG_DELETE,
            logDtoAdapter.toJson(dto),
            "LOG_DELETE:${log.syncId}"
        )
    }

    fun broadcastGrowthUpsert(record: GrowthRecord) {
        val type = if (record.isDeleted) CareMessageTypes.GROWTH_DELETE else CareMessageTypes.GROWTH_UPSERT
        enqueueAndMaybeSend(type, growthDtoAdapter.toJson(record.toDto()), "$type:${record.syncId}")
    }

    fun broadcastMedicalUpsert(record: MedicalRecord) {
        val type = if (record.isDeleted) CareMessageTypes.MEDICAL_DELETE else CareMessageTypes.MEDICAL_UPSERT
        enqueueAndMaybeSend(type, medicalDtoAdapter.toJson(record.toDto()), "$type:${record.syncId}")
    }

    fun broadcastMilkUpsert(item: MilkStashItem) {
        val type = if (item.isDeleted) CareMessageTypes.MILK_DELETE else CareMessageTypes.MILK_UPSERT
        enqueueAndMaybeSend(type, milkDtoAdapter.toJson(item.toDto()), "$type:${item.syncId}")
    }

    fun broadcastMilestoneUpsert(milestone: MilestoneRecord) {
        val type = if (milestone.isDeleted) CareMessageTypes.MILESTONE_DELETE else CareMessageTypes.MILESTONE_UPSERT
        enqueueAndMaybeSend(type, milestoneDtoAdapter.toJson(milestone.toDto()), "$type:${milestone.syncId}")
    }

    fun broadcastProfileUpsert(profile: BabyProfile) {
        enqueueAndMaybeSend(
            CareMessageTypes.PROFILE_UPSERT,
            profileDtoAdapter.toJson(profile.toDto()),
            "PROFILE_UPSERT:1"
        )
    }

    fun broadcastDutyClaim(session: DutySession) {
        cachedActiveDutyName = session.caregiverName.takeIf { session.isActive }
        enqueueAndMaybeSend(
            CareMessageTypes.DUTY_CLAIM,
            dutyDtoAdapter.toJson(session.toDto()),
            "DUTY:${session.syncId}"
        )
    }

    fun broadcastDutyRelease(session: DutySession) {
        cachedActiveDutyName = null
        enqueueAndMaybeSend(
            CareMessageTypes.DUTY_RELEASE,
            dutyDtoAdapter.toJson(session.toDto()),
            "DUTY:${session.syncId}"
        )
    }

    fun broadcastMemoryUpsert(item: MemoryItem) {
        val type = if (item.isDeleted) CareMessageTypes.MEMORY_DELETE else CareMessageTypes.MEMORY_UPSERT
        enqueueAndMaybeSend(type, memoryDtoAdapter.toJson(item.toDto()), "$type:${item.syncId}")
        if (!item.isDeleted && item.localPath.isNotBlank() && File(item.localPath).exists()) {
            sendMemoryFileToPeers(item)
        }
    }

    fun broadcastNoteUpsert(note: SharedNote) {
        val type = if (note.isDeleted) CareMessageTypes.NOTE_DELETE else CareMessageTypes.NOTE_UPSERT
        enqueueAndMaybeSend(type, noteDtoAdapter.toJson(note.toDto()), "$type:${note.syncId}")
    }

    fun broadcastListUpsert(list: SharedList) {
        val type = if (list.isDeleted) CareMessageTypes.LIST_DELETE else CareMessageTypes.LIST_UPSERT
        enqueueAndMaybeSend(type, listDtoAdapter.toJson(list.toDto()), "$type:${list.syncId}")
    }

    fun broadcastListItemUpsert(item: SharedListItem) {
        val type = if (item.isDeleted) CareMessageTypes.LIST_ITEM_DELETE else CareMessageTypes.LIST_ITEM_UPSERT
        enqueueAndMaybeSend(type, listItemDtoAdapter.toJson(item.toDto()), "$type:${item.syncId}")
    }

    fun vibrateDevice(context: Context, pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
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

    // ---- Outbox ----

    private fun enqueueAndMaybeSend(type: String, payloadJson: String, dedupeKey: String) {
        scope.launch {
            val repo = repository ?: return@launch
            repo.enqueueOutbox(type, payloadJson, dedupeKey)
            refreshOutboxCount()
            if (connectedEndpoints.isNotEmpty()) {
                broadcast(type, payloadJson)
                repo.removeOutboxByDedupeKey(dedupeKey)
                refreshOutboxCount()
                _lastSyncedAt.value = System.currentTimeMillis()
                updateStatusSummary()
            }
        }
    }

    private suspend fun drainOutbox(endpointId: String) {
        val repo = repository ?: return
        val items = repo.getOutboxOrdered()
        for (item in items) {
            sendTo(endpointId, item.messageType, item.payloadJson)
            repo.removeOutboxById(item.id)
        }
        refreshOutboxCount()
        if (items.isNotEmpty()) {
            _lastSyncedAt.value = System.currentTimeMillis()
            Log.d(TAG, "Drained ${items.size} outbox items to $endpointId")
        }
    }

    private suspend fun refreshOutboxCount() {
        val count = repository?.getOutboxOrdered()?.size ?: 0
        _outboxPendingCount.value = count
    }

    // ---- Nearby internals ----

    private fun startAdvertising() {
        val client = connectionsClient ?: return
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        client.startAdvertising(
            localEndpointName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising as $localEndpointName")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Advertising failed", e)
            _statusText.value = "Advertise failed: ${e.localizedMessage}"
        }
    }

    private fun startDiscovery() {
        val client = connectionsClient ?: return
        scanTimeoutJob?.cancel()
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        client.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery started")
            _isScanning.value = true
            scanTimeoutJob = scope.launch {
                kotlinx.coroutines.delay(30_000L)
                if (connectedEndpoints.isEmpty()) {
                    Log.d(TAG, "Scan window timed out after 30s — pausing active discovery to save battery")
                    connectionsClient?.stopDiscovery()
                    _isScanning.value = false
                    updateStatusSummary()
                }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Discovery failed", e)
            _statusText.value = "Discovery failed: ${e.localizedMessage}"
            _isScanning.value = false
        }
    }

    private fun stopAdvertisingAndDiscovery() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        connectionsClient?.stopAdvertising()
        connectionsClient?.stopDiscovery()
        _isScanning.value = false
    }

    private fun requestConnection(endpointId: String, endpointName: String) {
        val client = connectionsClient ?: return
        _connectionState.value = BluetoothConnectionState.CONNECTING
        _statusText.value = "Connecting to $endpointName..."
        client.requestConnection(
            localEndpointName,
            endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            Log.d(TAG, "Connection requested to $endpointName")
        }.addOnFailureListener { e ->
            Log.e(TAG, "requestConnection failed", e)
            if (e.localizedMessage?.contains("already connected", ignoreCase = true) != true) {
                _statusText.value = "Could not reach $endpointName"
                refreshConnectionState()
            }
        }
    }

    private fun disconnectAll() {
        val client = connectionsClient ?: return
        connectedEndpoints.keys.toList().forEach { id ->
            client.disconnectFromEndpoint(id)
        }
        connectedEndpoints.clear()
        pendingAuth.clear()
    }

    private fun sendHello(endpointId: String) {
        val payload = HelloPayload(
            pin = _passcode.value,
            caregiverName = myCaregiverName,
            caregiverRole = myCaregiverRole,
            babyName = babyName,
            deviceId = deviceId
        )
        sendTo(endpointId, CareMessageTypes.HELLO, helloAdapter.toJson(payload))
    }

    private fun sendSyncOffer(endpointId: String) {
        scope.launch {
            val repo = repository ?: return@launch
            drainOutbox(endpointId)
            val offer = SyncOfferPayload(
                latestUpdatedAt = repo.getLatestLogUpdatedAt(),
                logCount = repo.getLogCount()
            )
            sendTo(endpointId, CareMessageTypes.SYNC_OFFER, syncOfferAdapter.toJson(offer))
            pushFullSyncBatch(endpointId)
        }
    }

    private suspend fun pushFullSyncBatch(endpointId: String) {
        val repo = repository ?: return
        val logs = repo.getLogsForSync(30)
        val profile = repo.getBabyProfileDirect()
        val batch = SyncBatchPayload(
            batchId = UUID.randomUUID().toString(),
            logs = logs.map { it.toDto() },
            growth = repo.getGrowthForSync().map { it.toDto() },
            medical = repo.getMedicalForSync().map { it.toDto() },
            milk = repo.getMilkForSync().map { it.toDto() },
            milestones = repo.getMilestonesForSync().map { it.toDto() },
            profile = profile?.toDto(),
            duty = repo.getDutyForSync().map { it.toDto() },
            memories = repo.getMemoriesForSync().map { it.toDto() },
            notes = repo.getNotesForSync().map { it.toDto() },
            lists = repo.getListsForSync().map { it.toDto() },
            listItems = repo.getListItemsForSync().map { it.toDto() }
        )
        sendTo(endpointId, CareMessageTypes.SYNC_BATCH, syncBatchAdapter.toJson(batch))
        // Offer media files for memories that have local content
        for (memory in repo.getMemoriesForSync()) {
            if (!memory.isDeleted && memory.localPath.isNotBlank() && File(memory.localPath).exists()) {
                sendMemoryFileToEndpoint(endpointId, memory)
            }
        }
    }

    private fun handleEnvelope(endpointId: String, raw: String) {
        try {
            val envelope = envelopeAdapter.fromJson(raw) ?: return
            when (envelope.type) {
                CareMessageTypes.HELLO -> handleHello(endpointId, envelope.payloadJson)
                CareMessageTypes.HELLO_ACK -> handleHelloAck(endpointId, envelope.payloadJson)
                CareMessageTypes.CHAT -> handleChat(envelope.payloadJson)
                CareMessageTypes.PING -> handlePing(envelope.payloadJson)
                CareMessageTypes.CHAT_ACK -> handleChatAck(envelope.payloadJson)
                CareMessageTypes.CHAT_READ -> handleChatRead(envelope.payloadJson)
                CareMessageTypes.SYNC_OFFER -> {
                    scope.launch { pushFullSyncBatch(endpointId) }
                }
                CareMessageTypes.SYNC_BATCH -> handleSyncBatch(endpointId, envelope.payloadJson)
                CareMessageTypes.SYNC_ACK -> { /* optional */ }
                CareMessageTypes.LOG_UPSERT, CareMessageTypes.LOG_DELETE ->
                    handleLogUpsert(envelope.payloadJson)
                CareMessageTypes.GROWTH_UPSERT, CareMessageTypes.GROWTH_DELETE ->
                    handleGrowthUpsert(envelope.payloadJson)
                CareMessageTypes.MEDICAL_UPSERT, CareMessageTypes.MEDICAL_DELETE ->
                    handleMedicalUpsert(envelope.payloadJson)
                CareMessageTypes.MILK_UPSERT, CareMessageTypes.MILK_DELETE ->
                    handleMilkUpsert(envelope.payloadJson)
                CareMessageTypes.MILESTONE_UPSERT, CareMessageTypes.MILESTONE_DELETE ->
                    handleMilestoneUpsert(envelope.payloadJson)
                CareMessageTypes.PROFILE_UPSERT ->
                    handleProfileUpsert(envelope.payloadJson)
                CareMessageTypes.DUTY_CLAIM, CareMessageTypes.DUTY_RELEASE ->
                    handleDuty(envelope.payloadJson)
                CareMessageTypes.MEMORY_UPSERT, CareMessageTypes.MEMORY_DELETE ->
                    handleMemoryUpsert(endpointId, envelope.payloadJson)
                CareMessageTypes.MEMORY_FILE_OFFER ->
                    handleMemoryFileOffer(endpointId, envelope.payloadJson)
                CareMessageTypes.MEMORY_FILE_REQUEST ->
                    handleMemoryFileRequest(endpointId, envelope.payloadJson)
                CareMessageTypes.NOTE_UPSERT, CareMessageTypes.NOTE_DELETE ->
                    handleNoteUpsert(envelope.payloadJson)
                CareMessageTypes.LIST_UPSERT, CareMessageTypes.LIST_DELETE ->
                    handleListUpsert(envelope.payloadJson)
                CareMessageTypes.LIST_ITEM_UPSERT, CareMessageTypes.LIST_ITEM_DELETE ->
                    handleListItemUpsert(envelope.payloadJson)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse payload", e)
        }
    }

    private fun handleHello(endpointId: String, payloadJson: String) {
        val hello = helloAdapter.fromJson(payloadJson) ?: return
        val ok = hello.pin == _passcode.value
        val ack = HelloAckPayload(
            ok = ok,
            caregiverName = myCaregiverName,
            reason = if (ok) "" else "Incorrect Family PIN"
        )
        sendTo(endpointId, CareMessageTypes.HELLO_ACK, helloAckAdapter.toJson(ack))
        if (ok) {
            markAuthenticated(endpointId, hello.caregiverName)
        } else {
            connectionsClient?.disconnectFromEndpoint(endpointId)
            pendingAuth.remove(endpointId)
            _statusText.value = "Rejected ${hello.caregiverName}: wrong Family PIN"
            refreshConnectionState()
        }
    }

    private fun handleHelloAck(endpointId: String, payloadJson: String) {
        val ack = helloAckAdapter.fromJson(payloadJson) ?: return
        pendingAuth.remove(endpointId)
        if (ack.ok) {
            markAuthenticated(endpointId, ack.caregiverName.ifBlank { "Caregiver" })
        } else {
            connectionsClient?.disconnectFromEndpoint(endpointId)
            _statusText.value = "PIN rejected by peer. Use the same Family PIN on both phones."
            refreshConnectionState()
        }
    }

    private fun markAuthenticated(endpointId: String, peerName: String) {
        connectedEndpoints[endpointId] = peerName
        pendingAuth.remove(endpointId)
        discoveredEndpoints.remove(endpointId)
        publishDiscovered()
        _connectionState.value = BluetoothConnectionState.CONNECTED
        _connectedDeviceName.value = peerName
        _statusText.value = "Connected to $peerName · syncing"
        _lastSyncedAt.value = System.currentTimeMillis()
        appContext?.let { ctx ->
            CareSyncForegroundService.start(ctx, peerName)
            CareSyncPrefs.addRememberedDevice(ctx, peerName)
        }
        // Stop active discovery scanning when connected to preserve battery
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        connectionsClient?.stopDiscovery()
        _isScanning.value = false

        sendSyncOffer(endpointId)
        updateStatusSummary()
    }

    private fun handleChat(payloadJson: String) {
        val chat = chatAdapter.fromJson(payloadJson) ?: return
        val msg = BluetoothCareMessage(
            id = chat.syncId,
            senderName = chat.senderName,
            text = chat.text,
            timestampMillis = chat.timestampMillis,
            isPing = false,
            isFromMe = false,
            deliveryStatus = if (chatScreenVisible.get()) {
                MessageDeliveryStatus.READ
            } else {
                MessageDeliveryStatus.DELIVERED
            }
        )
        appendMessage(msg)
        persistChat(msg)
        sendChatAck(chat.syncId)
        if (chatScreenVisible.get()) {
            sendChatRead(listOf(chat.syncId))
        } else {
            maybeNotifyIncoming(msg)
        }
        if (_vibrateOnReceive.value) {
            appContext?.let { vibrateDevice(it, longArrayOf(0, 180, 80, 180)) }
        }
        scope.launch { refreshUnreadCount() }
    }

    private fun handlePing(payloadJson: String) {
        val ping = pingAdapter.fromJson(payloadJson) ?: return
        val preset = PingPresets.find { it.id == ping.presetId } ?: PingPresets[0]
        val msg = BluetoothCareMessage(
            id = ping.syncId,
            senderName = ping.senderName,
            text = ping.text,
            timestampMillis = ping.timestampMillis,
            isPing = true,
            pingIcon = ping.pingIcon ?: preset.icon,
            isFromMe = false,
            deliveryStatus = if (chatScreenVisible.get()) {
                MessageDeliveryStatus.READ
            } else {
                MessageDeliveryStatus.DELIVERED
            }
        )
        _lastPingReceived.value = msg
        appendMessage(msg)
        persistChat(msg)
        sendChatAck(ping.syncId)

        val shouldMute = shouldMuteNonUrgentPing(ping.presetId)
        if (chatScreenVisible.get()) {
            sendChatRead(listOf(ping.syncId))
        } else if (!shouldMute) {
            maybeNotifyIncoming(msg)
        }
        if (!shouldMute && _vibrateOnReceive.value) {
            appContext?.let { vibrateDevice(it, preset.vibratePattern) }
        }
        scope.launch { refreshUnreadCount() }
    }

    private fun handleChatAck(payloadJson: String) {
        val ack = chatAckAdapter.fromJson(payloadJson) ?: return
        updateLocalDeliveryStatus(ack.syncId, MessageDeliveryStatus.DELIVERED)
    }

    private fun handleChatRead(payloadJson: String) {
        val read = chatReadAdapter.fromJson(payloadJson) ?: return
        for (syncId in read.syncIds) {
            updateLocalDeliveryStatus(syncId, MessageDeliveryStatus.READ)
        }
    }

    private fun sendChatAck(syncId: String) {
        if (connectedEndpoints.isEmpty()) return
        broadcast(
            CareMessageTypes.CHAT_ACK,
            chatAckAdapter.toJson(ChatAckPayload(syncId))
        )
    }

    private fun sendChatRead(syncIds: List<String>) {
        if (syncIds.isEmpty() || connectedEndpoints.isEmpty()) return
        broadcast(
            CareMessageTypes.CHAT_READ,
            chatReadAdapter.toJson(ChatReadPayload(syncIds))
        )
    }

    private fun maybeNotifyIncoming(msg: BluetoothCareMessage) {
        if (chatScreenVisible.get()) return
        val ctx = appContext ?: return
        val body = if (msg.isPing) {
            "${msg.pingIcon ?: "📳"} ${msg.text}"
        } else {
            msg.text
        }
        BabyNotificationManager.showPeerMessageNotification(
            context = ctx,
            senderName = msg.senderName,
            text = body,
            timestampMillis = msg.timestampMillis
        )
    }

    private fun shouldMuteNonUrgentPing(presetId: String): Boolean {
        if (presetId == "URGENT") return false
        if (!_muteNonUrgentWhenOffDuty.value) return false
        val onDuty = cachedActiveDutyName
        if (onDuty.isNullOrBlank()) return true
        return !onDuty.equals(myCaregiverName, ignoreCase = true)
    }

    private fun handleSyncBatch(endpointId: String, payloadJson: String) {
        scope.launch {
            val batch = syncBatchAdapter.fromJson(payloadJson) ?: return@launch
            val repo = repository ?: return@launch
            var applied = 0
            for (dto in batch.logs) {
                if (repo.upsertSyncedLog(dto.toEntity())) applied++
            }
            for (dto in batch.growth) {
                if (repo.upsertSyncedGrowth(dto.toEntity())) applied++
            }
            for (dto in batch.medical) {
                if (repo.upsertSyncedMedical(dto.toEntity())) applied++
            }
            for (dto in batch.milk) {
                if (repo.upsertSyncedMilk(dto.toEntity())) applied++
            }
            for (dto in batch.milestones) {
                if (repo.upsertSyncedMilestone(dto.toEntity())) applied++
            }
            batch.profile?.let {
                if (repo.upsertSyncedProfile(it.toEntity())) applied++
            }
            for (dto in batch.duty) {
                if (repo.upsertSyncedDuty(dto.toEntity())) applied++
            }
            for (dto in batch.memories) {
                if (repo.upsertSyncedMemory(dto.toEntity())) {
                    applied++
                    maybeRequestMemoryFile(endpointId, dto)
                }
            }
            for (dto in batch.notes) {
                if (repo.upsertSyncedNote(dto.toEntity())) applied++
            }
            for (dto in batch.lists) {
                if (repo.upsertSyncedList(dto.toEntity())) applied++
            }
            for (dto in batch.listItems) {
                if (repo.upsertSyncedListItem(dto.toEntity())) applied++
            }
            cachedActiveDutyName = repo.getActiveDutyDirect()?.takeIf { it.isActive }?.caregiverName
            if (applied > 0) {
                _lastSyncedAt.value = System.currentTimeMillis()
                appContext?.let { BabyCareWidgetProvider.updateAllWidgets(it) }
            }
            val ack = SyncAckPayload(batchId = batch.batchId, appliedCount = applied)
            sendTo(endpointId, CareMessageTypes.SYNC_ACK, syncAckAdapter.toJson(ack))
            updateStatusSummary()
            Log.d(TAG, "Applied $applied items from peer batch")
        }
    }

    private fun handleLogUpsert(payloadJson: String) {
        scope.launch {
            val dto = logDtoAdapter.fromJson(payloadJson) ?: return@launch
            val repo = repository ?: return@launch
            if (repo.upsertSyncedLog(dto.toEntity())) {
                onRemoteDataApplied()
            }
        }
    }

    private fun handleGrowthUpsert(payloadJson: String) {
        scope.launch {
            val dto = growthDtoAdapter.fromJson(payloadJson) ?: return@launch
            if (repository?.upsertSyncedGrowth(dto.toEntity()) == true) onRemoteDataApplied()
        }
    }

    private fun handleMedicalUpsert(payloadJson: String) {
        scope.launch {
            val dto = medicalDtoAdapter.fromJson(payloadJson) ?: return@launch
            if (repository?.upsertSyncedMedical(dto.toEntity()) == true) onRemoteDataApplied()
        }
    }

    private fun handleMilkUpsert(payloadJson: String) {
        scope.launch {
            val dto = milkDtoAdapter.fromJson(payloadJson) ?: return@launch
            if (repository?.upsertSyncedMilk(dto.toEntity()) == true) onRemoteDataApplied()
        }
    }

    private fun handleMilestoneUpsert(payloadJson: String) {
        scope.launch {
            val dto = milestoneDtoAdapter.fromJson(payloadJson) ?: return@launch
            if (repository?.upsertSyncedMilestone(dto.toEntity()) == true) onRemoteDataApplied()
        }
    }

    private fun handleProfileUpsert(payloadJson: String) {
        scope.launch {
            val dto = profileDtoAdapter.fromJson(payloadJson) ?: return@launch
            if (repository?.upsertSyncedProfile(dto.toEntity()) == true) onRemoteDataApplied()
        }
    }

    private fun handleDuty(payloadJson: String) {
        scope.launch {
            val dto = dutyDtoAdapter.fromJson(payloadJson) ?: return@launch
            val entity = dto.toEntity()
            if (repository?.upsertSyncedDuty(entity) == true) {
                cachedActiveDutyName = repository?.getActiveDutyDirect()?.takeIf { it.isActive }?.caregiverName
                onRemoteDataApplied()
            }
        }
    }

    private fun handleMemoryUpsert(endpointId: String, payloadJson: String) {
        scope.launch {
            val dto = memoryDtoAdapter.fromJson(payloadJson) ?: return@launch
            if (repository?.upsertSyncedMemory(dto.toEntity()) == true) {
                onRemoteDataApplied()
                maybeRequestMemoryFile(endpointId, dto)
            }
        }
    }

    private fun handleMemoryFileOffer(endpointId: String, payloadJson: String) {
        val offer = memoryFileOfferAdapter.fromJson(payloadJson) ?: return
        expectedFileByEndpoint[endpointId] = offer.syncId
    }

    private fun handleMemoryFileRequest(endpointId: String, payloadJson: String) {
        scope.launch {
            val req = memoryFileRequestAdapter.fromJson(payloadJson) ?: return@launch
            val memory = repository?.getMemoryBySyncId(req.syncId) ?: return@launch
            if (memory.localPath.isNotBlank() && File(memory.localPath).exists()) {
                sendMemoryFileToEndpoint(endpointId, memory)
            }
        }
    }

    private fun handleNoteUpsert(payloadJson: String) {
        scope.launch {
            val dto = noteDtoAdapter.fromJson(payloadJson) ?: return@launch
            if (repository?.upsertSyncedNote(dto.toEntity()) == true) onRemoteDataApplied()
        }
    }

    private fun handleListUpsert(payloadJson: String) {
        scope.launch {
            val dto = listDtoAdapter.fromJson(payloadJson) ?: return@launch
            if (repository?.upsertSyncedList(dto.toEntity()) == true) onRemoteDataApplied()
        }
    }

    private fun handleListItemUpsert(payloadJson: String) {
        scope.launch {
            val dto = listItemDtoAdapter.fromJson(payloadJson) ?: return@launch
            if (repository?.upsertSyncedListItem(dto.toEntity()) == true) onRemoteDataApplied()
        }
    }

    private suspend fun maybeRequestMemoryFile(endpointId: String, dto: MemoryDto) {
        if (dto.isDeleted || dto.contentHash.isBlank()) return
        val existing = repository?.getMemoryBySyncId(dto.syncId) ?: return
        val hasFile = existing.localPath.isNotBlank() && File(existing.localPath).exists()
        if (hasFile && existing.contentHash == dto.contentHash) return
        val req = MemoryFileRequestPayload(syncId = dto.syncId, contentHash = dto.contentHash)
        sendTo(endpointId, CareMessageTypes.MEMORY_FILE_REQUEST, memoryFileRequestAdapter.toJson(req))
    }

    private fun sendMemoryFileToPeers(item: MemoryItem) {
        connectedEndpoints.keys.forEach { sendMemoryFileToEndpoint(it, item) }
    }

    private fun sendMemoryFileToEndpoint(endpointId: String, item: MemoryItem) {
        val file = File(item.localPath)
        if (!file.exists()) return
        val offer = MemoryFileOfferPayload(
            syncId = item.syncId,
            contentHash = item.contentHash,
            mimeType = item.mimeType,
            fileSizeBytes = item.fileSizeBytes
        )
        sendTo(endpointId, CareMessageTypes.MEMORY_FILE_OFFER, memoryFileOfferAdapter.toJson(offer))
        try {
            val payload = Payload.fromFile(file)
            connectionsClient?.sendPayload(endpointId, payload)
                ?.addOnFailureListener { e -> Log.e(TAG, "Memory file send failed", e) }
        } catch (e: Exception) {
            Log.e(TAG, "sendMemoryFileToEndpoint failed", e)
        }
    }

    private suspend fun finalizeIncomingMemoryFile(payload: Payload?, syncId: String) {
        val ctx = appContext ?: return
        val repo = repository ?: return
        val dest = File(ctx.cacheDir, "incoming_$syncId.tmp")
        try {
            val parcelFile = payload?.asFile() ?: return
            val existing = repo.getMemoryBySyncId(syncId)
            FileInputStream(parcelFile.asParcelFileDescriptor().fileDescriptor).use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            if (dest.length() == 0L) {
                Log.e(TAG, "Incoming memory file empty for $syncId")
                return
            }

            val hash = MediaCompressor.sha256File(dest)
            if (!existing?.contentHash.isNullOrBlank() && hash != existing.contentHash) {
                Log.e(TAG, "Memory file hash mismatch for $syncId")
                return
            }

            val ext = when {
                existing?.mimeType?.contains("video") == true -> "mp4"
                else -> "jpg"
            }
            if (ext != "mp4" && !MediaCompressor.isValidImageFile(dest)) {
                Log.e(TAG, "Incoming memory file is not a decodable image for $syncId")
                return
            }

            val finalFile = File(MediaCompressor.memoriesDir(ctx), "$syncId.$ext")
            dest.copyTo(finalFile, overwrite = true)
            dest.delete()

            val thumb = if (ext == "mp4") {
                ""
            } else {
                MediaCompressor.createThumbFromExisting(ctx, finalFile.absolutePath, syncId)
            }
            repo.attachMemoryFile(
                syncId = syncId,
                localPath = finalFile.absolutePath,
                thumbPath = thumb,
                fileSizeBytes = finalFile.length(),
                contentHash = hash
            )
            onRemoteDataApplied()
            Log.d(TAG, "Saved memory file for $syncId (${finalFile.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "finalizeIncomingMemoryFile failed", e)
        } finally {
            dest.delete()
        }
    }

    private fun onRemoteDataApplied() {
        _lastSyncedAt.value = System.currentTimeMillis()
        appContext?.let { BabyCareWidgetProvider.updateAllWidgets(it) }
        updateStatusSummary()
    }

    private fun broadcast(type: String, payloadJson: String) {
        connectedEndpoints.keys.forEach { id -> sendTo(id, type, payloadJson) }
    }

    private fun sendTo(endpointId: String, type: String, payloadJson: String) {
        val envelope = CareEnvelope(v = 1, type = type, payloadJson = payloadJson)
        val bytes = envelopeAdapter.toJson(envelope).toByteArray(Charsets.UTF_8)
        connectionsClient?.sendPayload(endpointId, Payload.fromBytes(bytes))
            ?.addOnFailureListener { e -> Log.e(TAG, "sendPayload failed", e) }
    }

    private fun refreshConnectionState() {
        if (connectedEndpoints.isNotEmpty()) {
            _connectionState.value = BluetoothConnectionState.CONNECTED
            _connectedDeviceName.value = connectedEndpoints.values.firstOrNull()
        } else if (_careSyncEnabled.value) {
            _connectionState.value = BluetoothConnectionState.HOSTING_SERVER
            _connectedDeviceName.value = null
        } else {
            _connectionState.value = BluetoothConnectionState.DISCONNECTED
            _connectedDeviceName.value = null
        }
        updateStatusSummary()
    }

    private fun updateStatusSummary() {
        val pending = _outboxPendingCount.value
        val pendingSuffix = if (pending > 0) " · $pending queued" else ""
        _statusText.value = when {
            !_careSyncEnabled.value -> "Care Sync off"
            connectedEndpoints.isNotEmpty() -> {
                val peers = connectedEndpoints.values.joinToString(", ")
                val synced = if (_lastSyncedAt.value > 0) " · synced" else ""
                "Connected to $peers$synced$pendingSuffix"
            }
            _isScanning.value ->
                "Searching for caregivers...$pendingSuffix"
            _connectionState.value == BluetoothConnectionState.HOSTING_SERVER ->
                "Care Sync Active • Tap Rescan to find devices$pendingSuffix"
            else -> "Care Sync on$pendingSuffix"
        }
    }

    private fun publishDiscovered() {
        val list = CareSyncDeviceLists.excludeConnectedFromDiscovered(
            discoveredEndpoints.values,
            connectedEndpoints.keys.toSet()
        )
        _discoveredDevices.value = list
        _pairedDevices.value = list.map { it.name to it.address }
    }

    private fun appendMessage(msg: BluetoothCareMessage) {
        if (_messages.value.any { it.id == msg.id }) return
        _messages.value = _messages.value + msg
    }

    private fun updateLocalDeliveryStatus(syncId: String, status: MessageDeliveryStatus) {
        val current = _messages.value
        val index = current.indexOfFirst { it.id == syncId }
        val statusToPersist = if (index >= 0) {
            val existing = current[index]
            val resolved = ChatDeliveryRules.resolveStatus(existing.deliveryStatus, status)
            if (resolved != existing.deliveryStatus) {
                _messages.value = current.toMutableList().also {
                    it[index] = existing.copy(deliveryStatus = resolved)
                }
            }
            resolved
        } else {
            status
        }
        scope.launch {
            repository?.updatePeerChatDeliveryStatus(syncId, statusToPersist.name)
        }
    }

    private fun persistChat(msg: BluetoothCareMessage) {
        scope.launch {
            repository?.insertPeerChatMessage(
                PeerChatMessage(
                    syncId = msg.id,
                    senderName = msg.senderName,
                    text = msg.text,
                    timestampMillis = msg.timestampMillis,
                    isPing = msg.isPing,
                    pingIcon = msg.pingIcon,
                    isFromMe = msg.isFromMe,
                    deliveryStatus = msg.deliveryStatus.name
                )
            )
        }
    }

    private suspend fun refreshUnreadCount() {
        val count = repository?.getUnreadIncomingMessages()?.size ?: 0
        _unreadIncomingCount.value = count
    }

    private suspend fun loadChatHistory() {
        val repo = repository ?: return
        val history = repo.getPeerChatMessages()
        if (history.isNotEmpty()) {
            _messages.value = history.map {
                BluetoothCareMessage(
                    id = it.syncId,
                    senderName = it.senderName,
                    text = it.text,
                    timestampMillis = it.timestampMillis,
                    isPing = it.isPing,
                    pingIcon = it.pingIcon,
                    isFromMe = it.isFromMe,
                    deliveryStatus = runCatching {
                        MessageDeliveryStatus.valueOf(it.deliveryStatus)
                    }.getOrDefault(MessageDeliveryStatus.PENDING)
                )
            }
        }
    }

    // ---- DTO mappers ----

    private fun ActivityLog.toDto() = ActivityLogDto(
        syncId = syncId,
        babyId = babyId,
        activityType = activityType,
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
        durationSeconds = durationSeconds,
        volumeMl = volumeMl,
        milkType = milkType,
        leftBreastDurationSec = leftBreastDurationSec,
        rightBreastDurationSec = rightBreastDurationSec,
        diaperStatus = diaperStatus,
        medicineName = medicineName,
        dosage = dosage,
        temperatureCelsius = temperatureCelsius,
        notes = notes,
        caregiverName = caregiverName,
        caregiverRole = caregiverRole,
        timestampMillis = timestampMillis,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted,
        isSystemIntelligent = isSystemIntelligent
    )

    private fun ActivityLogDto.toEntity() = ActivityLog(
        id = 0,
        babyId = babyId,
        activityType = activityType,
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
        durationSeconds = durationSeconds,
        volumeMl = volumeMl,
        milkType = milkType,
        leftBreastDurationSec = leftBreastDurationSec,
        rightBreastDurationSec = rightBreastDurationSec,
        diaperStatus = diaperStatus,
        medicineName = medicineName,
        dosage = dosage,
        temperatureCelsius = temperatureCelsius,
        notes = notes,
        caregiverName = caregiverName,
        caregiverRole = caregiverRole,
        timestampMillis = timestampMillis,
        syncId = syncId,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted,
        isSystemIntelligent = isSystemIntelligent
    )

    private fun GrowthRecord.toDto() = GrowthDto(
        syncId = syncId,
        babyId = babyId,
        dateMillis = dateMillis,
        weightKg = weightKg,
        heightCm = heightCm,
        headCircumferenceCm = headCircumferenceCm,
        notes = notes,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted
    )

    private fun GrowthDto.toEntity() = GrowthRecord(
        syncId = syncId,
        babyId = babyId,
        dateMillis = dateMillis,
        weightKg = weightKg,
        heightCm = heightCm,
        headCircumferenceCm = headCircumferenceCm,
        notes = notes,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted
    )

    private fun MedicalRecord.toDto() = MedicalDto(
        syncId = syncId,
        babyId = babyId,
        dateMillis = dateMillis,
        recordType = recordType,
        title = title,
        details = details,
        isCompleted = isCompleted,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted
    )

    private fun MedicalDto.toEntity() = MedicalRecord(
        syncId = syncId,
        babyId = babyId,
        dateMillis = dateMillis,
        recordType = recordType,
        title = title,
        details = details,
        isCompleted = isCompleted,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted
    )

    private fun MilkStashItem.toDto() = MilkStashDto(
        syncId = syncId,
        babyId = babyId,
        volumeMl = volumeMl,
        location = location,
        pumpedDateMillis = pumpedDateMillis,
        expirationDateMillis = expirationDateMillis,
        isUsed = isUsed,
        notes = notes,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted
    )

    private fun MilkStashDto.toEntity() = MilkStashItem(
        syncId = syncId,
        babyId = babyId,
        volumeMl = volumeMl,
        location = location,
        pumpedDateMillis = pumpedDateMillis,
        expirationDateMillis = expirationDateMillis,
        isUsed = isUsed,
        notes = notes,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted
    )

    private fun MilestoneRecord.toDto() = MilestoneDto(
        syncId = syncId,
        babyId = babyId,
        category = category,
        title = title,
        description = description,
        achievedDateMillis = achievedDateMillis,
        isAchieved = isAchieved,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted
    )

    private fun MilestoneDto.toEntity() = MilestoneRecord(
        syncId = syncId,
        babyId = babyId,
        category = category,
        title = title,
        description = description,
        achievedDateMillis = achievedDateMillis,
        isAchieved = isAchieved,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted
    )

    private fun BabyProfile.toDto() = BabyProfileDto(
        name = name,
        birthDateMillis = birthDateMillis,
        birthTimeFormatted = birthTimeFormatted,
        initialWeightKg = initialWeightKg,
        initialHeightCm = initialHeightCm,
        gender = gender,
        targetFeedingIntervalMinutes = targetFeedingIntervalMinutes,
        targetNapIntervalMinutes = targetNapIntervalMinutes,
        primaryCaregiverName = primaryCaregiverName,
        primaryCaregiverRole = primaryCaregiverRole,
        isInitialSetupDone = isInitialSetupDone,
        updatedAtMillis = updatedAtMillis
    )

    private fun BabyProfileDto.toEntity() = BabyProfile(
        id = 1,
        name = name,
        birthDateMillis = birthDateMillis,
        birthTimeFormatted = birthTimeFormatted,
        initialWeightKg = initialWeightKg,
        initialHeightCm = initialHeightCm,
        gender = gender,
        photoUri = null,
        targetFeedingIntervalMinutes = targetFeedingIntervalMinutes,
        targetNapIntervalMinutes = targetNapIntervalMinutes,
        primaryCaregiverName = primaryCaregiverName,
        primaryCaregiverRole = primaryCaregiverRole,
        isInitialSetupDone = isInitialSetupDone,
        updatedAtMillis = updatedAtMillis
    )

    private fun DutySession.toDto() = DutyDto(
        syncId = syncId,
        caregiverName = caregiverName,
        caregiverRole = caregiverRole,
        startedAtMillis = startedAtMillis,
        untilMillis = untilMillis,
        isActive = isActive,
        updatedAtMillis = updatedAtMillis,
        deviceId = deviceId
    )

    private fun DutyDto.toEntity() = DutySession(
        syncId = syncId,
        caregiverName = caregiverName,
        caregiverRole = caregiverRole,
        startedAtMillis = startedAtMillis,
        untilMillis = untilMillis,
        isActive = isActive,
        updatedAtMillis = updatedAtMillis,
        deviceId = deviceId
    )

    private fun MemoryItem.toDto() = MemoryDto(
        syncId = syncId,
        babyId = babyId,
        mediaType = mediaType,
        capturedAtMillis = capturedAtMillis,
        caption = caption,
        caregiverName = caregiverName,
        contentHash = contentHash,
        fileSizeBytes = fileSizeBytes,
        mimeType = mimeType,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted
    )

    private fun MemoryDto.toEntity() = MemoryItem(
        syncId = syncId,
        babyId = babyId,
        mediaType = mediaType,
        localPath = "",
        thumbPath = "",
        capturedAtMillis = capturedAtMillis,
        caption = caption,
        caregiverName = caregiverName,
        contentHash = contentHash,
        fileSizeBytes = fileSizeBytes,
        mimeType = mimeType,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted
    )

    private fun SharedNote.toDto() = NoteDto(
        syncId = syncId,
        babyId = babyId,
        title = title,
        body = body,
        pinnedDateMillis = pinnedDateMillis,
        caregiverName = caregiverName,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted
    )

    private fun NoteDto.toEntity() = SharedNote(
        syncId = syncId,
        babyId = babyId,
        title = title,
        body = body,
        pinnedDateMillis = pinnedDateMillis,
        caregiverName = caregiverName,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted
    )

    private fun SharedList.toDto() = ListDto(
        syncId = syncId,
        babyId = babyId,
        title = title,
        caregiverName = caregiverName,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted
    )

    private fun ListDto.toEntity() = SharedList(
        syncId = syncId,
        babyId = babyId,
        title = title,
        caregiverName = caregiverName,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted
    )

    private fun SharedListItem.toDto() = ListItemDto(
        syncId = syncId,
        listSyncId = listSyncId,
        text = text,
        isChecked = isChecked,
        sortOrder = sortOrder,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted
    )

    private fun ListItemDto.toEntity() = SharedListItem(
        syncId = syncId,
        listSyncId = listSyncId,
        text = text,
        isChecked = isChecked,
        sortOrder = sortOrder,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted
    )
}
