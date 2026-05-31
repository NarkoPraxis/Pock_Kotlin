package com.runoutzone.pockpock

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import gameobjects.Settings
import utility.AdActivityProvider
import utility.PurchaseManager
import utility.Sounds
import utility.Storage

class MainActivity : AppCompatActivity() {

    private var appliedDarkMode = false

    private val darkModeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "darkmode") recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Storage.initialize(this)
        appliedDarkMode = Storage.darkMode
        if (appliedDarkMode) setTheme(R.style.DarkMode) else setTheme(R.style.LightMode)
        super.onCreate(savedInstanceState)

        Sounds.initialize(this)

        if (Storage.unlockProgress < 100) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
                    .build()
            )
            MobileAds.initialize(this) {}
        }

        PurchaseManager.initialize(this) {
            Settings.unlockProgress = Storage.unlockProgress
        }

        setContent { AppRoot() }
    }

    override fun onPause() {
        super.onPause()
        if (AdActivityProvider.activity === this) AdActivityProvider.activity = null
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(darkModeListener)
        Sounds.pauseAll()
    }

    override fun onResume() {
        super.onResume()
        AdActivityProvider.activity = this
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(darkModeListener)
        if (Storage.darkMode != appliedDarkMode) {
            recreate()
            return
        }
        // muteForAd() / unmuteForAd() should be called from ad lifecycle callbacks here
        Sounds.resumeAll()
        Sounds.playMenuAmbiance()
        Settings.unlockProgress = Storage.unlockProgress
    }

    override fun onDestroy() {
        super.onDestroy()
        PurchaseManager.destroy()
    }
}
