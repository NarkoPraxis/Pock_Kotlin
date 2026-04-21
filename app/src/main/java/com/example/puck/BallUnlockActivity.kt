package com.example.puck

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
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
import utility.PurchaseManager
import utility.Storage

class BallUnlockActivity : AppCompatActivity() {

    private lateinit var progressBar: UnlockProgressBar
    private lateinit var watchAdButton: Button
    private lateinit var unlockAllButton: Button
    private lateinit var restoreButton: Button
    private lateinit var view: BallUnlockView
    private var rewardedAd: RewardedAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Storage.darkMode) setTheme(R.style.DarkMode) else setTheme(R.style.LightMode)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ball_unlock)

        progressBar = findViewById(R.id.unlockProgressBar)
        watchAdButton = findViewById(R.id.unlockWatchAdButton)
        unlockAllButton = findViewById(R.id.unlockAllButton)
        restoreButton = findViewById(R.id.unlockRestoreButton)
        view = findViewById(R.id.ballUnlockView)
        val back = findViewById<Button>(R.id.backButton)

        MobileAds.initialize(this) {}

        Settings.unlockProgress = Storage.unlockProgress
        progressBar.progress = Settings.unlockProgress

        back.setOnClickListener { finish() }
        watchAdButton.setOnClickListener { showAd() }
        unlockAllButton.setOnClickListener { PurchaseManager.purchaseUnlockAll(this) }
        restoreButton.setOnClickListener {
            PurchaseManager.restorePurchases(this) { success ->
                if (success) refreshUI()
            }
        }

        PurchaseManager.initialize(this) { refreshUI() }

        refreshUI()
        if (canLoadAdNow()) loadAd()
    }

    override fun onResume() {
        super.onResume()
        Settings.unlockProgress = Storage.unlockProgress
        progressBar.progress = Settings.unlockProgress
        refreshUI()
        if (rewardedAd == null && canLoadAdNow()) loadAd()
    }

    private fun canLoadAdNow(): Boolean {
        if (Storage.adsWatchedToday() >= 5) return false
        return Storage.minutesUntilNextAd() == 0L
    }

    override fun onPause() {
        super.onPause()
        view.clearTails()
    }

    override fun onDestroy() {
        super.onDestroy()
        PurchaseManager.destroy()
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
                    refreshUI()
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    Log.i("BallUnlock", "Ad failed: ${error.code}")
                    refreshUI()
                }
            }
        )
    }

    private fun showAd() {
        val ad = rewardedAd ?: return
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                refreshUI()
                if (canLoadAdNow()) loadAd()
            }
        }
        ad.show(this as Activity, OnUserEarnedRewardListener { _ ->
            Storage.recordAdWatched()
            Settings.unlockProgress = Storage.unlockProgress
            progressBar.progress = Settings.unlockProgress
            view.invalidate()
            refreshUI()
        })
    }

    private fun refreshUI() {
        val progress = Storage.unlockProgress
        Settings.unlockProgress = progress
        progressBar.progress = progress
        view.invalidate()

        if (progress >= 100) {
            progressBar.visibility = View.GONE
            unlockAllButton.visibility = View.GONE
            restoreButton.visibility = View.GONE
            watchAdButton.visibility = View.VISIBLE
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
            watchAdButton.text = "Support Me?"
            return
        }

        progressBar.visibility = View.VISIBLE
        unlockAllButton.visibility = View.VISIBLE
        restoreButton.visibility = View.VISIBLE

        val watchedToday = Storage.adsWatchedToday()
        if (watchedToday >= 5) {
            watchAdButton.visibility = View.VISIBLE
            watchAdButton.text = "Come back tomorrow"
            watchAdButton.isEnabled = false
            return
        }
        val mins = Storage.minutesUntilNextAd()
        if (mins > 0) {
            val timeText = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
            watchAdButton.visibility = View.VISIBLE
            watchAdButton.text = "Next ad in $timeText"
            watchAdButton.isEnabled = false
            return
        }
        watchAdButton.visibility = View.VISIBLE
        watchAdButton.isEnabled = rewardedAd != null
        watchAdButton.text = "Watch Ad to Unlock"
    }
}
