package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runoutzone.pockpock.menu.EdgePill
import com.runoutzone.pockpock.menu.MenuIconButton
import com.runoutzone.pockpock.menu.PillSide
import com.runoutzone.pockpock.menu.SlantedMenuButton
import com.runoutzone.pockpock.menu.poppinsFamily
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pock_kotlin.app.generated.resources.*
import utility.PaintBucket
import utility.PlatformAd
import utility.Storage

/**
 * Main menu, translated from Plans/UIOverhaul/Screens/Main.svg.
 *
 * Layout (responsive): a top-center logo framed by goal-height padding, two right-anchored slanted
 * "Play" buttons sitting low (centered on the y midpoint between screen-center and the demo game's
 * bottom goal), and a bottom Row holding a brand-blue icon tray (settings / share / localization)
 * plus a brand-red customize pill that doubles as the unlock-progress thermometer.
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
    val poppins = poppinsFamily()

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
        val density = LocalDensity.current

        // Demo-game goal geometry mirrors Settings: screenRatio = (widthPx/20)≤54,
        // goal height = screenRatio * scoreZoneHeight(3). Converted px→dp so the menu lines up
        // with the game rendering behind it.
        val screenHpx = with(density) { screenH.toPx() }
        val screenWpx = with(density) { screenW.toPx() }
        val goalPx = (screenWpx / 20f).coerceIn(0f, 54f) * 3f
        val goalH = with(density) { goalPx.toDp() }

        // Logo framing: [top goal] [goal-height pad] [logo] [goal-height pad → ends at middleY].
        val logoTopPad = goalH * 2f
        val logoHeight = with(density) { (screenHpx / 2f - 3f * goalPx).coerceAtLeast(0f).toDp() }

        val buttonHeight = (screenH * 0.082f).coerceIn(64.dp, 104.dp)
        val buttonSpacing = screenH * 0.022f
        // Every piece of chrome below is a fraction of buttonHeight so the menu keeps the SVG's
        // proportions at any screen size. The fractions are calibrated so a tablet-sized 104dp button
        // reproduces the previously hand-tuned values exactly (e.g. 0.51×104 ≈ 53), while a smaller
        // phone-sized button scales them down faithfully instead of leaving them fixed and oversized.
        val glyphSize = buttonHeight * 0.51f

        // Bottom nav chrome. Pills match the play-button height; the icons and their inset scale too.
        val navIcon = buttonHeight * 0.442f
        val navBox = buttonHeight * 0.558f
        val pillContentPad = buttonHeight * 0.173f
        val pillHeight = buttonHeight

        // ── Logo (top-center, framed by goal-height padding) ──
        Image(
            painter = painterResource(Res.drawable.logo_full_color),
            contentDescription = stringResource(Res.string.app_title),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = logoTopPad)
                .height(logoHeight)
                .aspectRatio(285.35f / 280.34f)
        )

        // ── Play buttons (right-anchored, low) ──
        // The group (two buttons + their gap, no outer padding) is centered on the y value halfway
        // between screen-center and the demo game's bottom goal top.
        val groupCenterY: Dp = with(density) {
            val bottomGoalTopPx = screenHpx - goalPx
            ((screenHpx / 2f + bottomGoalTopPx) / 2f).toDp()
        }
        val groupHeight = buttonHeight * 2 + buttonSpacing
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .offset(y = groupCenterY - groupHeight / 2),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(buttonSpacing)
        ) {
            SlantedMenuButton(
                text = stringResource(Res.string.play_a_friend),
                height = buttonHeight,
                fontFamily = poppins,
                onClick = onPlayTapped,
            ) {
                MenuGlyph(Res.drawable.ic_menu_player, glyphSize)
                // The two glyphs overlap slightly so they read as one cohesive icon.
                MenuGlyph(Res.drawable.ic_menu_player, glyphSize, Modifier.offset(x = (-5).dp))
            }
            SlantedMenuButton(
                text = stringResource(Res.string.play_ai),
                height = buttonHeight,
                fontFamily = poppins,
                onClick = onSinglePlayerTapped,
            ) {
                MenuGlyph(Res.drawable.ic_menu_player, glyphSize)
                MenuGlyph(Res.drawable.ic_menu_ai, glyphSize, Modifier.offset(x = (-5).dp))
            }
        }

        // ── Bottom tray: blue icon pill + expanding red customize/progress pill ──
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = screenH * 0.03f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EdgePill(
                side = PillSide.Start,
                color = PaintBucket.menuAccentBlue,
                contentPadding = pillContentPad,
                modifier = Modifier.height(pillHeight)
            ) {
                MenuIconButton(
                    painter = painterResource(Res.drawable.ic_menu_settings),
                    contentDescription = stringResource(Res.string.settings),
                    size = navIcon,
                    modifier = Modifier.size(navBox),
                    onClick = onSettingsTapped
                )
                PlatformShareButton(Modifier.size(navBox), navIcon)
                PlatformLanguageButton(Modifier.size(navBox), navIcon)
            }

            if (unlockProgress < 100) {
                // Red pill expands to fill the space between it and the blue tray. The customize
                // icon keeps its static size; the thermometer takes the remaining width.
                Spacer(Modifier.width(buttonHeight * 0.192f))
                EdgePill(
                    side = PillSide.End,
                    color = PaintBucket.menuAccentRed,
                    contentPadding = pillContentPad,
                    modifier = Modifier
                        .weight(1f)
                        .height(pillHeight)
                        .clickable(onClick = onCustomBallTapped)
                ) {
                    Image(
                        painter = painterResource(Res.drawable.ic_menu_customize),
                        contentDescription = stringResource(Res.string.custom_ball),
                        modifier = Modifier.size(navIcon),
                        colorFilter = ColorFilter.tint(PaintBucket.white)
                    )
                    Spacer(Modifier.width(buttonHeight * 0.135f))
                    UnlockThermometer(
                        progress = unlockProgress,
                        fontFamily = poppins,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            // Breathing room between the white outline and the pill edges.
                            .padding(vertical = pillHeight * 0.10f)
                    )
                }
            } else {
                // Fully unlocked: no meter, so the pill is just the customize button, flush right.
                Spacer(Modifier.weight(1f))
                EdgePill(
                    side = PillSide.End,
                    color = PaintBucket.menuAccentRed,
                    contentPadding = pillContentPad,
                    modifier = Modifier
                        .height(pillHeight)
                        .clickable(onClick = onCustomBallTapped)
                ) {
                    Image(
                        painter = painterResource(Res.drawable.ic_menu_customize),
                        contentDescription = stringResource(Res.string.custom_ball),
                        modifier = Modifier.size(navIcon),
                        colorFilter = ColorFilter.tint(PaintBucket.white)
                    )
                }
            }
        }
    }
}

/**
 * Round-pill unlock thermometer that lives inside the red customize pill and fills its height.
 *
 * Layered white outline → red outline → an interior that is filled white where progress is earned
 * and brand red where it is not. The percentage label is constrained to half the width so the fill
 * never collides with it: above 50% the left half is white so the label sits left in red text;
 * below 50% the label sits right in white text over the red remainder.
 */
