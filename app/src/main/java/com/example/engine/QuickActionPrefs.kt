package com.example.engine

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ActivityTypes

object QuickActionPrefs {
    private const val PREFS = "quick_action_prefs"
    private const val KEY_FAVORITE_TYPE = "favorite_action_type"
    private const val KEY_FAVORITE_LABEL = "favorite_action_label"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getFavoriteType(context: Context): String? =
        prefs(context).getString(KEY_FAVORITE_TYPE, null)?.takeIf { it.isNotBlank() }

    fun getFavoriteLabel(context: Context): String? =
        prefs(context).getString(KEY_FAVORITE_LABEL, null)?.takeIf { it.isNotBlank() }

    fun setFavorite(context: Context, type: String, label: String) {
        prefs(context).edit()
            .putString(KEY_FAVORITE_TYPE, type)
            .putString(KEY_FAVORITE_LABEL, label)
            .apply()
    }

    fun clearFavorite(context: Context) {
        prefs(context).edit()
            .remove(KEY_FAVORITE_TYPE)
            .remove(KEY_FAVORITE_LABEL)
            .apply()
    }

    fun defaultLabelForType(type: String): String = when (type) {
        ActivityTypes.BOTTLE -> "Bottle"
        ActivityTypes.BREASTFEEDING -> "Nurse"
        ActivityTypes.SLEEP -> "Sleep"
        ActivityTypes.DIAPER -> "Diaper"
        ActivityTypes.PUMPING -> "Pumping"
        ActivityTypes.MEDICINE -> "Medicine"
        ActivityTypes.TEMPERATURE -> "Temp"
        ActivityTypes.GROWTH -> "Growth"
        ActivityTypes.BATH -> "Bath"
        ActivityTypes.TUMMY_TIME -> "Tummy"
        ActivityTypes.MILESTONE -> "Milestone"
        ActivityTypes.CUSTOM -> "Custom"
        else -> type.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
    }
}
