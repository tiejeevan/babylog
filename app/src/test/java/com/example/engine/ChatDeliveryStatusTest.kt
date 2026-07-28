package com.example.engine

import com.example.data.model.MessageDeliveryStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatDeliveryStatusTest {

    @Test
    fun sendStartsPending_ackBecomesDelivered() {
        val afterSend = MessageDeliveryStatus.PENDING
        val afterAck = ChatDeliveryRules.applyAck(afterSend)
        assertEquals(MessageDeliveryStatus.DELIVERED, afterAck)
    }

    @Test
    fun deliveredThenReadBecomesRead() {
        val afterAck = MessageDeliveryStatus.DELIVERED
        val afterRead = ChatDeliveryRules.applyRead(afterAck)
        assertEquals(MessageDeliveryStatus.READ, afterRead)
    }

    @Test
    fun pendingCanJumpToRead() {
        assertEquals(
            MessageDeliveryStatus.READ,
            ChatDeliveryRules.applyRead(MessageDeliveryStatus.PENDING)
        )
    }

    @Test
    fun neverDowngradesFromReadToDelivered() {
        assertEquals(
            MessageDeliveryStatus.READ,
            ChatDeliveryRules.resolveStatus(
                MessageDeliveryStatus.READ,
                MessageDeliveryStatus.DELIVERED
            )
        )
    }

    @Test
    fun neverDowngradesFromDeliveredToPending() {
        assertEquals(
            MessageDeliveryStatus.DELIVERED,
            ChatDeliveryRules.resolveStatus(
                MessageDeliveryStatus.DELIVERED,
                MessageDeliveryStatus.PENDING
            )
        )
    }

    @Test
    fun batchReadUpdatesOnlyListedIds() {
        val before = mapOf(
            "a" to MessageDeliveryStatus.PENDING,
            "b" to MessageDeliveryStatus.DELIVERED,
            "c" to MessageDeliveryStatus.PENDING
        )
        val after = ChatDeliveryRules.applyBatchRead(before, listOf("a", "b"))
        assertEquals(MessageDeliveryStatus.READ, after["a"])
        assertEquals(MessageDeliveryStatus.READ, after["b"])
        assertEquals(MessageDeliveryStatus.PENDING, after["c"])
    }

    @Test
    fun emptyBatchReadIsNoOp() {
        val before = mapOf("a" to MessageDeliveryStatus.DELIVERED)
        assertEquals(before, ChatDeliveryRules.applyBatchRead(before, emptyList()))
    }
}
