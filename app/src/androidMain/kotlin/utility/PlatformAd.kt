package utility

// Android rewarded ads are loaded and shown directly in BallUnlockActivity and MainActivity
// using the Google Mobile Ads SDK. This actual satisfies the commonMain expect; it is never
// called on Android.
actual object PlatformAd {
    actual val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    actual val isReady: Boolean get() = false
    actual fun loadRewardedAd(adUnitId: String, onLoaded: () -> Unit, onFailed: () -> Unit) {}
    actual fun showRewardedAd(onEarned: () -> Unit, onDismissed: () -> Unit) {}
}
