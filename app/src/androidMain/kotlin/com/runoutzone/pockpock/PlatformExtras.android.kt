package com.runoutzone.pockpock

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import gameobjects.Settings
import org.jetbrains.compose.resources.stringResource
import pock_kotlin.app.generated.resources.*
import utility.PaintBucket
import utility.PurchaseManager
import utility.ShareHelper
import utility.Sounds
import utility.Storage

@Composable
actual fun PlatformMenuExtras() {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    var rewardedAd by remember { mutableStateOf<RewardedAd?>(null) }
    val unlockProgress = Storage.unlockProgress
    var languageFlag by remember { mutableStateOf(getCurrentLanguageFlag()) }

    LaunchedEffect(Unit) {
        if (Storage.unlockProgress < 100 && Storage.canWatchAdNow()) {
            loadRewardedAd(activity) { ad ->
                rewardedAd = ad
            }
        }
    }

    val isDark = LocalDarkMode.current

    val strComeBackTomorrow = stringResource(Res.string.come_back_tomorrow)
    val strWatchAdToUnlock = stringResource(Res.string.watch_ad_to_unlock)
    val strShareThanks = stringResource(Res.string.share_thanks)
    val strShareAlreadyClaimed = stringResource(Res.string.share_already_claimed)

    val watchedToday = Storage.adsWatchedToday()
    val minsUntil = Storage.minutesUntilNextAd()
    val timeStr = if (minsUntil >= 60) "${minsUntil / 60}h ${minsUntil % 60}m" else "${minsUntil}m"
    val strNextAdIn = stringResource(Res.string.next_ad_in, timeStr)

    val menuButtonColors = ButtonDefaults.buttonColors(
        containerColor = if (isDark) PaintBucket.menuButtonDark else PaintBucket.menuButtonLight,
        contentColor = if (isDark) PaintBucket.white else PaintBucket.menuBackgroundDark,
        disabledContainerColor = if (isDark) PaintBucket.segmentInactiveDark else Color(0xFFE8E0FF),
        disabledContentColor = if (isDark) Color(0xFF888899) else Color(0xFF8877AA)
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (unlockProgress < 100) {
            val adButtonText = when {
                watchedToday >= 5 -> strComeBackTomorrow
                minsUntil > 0 -> strNextAdIn
                else -> strWatchAdToUnlock
            }
            val adEnabled = watchedToday < 5 && minsUntil == 0L && rewardedAd != null

            Button(
                onClick = {
                    val ad = rewardedAd ?: return@Button
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdShowedFullScreenContent() {
                            Settings.adIsPlaying = true
                            Sounds.muteForAd()
                        }
                        override fun onAdDismissedFullScreenContent() {
                            Settings.adIsPlaying = false
                            Sounds.unmuteForAd()
                            rewardedAd = null
                            if (Storage.canWatchAdNow()) {
                                loadRewardedAd(activity) { newAd -> rewardedAd = newAd }
                            }
                        }
                    }
                    ad.show(activity, OnUserEarnedRewardListener { _ ->
                        Storage.recordAdWatched()
                        Settings.unlockProgress = Storage.unlockProgress
                    })
                },
                enabled = adEnabled,
                modifier = Modifier.width(200.dp),
                colors = menuButtonColors
            ) {
                Text(adButtonText)
            }
        }

        Button(
            onClick = {
                ShareHelper.shareAppPromo(activity) {
                    if (Storage.shareRewardClaimed) {
                        Toast.makeText(activity, strShareAlreadyClaimed, Toast.LENGTH_SHORT).show()
                    } else {
                        Storage.markShareRewardClaimed()
                        Storage.addBonusProgress(10)
                        Settings.unlockProgress = Storage.unlockProgress
                        Toast.makeText(activity, strShareThanks, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.width(200.dp),
            colors = menuButtonColors
        ) {
            Text(stringResource(Res.string.share))
        }

        TextButton(onClick = {
            showLanguagePicker(activity) { languageFlag = getCurrentLanguageFlag() }
        }) {
            Text(languageFlag, fontSize = 24.sp)
        }
    }
}

@Composable
actual fun PlatformBallUnlockTop() {
    val unlockProgress = Storage.unlockProgress
    if (unlockProgress >= 100) return

    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        UnlockProgressBar(
            progress = unlockProgress,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        )
    }
}

@Composable
actual fun PlatformBallUnlockBottom() {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    var rewardedAd by remember { mutableStateOf<RewardedAd?>(null) }
    val unlockProgress = Storage.unlockProgress

    LaunchedEffect(Unit) {
        if (Storage.unlockProgress < 100 && canLoadAdNow()) {
            loadRewardedAd(activity) { ad -> rewardedAd = ad }
        }
    }

    if (unlockProgress >= 100) return

    val isDark = LocalDarkMode.current

    val strPurchase = stringResource(Res.string.purchase)
    val strRestore = stringResource(Res.string.restore)
    val strComeBackTomorrow = stringResource(Res.string.come_back_tomorrow)
    val strWatchAdToUnlock = stringResource(Res.string.watch_ad_to_unlock)
    val watchedToday = Storage.adsWatchedToday()
    val minsUntil = Storage.minutesUntilNextAd()
    val timeStr = if (minsUntil >= 60) "${minsUntil / 60}h ${minsUntil % 60}m" else "${minsUntil}m"
    val strNextAdIn = stringResource(Res.string.next_ad_in, timeStr)

    val menuButtonColors = ButtonDefaults.buttonColors(
        containerColor = if (isDark) PaintBucket.menuButtonDark else PaintBucket.menuButtonLight,
        contentColor = if (isDark) PaintBucket.white else PaintBucket.menuBackgroundDark,
        disabledContainerColor = if (isDark) PaintBucket.segmentInactiveDark else Color(0xFFE8E0FF),
        disabledContentColor = if (isDark) Color(0xFF888899) else Color(0xFF8877AA)
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { PurchaseManager.purchaseUnlockAll(activity) },
                colors = menuButtonColors
            ) {
                Text(strPurchase)
            }
            Button(
                onClick = {
                    PurchaseManager.restorePurchases(activity) { success ->
                        if (success) {
                            Settings.unlockProgress = Storage.unlockProgress
                            Storage.notifyDataChanged()
                        }
                    }
                },
                colors = menuButtonColors
            ) {
                Text(strRestore)
            }
        }

        val adButtonText = when {
            watchedToday >= 5 -> strComeBackTomorrow
            minsUntil > 0 -> strNextAdIn
            else -> strWatchAdToUnlock
        }
        val adEnabled = watchedToday < 5 && minsUntil == 0L && rewardedAd != null

        Button(
            onClick = {
                val ad = rewardedAd ?: return@Button
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdShowedFullScreenContent() {
                        Settings.adIsPlaying = true
                        Sounds.muteForAd()
                    }
                    override fun onAdDismissedFullScreenContent() {
                        Settings.adIsPlaying = false
                        Sounds.unmuteForAd()
                        rewardedAd = null
                        if (canLoadAdNow()) loadRewardedAd(activity) { newAd -> rewardedAd = newAd }
                    }
                }
                ad.show(activity, OnUserEarnedRewardListener { _ ->
                    Storage.recordAdWatched()
                    Settings.unlockProgress = Storage.unlockProgress
                })
            },
            enabled = adEnabled,
            modifier = Modifier.fillMaxWidth(),
            colors = menuButtonColors
        ) {
            Text(adButtonText)
        }
    }
}

