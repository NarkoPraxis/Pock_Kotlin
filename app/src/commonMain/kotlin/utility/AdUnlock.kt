package utility

/**
 * Single commonMain entry point for unlocking a style component or color by watching a
 * rewarded ad. Works on both platforms: Android via [PlatformAd]'s actual (which presents
 * through AdActivityProvider), iOS via the AdMobBridge handlers.
 *
 * On reward it runs the existing meter economy ([Storage.recordAdWatched], +2 + cooldown)
 * and then the caller's [grant] (e.g. `Storage.unlockSkin(type)`).
 */
object AdUnlock {

    /** True when an unlock ad may be shown right now (not fully unlocked, within daily/cooldown caps). */
    fun canUnlockNow(): Boolean = Storage.unlockProgress < 100 && Storage.canWatchAdNow()

    /**
     * Loads (if needed) and shows a rewarded ad. On reward, bumps the meter, runs [grant],
     * then reports success. [onResult] is always called exactly once.
     */
    fun watchAdToUnlock(grant: () -> Unit, onResult: (Boolean) -> Unit) {
        if (!canUnlockNow()) { onResult(false); return }
        var rewarded = false

        fun show() {
            PlatformAd.showRewardedAd(
                onEarned = {
                    Storage.recordAdWatched()
                    grant()
                    rewarded = true
                },
                onDismissed = { onResult(rewarded) }
            )
        }

        if (PlatformAd.isReady) {
            show()
        } else {
            PlatformAd.loadRewardedAd(
                adUnitId = PlatformAd.TEST_REWARDED_AD_UNIT_ID,
                onLoaded = { show() },
                onFailed = { onResult(false) }
            )
        }
    }
}
