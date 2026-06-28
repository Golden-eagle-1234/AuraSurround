package com.demon.aurasurround.model

import com.google.gson.Gson

data class AudioEffectPrefs(
    var isEnabled: Boolean = false,
    var effectMode: EffectMode = EffectMode.SURROUND_8D,
    var virtualizerStrength: Int = 800,   // 0-1000
    var reverbLevel: Int = 500,            // 0-1000
    var bassBoost: Int = 300,              // 0-1000
    var rotationSpeed: Float = 0.5f,       // 0.1 - 2.0 (for 8D panning speed)
    var eqBands: IntArray = intArrayOf(0, 0, 0, 0, 0), // 5 bands in mB
    var targetPackages: MutableSet<String> = mutableSetOf() // empty = all apps
) {
    enum class EffectMode(val displayName: String) {
        SURROUND_8D("8D Surround"),
        CONCERT_HALL("Concert Hall"),
        STADIUM("Stadium"),
        CAVE("Cave Echo"),
        HEADPHONE_SPATIAL("Headphone Spatial"),
        CUSTOM("Custom")
    }

    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): AudioEffectPrefs =
            try { Gson().fromJson(json, AudioEffectPrefs::class.java) }
            catch (e: Exception) { AudioEffectPrefs() }

        // Preset configurations
        fun preset8D() = AudioEffectPrefs(
            effectMode = EffectMode.SURROUND_8D,
            virtualizerStrength = 1000,
            reverbLevel = 400,
            bassBoost = 300,
            rotationSpeed = 0.6f
        )

        fun presetConcert() = AudioEffectPrefs(
            effectMode = EffectMode.CONCERT_HALL,
            virtualizerStrength = 700,
            reverbLevel = 800,
            bassBoost = 200,
            rotationSpeed = 0.0f
        )

        fun presetStadium() = AudioEffectPrefs(
            effectMode = EffectMode.STADIUM,
            virtualizerStrength = 900,
            reverbLevel = 950,
            bassBoost = 500,
            rotationSpeed = 0.0f
        )

        fun presetCave() = AudioEffectPrefs(
            effectMode = EffectMode.CAVE,
            virtualizerStrength = 600,
            reverbLevel = 1000,
            bassBoost = 100,
            rotationSpeed = 0.0f
        )

        fun presetSpatial() = AudioEffectPrefs(
            effectMode = EffectMode.HEADPHONE_SPATIAL,
            virtualizerStrength = 850,
            reverbLevel = 300,
            bassBoost = 400,
            rotationSpeed = 0.0f
        )
    }
}
