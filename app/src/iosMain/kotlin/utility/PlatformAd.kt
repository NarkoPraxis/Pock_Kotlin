package utility

// iOS rewarded-ad integration. The actual Google Mobile Ads SDK calls live in
// Swift (iosApp/iosApp/AdMobBridge.swift). On app launch Swift assigns handlers
// to IosAdProvider; this object delegates the commonMain expect calls to them.
object IosAdProvider {
    var loadHandler: ((adUnitId: String, onLoaded: () -> Unit, onFailed: () -> Unit) -> Unit)? = null
    var showHandler: ((onEarned: () -> Unit, onDismissed: () -> Unit) -> Unit)? = null
    var readyProvider: () -> Boolean = { false }
}

actual object PlatformAd {
    actual val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/1712485313"
    actual val isReady: Boolean get() = IosAdProvider.readyProvider()

    actual fun loadRewardedAd(adUnitId: String, onLoaded: () -> Unit, onFailed: () -> Unit) {
        val h = IosAdProvider.loadHandler
        if (h == null) onFailed() else h(adUnitId, onLoaded, onFailed)
    }

    actual fun showRewardedAd(onEarned: () -> Unit, onDismissed: () -> Unit) {
        val h = IosAdProvider.showHandler
        if (h == null) onDismissed() else h(onEarned, onDismissed)
    }
}