@Composable
private fun UnlockThermometer(
    progress: Int,
    fontFamily: FontFamily?,
    modifier: Modifier = Modifier,
) {
    val frac = (progress / 100f).coerceIn(0f, 1f)
    val aboveHalf = progress >= 50
    val red = PaintBucket.menuAccentRed
    val white = PaintBucket.white

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val barH = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // White outline (outer pill).
            drawRoundRect(
                color = white,
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = CornerRadius(h / 2f, h / 2f)
            )
            // Red ring — doubles as both the red outline and the unfilled background.
            val s1 = h * 0.055f
            val innerH1 = h - 2 * s1
            drawRoundRect(
                color = red,
                topLeft = Offset(s1, s1),
                size = Size(w - 2 * s1, innerH1),
                cornerRadius = CornerRadius(innerH1 / 2f, innerH1 / 2f)
            )
            // White progress fill, inset by another red-stroke band so the red outline stays
            // visible around it. Clipped to the earned fraction; the remainder shows the red ring.
            val s2 = s1 + h * 0.055f
            val innerW2 = w - 2 * s2
            val innerH2 = h - 2 * s2
            val fillW = innerW2 * frac
            if (fillW > 0f) {
                clipRect(left = s2, top = s2, right = s2 + fillW, bottom = s2 + innerH2) {
                    drawRoundRect(
                        color = white,
                        topLeft = Offset(s2, s2),
                        size = Size(innerW2, innerH2),
                        cornerRadius = CornerRadius(innerH2 / 2f, innerH2 / 2f)
                    )
                }
            }
        }

        // Percentage label, restricted to half the width on the side opposite the fill front.
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = barH * 0.42f),
            contentAlignment = if (aboveHalf) Alignment.CenterStart else Alignment.CenterEnd
        ) {
            Text(
                text = "$progress%",
                color = if (aboveHalf) red else white,
                modifier = Modifier.fillMaxWidth(0.5f),
                textAlign = if (aboveHalf) TextAlign.Start else TextAlign.End,
                fontSize = (barH.value * 0.4f).sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Light,
                fontStyle = FontStyle.Italic,
                maxLines = 1
            )
        }
    }
}

/** White trailing glyph used inside the slanted play buttons. */
@Composable
private fun MenuGlyph(
    resource: org.jetbrains.compose.resources.DrawableResource,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(resource),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(PaintBucket.white)
    )
}
