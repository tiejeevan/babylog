package com.example.engine

import com.example.data.model.ActivityTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomLogRouterTest {

    @Test
    fun route_unusualBigPoop_routesToDiaperDirty() {
        val result = CustomLogRouter.route("Baby took unusual big poop")
        assertEquals(ActivityTypes.DIAPER, result.activityType)
        assertEquals("Dirty", result.diaperStatus)
        assertEquals("Baby took unusual big poop", result.notes)
    }

    @Test
    fun route_wetAndDirtyDiaper_routesToDiaperBoth() {
        val result = CustomLogRouter.route("Heavy pee and poop diaper")
        assertEquals(ActivityTypes.DIAPER, result.activityType)
        assertEquals("Both", result.diaperStatus)
    }

    @Test
    fun route_bottleWithVolumeInMl_routesToBottleWithMl() {
        val result = CustomLogRouter.route("Drank 150ml formula bottle")
        assertEquals(ActivityTypes.BOTTLE, result.activityType)
        assertEquals(150, result.volumeMl)
    }

    @Test
    fun route_bottleWithVolumeInOz_routesToBottleWithConvertedMl() {
        val result = CustomLogRouter.route("Drank 5 oz milk bottle")
        assertEquals(ActivityTypes.BOTTLE, result.activityType)
        assertEquals(148, result.volumeMl) // 5 * 29.5735 rounded
    }

    @Test
    fun route_breastfeeding_routesToBreastfeeding() {
        val result = CustomLogRouter.route("Nursed for 15 minutes")
        assertEquals(ActivityTypes.BREASTFEEDING, result.activityType)
    }

    @Test
    fun route_sleepWithDuration_routesToSleepWithSeconds() {
        val result = CustomLogRouter.route("Slept for 1h 30m nap")
        assertEquals(ActivityTypes.SLEEP, result.activityType)
        assertEquals(5400L, result.durationSeconds) // 1h 30m = 5400s
    }

    @Test
    fun route_medicine_routesToMedicine() {
        val result = CustomLogRouter.route("Gave Tylenol 1ml dose")
        assertEquals(ActivityTypes.MEDICINE, result.activityType)
    }

    @Test
    fun route_temperature_routesToTemperature() {
        val result = CustomLogRouter.route("Fever was 38.5 C")
        assertEquals(ActivityTypes.TEMPERATURE, result.activityType)
    }

    @Test
    fun route_tummyTime_routesToTummyTime() {
        val result = CustomLogRouter.route("Did tummy time exercise")
        assertEquals(ActivityTypes.TUMMY_TIME, result.activityType)
    }

    @Test
    fun route_bath_routesToBath() {
        val result = CustomLogRouter.route("Night bath time")
        assertEquals(ActivityTypes.BATH, result.activityType)
    }

    @Test
    fun route_unrecognizedAction_remainsCustom() {
        val result = CustomLogRouter.route("Went to park for a walk")
        assertEquals(ActivityTypes.CUSTOM, result.activityType)
        assertEquals("Went to park for a walk", result.notes)
    }
}
