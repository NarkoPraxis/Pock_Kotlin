package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
    val bgColor = if (isDark) Color(0xFF12102A) else Color(0xFFFFFFFF)
    val textPrimary = if (isDark) Color.White else Color(0xFF12102A)
    val dividerColor = if (isDark) Color(0xFF444466) else Color(0xFFCCCCDD)

    val displayTypes = remember { BallType.entries.filter { it != BallType.Random } }
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
    var unlockProgress by remember { mutableIntStateOf(Storage.unlockProgress) }
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
            for (i in 0 until count) {
                val theme = if (warmFlags[i]) ColorTheme.Warm else ColorTheme.Cold
                renderers[i] = BallStyleFactory.buildRenderer(displayTypes[i], theme)
            }
            renderersBuilt = true
        }
    }

    LaunchedEffect(Unit) {
        unlockProgress = Storage.unlockProgress
        Settings.unlockProgress = unlockProgress
        if (unlockProgress < 100 && Storage.canWatchAdNow()) {
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
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← Back", color = textPrimary, fontSize = 16.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "BALL TYPES",
                color = textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
        }

        // Unlock progress section (hidden when fully unlocked)
        if (unlockProgress < 100) {
            HorizontalDivider(color = dividerColor)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "$unlockProgress% Unlocked",
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Watch ads to unlock more ball types",
                        color = textPrimary.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
                LinearProgressIndicator(
                    progress = { unlockProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF6666AA),
                    trackColor = if (isDark) Color(0xFF333344) else Color(0xFFCCCCDD)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val adLabel = when {
                        Storage.adsWatchedToday() >= 5 -> "Come Back Tomorrow"
                        Storage.minutesUntilNextAd() > 0 -> {
                            val mins = Storage.minutesUntilNextAd()
                            "Next Ad in ${if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"}"
                        }
                        adReady -> "Watch Ad (+2%)"
                        else -> "Watch Ad (Loading...)"
                    }
                    Button(
                        onClick = {
                            PlatformAd.showRewardedAd(
                                onEarned = {
                                    Storage.recordAdWatched()
                                    unlockProgress = Storage.unlockProgress
                                    Settings.unlockProgress = unlockProgress
                                    adReady = false
                                },
                                onDismissed = {
                                    adReady = false
                                    if (Storage.canWatchAdNow()) {
                                        PlatformAd.loadRewardedAd(
                                            adUnitId = PlatformAd.TEST_REWARDED_AD_UNIT_ID,
                                            onLoaded = { adReady = true },
                                            onFailed = { adReady = false }
                                        )
                                    }
                                }
                            )
                        },
                        enabled = adReady && Storage.canWatchAdNow(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF444466),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF333344),
                            disabledContentColor = Color(0xFF888899)
                        )
                    ) {
                        Text(adLabel, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { /* TODO: iOS IAP - see PlatformAd.kt for integration notes */ },
                        enabled = false,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF444466),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF333344),
                            disabledContentColor = Color(0xFF888899)
                        )
                    ) {
                        Text("Get Pro", fontSize = 12.sp)
                    }
                }

                TextButton(
                    onClick = { /* TODO: iOS restore purchases */ },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Restore Purchases", color = textPrimary.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
            HorizontalDivider(color = dividerColor)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(displayTypes) { index, type ->
                val isUnlocked = BallStyleFactory.isUnlocked(type, unlockProgress)

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
                        val previewRenderer = renderers[index] ?: return@Canvas

                        val savedRatio = Settings.screenRatio
                        val savedStroke = Settings.strokeWidth
                        val ratio = if (screenWidth > 0) max(1f, min(screenWidth.toFloat(), screenHeight.toFloat()) / 18f)
                                    else min(size.width, size.height) / 18f
                        Settings.screenRatio = ratio
                        Settings.strokeWidth = ratio / 4f

                        val theme = if (warmFlags[index]) ColorTheme.Warm else ColorTheme.Cold

                        val cardBgColor = if (isDark)
                            Color(0x16 / 255f, 0x16 / 255f, 0x22 / 255f, 0xDC / 255f)
                        else
                            Color(0xE8 / 255f, 0xE8 / 255f, 0xF8 / 255f, 0xDC / 255f)
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
                        val puckY = baseCy + amplitude * sin(2f * PI.toFloat() * bounceFrame / 80f + phase)
                        val pr = ratio * 1.2f

                        previewRenderer.effectEnabled = false
                        previewRenderer.strokeWidth = ratio / 4f
                        previewRenderer.x = cx
                        previewRenderer.y = puckY
                        previewRenderer.radius = pr
                        previewRenderer.frame = bounceFrame
                        previewRenderer.theme = theme
                        previewRenderer.fillColor = theme.main.primary
                        previewRenderer.strokeColor = theme.main.secondary
                        previewRenderer.baseFillColor = theme.main.primary
                        previewRenderer.preview = !isUnlocked

                        with(previewRenderer) { draw() }

                        if (!isUnlocked) drawLock(cx, puckY, pr, ratio)

                        val pxPerSp = density * fontScale
                        val textColor = if (isDark) Color.White
                            else Color(0x0F / 255f, 0x0F / 255f, 0x23 / 255f, 0xE6 / 255f)
                        val subColor = if (isDark) Color(1f, 1f, 1f, 0xA0 / 255f)
                            else Color(0x14 / 255f, 0x14 / 255f, 0x32 / 255f, 0xA0 / 255f)

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

                        val statusText = if (isUnlocked) "Unlocked"
                            else BallStyleFactory.unlockThreshold(type)?.let { "Reach $it%" } ?: "Unlocked"
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

                        Settings.screenRatio = savedRatio
                        Settings.strokeWidth = savedStroke
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawLock(cx: Float, ly: Float, radius: Float, ratio: Float) {
    val strokeW = ratio * 0.22f
    val bodyW = radius * 0.8f
    val bodyH = radius * 0.7f

    drawRoundRect(
        color = Color.White,
        topLeft = Offset(cx - bodyW / 2f, ly - bodyH / 4f),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(ratio * 0.15f)
    )

    val shackleR = bodyW / 2.6f
    drawArc(
        color = Color.White,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - shackleR, ly - bodyH * 0.85f),
        size = Size(shackleR * 2f, bodyH * 0.75f),
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )
}
