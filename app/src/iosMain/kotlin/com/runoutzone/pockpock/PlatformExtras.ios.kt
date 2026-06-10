@file:OptIn(ExperimentalForeignApi::class)
package com.runoutzone.pockpock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runoutzone.pockpock.menu.MenuIconButton
import gameobjects.Settings
import kotlinx.cinterop.ExperimentalForeignApi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSMutableArray
import platform.Foundation.NSUserDefaults
import pock_kotlin.app.generated.resources.*
import utility.IosShareHelper
import utility.PaintBucket
import utility.PlatformAd
import utility.Sounds
import utility.Storage

@Composable
actual fun PlatformShareButton(modifier: Modifier, iconSize: Dp) {
    var toast by remember { mutableStateOf<String?>(null) }
    val strShareThanks = stringResource(Res.string.share_thanks)
    val strShareAlreadyClaimed = stringResource(Res.string.share_already_claimed)

    MenuIconButton(
        painter = painterResource(Res.drawable.ic_menu_share),
        contentDescription = stringResource(Res.string.share),
        modifier = modifier,
        size = iconSize,
        onClick = {
            IosShareHelper.shareAppPromo { completed ->
                if (!completed) return@shareAppPromo
                if (Storage.shareRewardClaimed) {
                    toast = strShareAlreadyClaimed
                } else {
                    Storage.markShareRewardClaimed()
                    Storage.addBonusProgress(10)
                    Settings.unlockProgress = Storage.unlockProgress
                    toast = strShareThanks
                }
            }
        }
    )

    toast?.let { msg ->
        AlertDialog(
            onDismissRequest = { toast = null },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("OK") } }
        )
    }
}

@Composable
actual fun PlatformLanguageButton(modifier: Modifier, iconSize: Dp) {
    var showPicker by remember { mutableStateOf(false) }

    MenuIconButton(
        painter = painterResource(Res.drawable.ic_menu_localization),
        contentDescription = stringResource(Res.string.language),
        modifier = modifier,
        size = iconSize,
        onClick = { showPicker = true }
    )

    if (showPicker) {
        val languages = listOf(
            "🇺🇸  English" to "",
            "🇩🇪  Deutsch" to "de",
            "🇪🇸  Español" to "es",
            "🇫🇷  Français" to "fr",
            "🇯🇵  日本語" to "ja",
            "🇨🇳  中文" to "zh"
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(Res.string.language)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    languages.forEach { (label, code) ->
                        TextButton(onClick = {
                            showPicker = false
                            val defaults = NSUserDefaults.standardUserDefaults
                            if (code.isEmpty()) {
                                defaults.removeObjectForKey("AppleLanguages")
                                defaults.removeObjectForKey("app_language_pref")
                            } else {
                                val arr = NSMutableArray()
                                arr.addObject(code)
                                defaults.setObject(arr, forKey = "AppleLanguages")
                                defaults.setObject(code, forKey = "app_language_pref")
                            }
                            defaults.synchronize()
                            LocaleController.bumpLocale()
                        }) {
                            Text(label, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
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
    val isDark = LocalDarkMode.current

    val unlockProgress = Storage.unlockProgress
    var adReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (Storage.unlockProgress < 100 && Storage.canWatchAdNow()) {
            PlatformAd.loadRewardedAd(
                adUnitId = PlatformAd.TEST_REWARDED_AD_UNIT_ID,
                onLoaded = { adReady = true },
                onFailed = { adReady = false }
            )
        }
    }

    if (unlockProgress >= 100) return

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
        val adButtonText = when {
            watchedToday >= 5 -> strComeBackTomorrow
            minsUntil > 0 -> strNextAdIn
            else -> strWatchAdToUnlock
        }
        val adEnabled = watchedToday < 5 && minsUntil == 0L && adReady

        Button(
            onClick = {
                Settings.adIsPlaying = true
                Sounds.muteForAd()
                PlatformAd.showRewardedAd(
                    onEarned = {
                        Storage.recordAdWatched()
                        Settings.unlockProgress = Storage.unlockProgress
                    },
                    onDismissed = {
                        Settings.adIsPlaying = false
                        Sounds.unmuteForAd()
                        adReady = false
                        if (Storage.unlockProgress < 100 && Storage.canWatchAdNow()) {
                            PlatformAd.loadRewardedAd(
                                adUnitId = PlatformAd.TEST_REWARDED_AD_UNIT_ID,
                                onLoaded = { adReady = true },
                                onFailed = { adReady = false }
                            )
                        }
                    }
                )
            },
            enabled = adEnabled,
            modifier = Modifier.fillMaxWidth(),
            colors = menuButtonColors
        ) {
            Text(adButtonText)
        }
    }
}

@Composable actual fun ImmersiveModeEffect() {}
