package com.demon.aurasurround.hook

import android.content.Context
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

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "AuraSurround"
        const val PREFS_NAME = "aura_surround_prefs"
        const val KEY_PREFS_JSON = "effect_prefs_json"
        const val KEY_ENABLED = "is_enabled"

        val activeEffects = mutableMapOf<Int, AudioEffectSet>()
    }

    data class AudioEffectSet(
        val virtualizer: Virtualizer?,
        val reverb: PresetReverb?,
        val bassBoost: BassBoost?,
        val equalizer: Equalizer?,
        val panner: AudioPanner?
    ) {
        fun release() {
            runCatching { virtualizer?.release() }
            runCatching { reverb?.release() }
            runCatching { bassBoost?.release() }
            runCatching { equalizer?.release() }
            panner?.stop()
        }
    }

    private var modulePath: String? = null

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook module status for UI detection
        if (lpparam.packageName == "com.demon.aurasurround") {
            hookModuleStatus(lpparam)
        }
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

            XposedHelpers.findAndHookMethod(
                AudioTrack::class.java,
                "stop",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val audioTrack = param.thisObject as AudioTrack
                        val sessionId = audioTrack.audioSessionId
                        activeEffects[sessionId]?.release()
                        activeEffects.remove(sessionId)
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                AudioTrack::class.java,
                "release",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val audioTrack = param.thisObject as AudioTrack
                        val sessionId = audioTrack.audioSessionId
                        activeEffects[sessionId]?.release()
                        activeEffects.remove(sessionId)
                    }
                }
            )

            XposedBridge.log("$TAG: AudioTrack hooks installed for ${lpparam.packageName}")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Hook failed for ${lpparam.packageName}: ${e.message}")
        }
    }

    private fun applyEffects(audioTrack: AudioTrack, packageName: String) {
        try {
            val sessionId = audioTrack.audioSessionId
            if (sessionId <= 0) return

            val prefs = loadPrefsFromFile() ?: return
            if (!prefs.isEnabled) {
                activeEffects[sessionId]?.release()
                activeEffects.remove(sessionId)
                return
            }

            if (prefs.targetPackages.isNotEmpty() && packageName !in prefs.targetPackages) return

            if (activeEffects.containsKey(sessionId)) {
                updateEffects(sessionId, prefs)
                return
            }

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
            val virtualizer = try {
                Virtualizer(0, sessionId).apply {
                    setStrength(prefs.virtualizerStrength.toShort().coerceIn(0, 1000))
                    enabled = true
                }
            } catch (e: Exception) { XposedBridge.log("$TAG: Virtualizer failed: ${e.message}"); null }

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
            } catch (e: Exception) { XposedBridge.log("$TAG: Reverb failed: ${e.message}"); null }

            val bassBoost = try {
                BassBoost(0, sessionId).apply {
                    setStrength(prefs.bassBoost.toShort().coerceIn(0, 1000))
                    enabled = prefs.bassBoost > 0
                }
            } catch (e: Exception) { null }

            val equalizer = try {
                Equalizer(0, sessionId).apply {
                    val numBands = numberOfBands
                    val bandsToSet = prefs.eqBands.size.coerceAtMost(numBands.toInt())
                    for (i in 0 until bandsToSet) {
                        setBandLevel(i.toShort(), prefs.eqBands[i].toShort())
                    }
                    enabled = prefs.eqBands.any { it != 0 }
                }
            } catch (e: Exception) { null }

            val panner = if (prefs.effectMode == AudioEffectPrefs.EffectMode.SURROUND_8D) {
                try { AudioPanner(sessionId, prefs.rotationSpeed).also { it.start() } } catch (e: Exception) { null }
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
            effectSet.virtualizer?.setStrength(prefs.virtualizerStrength.toShort().coerceIn(0, 1000))
            effectSet.bassBoost?.setStrength(prefs.bassBoost.toShort().coerceIn(0, 1000))
            effectSet.panner?.speed = prefs.rotationSpeed
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: updateEffects error: ${e.message}")
        }
    }

    private fun loadPrefsFromFile(): AudioEffectPrefs? {
        return try {
            val prefsFile = java.io.File("/data/data/com.demon.aurasurround/shared_prefs/${PREFS_NAME}.xml")
            if (!prefsFile.exists()) return AudioEffectPrefs()

            val content = prefsFile.readText()
            val jsonMatch = Regex("""name="$KEY_PREFS_JSON"[^>]*>([^<]+)""").find(content)
            val enabledMatch = Regex("""name="$KEY_ENABLED"[^>]*value="([^"]+)"""").find(content)

            val json = jsonMatch?.groupValues?.getOrNull(1)
            val enabled = enabledMatch?.groupValues?.getOrNull(1)?.toBoolean() ?: false

            val prefs = if (json != null) AudioEffectPrefs.fromJson(json) else AudioEffectPrefs()
            prefs.isEnabled = enabled
            prefs
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: loadPrefs error: ${e.message}")
            null
        }
    }
}
