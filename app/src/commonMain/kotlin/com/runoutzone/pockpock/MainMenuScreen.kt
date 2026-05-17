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
import org.jetbrains.compose.resources.stringResource
import pock_kotlin.app.generated.resources.*
import utility.PaintBucket
import utility.PlatformAd
import utility.Storage



@Composable
fun MainMenuScreen(
    onPlayTapped: () -> Unit,
    onSinglePlayerTapped: () -> Unit,
    onSettingsTapped: () -> Unit,
    onBallsTapped: () -> Unit,
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
            .background(PaintBucket.backgroundColor.copy(alpha = 0.00f))
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.app_title),
                color = textColor,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))
            MenuButton(text = stringResource(Res.string.play), onClick = onPlayTapped)
            MenuButton(text = stringResource(Res.string.play_solo), onClick = onSinglePlayerTapped)
            MenuButton(text = stringResource(Res.string.settings), onClick = onSettingsTapped)
            MenuButton(text = stringResource(Res.string.ball_types), onClick = onBallsTapped)
            PlatformMenuExtras()
        }
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
