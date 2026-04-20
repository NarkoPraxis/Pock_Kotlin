package com.example.puck

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import utility.Sounds
import utility.Storage

class SettingsActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Storage.darkMode) setTheme(R.style.SettingsThemeDark) else setTheme(R.style.SettingsThemeLight)
        super.onCreate(savedInstanceState)
        // Let the AppBarLayout in the layout absorb the status bar inset (edge-to-edge)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.settings_activity)
        val toolbar = findViewById<MaterialToolbar>(R.id.settings_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
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

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            return when (preference.key) {
                "calibrate_score" -> {
                    startActivity(Intent(requireContext(), ScoreCalibrationActivity::class.java))
                    true
                }
                "restore_defaults" -> {
                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .edit().clear().apply()
                    PreferenceManager.setDefaultValues(requireContext(), R.xml.root_preferences, true)
                    Storage.resetScoreOffsets()
                    requireActivity().recreate()
                    true
                }
                else -> super.onPreferenceTreeClick(preference)
            }
        }
    }
}