package com.example.engine

object VoiceCommandMatcher {
    const val DEFAULT_COOLDOWN_MS = 30_000L

    fun normalize(transcript: String): String =
        transcript
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Built-in + optional user-defined phrases, longest first.
     */
    fun phraseIndex(
        extraPhrases: Map<VoiceCommand, List<String>> = emptyMap()
    ): List<Pair<VoiceCommand, String>> {
        val combined = VoiceCommand.entries.flatMap { cmd ->
            val extras = extraPhrases[cmd].orEmpty().map { normalize(it) }.filter { it.isNotBlank() }
            (cmd.phrases + extras).distinct().map { cmd to it }
        }
        return combined.sortedByDescending { it.second.length }
    }

    /**
     * Returns the best matching command, or null if none / cooled down / ambiguous.
     */
    fun match(
        transcript: String,
        nowMillis: Long = System.currentTimeMillis(),
        lastFiredAt: Map<VoiceCommand, Long> = emptyMap(),
        cooldownMs: Long = DEFAULT_COOLDOWN_MS,
        extraPhrases: Map<VoiceCommand, List<String>> = emptyMap()
    ): VoiceCommand? =
        matchAll(transcript, nowMillis, lastFiredAt, cooldownMs, extraPhrases).firstOrNull()

    /**
     * Returns every distinct command found in [transcript] (non-overlapping phrases),
     * so "wet diaper and feed the baby" can log both.
     */
    fun matchAll(
        transcript: String,
        nowMillis: Long = System.currentTimeMillis(),
        lastFiredAt: Map<VoiceCommand, Long> = emptyMap(),
        cooldownMs: Long = DEFAULT_COOLDOWN_MS,
        extraPhrases: Map<VoiceCommand, List<String>> = emptyMap()
    ): List<VoiceCommand> {
        val normalized = normalize(transcript)
        if (normalized.isBlank()) return emptyList()

        data class Hit(val command: VoiceCommand, val start: Int, val end: Int, val length: Int)

        val phraseHits = mutableListOf<Hit>()
        for ((command, phrase) in phraseIndex(extraPhrases)) {
            var from = 0
            while (from <= normalized.length - phrase.length) {
                val idx = normalized.indexOf(phrase, from)
                if (idx < 0) break
                phraseHits += Hit(command, idx, idx + phrase.length, phrase.length)
                from = idx + phrase.length
            }
        }

        // Greedy non-overlapping: longest first, then earliest
        val selected = mutableListOf<Hit>()
        for (hit in phraseHits.sortedWith(compareByDescending<Hit> { it.length }.thenBy { it.start })) {
            val overlapsExisting = selected.any { a -> a.start < hit.end && hit.start < a.end }
            if (overlapsExisting) continue
            if (selected.any { it.command == hit.command }) continue
            selected += hit
        }

        if (selected.isEmpty()) {
            val keywordHits = VoiceCommand.entries.filter { cmd ->
                cmd.keywordSequence.isNotEmpty() &&
                    containsOrderedKeywords(normalized, cmd.keywordSequence)
            }
            return keywordHits.filter { cmd ->
                val last = lastFiredAt[cmd] ?: 0L
                last <= 0L || nowMillis - last >= cooldownMs
            }
        }

        return selected
            .sortedBy { it.start }
            .map { it.command }
            .filter { cmd ->
                val last = lastFiredAt[cmd] ?: 0L
                last <= 0L || nowMillis - last >= cooldownMs
            }
    }

    /** True if each keyword appears in order as whole words. */
    fun containsOrderedKeywords(normalized: String, keywords: List<String>): Boolean {
        if (keywords.isEmpty()) return false
        var fromIndex = 0
        for (word in keywords) {
            val next = findNextWholeWord(normalized, word, fromIndex)
            if (next < 0) return false
            fromIndex = next + word.length
        }
        return true
    }

    private fun findNextWholeWord(text: String, word: String, fromIndex: Int): Int {
        var start = fromIndex
        while (start <= text.length - word.length) {
            val idx = text.indexOf(word, start)
            if (idx < 0) return -1
            val beforeOk = idx == 0 || text[idx - 1].isWhitespace()
            val after = idx + word.length
            val afterOk = after >= text.length || text[after].isWhitespace()
            if (beforeOk && afterOk) return idx
            start = idx + 1
        }
        return -1
    }
}
