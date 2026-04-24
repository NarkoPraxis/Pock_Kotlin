package com.example.puck

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
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
            tab.text = when (position) {
                0 -> "Gameplay"
                1 -> "Controls"
                else -> "Visual"
            }
        }.attach()
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

    private class SettingsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount() = 3
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> GameplayFragment()
            1 -> ControlsFragment()
            else -> VisualFragment()
        }
    }

    class GameplayFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.gameplay_preferences, rootKey)
        }
    }

    class ControlsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.controls_preferences, rootKey)
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
                "restore_defaults" -> {
                    val ctx = requireContext()
                    PreferenceManager.getDefaultSharedPreferences(ctx).edit().clear().apply()
                    PreferenceManager.setDefaultValues(ctx, R.xml.gameplay_preferences, true)
                    PreferenceManager.setDefaultValues(ctx, R.xml.controls_preferences, true)
                    PreferenceManager.setDefaultValues(ctx, R.xml.visual_preferences, true)
                    Storage.resetScoreOffsets()
                    requireActivity().recreate()
                    true
                }
                else -> super.onPreferenceTreeClick(preference)
            }
        }
    }
}
