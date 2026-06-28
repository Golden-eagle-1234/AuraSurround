package com.demon.aurasurround.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.demon.aurasurround.model.AudioEffectPrefs
import com.demon.aurasurround.utils.PrefUtils

class MainViewModel : ViewModel() {

    private val _prefs = MutableLiveData(AudioEffectPrefs())
    val prefs: LiveData<AudioEffectPrefs> = _prefs

    fun loadPrefs(context: Context) {
        _prefs.value = PrefUtils.loadPrefs(context)
    }

    fun savePrefs(context: Context) {
        _prefs.value?.let { PrefUtils.savePrefs(context, it) }
    }

    fun setEnabled(enabled: Boolean) {
        _prefs.value = _prefs.value?.copy(isEnabled = enabled)
    }

    fun setMode(mode: AudioEffectPrefs.EffectMode) {
        _prefs.value = _prefs.value?.copy(effectMode = mode)
    }

    fun setVirtualizerStrength(value: Int) {
        _prefs.value = _prefs.value?.copy(virtualizerStrength = value)
    }

    fun setReverbLevel(value: Int) {
        _prefs.value = _prefs.value?.copy(reverbLevel = value)
    }

    fun setBassBoost(value: Int) {
        _prefs.value = _prefs.value?.copy(bassBoost = value)
    }

    fun setRotationSpeed(value: Float) {
        _prefs.value = _prefs.value?.copy(rotationSpeed = value)
    }

    fun applyPreset(preset: AudioEffectPrefs) {
        val current = _prefs.value
        _prefs.value = preset.copy(
            isEnabled = current?.isEnabled ?: false,
            targetPackages = current?.targetPackages ?: mutableSetOf()
        )
    }
}
