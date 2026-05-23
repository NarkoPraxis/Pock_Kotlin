package com.runoutzone.pockpock

import androidx.compose.foundation.Image
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
import org.jetbrains.compose.resources.painterResource
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
    onCustomBallTapped: () -> Unit,
) {
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
            Image(
                painter = painterResource(Res.drawable.logo_full_color),
                contentDescription = stringResource(Res.string.app_title),
                modifier = Modifier
                    .width(160.dp)
                    .aspectRatio(285.35f / 280.34f)
            )
            if (unlockProgress < 100) {
                UnlockProgressBar(
                    progress = unlockProgress,
                    modifier = Modifier.width(200.dp).height(40.dp)
                )
            }
            MenuButton(text = stringResource(Res.string.play), onClick = onPlayTapped)
            MenuButton(text = stringResource(Res.string.play_solo), onClick = onSinglePlayerTapped)
            MenuButton(text = stringResource(Res.string.settings), onClick = onSettingsTapped)
            MenuButton(text = stringResource(Res.string.ball_types), onClick = onBallsTapped)
            MenuButton(text = stringResource(Res.string.custom_ball), onClick = onCustomBallTapped)
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
            containerColor = if (isDark) PaintBucket.menuButtonDark else PaintBucket.menuButtonLight,
            contentColor = if (isDark) PaintBucket.white else PaintBucket.menuBackgroundDark,
            disabledContainerColor = if (isDark) PaintBucket.segmentInactiveDark else Color(0xFFE8E0FF),
            disabledContentColor = if (isDark) Color(0xFF888899) else Color(0xFF8877AA)
        )
    ) {
        Text(text = text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
