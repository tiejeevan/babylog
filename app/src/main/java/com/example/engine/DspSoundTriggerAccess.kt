package com.example.engine

import android.content.Context
import android.content.Intent
import android.provider.Settings

object DspSoundTriggerAccess {

    enum class ConnectionPath {
        DSP_ACTIVE,
        DSP_READY,
        SOFTWARE_FALLBACK
    }

    data class DspStatus(
        val connectionPath: ConnectionPath,
        val summary: String,
        val detailLines: List<String>
    )

    fun probe(context: Context): DspStatus {
        return DspStatus(
            connectionPath = ConnectionPath.SOFTWARE_FALLBACK,
            summary = "Hardware DSP Voice Trigger: Standby / Software Fallback",
            detailLines = listOf(
                "• Continuous mic listener active via foreground service",
                "• Hotword detection powered by SpeechRecognizer & internal engine"
            )
        )
    }

    fun openVoiceInteractionSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}
