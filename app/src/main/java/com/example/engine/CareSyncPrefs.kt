package com.example.engine

import android.content.Context
import android.content.SharedPreferences

object CareSyncPrefs {
    private const val PREFS = "care_sync_prefs"
    private const val KEY_PIN = "family_pin"
    private const val KEY_ENABLED = "care_sync_enabled"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_MUTE_OFF_DUTY = "mute_non_urgent_when_off_duty"
    private const val KEY_VIBRATE_ON_RECEIVE = "vibrate_on_receive"
    private const val DEFAULT_PIN = "1234"

    private const val KEY_CAREGIVER_NAME = "caregiver_name"
    private const val KEY_CAREGIVER_ROLE = "caregiver_role"
    private const val KEY_FORGOTTEN_DEVICES = "forgotten_devices"
    private const val KEY_REMEMBERED_DEVICES = "remembered_devices"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getFamilyPin(context: Context): String =
        prefs(context).getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN

    fun setFamilyPin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_PIN, pin.trim().ifBlank { DEFAULT_PIN }).apply()
    }

    fun isCareSyncEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setCareSyncEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isMuteNonUrgentWhenOffDuty(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MUTE_OFF_DUTY, true)

    fun setMuteNonUrgentWhenOffDuty(context: Context, mute: Boolean) {
        prefs(context).edit().putBoolean(KEY_MUTE_OFF_DUTY, mute).apply()
    }

    fun isVibrateOnReceive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIBRATE_ON_RECEIVE, true)

    fun setVibrateOnReceive(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VIBRATE_ON_RECEIVE, enabled).apply()
    }

    fun getOrCreateDeviceId(context: Context): String {
        val existing = prefs(context).getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val id = java.util.UUID.randomUUID().toString()
        prefs(context).edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    fun getCaregiverName(context: Context, defaultName: String = "Parent"): String =
        prefs(context).getString(KEY_CAREGIVER_NAME, defaultName) ?: defaultName

    fun setCaregiverName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_CAREGIVER_NAME, name.trim().ifBlank { "Parent" }).apply()
    }

    fun getCaregiverRole(context: Context, defaultRole: String = "Primary Caregiver"): String =
        prefs(context).getString(KEY_CAREGIVER_ROLE, defaultRole) ?: defaultRole

    fun setCaregiverRole(context: Context, role: String) {
        prefs(context).edit().putString(KEY_CAREGIVER_ROLE, role.trim()).apply()
    }

    fun getForgottenDevices(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_FORGOTTEN_DEVICES, emptySet()) ?: emptySet()

    fun addForgottenDevice(context: Context, deviceIdOrName: String) {
        val current = getForgottenDevices(context).toMutableSet()
        current.add(deviceIdOrName)
        prefs(context).edit().putStringSet(KEY_FORGOTTEN_DEVICES, current).apply()
    }

    fun removeForgottenDevice(context: Context, deviceIdOrName: String) {
        val current = getForgottenDevices(context).toMutableSet()
        current.remove(deviceIdOrName)
        prefs(context).edit().putStringSet(KEY_FORGOTTEN_DEVICES, current).apply()
    }

    fun getRememberedDevices(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_REMEMBERED_DEVICES, emptySet()) ?: emptySet()

    fun addRememberedDevice(context: Context, deviceIdOrName: String) {
        if (deviceIdOrName.isBlank()) return
        val current = getRememberedDevices(context).toMutableSet()
        current.add(deviceIdOrName)
        prefs(context).edit().putStringSet(KEY_REMEMBERED_DEVICES, current).apply()
    }

    fun removeRememberedDevice(context: Context, deviceIdOrName: String) {
        val current = getRememberedDevices(context).toMutableSet()
        current.remove(deviceIdOrName)
        prefs(context).edit().putStringSet(KEY_REMEMBERED_DEVICES, current).apply()
    }
}
