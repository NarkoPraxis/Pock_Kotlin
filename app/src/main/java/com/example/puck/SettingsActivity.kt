package com.example.puck

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import utility.Sounds
import utility.Storage

class SettingsActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Storage.darkMode) setTheme(R.style.SettingsThemeDark) else setTheme(R.style.SettingsThemeLight)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.settings_activity)

        val toolbar = findViewById<MaterialToolbar>(R.id.settings_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val pager = findViewById<ViewPager2>(R.id.settings_pager)
        val tabs = findViewById<TabLayout>(R.id.settings_tabs)
        pager.adapter = SettingsPagerAdapter(this)

        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = tabName(position)
        }.attach()

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                supportActionBar?.title = "${tabName(position)} Settings"
            }
        })
        supportActionBar?.title = "${tabName(0)} Settings"
    }

    private fun tabName(position: Int) = when (position) {
        0 -> "Graphics"
        1 -> "Sound"
        else -> "Gameplay"
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.settings_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_restore_defaults -> {
                AlertDialog.Builder(this)
                    .setMessage("Are you sure you want to reset all your settings?")
                    .setPositiveButton("Reset") { _, _ ->
                        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                        prefs.edit { clear() }
                        PreferenceManager.setDefaultValues(this, R.xml.gameplay_preferences, true)
                        PreferenceManager.setDefaultValues(this, R.xml.visual_preferences, true)
                        prefs.edit {
                            putInt("sound_master_volume", 100)
                            putInt("sound_background_volume", 100)
                            putInt("sound_sfx_volume", 100)
                            putBoolean("sound_master_muted", false)
                            putBoolean("sound_background_muted", false)
                            putBoolean("sound_sfx_muted", false)
                        }
                        Storage.resetScoreOffsets()
                        Sounds.applyBackgroundVolume()
                        recreate()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this)
        Sounds.playMenuAmbiance()
    }

    override fun onPause() {
        super.onPause()
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
        Sounds.pauseAll()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (key == "darkmode") recreate()
    }

    private class SettingsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount() = 3
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> VisualFragment()
            1 -> SoundFragment()
            else -> GameplayFragment()
        }
    }

    class GameplayFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.gameplay_preferences, rootKey)
        }
    }

    class VisualFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.visual_preferences, rootKey)
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            return when (preference.key) {
                "calibrate_score" -> {
                    startActivity(Intent(requireContext(), ScoreCalibrationActivity::class.java))
                    true
                }
                else -> super.onPreferenceTreeClick(preference)
            }
        }
    }

    class SoundFragment : Fragment() {

        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
        ): View = inflater.inflate(R.layout.fragment_sound_settings, container, false)

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            val accentColor = requireContext().getColor(R.color.effectColor)
            val mutedColor = requireContext().getColor(R.color.inertSecondary)

            setupVolumeControl(
                view,
                seekId = R.id.seek_master,
                valueId = R.id.value_master,
                muteId = R.id.mute_master,
                accentColor = accentColor,
                mutedColor = mutedColor,
                getVolume = { Storage.soundMasterVolume },
                getMuted = { Storage.soundMasterMuted },
                saveVolume = { v -> Storage.saveSoundMasterVolume(v); Sounds.applyBackgroundVolume() },
                saveMuted = { m -> Storage.saveSoundMasterMuted(m); Sounds.applyBackgroundVolume() }
            )
            setupVolumeControl(
                view,
                seekId = R.id.seek_background,
                valueId = R.id.value_background,
                muteId = R.id.mute_background,
                accentColor = accentColor,
                mutedColor = mutedColor,
                getVolume = { Storage.soundBackgroundVolume },
                getMuted = { Storage.soundBackgroundMuted },
                saveVolume = { v -> Storage.saveSoundBackgroundVolume(v); Sounds.applyBackgroundVolume() },
                saveMuted = { m -> Storage.saveSoundBackgroundMuted(m); Sounds.applyBackgroundVolume() }
            )
            setupVolumeControl(
                view,
                seekId = R.id.seek_sfx,
                valueId = R.id.value_sfx,
                muteId = R.id.mute_sfx,
                accentColor = accentColor,
                mutedColor = mutedColor,
                getVolume = { Storage.soundSfxVolume },
                getMuted = { Storage.soundSfxMuted },
                saveVolume = { v -> Storage.saveSoundSfxVolume(v) },
                saveMuted = { m -> Storage.saveSoundSfxMuted(m) }
            )
        }

        private fun setupVolumeControl(
            root: View,
            seekId: Int,
            valueId: Int,
            muteId: Int,
            accentColor: Int,
            mutedColor: Int,
            getVolume: () -> Int,
            getMuted: () -> Boolean,
            saveVolume: (Int) -> Unit,
            saveMuted: (Boolean) -> Unit
        ) {
            val seek = root.findViewById<SeekBar>(seekId)
            val label = root.findViewById<TextView>(valueId)
            val muteBtn = root.findViewById<MaterialButton>(muteId)

            var isMuted = getMuted()
            seek.progress = getVolume()
            label.text = "${getVolume()}%"
            applyMuteVisuals(seek, muteBtn, isMuted, accentColor, mutedColor)

            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    label.text = "$progress%"
                    saveVolume(progress)
                }
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}
            })

            muteBtn.setOnClickListener {
                isMuted = !isMuted
                saveMuted(isMuted)
                applyMuteVisuals(seek, muteBtn, isMuted, accentColor, mutedColor)
            }
        }

        private fun applyMuteVisuals(
            seek: SeekBar, btn: MaterialButton, muted: Boolean, accentColor: Int, mutedColor: Int
        ) {
            seek.isEnabled = !muted
            seek.alpha = if (muted) 0.4f else 1f
            if (muted) {
                btn.text = "Muted"
                btn.strokeColor = ColorStateList.valueOf(mutedColor)
                btn.setTextColor(mutedColor)
            } else {
                btn.text = "Mute"
                btn.strokeColor = ColorStateList.valueOf(accentColor)
                btn.setTextColor(accentColor)
            }
        }
    }
}
