package com.example.engine

/**
 * Pure helpers for Care Sync device-list categorization.
 * Keeps Connected / Discovered / Forgotten buckets consistent across engine + UI.
 */
object CareSyncDeviceLists {

    fun discoveredDevicesForUi(
        discovered: List<DiscoveredBluetoothDevice>,
        forgottenNames: Set<String>,
        connectedDeviceName: String?
    ): List<DiscoveredBluetoothDevice> =
        discovered.filter { device ->
            device.name !in forgottenNames &&
                (connectedDeviceName == null || device.name != connectedDeviceName)
        }

    fun nearbyForgottenDevices(
        forgottenNames: Set<String>,
        discovered: List<DiscoveredBluetoothDevice>
    ): List<String> =
        forgottenNames
            .filter { name -> discovered.any { it.name == name } }
            .sorted()

    fun excludeConnectedFromDiscovered(
        discovered: Collection<DiscoveredBluetoothDevice>,
        connectedEndpointIds: Set<String>
    ): List<DiscoveredBluetoothDevice> =
        discovered.filter { it.address !in connectedEndpointIds }

    fun shouldAutoConnect(
        endpointName: String,
        forgottenNames: Set<String>,
        rememberedNames: Set<String>,
        localEndpointName: String,
        localDeviceId: String,
        endpointId: String,
        alreadyConnectedOrPending: Boolean
    ): Boolean {
        if (alreadyConnectedOrPending) return false
        if (endpointName in forgottenNames) return false
        val isRemembered = endpointName in rememberedNames
        return isRemembered || when {
            localEndpointName < endpointName -> true
            localEndpointName > endpointName -> false
            else -> localDeviceId < endpointId
        }
    }

    fun clearDiscoveredExceptConnected(
        discovered: MutableMap<String, DiscoveredBluetoothDevice>,
        connectedEndpointIds: Set<String>
    ) {
        val staleKeys = discovered.keys.filter { it !in connectedEndpointIds }
        staleKeys.forEach { discovered.remove(it) }
    }

    fun removeDiscoveredByName(
        discovered: MutableMap<String, DiscoveredBluetoothDevice>,
        deviceName: String
    ): Boolean {
        val matching = discovered.entries.filter { it.value.name == deviceName }.map { it.key }
        matching.forEach { discovered.remove(it) }
        return matching.isNotEmpty()
    }
}
