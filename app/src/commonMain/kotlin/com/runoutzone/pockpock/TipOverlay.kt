package com.runoutzone.pockpock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import com.runoutzone.pockpock.menu.poppinsFamily
import pock_kotlin.app.generated.resources.Res
import pock_kotlin.app.generated.resources.ball_select_ready
import pock_kotlin.app.generated.resources.tip_charging
import pock_kotlin.app.generated.resources.tip_controls
import pock_kotlin.app.generated.resources.tip_overcharge
import pock_kotlin.app.generated.resources.tip_scoring
import pock_kotlin.app.generated.resources.tip_shields
import enums.TouchState
import gameobjects.Settings
import org.jetbrains.compose.resources.stringResource
import utility.Drawing
import utility.Logic
import utility.PaintBucket
import utility.Storage

// The rotating gameplay tips are hidden in this version of the ball-selection screen but kept in
// code (and their strings remain in strings.xml) so they can be reintroduced later. Flip to true
// to restore the controls/scoring/charging/… body text under each carousel.
private const val SHOW_GAMEPLAY_TIPS = false

@Composable
fun TipOverlay(gameLoopTick: State<Int>) {
    @Suppress("UNUSED_EXPRESSION")
    gameLoopTick.value

    val highOpen = Logic.highBallPopup.isOpen
    val lowOpen = Logic.lowBallPopup.isOpen
    if (!highOpen && !lowOpen) return

    val sr = Settings.screenRatio
    val isDark = Storage.darkMode

    // 6 regions top→bottom: HighGoal | HighPopup | EmptyAboveMid | EmptyBelowMid | LowPopup | LowGoal
    val popupH = sr * 6f
    val middle = Settings.screenHeight / 2f
    val highPopupBottom = Settings.topGoalBottom + popupH   // bottom edge of high popup
    val lowPopupTop = Settings.bottomGoalTop - popupH       // top edge of low popup

    Box(modifier = Modifier.fillMaxSize()) {
        // "Ready" prompt for each player, between their carousel and mid-screen. Shown by default
        // and hidden only while that player physically holds a finger down on their side (the
        // ready-up hold). It reappears the instant they lift — note we gate on TouchState.Down, NOT
        // isTouching: a released hold becomes TouchState.Ready (which still counts as "touching"),
        // and browsing the carousel never sets Down at all.
        if (highOpen && Logic.highPlayer.touch != TouchState.Down) {
            ReadyPill(
                screenRatio = sr,
                centerYPx = (highPopupBottom + middle) / 2f,
                rotated = true
            )
        }
        if (lowOpen && Logic.lowPlayer.touch != TouchState.Down) {
            ReadyPill(
                screenRatio = sr,
                centerYPx = (middle + lowPopupTop) / 2f,
                rotated = false
            )
        }

        if (SHOW_GAMEPLAY_TIPS) {
            val textColor = if (isDark) PaintBucket.white else PaintBucket.black
            val tips = listOf(
                stringResource(Res.string.tip_controls),
                stringResource(Res.string.tip_scoring),
                stringResource(Res.string.tip_charging),
                stringResource(Res.string.tip_shields),
                stringResource(Res.string.tip_overcharge),
            )
            if (highOpen) {
                TipBody(tips[Drawing.highTipIndex], textColor, sr, highPopupBottom, rotated = true)
            }
            if (lowOpen) {
                TipBody(tips[Drawing.lowTipIndex], textColor, sr, lowPopupTop, rotated = false)
            }
        }
    }
}

// The "Ready" prompt, styled as the blue Poppins parallelogram banner used by the "Set Score
// Position" button. Centered horizontally at the given screen Y; the high player's pill is rotated
// 180° so it reads from that end of the table.
@Composable
private fun ReadyPill(
    screenRatio: Float,
    centerYPx: Float,
    rotated: Boolean
) {
    val density = LocalDensity.current
    val poppins = poppinsFamily()
    val labelSizeSp = with(density) { (screenRatio * 1.4f).toSp() }
    val pillHeight = with(density) { (screenRatio * 2.4f).toDp() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .absoluteOffset { IntOffset(0, centerYPx.toInt()) }
            .graphicsLayer {
                translationY = -size.height / 2f
                rotationZ = if (rotated) 180f else 0f
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(pillHeight)
                .clip(readyPillShape())
                .background(PaintBucket.menuAccentBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(Res.string.ball_select_ready),
                color = PaintBucket.white,
                fontFamily = poppins,
                fontWeight = FontWeight.Bold,
                fontSize = labelSizeSp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Symmetric parallelogram (both side edges lean left), matching the "Set Score Position" banner in
 * the settings screen. Top edge is shifted right of the bottom edge by [slantFraction] × height.
 */
private fun readyPillShape(slantFraction: Float = 0.34f): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val slant = size.height * slantFraction
        val path = Path().apply {
            moveTo(slant, 0f)                        // top-left
            lineTo(size.width, 0f)                   // top-right
            lineTo(size.width - slant, size.height)  // bottom-right
            lineTo(0f, size.height)                  // bottom-left
            close()
        }
        return Outline.Generic(path)
    }
}

// bodyAnchorYPx:
//   rotated=false → visual bottom of body lands here (layout shifted up by its own height)
//   rotated=true  → layout top placed here; after 180° rotation the visual bottom lands here
@Composable
private fun TipBody(
    body: String,
    textColor: Color,
    screenRatio: Float,
    bodyAnchorYPx: Float,
    rotated: Boolean
) {
    val density = LocalDensity.current
    val hPadDp = with(density) { screenRatio.toDp() }
    val bodySizeSp = with(density) { (screenRatio * 0.975f).toSp() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .absoluteOffset { IntOffset(0, bodyAnchorYPx.toInt()) }
            .graphicsLayer {
                if (!rotated) translationY = -size.height
                rotationZ = if (rotated) 180f else 0f
            }
            .padding(horizontal = hPadDp)
    ) {
        Text(
            text = body,
            color = textColor,
            fontSize = bodySizeSp,
            lineHeight = bodySizeSp * 1.35f
        )
    }
}
