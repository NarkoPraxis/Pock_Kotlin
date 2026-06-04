package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.runoutzone.pockpock.menu.EdgePill
import com.runoutzone.pockpock.menu.PillSide
import com.runoutzone.pockpock.menu.poppinsFamily
import gameobjects.Settings
import org.jetbrains.compose.resources.painterResource
import pock_kotlin.app.generated.resources.*
import utility.Logic
import utility.PaintBucket
import utility.Storage

/**
 * Score Placement screen — redesigned from Plans/UIOverhaul/Screens/Score Placement.svg.
 *
 * Visual-only overhaul (the drag logic and persistence are unchanged): each player's score numeral
 * sits centered inside a dashed "target" ring drawn over the brand-blue goal zone, with a blue
 * left/right arrow puck connected by a dashed tether above it — extra indication that the number is
 * the draggable setting. Dragging anywhere in a half still moves that player's number (and saves
 * `scoreOffset*`) exactly as before. The red right-edge checkmark pill commits and returns.
 */
@Composable
fun ScoreCalibrationScreen(onBack: () -> Unit) {
    val isDark = LocalDarkMode.current
    var highOffset by remember { mutableStateOf(Settings.scoreOffsetHigh) }
    var lowOffset by remember { mutableStateOf(Settings.scoreOffsetLow) }
    var initialized by remember { mutableStateOf(Settings.screenRatio > 0f) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val poppins = poppinsFamily()

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
            TextStyle(fontSize = TextUnit(fontSizeSp, TextUnitType.Sp), fontFamily = poppins, fontWeight = FontWeight.Bold)
        ).size.width.toFloat()
    }

    // Per-half pointer tracking.
    val highPointerId = remember { mutableLongStateOf(-1L) }
    val lowPointerId = remember { mutableLongStateOf(-1L) }
    val highLastX = remember { mutableFloatStateOf(0f) }
    val lowLastX = remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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
            val ratio = Settings.screenRatio
            val zone = tgb  // goal-zone height

            // Background + brand-blue goal zones (Score Placement.svg paints them #52B6F2).
            drawRect(PaintBucket.backgroundColor, topLeft = Offset.Zero, size = Size(sw, sh))
            drawRect(PaintBucket.menuAccentBlue, topLeft = Offset.Zero, size = Size(sw, tgb))
            drawRect(PaintBucket.menuAccentBlue, topLeft = Offset(0f, bgt), size = Size(sw, sh - bgt))

            // Score text — measured exactly like the live game (Drawing.drawScores) for width parity.
            val d = drawContext.density.density
            val scoreFontSizeSp = PaintBucket.scoreFontSize / d
            val scoreStyle = TextStyle(
                fontSize = TextUnit(scoreFontSizeSp, TextUnitType.Sp),
                fontFamily = poppins,
                fontWeight = FontWeight.Bold,
                color = PaintBucket.white
            )
            val scoreResult = textMeasurer.measure("5", scoreStyle)
            val textW = scoreResult.size.width.toFloat()
            val xMargin = ratio * 3f

            // ── Geometry (proportions read from the SVG, expressed in goal-zone heights) ──
            val radHash = zone * 0.42f              // dashed target ring radius
            val arrowR = radHash * 0.57f            // arrow-puck radius
            val gap = zone * 0.12f
            // Dashed strokes: ring uses ~10/25 dash, the tether a similar pattern, scaled to ratio.
            val dash = PathEffect.dashPathEffect(floatArrayOf(ratio * 0.2f, ratio * 0.5f), 0f)
            val strokeW = ratio * 0.18f
            val capPx = PaintBucket.scoreFontSize * 0.72f   // optical cap-height of the numeral

            // The numeral is centered vertically in the goal zone; the arrow-puck sits above it.
            val circleCy = sh - zone / 2f
            val arrowCy = bgt - zone * 0.71f

            fun DrawScope.placement(scoreCenterX: Float) {
                // Dashed tether between the arrow puck and the ring.
                drawLine(
                    color = PaintBucket.menuHashStroke,
                    start = Offset(scoreCenterX, arrowCy + arrowR + gap),
                    end = Offset(scoreCenterX, circleCy - radHash - gap),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round,
                    pathEffect = dash
                )
                // Arrow puck (solid blue) with white left/right chevrons.
                drawCircle(PaintBucket.menuAccentBlue, radius = arrowR, center = Offset(scoreCenterX, arrowCy))
                val hw = arrowR * 0.34f
                val rightArrow = Path().apply {
                    moveTo(scoreCenterX + arrowR * 0.58f, arrowCy)
                    lineTo(scoreCenterX + arrowR * 0.05f, arrowCy - hw)
                    lineTo(scoreCenterX + arrowR * 0.05f, arrowCy + hw)
                    close()
                }
                val leftArrow = Path().apply {
                    moveTo(scoreCenterX - arrowR * 0.58f, arrowCy)
                    lineTo(scoreCenterX - arrowR * 0.05f, arrowCy - hw)
                    lineTo(scoreCenterX - arrowR * 0.05f, arrowCy + hw)
                    close()
                }
                drawPath(rightArrow, PaintBucket.white)
                drawPath(leftArrow, PaintBucket.white)
                // Dashed target ring around the numeral.
                drawCircle(
                    color = PaintBucket.menuHashStroke,
                    radius = radHash,
                    center = Offset(scoreCenterX, circleCy),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round, pathEffect = dash)
                )
                // Numeral, optically centered in the ring.
                val baselineY = circleCy + capPx / 2f
                drawText(
                    scoreResult,
                    topLeft = Offset(scoreCenterX - textW / 2f, baselineY - scoreResult.firstBaseline)
                )
            }

            // Low player (drawn directly in the bottom zone).
            val lowDrawX = (xMargin + lowOffset).coerceIn(0f, sw - textW)
            placement(lowDrawX + textW / 2f)

            // High player — same local coordinates, mirrored to the top zone (matches the live game).
            withTransform({ scale(-1f, -1f, pivot = Offset(cx, cy)) }) {
                val highDrawX = (xMargin + highOffset).coerceIn(0f, sw - textW)
                placement(highDrawX + textW / 2f)
            }
        }

        // Red right-edge checkmark pill — commits the placement and returns (changes are already
        // persisted on every drag). Replaces the old centered "Save Positions" button.
        if (initialized) {
            val pillH = (maxHeight * 0.07f).coerceIn(48.dp, 92.dp)
            val interaction = remember { MutableInteractionSource() }
            EdgePill(
                side = PillSide.End,
                color = PaintBucket.menuAccentRed,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(pillH)
                    .width(pillH * 1.3f)
                    .clickable(interactionSource = interaction, indication = null, onClick = onBack)
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_menu_check),
                    contentDescription = null,
                    modifier = Modifier.size(pillH * 0.5f),
                    colorFilter = ColorFilter.tint(PaintBucket.white)
                )
            }
        }
    }
}
