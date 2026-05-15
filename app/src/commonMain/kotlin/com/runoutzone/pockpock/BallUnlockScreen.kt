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
import utility.PaintBucket
import utility.Storage
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

@Composable
fun BallUnlockScreen(onBack: () -> Unit) {
    val displayTypes = remember { BallType.entries.filter { it != BallType.Random } }
    val count = displayTypes.size
    val textMeasurer = rememberTextMeasurer()

    val warmFlags = remember { BooleanArray(count) { i -> i % 2 == 0 } }
    var bounceFrame by remember { mutableIntStateOf(0) }
    var bouncingIndex by remember { mutableIntStateOf(-1) }
    val renderers = remember { arrayOfNulls<PuckRenderer>(count) }
    var renderersBuilt by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(16L)
            bounceFrame++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PaintBucket.backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← Back", color = Color.White, fontSize = 16.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "BALL TYPES",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
        }

        PlatformBallUnlockExtras()

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(displayTypes) { index, type ->
                val isUnlocked = BallStyleFactory.isUnlocked(type, Settings.unlockProgress)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .onSizeChanged { size ->
                            if (!renderersBuilt && size.width > 0) {
                                val r = min(size.width.toFloat(), size.height.toFloat()) / 18f
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
                        val ratio = min(size.width, size.height) / 18f
                        Settings.screenRatio = ratio
                        Settings.strokeWidth = ratio / 4f

                        val theme = if (warmFlags[index]) ColorTheme.Warm else ColorTheme.Cold

                        val bgColor = if (Storage.darkMode)
                            Color(0x16 / 255f, 0x16 / 255f, 0x22 / 255f, 0xDC / 255f)
                        else
                            Color(0xE8 / 255f, 0xE8 / 255f, 0xF8 / 255f, 0xDC / 255f)
                        drawRoundRect(color = bgColor, cornerRadius = CornerRadius(ratio * 0.4f))

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
                        val textColor = if (Storage.darkMode) Color.White
                            else Color(0x0F / 255f, 0x0F / 255f, 0x23 / 255f, 0xE6 / 255f)
                        val subColor = if (Storage.darkMode) Color(1f, 1f, 1f, 0xA0 / 255f)
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