@Composable
actual fun ImmersiveModeEffect() {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    DisposableEffect(Unit) {
        hideSystemUI(activity)
        onDispose { showSystemUI(activity) }
    }
}

private fun hideSystemUI(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }
}

private fun showSystemUI(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.window.insetsController?.show(WindowInsets.Type.systemBars())
    } else {
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }
}

private fun loadRewardedAd(activity: Activity, onLoaded: (RewardedAd?) -> Unit) {
    val id = BuildConfig.ADMOB_REWARDED_UNIT_ID
    RewardedAd.load(
        activity,
        id,
        AdRequest.Builder().build(),
        object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { onLoaded(ad) }
            override fun onAdFailedToLoad(error: LoadAdError) { onLoaded(null) }
        }
    )
}

private fun canLoadAdNow(): Boolean {
    if (Storage.adsWatchedToday() >= 5) return false
    return Storage.minutesUntilNextAd() == 0L
}

private fun getCurrentLanguageFlag(): String {
    val locale = AppCompatDelegate.getApplicationLocales()[0]
    return when (locale?.language) {
        "de" -> "🇩🇪"
        "es" -> "🇪🇸"
        "fr" -> "🇫🇷"
        "ja" -> "🇯🇵"
        "zh" -> "🇨🇳"
        else -> if (java.util.Locale.getDefault().country == "US") "🇺🇸" else "🇬🇧"
    }
}

private fun showLanguagePicker(activity: Activity, onSelected: () -> Unit) {
    val englishFlag = if (java.util.Locale.getDefault().country == "US") "🇺🇸" else "🇬🇧"
    val languages = listOf(
        "$englishFlag  English" to "",
        "🇩🇪  Deutsch" to "de",
        "🇪🇸  Español" to "es",
        "🇫🇷  Français" to "fr",
        "🇯🇵  日本語" to "ja",
        "🇨🇳  中文" to "zh"
    )
    AlertDialog.Builder(activity)
        .setItems(languages.map { it.first }.toTypedArray()) { _, which ->
            val code = languages[which].second
            val localeList = if (code.isEmpty()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(code)
            AppCompatDelegate.setApplicationLocales(localeList)
            onSelected()
        }
        .show()
}
