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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import utility.PurchaseManager
import utility.ShareHelper
import utility.Storage

@Composable
actual fun PlatformMenuExtras() {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    var rewardedAd by remember { mutableStateOf<RewardedAd?>(null) }
    var unlockProgress by remember { mutableIntStateOf(Storage.unlockProgress) }
    var languageFlag by remember { mutableStateOf(getCurrentLanguageFlag()) }

    LaunchedEffect(Unit) {
        if (Storage.unlockProgress < 100 && Storage.canWatchAdNow()) {
            loadRewardedAd(activity) { ad ->
                rewardedAd = ad
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (unlockProgress < 100) {
            Spacer(Modifier.height(8.dp))
            UnlockProgressBar(
                progress = unlockProgress,
                modifier = Modifier.width(200.dp).height(40.dp)
            )

            val watchedToday = Storage.adsWatchedToday()
            val minsUntil = Storage.minutesUntilNextAd()
            val adButtonText = when {
                watchedToday >= 5 -> "Come back tomorrow"
                minsUntil > 0 -> {
                    val t = if (minsUntil >= 60) "${minsUntil / 60}h ${minsUntil % 60}m" else "${minsUntil}m"
                    "Next ad in $t"
                }
                else -> "Watch Ad to Unlock"
            }
            val adEnabled = watchedToday < 5 && minsUntil == 0L && rewardedAd != null

            Button(
                onClick = {
                    val ad = rewardedAd ?: return@Button
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            rewardedAd = null
                            if (Storage.canWatchAdNow()) {
                                loadRewardedAd(activity) { newAd -> rewardedAd = newAd }
                            }
                        }
                    }
                    ad.show(activity, OnUserEarnedRewardListener { _ ->
                        Storage.recordAdWatched()
                        Settings.unlockProgress = Storage.unlockProgress
                        unlockProgress = Storage.unlockProgress
                    })
                },
                enabled = adEnabled,
                modifier = Modifier.width(200.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444466), contentColor = Color.White)
            ) {
                Text(adButtonText)
            }
        }

        Button(
            onClick = {
                ShareHelper.shareAppPromo(activity) {
                    if (Storage.shareRewardClaimed) {
                        Toast.makeText(activity, "Thanks for sharing again! The reward has already been claimed.", Toast.LENGTH_SHORT).show()
                    } else {
                        Storage.markShareRewardClaimed()
                        Storage.addBonusProgress(10)
                        Settings.unlockProgress = Storage.unlockProgress
                        unlockProgress = Storage.unlockProgress
                        Toast.makeText(activity, "Thanks for sharing! Unlock progress +10%.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.width(200.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444466), contentColor = Color.White)
        ) {
            Text("Share")
        }

        TextButton(onClick = {
            showLanguagePicker(activity) { languageFlag = getCurrentLanguageFlag() }
        }) {
            Text(languageFlag, fontSize = 24.sp)
        }
    }
}

@Composable
actual fun PlatformBallUnlockExtras() {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    var rewardedAd by remember { mutableStateOf<RewardedAd?>(null) }
    var unlockProgress by remember { mutableIntStateOf(Storage.unlockProgress) }

    LaunchedEffect(Unit) {
        if (Storage.unlockProgress < 100 && canLoadAdNow()) {
            loadRewardedAd(activity) { ad -> rewardedAd = ad }
        }
    }

    if (unlockProgress >= 100) return

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        UnlockProgressBar(
            progress = unlockProgress,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { PurchaseManager.purchaseUnlockAll(activity) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444466), contentColor = Color.White)
            ) {
                Text("Purchase")
            }
            OutlinedButton(
                onClick = {
                    PurchaseManager.restorePurchases(activity) { success ->
                        if (success) {
                            Settings.unlockProgress = Storage.unlockProgress
                            unlockProgress = Storage.unlockProgress
                        }
                    }
                }
            ) {
                Text("Restore", color = Color.White)
            }
        }

        val watchedToday = Storage.adsWatchedToday()
        val minsUntil = Storage.minutesUntilNextAd()
        val adButtonText = when {
            watchedToday >= 5 -> "Come back tomorrow"
            minsUntil > 0 -> {
                val t = if (minsUntil >= 60) "${minsUntil / 60}h ${minsUntil % 60}m" else "${minsUntil}m"
                "Next ad in $t"
            }
            else -> "Watch Ad to Unlock"
        }
        val adEnabled = watchedToday < 5 && minsUntil == 0L && rewardedAd != null

        Button(
            onClick = {
                val ad = rewardedAd ?: return@Button
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        rewardedAd = null
                        if (canLoadAdNow()) loadRewardedAd(activity) { newAd -> rewardedAd = newAd }
                    }
                }
                ad.show(activity, OnUserEarnedRewardListener { _ ->
                    Storage.recordAdWatched()
                    Settings.unlockProgress = Storage.unlockProgress
                    unlockProgress = Storage.unlockProgress
                })
            },
            enabled = adEnabled,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444466), contentColor = Color.White)
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
    val id = "ca-app-pub-3940256099942544/5224354917" // TODO replace with live rewarded ad ID before launch
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
