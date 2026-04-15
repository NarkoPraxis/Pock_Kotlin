package com.example.puck

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import gameobjects.Settings
import utility.Storage

class BallUnlockActivity : AppCompatActivity() {

    private lateinit var progressBar: UnlockProgressBar
    private lateinit var watchAdButton: Button
    private lateinit var view: BallUnlockView
    private var rewardedAd: RewardedAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Storage.darkMode) setTheme(R.style.DarkMode) else setTheme(R.style.LightMode)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ball_unlock)

        progressBar = findViewById(R.id.unlockProgressBar)
        watchAdButton = findViewById(R.id.unlockWatchAdButton)
        view = findViewById(R.id.ballUnlockView)
        val back = findViewById<Button>(R.id.backButton)

        MobileAds.initialize(this) {}

        Settings.unlockProgress = Storage.unlockProgress
        progressBar.progress = Settings.unlockProgress

        back.setOnClickListener { finish() }
        watchAdButton.setOnClickListener { showAd() }

        updateAdButton()
        if (Storage.canWatchAdNow()) loadAd()
    }

    override fun onResume() {
        super.onResume()
        // Refresh state when returning from another screen or after time passes.
        Settings.unlockProgress = Storage.unlockProgress
        progressBar.progress = Settings.unlockProgress
        updateAdButton()
        if (rewardedAd == null && Storage.canWatchAdNow()) loadAd()
    }

    override fun onPause() {
        super.onPause()
        view.clearTails()
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
                    Log.i("BallUnlock", "Ad loaded")
                    updateAdButton()
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    Log.i("BallUnlock", "Ad failed: ${error.code}")
                    updateAdButton()
                }
            }
        )
    }

    private fun showAd() {
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
            progressBar.progress = Settings.unlockProgress
            view.invalidate()
            updateAdButton()
        })
    }

    private fun updateAdButton() {
        val progress = Storage.unlockProgress
        progressBar.visibility = if (progress >= 100) android.view.View.GONE else android.view.View.VISIBLE
        if (progress >= 100) {
            watchAdButton.text = "All balls unlocked!"
            watchAdButton.isEnabled = rewardedAd != null
            return
        }
        val watchedToday = Storage.adsWatchedToday()
        if (watchedToday >= 5) {
            watchAdButton.text = "Come back tomorrow"
            watchAdButton.isEnabled = false
            return
        }
        val mins = Storage.minutesUntilNextAd()
        if (mins > 0) {
            val timeText = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
            watchAdButton.text = "Next ad in $timeText"
            watchAdButton.isEnabled = false
            return
        }
        watchAdButton.isEnabled = rewardedAd != null
        watchAdButton.text = "Watch Ad to Unlock"
    }
}
