package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CareSyncDeviceListsTest {

    private fun device(name: String, address: String) =
        DiscoveredBluetoothDevice(name = name, address = address)

    @Test
    fun discoveredDevicesForUi_excludesConnectedAndForgotten() {
        val discovered = listOf(
            device("Dad", "ep1"),
            device("Mom", "ep2"),
            device("Nanny", "ep3")
        )
        val result = CareSyncDeviceLists.discoveredDevicesForUi(
            discovered = discovered,
            forgottenNames = setOf("Nanny"),
            connectedDeviceName = "Dad"
        )
        assertEquals(listOf(device("Mom", "ep2")), result)
    }

    @Test
    fun nearbyForgottenDevices_onlyShowsNamesCurrentlyDiscovered() {
        val forgotten = setOf("OldPhone", "Mom", "Ghost")
        val discovered = listOf(
            device("Mom", "ep2"),
            device("Dad", "ep1")
        )
        val nearby = CareSyncDeviceLists.nearbyForgottenDevices(forgotten, discovered)
        assertEquals(listOf("Mom"), nearby)
    }

    @Test
    fun nearbyForgottenDevices_hidesStaleForgottenNames() {
        val forgotten = setOf("RandomDevice", "AnotherOldOne")
        val nearby = CareSyncDeviceLists.nearbyForgottenDevices(
            forgottenNames = forgotten,
            discovered = listOf(device("Dad", "ep1"))
        )
        assertTrue(nearby.isEmpty())
    }

    @Test
    fun excludeConnectedFromDiscovered_filtersByEndpointId() {
        val discovered = listOf(
            device("Dad", "ep1"),
            device("Mom", "ep2")
        )
        val result = CareSyncDeviceLists.excludeConnectedFromDiscovered(
            discovered,
            connectedEndpointIds = setOf("ep1")
        )
        assertEquals(listOf(device("Mom", "ep2")), result)
    }

    @Test
    fun shouldAutoConnect_skipsForgottenDevices() {
        val should = CareSyncDeviceLists.shouldAutoConnect(
            endpointName = "Mom",
            forgottenNames = setOf("Mom"),
            rememberedNames = emptySet(),
            localEndpointName = "Dad",
            localDeviceId = "local-a",
            endpointId = "ep-mom",
            alreadyConnectedOrPending = false
        )
        assertFalse(should)
    }

    @Test
    fun shouldAutoConnect_connectsRememberedWhenNotForgotten() {
        val should = CareSyncDeviceLists.shouldAutoConnect(
            endpointName = "Mom",
            forgottenNames = emptySet(),
            rememberedNames = setOf("Mom"),
            localEndpointName = "Zzz", // would otherwise lose tie-break
            localDeviceId = "zzzz",
            endpointId = "ep-mom",
            alreadyConnectedOrPending = false
        )
        assertTrue(should)
    }

    @Test
    fun shouldAutoConnect_skipsWhenAlreadyPending() {
        val should = CareSyncDeviceLists.shouldAutoConnect(
            endpointName = "Mom",
            forgottenNames = emptySet(),
            rememberedNames = setOf("Mom"),
            localEndpointName = "Dad",
            localDeviceId = "local-a",
            endpointId = "ep-mom",
            alreadyConnectedOrPending = true
        )
        assertFalse(should)
    }

    @Test
    fun shouldAutoConnect_usesLexicographicTieBreaker() {
        // Local "Dad" < remote "Mom" → local initiates
        assertTrue(
            CareSyncDeviceLists.shouldAutoConnect(
                endpointName = "Mom",
                forgottenNames = emptySet(),
                rememberedNames = emptySet(),
                localEndpointName = "Dad",
                localDeviceId = "b",
                endpointId = "a",
                alreadyConnectedOrPending = false
            )
        )
        // Local "Mom" > remote "Dad" → local does not initiate
        assertFalse(
            CareSyncDeviceLists.shouldAutoConnect(
                endpointName = "Dad",
                forgottenNames = emptySet(),
                rememberedNames = emptySet(),
                localEndpointName = "Mom",
                localDeviceId = "a",
                endpointId = "b",
                alreadyConnectedOrPending = false
            )
        )
    }

    @Test
    fun clearDiscoveredExceptConnected_removesStaleEntries() {
        val map = mutableMapOf(
            "ep1" to device("Dad", "ep1"),
            "ep2" to device("Mom", "ep2"),
            "ep3" to device("Nanny", "ep3")
        )
        CareSyncDeviceLists.clearDiscoveredExceptConnected(map, setOf("ep1"))
        assertEquals(setOf("ep1"), map.keys)
        assertEquals("Dad", map["ep1"]?.name)
    }

    @Test
    fun clearDiscoveredExceptConnected_clearsAllWhenNothingConnected() {
        val map = mutableMapOf(
            "ep1" to device("Dad", "ep1"),
            "ep2" to device("Mom", "ep2")
        )
        CareSyncDeviceLists.clearDiscoveredExceptConnected(map, emptySet())
        assertTrue(map.isEmpty())
    }

    @Test
    fun removeDiscoveredByName_removesMatchingEntries() {
        val map = mutableMapOf(
            "ep1" to device("Dad", "ep1"),
            "ep2" to device("Mom", "ep2"),
            "ep3" to device("Dad", "ep3")
        )
        val removed = CareSyncDeviceLists.removeDiscoveredByName(map, "Dad")
        assertTrue(removed)
        assertEquals(setOf("ep2"), map.keys)
    }
}
