package com.example.engine

/**
 * Hands-free voice commands. Phrases include easy STT-friendly forms plus
 * distinctive "code" slang aliases.
 */
enum class VoiceCommand(
    val id: String,
    val displayLabel: String,
    val phrases: List<String>,
    /** Ordered keywords that must appear in sequence (allows filler words between). */
    val keywordSequence: List<String> = emptyList()
) {
    CODE_BROWN(
        id = "code_brown",
        displayLabel = "Dirty diaper",
        phrases = listOf(
            "dirty diaper",
            "poopy diaper",
            "poop diaper",
            "code brown",
            "code browns",
            "coat brown",
            "cold brown",
            "brown diaper"
        ),
        keywordSequence = listOf("dirty", "diaper")
    ),
    CODE_YELLOW(
        id = "code_yellow",
        displayLabel = "Wet diaper",
        phrases = listOf(
            "wet diaper",
            "pee diaper",
            "code yellow",
            "coat yellow",
            "cold yellow",
            "yellow diaper"
        ),
        keywordSequence = listOf("wet", "diaper")
    ),
    FEEDING_BABY(
        id = "feeding_baby",
        displayLabel = "Feeding baby",
        phrases = listOf(
            "feeding baby",
            "feeding the baby",
            "bottle feed",
            "bottle feeding",
            "log bottle",
            "feed baby",
            "feed the baby"
        ),
        keywordSequence = listOf("feed", "baby")
    ),
    NURSE_BABY(
        id = "nurse_baby",
        displayLabel = "Nurse baby",
        phrases = listOf(
            "nursing baby",
            "nursing the baby",
            "start nursing",
            "nurse baby",
            "nurse the baby",
            "breastfeeding baby",
            "breast feeding baby"
        ),
        keywordSequence = listOf("nurse", "baby")
    );

    companion object {
        /** All (command, phrase) pairs sorted by phrase length descending. */
        val phrasesLongestFirst: List<Pair<VoiceCommand, String>> =
            entries
                .flatMap { cmd -> cmd.phrases.map { phrase -> cmd to phrase } }
                .sortedByDescending { it.second.length }
    }
}
