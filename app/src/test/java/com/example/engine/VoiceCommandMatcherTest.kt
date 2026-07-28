package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandMatcherTest {

    @Test
    fun primaryPhrases_matchCorrectCommands() {
        assertEquals(VoiceCommand.CODE_BROWN, VoiceCommandMatcher.match("Dirty diaper"))
        assertEquals(VoiceCommand.CODE_YELLOW, VoiceCommandMatcher.match("wet diaper!"))
        assertEquals(VoiceCommand.FEEDING_BABY, VoiceCommandMatcher.match("Feeding baby"))
        assertEquals(VoiceCommand.NURSE_BABY, VoiceCommandMatcher.match("Nurse baby"))
    }

    @Test
    fun codeSlang_stillMatches() {
        assertEquals(VoiceCommand.CODE_BROWN, VoiceCommandMatcher.match("Code brown"))
        assertEquals(VoiceCommand.CODE_YELLOW, VoiceCommandMatcher.match("code yellow"))
    }

    @Test
    fun aliases_matchFeedingAndNursing() {
        assertEquals(VoiceCommand.FEEDING_BABY, VoiceCommandMatcher.match("please bottle feed now"))
        assertEquals(VoiceCommand.FEEDING_BABY, VoiceCommandMatcher.match("log bottle"))
        assertEquals(VoiceCommand.NURSE_BABY, VoiceCommandMatcher.match("nursing baby"))
        assertEquals(VoiceCommand.NURSE_BABY, VoiceCommandMatcher.match("start nursing"))
    }

    @Test
    fun babyAlone_doesNotMatch() {
        assertNull(VoiceCommandMatcher.match("baby"))
        assertNull(VoiceCommandMatcher.match("the baby is cute"))
    }

    @Test
    fun feedingDoesNotMatchNursing() {
        assertEquals(VoiceCommand.FEEDING_BABY, VoiceCommandMatcher.match("feeding baby"))
        assertNull(VoiceCommandMatcher.match("feeding"))
        assertNull(VoiceCommandMatcher.match("nurse"))
    }

    @Test
    fun normalization_handlesPunctuationAndCasing() {
        assertEquals(
            VoiceCommand.CODE_BROWN,
            VoiceCommandMatcher.match("  CODE-BROWN!!  ")
        )
        assertEquals(
            VoiceCommand.CODE_YELLOW,
            VoiceCommandMatcher.match("Code, Yellow.")
        )
    }

    @Test
    fun cooldown_blocksRapidDuplicate() {
        val now = 1_000_000L
        val lastFired = mapOf(VoiceCommand.CODE_BROWN to now - 5_000L)
        assertNull(
            VoiceCommandMatcher.match(
                transcript = "dirty diaper",
                nowMillis = now,
                lastFiredAt = lastFired,
                cooldownMs = 30_000L
            )
        )
        assertEquals(
            VoiceCommand.CODE_BROWN,
            VoiceCommandMatcher.match(
                transcript = "dirty diaper",
                nowMillis = now,
                lastFiredAt = lastFired,
                cooldownMs = 4_000L
            )
        )
    }

    @Test
    fun matchAll_findsMultipleCommandsInOneUtterance() {
        val hits = VoiceCommandMatcher.matchAll("wet diaper and feed the baby please")
        assertEquals(
            listOf(VoiceCommand.CODE_YELLOW, VoiceCommand.FEEDING_BABY),
            hits
        )
    }

    @Test
    fun sttMishears_andKeywordSequences_match() {
        assertEquals(VoiceCommand.CODE_BROWN, VoiceCommandMatcher.match("coat brown"))
        assertEquals(VoiceCommand.CODE_BROWN, VoiceCommandMatcher.match("um dirty uh diaper please"))
        assertEquals(VoiceCommand.CODE_YELLOW, VoiceCommandMatcher.match("wet diaper now"))
        assertEquals(VoiceCommand.FEEDING_BABY, VoiceCommandMatcher.match("feed the baby"))
        assertEquals(VoiceCommand.NURSE_BABY, VoiceCommandMatcher.match("nurse the baby"))
    }

    @Test
    fun customExtraPhrases_match() {
        val extras = mapOf(VoiceCommand.CODE_YELLOW to listOf("pee pee time"))
        assertEquals(
            VoiceCommand.CODE_YELLOW,
            VoiceCommandMatcher.match("pee pee time", extraPhrases = extras)
        )
        assertNull(VoiceCommandMatcher.match("pee pee time"))
    }
}
