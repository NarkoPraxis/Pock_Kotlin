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
import utility.PlatformAd
import utility.Storage

@Composable
fun MainMenuScreen(
    onPlayTapped: () -> Unit,
    onSinglePlayerTapped: () -> Unit,
    onSettingsTapped: () -> Unit,
    onBallsTapped: () -> Unit
) {
    val isDark = LocalDarkMode.current
    val bgColor = if (isDark) Color(0xFF12102A) else Color(0xFFFFFFFF)
    val textColor = if (isDark) Color.White else Color(0xFF12102A)

    var unlockProgress by remember { mutableIntStateOf(Storage.unlockProgress) }
    var adReady by remember { mutableStateOf(false) }

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
                    "$unlockProgress% Unlocked",
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(8.dp))
            MenuButton(text = "PLAY", textColor = textColor, onClick = onPlayTapped)
            MenuButton(text = "PLAY SOLO", textColor = textColor, onClick = onSinglePlayerTapped)

            if (unlockProgress < 100) {
                val adLabel = when {
                    Storage.adsWatchedToday() >= 5 -> "Come Back Tomorrow"
                    Storage.minutesUntilNextAd() > 0 -> {
                        val mins = Storage.minutesUntilNextAd()
                        "Next Ad in ${if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"}"
                    }
                    adReady -> "Watch Ad to Unlock"
                    else -> "Watch Ad (Loading...)"
                }
                val adEnabled = adReady && Storage.canWatchAdNow()
                MenuButton(
                    text = adLabel,
                    textColor = textColor,
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

            MenuButton(text = "SETTINGS", textColor = textColor, onClick = onSettingsTapped)
            MenuButton(text = "BALL TYPES", textColor = textColor, onClick = onBallsTapped)
        }
    }
}

@Composable
private fun MenuButton(
    text: String,
    textColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.width(200.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF444466),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF333344),
            disabledContentColor = Color(0xFF888899)
        )
    ) {
        Text(text = text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
