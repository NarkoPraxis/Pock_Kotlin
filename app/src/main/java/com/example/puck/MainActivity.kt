package com.example.puck

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.transition.Explode
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdCallback
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import gameobjects.Settings
import com.example.puck.databinding.ActivityMainBinding
import utility.ShareHelper
import utility.Sounds
import utility.Storage
import java.time.LocalDate


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var pendingShareToast: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Storage.initialize(this)
        Sounds.initialize(this)
        Sounds.playMenuAmbiance()
        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false)

//        with(window) {
//            requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
//            exitTransition = Explode()
//        }


        Settings.adsLeft = Storage.adsRemaining
        Settings.adShownToday = LocalDate.parse(Storage.lastSeenAdDate).isEqual(LocalDate.now())
        binding.AdRatioText.text = if (Settings.adsLeft > 0) "${Settings.adsLeft}/${Settings.maxAds}" else ""
        MobileAds.initialize(this) { }
        loadAds(binding.AdRatioText)

        if (Storage.shareRewardClaimed) {
            binding.shareButton.text = "Share"
        }
        binding.shareButton.setOnClickListener { shareAndReward() }
    }

    private fun shareAndReward() {
        ShareHelper.shareAppPromo(this) { grantShareReward() }
    }

    private fun grantShareReward() {
        if (Storage.shareRewardClaimed) {
            pendingShareToast = "Thanks for sharing again! The reward has already been claimed."
            return
        }
        Storage.markShareRewardClaimed()
        Settings.adsLeft -= 5
        Storage.storeAdsRemaining(Settings.adsLeft)
        binding.AdRatioText.text = if (Settings.adsLeft > 0) "${Settings.adsLeft}/${Settings.maxAds}" else ""
        pendingShareToast = "Thanks for sharing! 5 ad credits earned."
    }


    override fun onPause() {
        super.onPause()
        Sounds.pauseAll()
    }

    override fun onResume() {
        super.onResume()
        Sounds.soundPool.autoResume()
        Sounds.playMenuAmbiance()
        pendingShareToast?.let {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            pendingShareToast = null
        }
    }

    fun goToGameView(view: View) {
        Settings.startWithTutorial = false;
        val intent = Intent(this, GameActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        Sounds.playGameAmbiance()
//        startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(this).toBundle())
        startActivity(intent)
    }

    fun goToTutorial(view: View) {
        Settings.startWithTutorial = true;
        val intent = Intent(this, tutorial::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    fun goToSettings(view: View) {
        val intent = Intent(this, SettingsActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    fun goToAds(view: View) {
        val intent = Intent(this, Ads::class.java)
        startActivity(intent)
    }


    private lateinit var rewardedAd : RewardedAd
    fun loadAds(view: View) {
        var id = "ca-app-pub-3940256099942544/5224354917" // todo replace this with rewarded Ad id before launching on store
//      var id = " ca-app-pub-1111532606958888/6682727846") // This is the live APP


        rewardedAd = RewardedAd(this, id);


        val adLoadCallback = object: RewardedAdLoadCallback() {
            override fun onRewardedAdLoaded() {
                super.onRewardedAdLoaded()
                binding.rewardedAdButton.isEnabled = true
                Log.i("AdLoad", "OnRewardedAd Loaded");
            }
            override fun onRewardedAdFailedToLoad(errorCode: Int) {
                super.onRewardedAdLoaded()
                binding.rewardedAdButton.isEnabled = false
                Log.i("AdLoad", "OnRewardedAd Failed To Load")
            }
        }

        rewardedAd.loadAd(AdRequest.Builder().build(), adLoadCallback)

    }

    fun showAd(view: View) {
        if (rewardedAd.isLoaded) {
            val activityContext: Activity = this@MainActivity
            val adCallback = object: RewardedAdCallback() {
                override fun onRewardedAdOpened() {

                }
                override fun onRewardedAdClosed() {
                    binding.rewardedAdButton.text = "Come back tomorrow for your next ad"
                    binding.rewardedAdButton.isEnabled = false;
                }
                override fun onUserEarnedReward(@NonNull reward: RewardItem) {
                    Settings.adsLeft -= 2
                    binding.AdRatioText.text = "${Settings.adsLeft}/${Settings.maxAds}"
                    Storage.storeAdsRemaining(Settings.adsLeft)
                }
                override fun onRewardedAdFailedToShow(errorCode: Int) {
                    // Ad failed to display.
                }
            }
            rewardedAd.show(activityContext, adCallback)
        }
        else {
            Log.d("ShowedAd", "The rewarded ad wasn't loaded yet.")
        }
    }

}
