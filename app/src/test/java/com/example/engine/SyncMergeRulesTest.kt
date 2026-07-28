package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeRulesTest {

    @Test
    fun newerTimestampWins() {
        assertTrue(SyncMergeRules.shouldReplace(100, false, 200, false))
        assertFalse(SyncMergeRules.shouldReplace(200, false, 100, false))
    }

    @Test
    fun tombstoneWinsOnTie() {
        assertTrue(SyncMergeRules.shouldReplace(100, false, 100, true))
        assertFalse(SyncMergeRules.shouldReplace(100, true, 100, false))
    }

    @Test
    fun equalNonDeletedKeepsExisting() {
        assertFalse(SyncMergeRules.shouldReplace(100, false, 100, false))
    }

    @Test
    fun growthMedicalMilkUseSameLwwRules() {
        // Soft-deleted remote growth should replace live local on equal timestamp
        assertTrue(SyncMergeRules.shouldReplace(500, false, 500, true))
        // Older medical update must not overwrite
        assertFalse(SyncMergeRules.shouldReplace(900, false, 800, false))
        // Newer milk stash update wins
        assertTrue(SyncMergeRules.shouldReplace(100, true, 200, false))
    }

    @Test
    fun dutyReleaseTreatedAsDeletedOnTie() {
        // isActive=false maps to "deleted" for merge helper
        assertTrue(SyncMergeRules.shouldReplace(100, false, 100, true))
        assertFalse(SyncMergeRules.shouldReplace(100, true, 100, false))
    }
}

/**
 * Pure ordering helper mirroring outbox drain (createdAt ascending, stable by id).
 */
object OutboxOrdering {
    data class Item(val id: Long, val createdAtMillis: Long, val dedupeKey: String)

    fun drainOrder(items: List<Item>): List<Item> =
        items.sortedWith(compareBy({ it.createdAtMillis }, { it.id }))

    /** Later write with same dedupeKey replaces earlier pending send. */
    fun applyDedupe(pending: List<Item>, incoming: Item): List<Item> =
        pending.filterNot { it.dedupeKey == incoming.dedupeKey } + incoming
}

class OutboxOrderingTest {

    @Test
    fun drainsInCreatedAtOrder() {
        val items = listOf(
            OutboxOrdering.Item(3, 300, "LOG:c"),
            OutboxOrdering.Item(1, 100, "LOG:a"),
            OutboxOrdering.Item(2, 200, "CHAT:b")
        )
        val ordered = OutboxOrdering.drainOrder(items)
        assertEquals(listOf(100L, 200L, 300L), ordered.map { it.createdAtMillis })
    }

    @Test
    fun dedupeKeepsLatestPayloadForSameKey() {
        val first = OutboxOrdering.Item(1, 100, "LOG_UPSERT:abc")
        val second = OutboxOrdering.Item(2, 200, "LOG_UPSERT:abc")
        val result = OutboxOrdering.applyDedupe(listOf(first), second)
        assertEquals(1, result.size)
        assertEquals(2L, result.single().id)
        assertEquals(200L, result.single().createdAtMillis)
    }

    @Test
    fun differentKeysStayQueuedInOrder() {
        val a = OutboxOrdering.Item(1, 100, "PING:1")
        val b = OutboxOrdering.Item(2, 150, "CHAT:2")
        val merged = OutboxOrdering.applyDedupe(listOf(a), b)
        val ordered = OutboxOrdering.drainOrder(merged)
        assertEquals(listOf("PING:1", "CHAT:2"), ordered.map { it.dedupeKey })
    }
}

class MilestoneSyncIdTest {

    @Test
    fun seededSyncIdsAreDeterministicAcrossPeers() {
        val a = com.example.data.model.MilestoneRecord.seededSyncId("Motor", "Social Smile")
        val b = com.example.data.model.MilestoneRecord.seededSyncId("Motor", "Social Smile")
        assertEquals(a, b)
        assertTrue(a.startsWith("ms:"))
    }
}
