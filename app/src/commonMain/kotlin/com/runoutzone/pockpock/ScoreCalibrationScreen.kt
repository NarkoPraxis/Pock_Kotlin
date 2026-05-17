package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gameobjects.Settings
import org.jetbrains.compose.resources.stringResource
import pock_kotlin.app.generated.resources.*
import utility.Logic
import utility.PaintBucket
import utility.Storage

@Composable
fun ScoreCalibrationScreen(onBack: () -> Unit) {
    val isDark = LocalDarkMode.current
    var highOffset by remember { mutableStateOf(Settings.scoreOffsetHigh) }
    var lowOffset by remember { mutableStateOf(Settings.scoreOffsetLow) }
    var initialized by remember { mutableStateOf(Settings.screenRatio > 0f) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val strHint = stringResource(Res.string.score_calibration_hint)
    val strSave = stringResource(Res.string.score_calibration_save)

    ImmersiveModeEffect()

    LaunchedEffect(isDark) {
        if (Settings.screenRatio > 0f) PaintBucket.initializePlatformColors(isDark)
    }

    // Score text width for offset clamping; recomputed if density or initialized changes.
    val scoreTextWidthPx = remember(density, initialized) {
        if (!initialized || PaintBucket.scoreFontSize == 0f) return@remember 0f
        val fontSizeSp = PaintBucket.scoreFontSize / density.density
        textMeasurer.measure(
            "5",
            TextStyle(fontSize = TextUnit(fontSizeSp, TextUnitType.Sp))
        ).size.width.toFloat()
    }

    // Per-half pointer tracking.
    val highPointerId = remember { mutableLongStateOf(-1L) }
    val lowPointerId = remember { mutableLongStateOf(-1L) }
    val highLastX = remember { mutableFloatStateOf(0f) }
    val lowLastX = remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    if (size.width > 0 && size.height > 0) {
                        if (Settings.screenRatio == 0f) {
                            Logic.initializeSettings(size.width, size.height)
                            PaintBucket.initialize(Settings.screenRatio)
                            PaintBucket.initializePlatformColors(isDark)
                        }
                        highOffset = Settings.scoreOffsetHigh
                        lowOffset = Settings.scoreOffsetLow
                        initialized = true
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            event.changes.forEach { change ->
                                if (change.isConsumed) return@forEach
                                val x = change.position.x
                                val y = change.position.y
                                val pid = change.id.value
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        if (change.pressed && !change.previousPressed) {
                                            if (y < Settings.screenHeight / 2f) {
                                                if (highPointerId.longValue == -1L) {
                                                    highPointerId.longValue = pid
                                                    highLastX.floatValue = x
                                                }
                                            } else {
                                                if (lowPointerId.longValue == -1L) {
                                                    lowPointerId.longValue = pid
                                                    lowLastX.floatValue = x
                                                }
                                            }
                                        }
                                    }
                                    PointerEventType.Move -> {
                                        if (change.pressed) {
                                            val xMargin = Settings.screenRatio * 3f
                                            val minOff = -xMargin
                                            val maxOff = Settings.screenWidth - xMargin - scoreTextWidthPx
                                            if (pid == highPointerId.longValue) {
                                                val dx = x - highLastX.floatValue
                                                highLastX.floatValue = x
                                                // Invert dx: high player's score is drawn in mirrored canvas space.
                                                val newOff = (highOffset - dx).coerceIn(minOff, maxOff)
                                                highOffset = newOff
                                                Settings.scoreOffsetHigh = newOff
                                                Storage.saveScoreOffsetHigh(newOff.toInt())
                                            } else if (pid == lowPointerId.longValue) {
                                                val dx = x - lowLastX.floatValue
                                                lowLastX.floatValue = x
                                                val newOff = (lowOffset + dx).coerceIn(minOff, maxOff)
                                                lowOffset = newOff
                                                Settings.scoreOffsetLow = newOff
                                                Storage.saveScoreOffsetLow(newOff.toInt())
                                            }
                                        }
                                    }
                                    PointerEventType.Release -> {
                                        if (!change.pressed && change.previousPressed) {
                                            if (pid == highPointerId.longValue) highPointerId.longValue = -1L
                                            if (pid == lowPointerId.longValue) lowPointerId.longValue = -1L
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }
        ) {
            if (!initialized || Settings.screenWidth == 0f) return@Canvas

            val sw = Settings.screenWidth
            val sh = Settings.screenHeight
            val tgb = Settings.topGoalBottom
            val bgt = Settings.bottomGoalTop
            val cx = sw / 2f
            val cy = sh / 2f

            // Background
            drawRect(PaintBucket.backgroundColor, topLeft = Offset.Zero, size = Size(sw, sh))

            // Goal zones
            drawRect(PaintBucket.goalColor, topLeft = Offset.Zero, size = Size(sw, tgb))
            drawRect(PaintBucket.goalColor, topLeft = Offset(0f, bgt), size = Size(sw, sh - bgt))

            // Score text — identical style, position, and transform as Drawing.drawScores()
            val d = drawContext.density.density
            val scoreFontSizeSp = PaintBucket.scoreFontSize / d
            val scoreStyle = TextStyle(
                fontSize = TextUnit(scoreFontSizeSp, TextUnitType.Sp),
                color = PaintBucket.white
            )
            val scoreResult = textMeasurer.measure("5", scoreStyle)
            val textW = scoreResult.size.width.toFloat()
            val xMargin = Settings.screenRatio * 3f
            val scoreY = sh  // matches Drawing.drawScores: yMargin = 0

            // Low player score
            val lowDrawX = (xMargin + lowOffset).coerceIn(0f, sw - textW)
            drawText(scoreResult, topLeft = Offset(lowDrawX, scoreY - scoreResult.size.height))

            // High player score (mirrored — same transform as the live game)
            withTransform({ scale(-1f, -1f, pivot = Offset(cx, cy)) }) {
                val highDrawX = (xMargin + highOffset).coerceIn(0f, sw - textW)
                drawText(scoreResult, topLeft = Offset(highDrawX, scoreY - scoreResult.size.height))
            }

            // Drag-hint text: centered between screen middle and the inner edge of each goal zone
            val hintFontSizeSp = PaintBucket.menuHintFontSize / d
            val hintStyle = TextStyle(
                fontSize = TextUnit(hintFontSizeSp, TextUnitType.Sp),
                color = PaintBucket.white.copy(alpha = 0.55f)
            )
            val hintResult = textMeasurer.measure(strHint, hintStyle)
            val hintHalfW = hintResult.size.width / 2f
            val hintHalfH = hintResult.size.height / 2f

            // Low hint: midpoint between screen center and bottomGoalTop
            val lowHintCenterY = cy + (bgt - cy) / 2f
            drawText(hintResult, topLeft = Offset(cx - hintHalfW, lowHintCenterY - hintHalfH))

            // High hint: midpoint between topGoalBottom and screen center, mirrored
            val highHintCenterY = tgb + (cy - tgb) / 2f
            withTransform({ scale(-1f, -1f, pivot = Offset(cx, highHintCenterY)) }) {
                drawText(hintResult, topLeft = Offset(cx - hintHalfW, highHintCenterY - hintHalfH))
            }
        }

        // "Save Positions" button in the center of the play area.
        // Changes are written to Storage on every drag event; this button just navigates back.
        if (initialized) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PaintBucket.segmentActive,
                        contentColor = PaintBucket.white
                    ),
                    modifier = Modifier.width(220.dp)
                ) {
                    Text(strSave, fontSize = 16.sp)
                }
            }
        }
    }
}
