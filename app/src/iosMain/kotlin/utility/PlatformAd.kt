package utility

// iOS Google Mobile Ads SDK is not yet integrated. To enable real rewarded ads:
// 1. Add `pod 'Google-Mobile-Ads-SDK'` to iosApp/Podfile and run `pod install`
// 2. Add GADApplicationIdentifier to iosApp/iosApp/Info.plist
// 3. Create a Swift @objc class wrapping GADRewardedAd with load/show callbacks
// 4. Expose it to Kotlin via cinterop or a Kotlin companion object bridge
// 5. Replace the stub bodies below with real SDK calls
actual object PlatformAd {
    actual val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/1712485313"
    actual val isReady: Boolean get() = false

    actual fun loadRewardedAd(adUnitId: String, onLoaded: () -> Unit, onFailed: () -> Unit) {
        onFailed()
    }

    actual fun showRewardedAd(onEarned: () -> Unit, onDismissed: () -> Unit) {
        onDismissed()
    }
}
