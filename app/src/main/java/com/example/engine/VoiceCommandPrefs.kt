package com.example.engine

import android.content.Context
import android.content.SharedPreferences

object VoiceCommandPrefs {
    private const val PREFS = "voice_command_prefs"
    private const val KEY_ENABLED = "voice_commands_enabled"
    private const val KEY_LAST_FIRED_PREFIX = "last_fired_"
    private const val KEY_CUSTOM_PREFIX = "custom_phrases_"
    private const val KEY_LAST_HEARD = "last_heard_transcript"
    private const val KEY_LAST_HEARD_AT = "last_heard_at"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getLastFiredAt(context: Context, command: VoiceCommand): Long =
        prefs(context).getLong(KEY_LAST_FIRED_PREFIX + command.id, 0L)

    fun setLastFiredAt(context: Context, command: VoiceCommand, atMillis: Long) {
        prefs(context).edit().putLong(KEY_LAST_FIRED_PREFIX + command.id, atMillis).apply()
    }

    fun lastFiredMap(context: Context): Map<VoiceCommand, Long> =
        VoiceCommand.entries.associateWith { getLastFiredAt(context, it) }

    fun getLastHeard(context: Context): String =
        prefs(context).getString(KEY_LAST_HEARD, "").orEmpty()

    fun getLastHeardAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_HEARD_AT, 0L)

    fun setLastHeard(context: Context, transcript: String, atMillis: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putString(KEY_LAST_HEARD, transcript.take(120))
            .putLong(KEY_LAST_HEARD_AT, atMillis)
            .apply()
    }

    fun getCustomPhrases(context: Context, command: VoiceCommand): List<String> {
        val raw = prefs(context).getString(KEY_CUSTOM_PREFIX + command.id, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split('\n')
            .map { VoiceCommandMatcher.normalize(it) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun setCustomPhrases(context: Context, command: VoiceCommand, phrases: List<String>) {
        val cleaned = phrases
            .map { VoiceCommandMatcher.normalize(it) }
            .filter { it.isNotBlank() }
            .distinct()
        prefs(context).edit()
            .putString(KEY_CUSTOM_PREFIX + command.id, cleaned.joinToString("\n"))
            .apply()
    }

    fun addCustomPhrase(context: Context, command: VoiceCommand, phrase: String): Boolean {
        val normalized = VoiceCommandMatcher.normalize(phrase)
        if (normalized.isBlank()) return false
        val existing = getCustomPhrases(context, command)
        if (normalized in existing) return false
        setCustomPhrases(context, command, existing + normalized)
        return true
    }

    fun removeCustomPhrase(context: Context, command: VoiceCommand, phrase: String) {
        val normalized = VoiceCommandMatcher.normalize(phrase)
        setCustomPhrases(
            context,
            command,
            getCustomPhrases(context, command).filterNot { it == normalized }
        )
    }

    fun allExtraPhrases(context: Context): Map<VoiceCommand, List<String>> =
        VoiceCommand.entries.associateWith { getCustomPhrases(context, it) }
}
