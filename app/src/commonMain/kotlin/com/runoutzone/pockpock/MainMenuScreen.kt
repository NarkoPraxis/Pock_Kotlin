package com.runoutzone.pockpock

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runoutzone.pockpock.menu.EdgePill
import com.runoutzone.pockpock.menu.MenuIconButton
import com.runoutzone.pockpock.menu.PillSide
import com.runoutzone.pockpock.menu.SlantedMenuButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pock_kotlin.app.generated.resources.*
import utility.PaintBucket
import utility.PlatformAd
import utility.Storage

/**
 * Main menu, translated from Plans/UIOverhaul/Screens/Main.svg.
 *
 * Layout (responsive): top-center logo, two right-anchored slanted "Play" buttons in the middle,
 * a bottom-left brand-blue icon tray (settings / share / localization) and a bottom-right brand-red
 * customize pill that doubles as the unlock-progress meter.
 *
 * The demo game renders behind this screen (see [AppRoot]). Menu chrome uses the fixed
 * [PaintBucket] brand accents and intentionally does not respond to the dark-mode toggle.
 */
@Composable
fun MainMenuScreen(
    onPlayTapped: () -> Unit,
    onSinglePlayerTapped: () -> Unit,
    onSettingsTapped: () -> Unit,
    onCustomBallTapped: () -> Unit,
) {
    val unlockProgress = Storage.unlockProgress

    // Preload a rewarded ad only while there is still progress to earn (used by the CBC the
    // customize pill leads to). No-op once everything is unlocked.
    LaunchedEffect(Unit) {
        if (Storage.unlockProgress < 100 && Storage.canWatchAdNow()) {
            PlatformAd.loadRewardedAd(
                adUnitId = PlatformAd.TEST_REWARDED_AD_UNIT_ID,
                onLoaded = {},
                onFailed = {}
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenW = maxWidth
        val screenH = maxHeight
        val buttonHeight = (screenH * 0.082f).coerceIn(64.dp, 104.dp)
        val pillHeight = (screenH * 0.072f).coerceIn(56.dp, 84.dp)
        val buttonGlyph = buttonHeight * 0.5f

        // ── Logo (top-center) ──
        Image(
            painter = painterResource(Res.drawable.logo_full_color),
            contentDescription = stringResource(Res.string.app_title),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = screenH * 0.10f)
                .width(screenW * 0.42f)
                .aspectRatio(285.35f / 280.34f)
        )

        // ── Play buttons (right-anchored, vertically centered) ──
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(screenH * 0.022f)
        ) {
            SlantedMenuButton(
                text = stringResource(Res.string.play_a_friend),
                modifier = Modifier.width(screenW * 0.88f),
                height = buttonHeight,
                onClick = onPlayTapped,
            ) {
                MenuGlyph(Res.drawable.ic_menu_player, buttonGlyph)
                Spacer(Modifier.width(8.dp))
                MenuGlyph(Res.drawable.ic_menu_player, buttonGlyph)
            }
            SlantedMenuButton(
                text = stringResource(Res.string.play_ai),
                modifier = Modifier.width(screenW * 0.74f),
                height = buttonHeight,
                onClick = onSinglePlayerTapped,
            ) {
                MenuGlyph(Res.drawable.ic_menu_player, buttonGlyph)
                Spacer(Modifier.width(8.dp))
                MenuGlyph(Res.drawable.ic_menu_ai, buttonGlyph)
            }
        }

        // ── Bottom-left icon tray (settings / share / localization) ──
        EdgePill(
            side = PillSide.Start,
            color = PaintBucket.menuAccentBlue,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = screenH * 0.03f)
                .height(pillHeight)
        ) {
            MenuIconButton(
                painter = painterResource(Res.drawable.ic_menu_settings),
                contentDescription = stringResource(Res.string.settings),
                size = pillHeight * 0.46f,
                onClick = onSettingsTapped
            )
            PlatformShareButton(Modifier.size(pillHeight * 0.46f + 16.dp))
            PlatformLanguageButton(Modifier.size(pillHeight * 0.46f + 16.dp))
        }

        // ── Bottom-right customize / progress pill ──
        EdgePill(
            side = PillSide.End,
            color = PaintBucket.menuAccentRed,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = screenH * 0.03f)
                .height(pillHeight)
                .clickable(onClick = onCustomBallTapped)
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_menu_customize),
                contentDescription = stringResource(Res.string.custom_ball),
                modifier = Modifier.size(pillHeight * 0.5f),
                colorFilter = ColorFilter.tint(PaintBucket.white)
            )
            // The pill doubles as the unlock meter. Once everything is unlocked the ad/progress
            // economy is gone, so the percentage disappears and it is just the customize button.
            if (unlockProgress < 100) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "$unlockProgress%",
                    color = PaintBucket.white,
                    fontSize = (pillHeight.value * 0.32f).sp,
                    fontWeight = FontWeight.Light,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

/** White trailing glyph used inside the slanted play buttons. */
@Composable
private fun MenuGlyph(resource: org.jetbrains.compose.resources.DrawableResource, size: androidx.compose.ui.unit.Dp) {
    Image(
        painter = painterResource(resource),
        contentDescription = null,
        modifier = Modifier.size(size),
        colorFilter = ColorFilter.tint(PaintBucket.white)
    )
}
