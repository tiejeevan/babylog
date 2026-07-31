package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NapPlacementStateTest {

    private fun base(
        gapHours: Int = 3,
        napMinutes: Int = 60,
        intermediates: List<TimelineAnchor> = emptyList()
    ): NapPlacementState {
        val gapStart = 1_000_000L
        val gapEnd = gapStart + gapHours * 3_600_000L
        val napStart = gapStart + 60 * 60_000L
        return NapPlacementState(
            gapStartMillis = gapStart,
            gapEndMillis = gapEnd,
            napStartMillis = napStart,
            napEndMillis = napStart + napMinutes * 60_000L,
            intermediateActivities = intermediates
        )
    }

    @Test
    fun withCenteredDurationKeepsBuffers() {
        val centered = base().withCenteredDuration(60)
        assertTrue(centered.awakeBeforeMillis >= 15 * 60_000L - 1_000L)
        assertTrue(centered.awakeAfterMillis >= 15 * 60_000L - 1_000L)
        assertEquals(60, centered.durationMinutes)
    }

    @Test
    fun clampPreventsLeavingGap() {
        val over = base().copy(
            napStartMillis = 0L,
            napEndMillis = 10_000L
        ).clamp()
        assertTrue(over.napStartMillis >= over.gapStartMillis)
        assertTrue(over.napEndMillis <= over.gapEndMillis)
    }

    @Test
    fun snapAfterMovesStartToPrevEnd() {
        val prev = TimelineAnchor(
            activityType = "BOTTLE",
            title = "Bottle Feeding",
            timeMillis = 1_000_000L + 30 * 60_000L,
            endTimeMillis = 1_000_000L + 45 * 60_000L
        )
        val snapped = base().snapAfter(prev)
        assertEquals(prev.endTimeMillis, snapped.napStartMillis)
    }

    @Test
    fun snapBeforeMovesEndToNextStart() {
        val next = TimelineAnchor(
            activityType = "DIAPER",
            title = "Diaper Change",
            timeMillis = 1_000_000L + 150 * 60_000L
        )
        val snapped = base().snapBefore(next)
        assertEquals(next.timeMillis, snapped.napEndMillis)
    }

    @Test
    fun overlapDetectedAgainstIntermediate() {
        val bottle = TimelineAnchor(
            activityType = "BOTTLE",
            title = "Bottle Feeding",
            timeMillis = 1_000_000L + 70 * 60_000L,
            endTimeMillis = 1_000_000L + 85 * 60_000L
        )
        val state = base(intermediates = listOf(bottle)).withCenteredDuration(60)
        // Force overlap by placing nap over bottle
        val overlapping = state.copy(
            napStartMillis = bottle.timeMillis - 10 * 60_000L,
            napEndMillis = bottle.endTimeMillis!! + 10 * 60_000L
        )
        assertTrue(overlapping.hasOverlap)
        assertEquals("Bottle Feeding", overlapping.overlappingActivities.first().title)
    }

    @Test
    fun noOverlapWhenClearOfIntermediates() {
        val bottle = TimelineAnchor(
            activityType = "BOTTLE",
            title = "Bottle Feeding",
            timeMillis = 1_000_000L + 20 * 60_000L,
            endTimeMillis = 1_000_000L + 30 * 60_000L
        )
        val state = base(intermediates = listOf(bottle)).copy(
            napStartMillis = 1_000_000L + 90 * 60_000L,
            napEndMillis = 1_000_000L + 150 * 60_000L
        )
        assertFalse(state.hasOverlap)
    }

    @Test
    fun nearestPresetWithinFiveMinutes() {
        assertEquals(60, NapPlacementState.nearestPresetMinutes(61))
        assertEquals(45, NapPlacementState.nearestPresetMinutes(50))
        assertEquals(null, NapPlacementState.nearestPresetMinutes(105))
    }
}
