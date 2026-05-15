package utility

expect object PlatformAd {
    val TEST_REWARDED_AD_UNIT_ID: String
    val isReady: Boolean
    fun loadRewardedAd(adUnitId: String, onLoaded: () -> Unit, onFailed: () -> Unit)
    fun showRewardedAd(onEarned: () -> Unit, onDismissed: () -> Unit)
}
