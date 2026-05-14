package com.runoutzone.pockpock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import utility.LanguageHelper
import utility.LocalStrings
import utility.PlatformAd
import utility.Storage



@Composable
fun MainMenuScreen(
    onPlayTapped: () -> Unit,
    onSinglePlayerTapped: () -> Unit,
    onSettingsTapped: () -> Unit,
    onBallsTapped: () -> Unit,
    onLanguageChanged: (String) -> Unit = {}
) {
    val isDark = LocalDarkMode.current
    val bgColor = if (isDark) Color(0xFF12102A) else Color(0xFFFFFFFF)
    val textColor = if (isDark) Color.White else Color(0xFF12102A)
    val currentLanguage = LocalLanguage.current
    val strings = LocalStrings.current

    var unlockProgress by remember { mutableIntStateOf(Storage.unlockProgress) }
    var adReady by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        unlockProgress = Storage.unlockProgress
        if (unlockProgress < 100 && Storage.canWatchAdNow()) {
            PlatformAd.loadRewardedAd(
                adUnitId = PlatformAd.TEST_REWARDED_AD_UNIT_ID,
                onLoaded = { adReady = true },
                onFailed = { adReady = false }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "POCK",
                color = textColor,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold
            )

            if (unlockProgress < 100) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { unlockProgress / 100f },
                    modifier = Modifier.width(200.dp),
                    color = Color(0xFF6666AA),
                    trackColor = if (isDark) Color(0xFF333344) else Color(0xFFCCCCDD)
                )
                Text(
                    strings.percentUnlocked(unlockProgress),
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(8.dp))
            MenuButton(text = strings.play, onClick = onPlayTapped)
            MenuButton(text = strings.playSolo, onClick = onSinglePlayerTapped)

            if (unlockProgress < 100) {
                val adLabel = when {
                    Storage.adsWatchedToday() >= 5 -> strings.comeBackTomorrow
                    Storage.minutesUntilNextAd() > 0 -> {
                        val mins = Storage.minutesUntilNextAd()
                        strings.nextAdIn(if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m")
                    }
                    adReady -> strings.watchAdToUnlock
                    else -> strings.watchAdLoading
                }
                val adEnabled = adReady && Storage.canWatchAdNow()
                MenuButton(
                    text = adLabel,
                    enabled = adEnabled,
                    onClick = {
                        PlatformAd.showRewardedAd(
                            onEarned = {
                                Storage.recordAdWatched()
                                unlockProgress = Storage.unlockProgress
                                adReady = false
                            },
                            onDismissed = {
                                adReady = false
                                if (Storage.canWatchAdNow()) {
                                    PlatformAd.loadRewardedAd(
                                        adUnitId = PlatformAd.TEST_REWARDED_AD_UNIT_ID,
                                        onLoaded = { adReady = true },
                                        onFailed = { adReady = false }
                                    )
                                }
                            }
                        )
                    }
                )
            }

            MenuButton(text = strings.settings, onClick = onSettingsTapped)
            MenuButton(text = strings.ballTypes, onClick = onBallsTapped)
        }

        // Language picker button — bottom-right corner, matching Android placement
        TextButton(
            onClick = { showLanguageDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
        ) {
            Text(
                text = LanguageHelper.flagForCode(currentLanguage),
                fontSize = 28.sp
            )
        }
    }

    if (showLanguageDialog) {
        val languages = listOf(
            "🇺🇸  English" to "",
            "🇩🇪  Deutsch" to "de",
            "🇪🇸  Español" to "es",
            "🇫🇷  Français" to "fr",
            "🇯🇵  日本語" to "ja",
            "🇨🇳  中文" to "zh"
        )
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(strings.language, color = textColor) },
            text = {
                Column {
                    languages.forEach { (label, code) ->
                        TextButton(
                            onClick = {
                                showLanguageDialog = false
                                onLanguageChanged(code)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, color = textColor, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(strings.cancel, color = textColor)
                }
            },
            containerColor = bgColor
        )
    }
}

@Composable
private fun MenuButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val isDark = LocalDarkMode.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.width(200.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isDark) Color(0xFF444466) else Color(0xFFD4C8FF),
            contentColor = if (isDark) Color.White else Color(0xFF12102A),
            disabledContainerColor = if (isDark) Color(0xFF333344) else Color(0xFFE8E0FF),
            disabledContentColor = if (isDark) Color(0xFF888899) else Color(0xFF8877AA)
        )
    ) {
        Text(text = text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
