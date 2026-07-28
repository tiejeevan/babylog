package com.example.engine

/**
 * Pure conflict rules for peer ActivityLog merge (testable without Room).
 */
object SyncMergeRules {
    /**
     * @return true if [incoming] should replace [existing]
     */
    fun shouldReplace(
        existingUpdatedAt: Long,
        existingDeleted: Boolean,
        incomingUpdatedAt: Long,
        incomingDeleted: Boolean
    ): Boolean {
        return when {
            incomingUpdatedAt > existingUpdatedAt -> true
            incomingUpdatedAt < existingUpdatedAt -> false
            incomingDeleted && !existingDeleted -> true
            else -> false
        }
    }
}
