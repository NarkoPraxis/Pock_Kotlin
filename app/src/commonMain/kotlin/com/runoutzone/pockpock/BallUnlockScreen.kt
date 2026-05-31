package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import enums.BallType
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import pock_kotlin.app.generated.resources.*
import utility.PaintBucket
import utility.PlatformAd
import utility.Storage
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
fun BallUnlockScreen(onBack: () -> Unit) {
    val isDark = LocalDarkMode.current
    val bgColor = if (isDark) PaintBucket.menuBackgroundDark else PaintBucket.menuBackgroundLight
    val textPrimary = if (isDark) PaintBucket.white else PaintBucket.menuBackgroundDark
    val dividerColor = if (isDark) PaintBucket.dividerDark else PaintBucket.dividerLight

    val displayTypes = remember { BallType.entries.toList() }
    val count = displayTypes.size
    val textMeasurer = rememberTextMeasurer()

    val warmFlags = remember { BooleanArray(count) { i -> i % 2 == 0 } }
    var bounceFrame by remember { mutableIntStateOf(0) }
    var bouncingIndex by remember { mutableIntStateOf(-1) }
    val renderers = remember { arrayOfNulls<PuckRenderer>(count) }
    var renderersBuilt by remember { mutableStateOf(false) }
    var screenWidth by remember { mutableIntStateOf(0) }
    var screenHeight by remember { mutableIntStateOf(0) }

    // Read unlock progress from Storage (not Settings, which requires game init first)
    val unlockProgress = Storage.unlockProgress
    Settings.unlockProgress = unlockProgress

    var adReady by remember { mutableStateOf(false) }

    LaunchedEffect(screenWidth, screenHeight) {
        if (!renderersBuilt && screenWidth > 0 && screenHeight > 0) {
            val r = max(1f, min(screenWidth.toFloat(), screenHeight.toFloat()) / 18f)
            if (Settings.screenRatio == 0f) {
                Settings.screenRatio = r
                Settings.strokeWidth = r / 4f
                PaintBucket.initialize(r)
            }
            PaintBucket.applyPlayerHues()
            for (i in 0 until count) {
                val theme = if (warmFlags[i]) ColorTheme.Warm else ColorTheme.Cold
                renderers[i] = BallStyleFactory.buildRenderer(displayTypes[i], theme)
            }
            renderersBuilt = true
        }
    }

    LaunchedEffect(Unit) {
        if (Storage.unlockProgress < 100 && Storage.canWatchAdNow()) {
            PlatformAd.loadRewardedAd(
                adUnitId = PlatformAd.TEST_REWARDED_AD_UNIT_ID,
                onLoaded = { adReady = true },
                onFailed = { adReady = false }
            )
        }
        while (true) {
            delay(16L)
            bounceFrame++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .onSizeChanged { screenWidth = it.width; screenHeight = it.height }
    ) {
        // Header row: Back | [title centered over progress bar]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(Res.string.back), color = textPrimary, fontSize = 16.sp)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (unlockProgress < 100) {
                    UnlockProgressBar(
                        progress = unlockProgress,
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    )
                }
                Text(
                    stringResource(Res.string.ball_types_title),
                    color = if (unlockProgress < 100) PaintBucket.white else textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = if (unlockProgress < 100) 44.dp else 0.dp)
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(displayTypes) { index, type ->
                // Deprecated screen (no longer reachable). Kept compiling only.
                val isUnlocked = utility.Storage.isSkinUnlocked(type) &&
                    utility.Storage.isTailUnlocked(type) &&
                    utility.Storage.isPaddleUnlocked(type)
                val statusText = ""

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clickable {
                            if (renderersBuilt) {
                                warmFlags[index] = !warmFlags[index]
                                renderers[index]?.tail?.clear()
                                val newTheme = if (warmFlags[index]) ColorTheme.Warm else ColorTheme.Cold
                                renderers[index] = BallStyleFactory.buildRenderer(type, newTheme)
                                bouncingIndex = index
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
                        val frame = bounceFrame
                        val previewRenderer = renderers[index] ?: return@Canvas

                        val savedRatio = Settings.screenRatio
                        val savedStroke = Settings.strokeWidth
                        val ratio = if (screenWidth > 0) max(1f, min(screenWidth.toFloat(), screenHeight.toFloat()) / 18f)
                                    else min(size.width, size.height) / 18f
                        Settings.screenRatio = ratio
                        Settings.strokeWidth = ratio / 4f

                        val theme = if (warmFlags[index]) ColorTheme.Warm else ColorTheme.Cold

                        val cardBgColor = if (isDark)
                            PaintBucket.menuBackgroundDark.copy(alpha = 0xDC / 255f)
                        else
                            PaintBucket.cardBackgroundLight.copy(alpha = 0xDC / 255f)
                        drawRoundRect(color = cardBgColor, cornerRadius = CornerRadius(ratio * 0.4f))

                        drawRoundRect(
                            color = Color(theme.main.primary),
                            cornerRadius = CornerRadius(ratio * 0.4f),
                            style = Stroke(width = ratio * 0.24f)
                        )

                        val cx = size.width / 2f
                        val baseCy = size.height / 2f - ratio * 0.4f
                        val amplitude = if (index == bouncingIndex) ratio * 1.1f else ratio * 0.5f
                        val phase = index * 0.7f
                        val puckY = baseCy + amplitude * sin(2f * PI.toFloat() * frame / 80f + phase)
                        val pr = ratio * 1.2f

                        previewRenderer.effectEnabled = false
                        previewRenderer.strokeWidth = ratio / 4f
                        previewRenderer.x = cx
                        previewRenderer.y = puckY
                        previewRenderer.radius = pr
                        previewRenderer.frame = frame
                        previewRenderer.theme = theme
                        previewRenderer.fillColor = theme.main.primary
                        previewRenderer.strokeColor = theme.main.secondary
                        previewRenderer.baseFillColor = theme.main.primary
                        previewRenderer.preview = false

                        with(previewRenderer) { draw() }

                        if (!isUnlocked) drawLock(cx, puckY, pr, ratio)

                        val pxPerSp = density * fontScale
                        val textColor = if (isDark) PaintBucket.white
                            else PaintBucket.menuBackgroundDark.copy(alpha = 0xE6 / 255f)
                        val subColor = if (isDark) PaintBucket.white.copy(alpha = 0xA0 / 255f)
                            else PaintBucket.menuBackgroundDark.copy(alpha = 0xA0 / 255f)

                        val nameStyle = TextStyle(
                            color = textColor,
                            fontSize = (ratio * 0.7f / pxPerSp).sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        val nameMeasured = textMeasurer.measure(type.name, nameStyle)
                        drawText(
                            nameMeasured,
                            topLeft = Offset(
                                cx - nameMeasured.size.width / 2f,
                                size.height - ratio * 0.85f - nameMeasured.size.height
                            )
                        )

                        if (statusText.isNotEmpty()) {
                            val subStyle = TextStyle(
                                color = subColor,
                                fontSize = (ratio * 0.45f / pxPerSp).sp,
                                textAlign = TextAlign.Center
                            )
                            val subMeasured = textMeasurer.measure(statusText, subStyle)
                            drawText(
                                subMeasured,
                                topLeft = Offset(
                                    cx - subMeasured.size.width / 2f,
                                    size.height - ratio * 0.3f - subMeasured.size.height
                                )
                            )
                        }

                        Settings.screenRatio = savedRatio
                        Settings.strokeWidth = savedStroke
                    }
                }
            }
        }

        PlatformBallUnlockBottom()
    }
}

private fun DrawScope.drawLock(cx: Float, ly: Float, radius: Float, ratio: Float) {
    val lockColor = PaintBucket.shieldPrimary
    val strokeW = ratio * 0.22f
    val bodyW = radius * 0.8f
    val bodyH = radius * 0.7f

    drawRoundRect(
        color = lockColor,
        topLeft = Offset(cx - bodyW / 2f, ly - bodyH / 4f),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(ratio * 0.15f)
    )

    val shackleR = bodyW / 2.6f
    drawArc(
        color = lockColor,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - shackleR, ly - bodyH * 0.85f),
        size = Size(shackleR * 2f, bodyH * 0.75f),
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )
}
