package com.example.puck

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdCallback
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import gameobjects.Settings
import utility.Storage
import java.time.LocalDate

class BallUnlockActivity : AppCompatActivity() {

    private lateinit var adRatio: TextView
    private lateinit var watchAdButton: Button
    private lateinit var view: BallUnlockView
    private var rewardedAd: RewardedAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Storage.darkMode) setTheme(R.style.DarkMode) else setTheme(R.style.LightMode)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ball_unlock)

        adRatio = findViewById(R.id.unlockAdRatio)
        watchAdButton = findViewById(R.id.unlockWatchAdButton)
        view = findViewById(R.id.ballUnlockView)
        val back = findViewById<Button>(R.id.backButton)

        Settings.adsLeft = Storage.adsRemaining
        Settings.adShownToday = LocalDate.parse(Storage.lastSeenAdDate).isEqual(LocalDate.now())
        refreshAdRatio()

        back.setOnClickListener { finish() }
        watchAdButton.setOnClickListener { showAd() }

        if (Settings.adShownToday) {
            watchAdButton.text = "Come back tomorrow"
            watchAdButton.isEnabled = false
        } else {
            MobileAds.initialize(this) {}
            loadAd()
        }
    }

    override fun onPause() {
        super.onPause()
        view.clearTails()
    }

    private fun refreshAdRatio() {
        adRatio.text = if (Settings.adsLeft > 0) "${Settings.adsLeft}/${Settings.maxAds}" else "All unlocked"
        view.invalidate()
    }

    private fun loadAd() {
        val id = "ca-app-pub-3940256099942544/5224354917"
        rewardedAd = RewardedAd(this, id)
        val callback = object : RewardedAdLoadCallback() {
            override fun onRewardedAdLoaded() {
                watchAdButton.isEnabled = true
                Log.i("BallUnlock", "Ad loaded")
            }
            override fun onRewardedAdFailedToLoad(errorCode: Int) {
                watchAdButton.isEnabled = false
                Log.i("BallUnlock", "Ad failed: $errorCode")
            }
        }
        rewardedAd?.loadAd(AdRequest.Builder().build(), callback)
    }

    private fun showAd() {
        val ad = rewardedAd ?: return
        if (!ad.isLoaded) return
        val activityCtx: Activity = this
        val adCallback = object : RewardedAdCallback() {
            override fun onRewardedAdOpened() {}
            override fun onRewardedAdClosed() {
                watchAdButton.text = "Come back tomorrow"
                watchAdButton.isEnabled = false
            }
            override fun onUserEarnedReward(@NonNull reward: RewardItem) {
                Settings.adsLeft -= 2
                if (Settings.adsLeft < 0) Settings.adsLeft = 0
                Storage.storeAndUpdateAdsRemaining(Settings.adsLeft)
                Settings.adShownToday = true
                refreshAdRatio()
            }
            override fun onRewardedAdFailedToShow(errorCode: Int) {}
        }
        ad.show(activityCtx, adCallback)
    }
}
