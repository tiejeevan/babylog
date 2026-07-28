package com.example.engine

import android.content.Context
import android.content.SharedPreferences

object SleepSoundPrefs {
    private const val PREFS = "sleep_sound_prefs"
    private const val KEY_SOUND = "sound_type"
    private const val KEY_VOLUME = "volume"
    private const val KEY_TIMER_MINUTES = "timer_minutes"
    private const val KEY_COLOR_INDEX = "color_index"
    private const val KEY_BRIGHTNESS = "brightness"
    private const val KEY_KEEP_AWAKE = "keep_awake"
    private const val KEY_PULSE = "pulse_enabled"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getSoundType(context: Context): SoundType {
        val name = prefs(context).getString(KEY_SOUND, SoundType.PINK_NOISE.name)
        return SoundType.entries.find { it.name == name } ?: SoundType.PINK_NOISE
    }

    fun setSoundType(context: Context, sound: SoundType) {
        prefs(context).edit().putString(KEY_SOUND, sound.name).apply()
    }

    fun getVolume(context: Context): Float =
        prefs(context).getFloat(KEY_VOLUME, 0.7f).coerceIn(0f, 1f)

    fun setVolume(context: Context, volume: Float) {
        prefs(context).edit().putFloat(KEY_VOLUME, volume.coerceIn(0f, 1f)).apply()
    }

    fun getTimerMinutes(context: Context): Int =
        prefs(context).getInt(KEY_TIMER_MINUTES, 0)

    fun setTimerMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_TIMER_MINUTES, minutes.coerceAtLeast(0)).apply()
    }

    fun getColorIndex(context: Context): Int =
        prefs(context).getInt(KEY_COLOR_INDEX, 0)

    fun setColorIndex(context: Context, index: Int) {
        prefs(context).edit().putInt(KEY_COLOR_INDEX, index.coerceAtLeast(0)).apply()
    }

    fun getBrightness(context: Context): Float =
        prefs(context).getFloat(KEY_BRIGHTNESS, 0.35f).coerceIn(0.01f, 1f)

    fun setBrightness(context: Context, brightness: Float) {
        prefs(context).edit().putFloat(KEY_BRIGHTNESS, brightness.coerceIn(0.01f, 1f)).apply()
    }

    fun isKeepAwake(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KEEP_AWAKE, true)

    fun setKeepAwake(context: Context, keepAwake: Boolean) {
        prefs(context).edit().putBoolean(KEY_KEEP_AWAKE, keepAwake).apply()
    }

    fun isPulseEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PULSE, false)

    fun setPulseEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PULSE, enabled).apply()
    }
}
