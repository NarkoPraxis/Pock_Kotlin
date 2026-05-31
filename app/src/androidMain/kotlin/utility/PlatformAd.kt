package utility

import android.app.Activity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.runoutzone.pockpock.BuildConfig
import gameobjects.Settings

/**
 * Holds the Activity currently hosting Compose so commonMain code (CBC/CCP via [AdUnlock])
 * can present a rewarded ad. Set from ComposeMainActivity.onResume / cleared onPause.
 */
object AdActivityProvider {
    var activity: Activity? = null
}

actual object PlatformAd {
    actual val TEST_REWARDED_AD_UNIT_ID = BuildConfig.ADMOB_REWARDED_UNIT_ID

    private var loadedAd: RewardedAd? = null

    actual val isReady: Boolean get() = loadedAd != null

    actual fun loadRewardedAd(adUnitId: String, onLoaded: () -> Unit, onFailed: () -> Unit) {
        val activity = AdActivityProvider.activity
        if (activity == null) { onFailed(); return }
        RewardedAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { loadedAd = ad; onLoaded() }
                override fun onAdFailedToLoad(error: LoadAdError) { loadedAd = null; onFailed() }
            }
        )
    }

    actual fun showRewardedAd(onEarned: () -> Unit, onDismissed: () -> Unit) {
        val activity = AdActivityProvider.activity
        val ad = loadedAd
        if (activity == null || ad == null) { onDismissed(); return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Settings.adIsPlaying = true
                Sounds.muteForAd()
            }
            override fun onAdDismissedFullScreenContent() {
                Settings.adIsPlaying = false
                Sounds.unmuteForAd()
                loadedAd = null
                onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                Settings.adIsPlaying = false
                Sounds.unmuteForAd()
                loadedAd = null
                onDismissed()
            }
        }
        ad.show(activity, OnUserEarnedRewardListener { onEarned() })
    }
}
