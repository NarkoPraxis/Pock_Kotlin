package com.runoutzone.pockpock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import pock_kotlin.app.generated.resources.Res
import pock_kotlin.app.generated.resources.tip_charging
import pock_kotlin.app.generated.resources.tip_controls
import pock_kotlin.app.generated.resources.tip_overcharge
import pock_kotlin.app.generated.resources.tip_scoring
import pock_kotlin.app.generated.resources.tip_shields
import pock_kotlin.app.generated.resources.tip_title
import gameobjects.Settings
import org.jetbrains.compose.resources.stringResource
import utility.Drawing
import utility.Logic
import utility.PaintBucket
import utility.Storage

@Composable
fun TipOverlay(gameLoopTick: State<Int>) {
    @Suppress("UNUSED_EXPRESSION")
    gameLoopTick.value

    val highOpen = Logic.highBallPopup.isOpen
    val lowOpen = Logic.lowBallPopup.isOpen
    if (!highOpen && !lowOpen) return

    val sr = Settings.screenRatio
    val textColor = if (Storage.darkMode) PaintBucket.white else PaintBucket.black

    val tipTitle = stringResource(Res.string.tip_title)
    val tips = listOf(
        stringResource(Res.string.tip_controls),
        stringResource(Res.string.tip_scoring),
        stringResource(Res.string.tip_charging),
        stringResource(Res.string.tip_shields),
        stringResource(Res.string.tip_overcharge),
    )

    // 6 regions top→bottom: HighGoal | HighPopup | EmptyAboveMid | EmptyBelowMid | LowPopup | LowGoal
    val popupH = sr * 6f
    val middle = Settings.screenHeight / 2f
    val highPopupBottom = Settings.topGoalBottom + popupH   // bottom edge of high popup
    val lowPopupTop = Settings.bottomGoalTop - popupH       // top edge of low popup

    Box(modifier = Modifier.fillMaxSize()) {
        if (highOpen) {
            TipContent(
                title = tipTitle,
                body = tips[Drawing.highTipIndex],
                textColor = textColor,
                screenRatio = sr,
                titleCenterYPx = (highPopupBottom + middle) / 2f,
                bodyAnchorYPx = highPopupBottom,
                rotated = true
            )
        }
        if (lowOpen) {
            TipContent(
                title = tipTitle,
                body = tips[Drawing.lowTipIndex],
                textColor = textColor,
                screenRatio = sr,
                titleCenterYPx = (middle + lowPopupTop) / 2f,
                bodyAnchorYPx = lowPopupTop,
                rotated = false
            )
        }
    }
}

// titleCenterYPx: Y where the title should be vertically centered (screen coords).
// bodyAnchorYPx:
//   rotated=false → visual bottom of body lands here (layout shifted up by its own height)
//   rotated=true  → layout top placed here; after 180° rotation the visual bottom lands here
@Composable
private fun TipContent(
    title: String,
    body: String,
    textColor: Color,
    screenRatio: Float,
    titleCenterYPx: Float,
    bodyAnchorYPx: Float,
    rotated: Boolean
) {
    val density = LocalDensity.current
    val hPadDp = with(density) { screenRatio.toDp() }
    val titleSizeSp = with(density) { (screenRatio * 1.125f).toSp() }
    val bodySizeSp = with(density) { (screenRatio * 0.975f).toSp() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .absoluteOffset { IntOffset(0, titleCenterYPx.toInt()) }
            .graphicsLayer {
                translationY = -size.height / 2f
                rotationZ = if (rotated) 180f else 0f
            }
            .padding(horizontal = hPadDp)
    ) {
        Text(
            text = title,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = titleSizeSp,
            lineHeight = titleSizeSp * 1.3f,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

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
