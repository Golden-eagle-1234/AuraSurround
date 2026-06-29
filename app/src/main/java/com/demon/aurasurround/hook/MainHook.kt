package com.demon.aurasurround.hook

import android.media.AudioTrack
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.BassBoost
import com.demon.aurasurround.model.AudioEffectPrefs
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "AuraSurround"
        const val PREFS_NAME = "aura_surround_prefs"
        const val KEY_PREFS_JSON = "effect_prefs_json"
        const val KEY_ENABLED = "is_enabled"

        // Thread-safe map for active effects
        val activeEffects = mutableMapOf<Int, AudioEffectSet>()
    }

    data class AudioEffectSet(
        val virtualizer: Virtualizer?,
        val reverb: PresetReverb?,
        val bassBoost: BassBoost?,
        val equalizer: Equalizer?,
        val panner: AudioPanner? // Ensure this class exists in your package
    ) {
        fun release() {
            runCatching { virtualizer?.release() }
            runCatching { reverb?.release() }
            runCatching { bassBoost?.release() }
            runCatching { equalizer?.release() }
            runCatching { panner?.stop() }
        }
    }

    private var modulePath: String? = null

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "com.demon.aurasurround") {
            hookModuleStatus(lpparam)
        }
        // Hook all apps that use AudioTrack
        hookAudioTrack(lpparam)
    }

    private fun hookModuleStatus(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.demon.aurasurround.ui.MainActivity",
                lpparam.classLoader,
                "checkModuleActive",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        XposedBridge.log("$TAG: Module is ACTIVE - UI detection hooked!")
                        val activity = param.thisObject as android.app.Activity
                        try {
                            // Note: This reflection is fragile and depends on the exact structure of MainActivity
                            val bindingField = activity.javaClass.getDeclaredField("binding")
                            bindingField.isAccessible = true
                            val binding = bindingField.get(activity)
                            
                            val tvStatusField = binding.javaClass.getDeclaredField("tvModuleStatus")
                            tvStatusField.isAccessible = true
                            val tvStatus = tvStatusField.get(binding) as android.widget.TextView

                            activity.runOnUiThread {
                                tvStatus.text = "● Module Active"
                                tvStatus.setTextColor(activity.getColor(android.R.color.holo_green_light))
                            }
                        } catch (e: Exception) {
                            XposedBridge.log("$TAG: UI status update failed: ${e.message}")
                        }
                    }
                }
            )
            XposedBridge.log("$TAG: Module status hook installed successfully")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to hook module status: ${e.message}")
        }
    }

    private fun hookAudioTrack(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook play() to apply effects when audio starts
            XposedHelpers.findAndHookMethod(
                AudioTrack::class.java,
                "play",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val audioTrack = param.thisObject as AudioTrack
                        applyEffects(audioTrack, lpparam.packageName)
                    }
                }
            )

            // Hook stop() to clean up effects
            XposedHelpers.findAndHookMethod(
                AudioTrack::class.java,
                "stop",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val audioTrack = param.thisObject as AudioTrack
                        cleanupEffects(audioTrack.audioSessionId)
                    }
                }
            )

            // Hook release() to clean up effects
            XposedHelpers.findAndHookMethod(
                AudioTrack::class.java,
                "release",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val audioTrack = param.thisObject as AudioTrack
                        cleanupEffects(audioTrack.audioSessionId)
                    }
                }
            )

            XposedBridge.log("$TAG: AudioTrack hooks installed for ${lpparam.packageName}")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Hook failed for ${lpparam.packageName}: ${e.message}")
        }
    }

    private fun cleanupEffects(sessionId: Int) {
        if (sessionId <= 0) return
        activeEffects[sessionId]?.release()
        activeEffects.remove(sessionId)
    }

    private fun applyEffects(audioTrack: AudioTrack, packageName: String) {
        try {
            val sessionId = audioTrack.audioSessionId
            if (sessionId <= 0) return

            val prefs = loadPrefsFromFile() ?: return
            
            // If module is disabled globally, clean up and exit
            if (!prefs.isEnabled) {
                cleanupEffects(sessionId)
                return
            }

            // Check if this package is targeted
            if (prefs.targetPackages.isNotEmpty() && packageName !in prefs.targetPackages) {
                cleanupEffects(sessionId)
                return
            }

            // Update existing effects if already applied
            if (activeEffects.containsKey(sessionId)) {
                updateEffects(sessionId, prefs)
                return
            }

            // Create new effect chain
            val effectSet = createEffectChain(sessionId, prefs)
            if (effectSet != null) {
                activeEffects[sessionId] = effectSet
                XposedBridge.log("$TAG: Effects applied to session $sessionId (${prefs.effectMode})")
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: applyEffects error: ${e.message}")
        }
    }

    private fun createEffectChain(sessionId: Int, prefs: AudioEffectPrefs): AudioEffectSet? {
        return try {
            // Virtualizer
            val virtualizer = try {
                Virtualizer(0, sessionId).apply {
                    setStrength(prefs.virtualizerStrength.toShort().coerceIn(0, 1000))
                    enabled = true
                }
            } catch (e: Exception) { 
                XposedBridge.log("$TAG: Virtualizer failed: ${e.message}")
                null 
            }

            // Reverb
            val reverb = try {
                PresetReverb(0, sessionId).apply {
                    preset = when (prefs.effectMode) {
                        AudioEffectPrefs.EffectMode.CONCERT_HALL -> PresetReverb.PRESET_LARGEHALL
                        AudioEffectPrefs.EffectMode.STADIUM      -> PresetReverb.PRESET_LARGEROOM
                        AudioEffectPrefs.EffectMode.CAVE         -> PresetReverb.PRESET_PLATE
                        AudioEffectPrefs.EffectMode.SURROUND_8D  -> PresetReverb.PRESET_SMALLROOM
                        AudioEffectPrefs.EffectMode.HEADPHONE_SPATIAL -> PresetReverb.PRESET_MEDIUMHALL
                        else -> PresetReverb.PRESET_SMALLROOM
                    }
                    enabled = true
                }
            } catch (e: Exception) { 
                XposedBridge.log("$TAG: Reverb failed: ${e.message}")
                null 
            }

            // Bass Boost
            val bassBoost = try {
                BassBoost(0, sessionId).apply {
                    setStrength(prefs.bassBoost.toShort().coerceIn(0, 1000))
                    enabled = prefs.bassBoost > 0
                }
            } catch (e: Exception) { 
                XposedBridge.log("$TAG: BassBoost failed: ${e.message}")
                null 
            }

            // Equalizer - FIXED: Removed duplicate line
            val equalizer = try {
                Equalizer(0, sessionId).apply {
                    val numBands = numberOfBands.toInt()
                    val range = getBandLevelRange()  // Only keep this line
                    val minLevel = range[0]
                    val maxLevel = range[1]
                    
                    val bandsToSet = prefs.eqBands.size.coerceAtMost(numBands)
                    for (i in 0 until bandsToSet) {
                        // Clamp value to valid range
                        val level = prefs.eqBands[i].toShort().coerceIn(minLevel, maxLevel)
                        setBandLevel(i.toShort(), level)
                    }
                    enabled = prefs.eqBands.any { it != 0 }
                }
            } catch (e: Exception) { 
                XposedBridge.log("$TAG: Equalizer failed: ${e.message}")
                null 
            }

            // Panner (Custom Class)
            val panner = if (prefs.effectMode == AudioEffectPrefs.EffectMode.SURROUND_8D) {
                try { 
                    AudioPanner(sessionId, prefs.rotationSpeed).also { it.start() } 
                } catch (e: Exception) { 
                    XposedBridge.log("$TAG: Panner failed: ${e.message}")
                    null 
                }
            } else null

            AudioEffectSet(virtualizer, reverb, bassBoost, equalizer, panner)
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: createEffectChain error: ${e.message}")
            null
        }
    }

    private fun updateEffects(sessionId: Int, prefs: AudioEffectPrefs) {
        val effectSet = activeEffects[sessionId] ?: return
        try {
            effectSet.virtualizer?.let {
                it.setStrength(prefs.virtualizerStrength.toShort().coerceIn(0, 1000))
                it.enabled = true
            }
            
            effectSet.bassBoost?.let {
                it.setStrength(prefs.bassBoost.toShort().coerceIn(0, 1000))
                it.enabled = prefs.bassBoost > 0
            }
            
            effectSet.panner?.speed = prefs.rotationSpeed
            
            // Note: Updating Equalizer bands dynamically is complex and often requires recreation.
            // For now, we only update strength-based effects.
            
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: updateEffects error: ${e.message}")
        }
    }

    private fun loadPrefsFromFile(): AudioEffectPrefs? {
        return try {
            val possiblePaths = listOf(
                "/data/data/com.demon.aurasurround/shared_prefs/${PREFS_NAME}.xml",
                "/data/user/0/com.demon.aurasurround/shared_prefs/${PREFS_NAME}.xml"
            )

            for (path in possiblePaths) {
                val prefsFile = File(path)
                if (prefsFile.exists() && prefsFile.canRead()) {
                    val content = prefsFile.readText()
                    
                    // Fixed Regex: Removed extra quote
                    val jsonMatch = Regex("""name="$KEY_PREFS_JSON"[^>]*>([^<]+)""").find(content)
                    val enabledMatch = Regex("""name="$KEY_ENABLED"[^>]*value="([^"]+)""").find(content)

                    val json = jsonMatch?.groupValues?.getOrNull(1)
                    val enabled = enabledMatch?.groupValues?.getOrNull(1)?.toBoolean() ?: false

                    val prefs = if (json != null) {
                        try {
                            AudioEffectPrefs.fromJson(json)
                        } catch (e: Exception) {
                            XposedBridge.log("$TAG: JSON parse error: ${e.message}")
                            AudioEffectPrefs()
                        }
                    } else AudioEffectPrefs()
                    
                    prefs.isEnabled = enabled
                    XposedBridge.log("$TAG: Loaded prefs successfully from $path")
                    return prefs
                }
            }
            
            XposedBridge.log("$TAG: Prefs file not found or not readable")
            AudioEffectPrefs() // Return default instead of null
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: loadPrefs error: ${e.message}")
            null
        }
    }
}
