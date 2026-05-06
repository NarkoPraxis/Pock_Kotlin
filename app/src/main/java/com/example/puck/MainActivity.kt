package com.example.puck

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import gameobjects.BotConfig
import gameobjects.Settings
import com.example.puck.databinding.ActivityMainBinding
import utility.ShareHelper
import utility.Sounds
import utility.Storage
import android.view.View

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var pendingShareToast: String? = null
    private var appliedDarkMode = false
    private var rewardedAd: RewardedAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Storage.initialize(this)
        appliedDarkMode = Storage.darkMode
        if (appliedDarkMode) setTheme(R.style.DarkMode) else setTheme(R.style.LightMode)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Sounds.initialize(this)
        Sounds.playMenuAmbiance()
        PreferenceManager.setDefaultValues(this, R.xml.gameplay_preferences, false)
        PreferenceManager.setDefaultValues(this, R.xml.controls_preferences, false)
        PreferenceManager.setDefaultValues(this, R.xml.visual_preferences, false)

        Settings.unlockProgress = Storage.unlockProgress
        binding.unlockProgressBar.progress = Settings.unlockProgress

        if (Storage.unlockProgress < 100) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
                    .build()
            )
            MobileAds.initialize(this) {}
        }

        if (Storage.shareRewardClaimed) {
            binding.shareButton.text = getString(R.string.menu_share)
        }
        binding.shareButton.setOnClickListener { shareAndReward() }
        binding.rewardedAdButton.setOnClickListener { showAd() }

        updateAdButton()
        if (Storage.canWatchAdNow()) loadAd()

        binding.languageButton.text = currentLanguageFlag()
        binding.languageButton.setOnClickListener { showLanguagePicker() }
    }

    private fun currentLanguageFlag(): String {
        val locale = AppCompatDelegate.getApplicationLocales()[0]
        return when (locale?.language) {
            "de" -> "🇩🇪"
            "es" -> "🇪🇸"
            "fr" -> "🇫🇷"
            "ja" -> "🇯🇵"
            "zh" -> "🇨🇳"
            else -> englishFlag()
        }
    }

    private fun englishFlag(): String =
        if (java.util.Locale.getDefault().country == "US") "🇺🇸" else "🇬🇧"

    private fun showLanguagePicker() {
        val languages = listOf(
            "${englishFlag()}  English" to "",
            "🇩🇪  Deutsch" to "de",
            "🇪🇸  Español" to "es",
            "🇫🇷  Français" to "fr",
            "🇯🇵  日本語" to "ja",
            "🇨🇳  中文" to "zh"
        )
        val labels = languages.map { it.first }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setItems(labels) { _, which ->
                val code = languages[which].second
                val localeList = if (code.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(code)
                }
                AppCompatDelegate.setApplicationLocales(localeList)
            }
            .show()
    }

    private fun shareAndReward() {
        ShareHelper.shareAppPromo(this) { grantShareReward() }
    }

    private fun grantShareReward() {
        if (Storage.shareRewardClaimed) {
            pendingShareToast = getString(R.string.menu_share_already_claimed)
            return
        }
        Storage.markShareRewardClaimed()
        Storage.addBonusProgress(10)
        Settings.unlockProgress = Storage.unlockProgress
        binding.unlockProgressBar.progress = Settings.unlockProgress
        pendingShareToast = getString(R.string.menu_share_thanks)
    }

    override fun onPause() {
        super.onPause()
        Sounds.pauseAll()
    }

    override fun onResume() {
        super.onResume()
        if (Storage.darkMode != appliedDarkMode) {
            recreate()
            return
        }
        Sounds.soundPool.autoResume()
        Sounds.playMenuAmbiance()
        // Re-check ad availability each time the screen comes back into view.
        Settings.unlockProgress = Storage.unlockProgress
        binding.unlockProgressBar.progress = Settings.unlockProgress
        updateAdButton()
        if (rewardedAd == null && Storage.unlockProgress < 100 && Storage.canWatchAdNow()) loadAd()
        binding.languageButton.text = currentLanguageFlag()
        pendingShareToast?.let {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            pendingShareToast = null
        }
    }

    fun goToGameView(view: View) {
        Settings.startWithTutorial = false
        Settings.isSinglePlayer = false
        val intent = Intent(this, GameActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        Sounds.playGameAmbiance()
        startActivity(intent)
    }

    fun goToSinglePlayer(view: View) {
        val options = arrayOf(getString(R.string.difficulty_easy), getString(R.string.difficulty_medium), getString(R.string.difficulty_hard))
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.difficulty_choose))
            .setItems(options) { _, which ->
                Settings.botConfig = when (which) {
                    0 -> BotConfig.Easy
                    1 -> BotConfig.Medium
                    else -> BotConfig.Hard
                }
                Settings.isSinglePlayer = true
                Settings.startWithTutorial = false
                val intent = Intent(this, GameActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                Sounds.playGameAmbiance()
                startActivity(intent)
            }
            .show()
    }

    fun goToSettings(view: View) {
        val intent = Intent(this, SettingsActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    fun goToBalls(view: View) {
        val intent = Intent(this, BallUnlockActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    private fun loadAd() {
        val id = "ca-app-pub-3940256099942544/5224354917" // TODO replace with live rewarded ad ID before launch
        RewardedAd.load(
            this,
            id,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    updateAdButton()
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    updateAdButton()
                }
            }
        )
    }

    fun showAd(view: View = binding.rewardedAdButton) {
        val ad = rewardedAd ?: return
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                updateAdButton()
                if (Storage.canWatchAdNow()) loadAd()
            }
        }
        ad.show(this as Activity, OnUserEarnedRewardListener { _ ->
            Storage.recordAdWatched()
            Settings.unlockProgress = Storage.unlockProgress
            binding.unlockProgressBar.progress = Settings.unlockProgress
            updateAdButton()
        })
    }

    private fun updateAdButton() {
        val progress = Storage.unlockProgress
        if (progress >= 100) {
            binding.unlockProgressBar.visibility = View.GONE
            binding.rewardedAdButton.visibility = View.GONE
            return
        }
        binding.unlockProgressBar.visibility = View.VISIBLE
        binding.rewardedAdButton.visibility = View.VISIBLE
        val watchedToday = Storage.adsWatchedToday()
        if (watchedToday >= 5) {
            binding.rewardedAdButton.text = getString(R.string.menu_come_back_tomorrow)
            binding.rewardedAdButton.isEnabled = false
            return
        }
        val mins = Storage.minutesUntilNextAd()
        if (mins > 0) {
            val timeText = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
            binding.rewardedAdButton.text = getString(R.string.menu_next_ad_in, timeText)
            binding.rewardedAdButton.isEnabled = false
            return
        }
        binding.rewardedAdButton.isEnabled = rewardedAd != null
        binding.rewardedAdButton.text = getString(R.string.menu_watch_ad)
    }
}
