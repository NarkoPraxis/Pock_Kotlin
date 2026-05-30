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
import gameobjects.Settings
import org.jetbrains.compose.resources.stringResource
import pock_kotlin.app.generated.resources.Res
import pock_kotlin.app.generated.resources.stay_on_your_side
import utility.Logic

@Composable
fun WrongSideWarning(gameLoopTick: State<Int>) {
    @Suppress("UNUSED_EXPRESSION")
    gameLoopTick.value

    if (!Logic.isInitialized) return

    val highFlash = (Logic.lowSideHasMultiTouch || Logic.highPlayerCrossedCenter) &&
        Logic.highPlayer.isTouching
    val lowFlash = (Logic.highSideHasMultiTouch || Logic.lowPlayerCrossedCenter) &&
        Logic.lowPlayer.isTouching
    if (!highFlash && !lowFlash) return

    val sr = Settings.screenRatio
    val message = stringResource(Res.string.stay_on_your_side)

    // Vertically centered between the centerline and the inside edge of each goal.
    val highBandCenter = (Settings.topGoalBottom + Settings.middleY) / 2f
    val lowBandCenter  = (Settings.middleY + Settings.bottomGoalTop) / 2f

    Box(modifier = Modifier.fillMaxSize()) {
        if (highFlash) {
            WarningText(
                text = message,
                screenRatio = sr,
                centerYPx = highBandCenter,
                rotated = true
            )
        }
        if (lowFlash) {
            WarningText(
                text = message,
                screenRatio = sr,
                centerYPx = lowBandCenter,
                rotated = false
            )
        }
    }
}

@Composable
private fun WarningText(
    text: String,
    screenRatio: Float,
    centerYPx: Float,
    rotated: Boolean
) {
    val density = LocalDensity.current
    val hPadDp = with(density) { screenRatio.toDp() }
    val fontSizeSp = with(density) { (screenRatio * 1.2f).toSp() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .absoluteOffset { IntOffset(0, centerYPx.toInt()) }
            .graphicsLayer {
                translationY = -size.height / 2f
                rotationZ = if (rotated) 180f else 0f
            }
            .padding(horizontal = hPadDp)
    ) {
        Text(
            text = text,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = fontSizeSp,
            lineHeight = fontSizeSp * 1.2f,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
