package com.example.engine

import com.example.data.model.MessageDeliveryStatus

/**
 * Pure helpers for chat delivery / read receipt status transitions.
 */
object ChatDeliveryRules {

    /** Never downgrade: PENDING < DELIVERED < READ. */
    fun resolveStatus(
        current: MessageDeliveryStatus,
        incoming: MessageDeliveryStatus
    ): MessageDeliveryStatus {
        return if (incoming.ordinal >= current.ordinal) incoming else current
    }

    fun applyAck(current: MessageDeliveryStatus): MessageDeliveryStatus =
        resolveStatus(current, MessageDeliveryStatus.DELIVERED)

    fun applyRead(current: MessageDeliveryStatus): MessageDeliveryStatus =
        resolveStatus(current, MessageDeliveryStatus.READ)

    fun applyBatchRead(
        statuses: Map<String, MessageDeliveryStatus>,
        syncIds: List<String>
    ): Map<String, MessageDeliveryStatus> {
        if (syncIds.isEmpty()) return statuses
        val target = syncIds.toSet()
        return statuses.mapValues { (id, status) ->
            if (id in target) applyRead(status) else status
        }
    }
}
