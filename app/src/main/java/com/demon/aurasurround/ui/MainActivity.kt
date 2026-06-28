package com.demon.aurasurround.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.demon.aurasurround.databinding.ActivityMainBinding
import com.demon.aurasurround.model.AudioEffectPrefs
import com.demon.aurasurround.utils.PrefUtils
import com.demon.aurasurround.viewmodel.MainViewModel
import com.google.android.material.chip.Chip

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        viewModel.loadPrefs(this)

        setupUI()
        observeViewModel()
        checkModuleActive()
    }

    private fun checkModuleActive() {
        // If this method runs normally (not hooked), module is NOT active
        binding.tvModuleStatus.text = "● Module NOT Active"
        binding.tvModuleStatus.setTextColor(getColor(android.R.color.holo_red_light))
    }

    private fun setupUI() {
        // Master power toggle
        binding.switchMaster.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setEnabled(isChecked)
            updatePowerUI(isChecked)
        }

        // Effect mode chips
        binding.chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val chip = group.findViewById<Chip>(checkedIds[0])
            val mode = when (chip.tag as? String) {
                "8d"      -> AudioEffectPrefs.EffectMode.SURROUND_8D
                "concert" -> AudioEffectPrefs.EffectMode.CONCERT_HALL
                "stadium" -> AudioEffectPrefs.EffectMode.STADIUM
                "cave"    -> AudioEffectPrefs.EffectMode.CAVE
                "spatial" -> AudioEffectPrefs.EffectMode.HEADPHONE_SPATIAL
                "custom"  -> AudioEffectPrefs.EffectMode.CUSTOM
                else      -> AudioEffectPrefs.EffectMode.SURROUND_8D
            }
            viewModel.setMode(mode)
            updateModeDescription(mode)
        }

        // Virtualizer strength slider
        binding.sliderVirtualizer.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.setVirtualizerStrength(value.toInt())
                binding.tvVirtualizerValue.text = "${value.toInt() / 10}%"
            }
        }

        // Reverb slider
        binding.sliderReverb.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.setReverbLevel(value.toInt())
                binding.tvReverbValue.text = "${value.toInt() / 10}%"
            }
        }

        // Bass boost slider
        binding.sliderBass.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.setBassBoost(value.toInt())
                binding.tvBassValue.text = "${value.toInt() / 10}%"
            }
        }

        // 8D rotation speed slider
        binding.slider8DSpeed.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.setRotationSpeed(value)
                binding.tv8DSpeedValue.text = String.format("%.1fx", value)
            }
        }

        // Preset buttons
        binding.btnPreset8D.setOnClickListener { applyPreset(AudioEffectPrefs.preset8D()) }
        binding.btnPresetConcert.setOnClickListener { applyPreset(AudioEffectPrefs.presetConcert()) }
        binding.btnPresetStadium.setOnClickListener { applyPreset(AudioEffectPrefs.presetStadium()) }
        binding.btnPresetCave.setOnClickListener { applyPreset(AudioEffectPrefs.presetCave()) }
        binding.btnPresetSpatial.setOnClickListener { applyPreset(AudioEffectPrefs.presetSpatial()) }

        // Save button
        binding.btnSave.setOnClickListener {
            viewModel.savePrefs(this)
            Toast.makeText(this, "✓ Settings saved! Restart your music app.", Toast.LENGTH_LONG).show()
            binding.btnSave.text = "✓ Saved!"
            binding.btnSave.postDelayed({ binding.btnSave.text = "Save & Apply" }, 2000)
        }
    }

    private fun observeViewModel() {
        viewModel.prefs.observe(this) { prefs ->
            // Update UI from prefs without triggering listeners
            binding.switchMaster.isChecked = prefs.isEnabled
            updatePowerUI(prefs.isEnabled)

            binding.sliderVirtualizer.value = prefs.virtualizerStrength.toFloat()
            binding.tvVirtualizerValue.text = "${prefs.virtualizerStrength / 10}%"

            binding.sliderReverb.value = prefs.reverbLevel.toFloat()
            binding.tvReverbValue.text = "${prefs.reverbLevel / 10}%"

            binding.sliderBass.value = prefs.bassBoost.toFloat()
            binding.tvBassValue.text = "${prefs.bassBoost / 10}%"

            binding.slider8DSpeed.value = prefs.rotationSpeed
            binding.tv8DSpeedValue.text = String.format("%.1fx", prefs.rotationSpeed)

            // Select correct mode chip
            val chipId = when (prefs.effectMode) {
                AudioEffectPrefs.EffectMode.SURROUND_8D        -> binding.chip8D.id
                AudioEffectPrefs.EffectMode.CONCERT_HALL       -> binding.chipConcert.id
                AudioEffectPrefs.EffectMode.STADIUM            -> binding.chipStadium.id
                AudioEffectPrefs.EffectMode.CAVE               -> binding.chipCave.id
                AudioEffectPrefs.EffectMode.HEADPHONE_SPATIAL  -> binding.chipSpatial.id
                AudioEffectPrefs.EffectMode.CUSTOM             -> binding.chipCustom.id
            }
            binding.chipGroup.check(chipId)
            updateModeDescription(prefs.effectMode)

            // Show/hide 8D speed section
            binding.card8DSpeed.visibility =
                if (prefs.effectMode == AudioEffectPrefs.EffectMode.SURROUND_8D) View.VISIBLE
                else View.GONE
        }
    }

    private fun updatePowerUI(enabled: Boolean) {
        binding.ivPowerGlow.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
        binding.tvPowerLabel.text = if (enabled) "ON" else "OFF"
        binding.layoutControls.alpha = if (enabled) 1.0f else 0.5f
        binding.layoutControls.isEnabled = enabled
    }

    private fun updateModeDescription(mode: AudioEffectPrefs.EffectMode) {
        binding.tvModeDesc.text = when (mode) {
            AudioEffectPrefs.EffectMode.SURROUND_8D ->
                "🎧 Sound rotates 360° around your head. Best with earphones/ANC buds."
            AudioEffectPrefs.EffectMode.CONCERT_HALL ->
                "🎼 Large hall reverb. Feel like you're at a live concert."
            AudioEffectPrefs.EffectMode.STADIUM ->
                "🏟️ Massive stadium echo. Great for EDM & live tracks."
            AudioEffectPrefs.EffectMode.CAVE ->
                "🗿 Deep cave reverb. Eerie, immersive atmosphere."
            AudioEffectPrefs.EffectMode.HEADPHONE_SPATIAL ->
                "🔊 Optimized spatial audio for wired/wireless earphones."
            AudioEffectPrefs.EffectMode.CUSTOM ->
                "⚙️ Tune each parameter manually for your perfect sound."
        }
    }

    private fun applyPreset(preset: AudioEffectPrefs) {
        viewModel.applyPreset(preset)
        Toast.makeText(this, "Preset applied!", Toast.LENGTH_SHORT).show()
    }
}
