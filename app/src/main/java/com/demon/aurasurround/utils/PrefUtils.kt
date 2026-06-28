package com.demon.aurasurround.utils

import android.content.Context
import android.content.SharedPreferences
import com.demon.aurasurround.model.AudioEffectPrefs

object PrefUtils {
    const val PREF_NAME = "aura_surround_prefs"
    const val KEY_PREFS_JSON = "effect_prefs_json"
    const val KEY_ENABLED = "is_enabled"

    // For hook process - uses world-readable prefs (Xposed way)
    fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_WORLD_READABLE)

    fun savePrefs(context: Context, prefs: AudioEffectPrefs) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_WORLD_READABLE)
            .edit()
            .putString(KEY_PREFS_JSON, prefs.toJson())
            .putBoolean(KEY_ENABLED, prefs.isEnabled)
            .apply()
    }

    fun loadPrefs(context: Context): AudioEffectPrefs {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_WORLD_READABLE)
        val json = sp.getString(KEY_PREFS_JSON, null) ?: return AudioEffectPrefs()
        return AudioEffectPrefs.fromJson(json)
    }
}
