package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.CustomBallConfig
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import pock_kotlin.app.generated.resources.Res
import pock_kotlin.app.generated.resources.cbc_high_player
import pock_kotlin.app.generated.resources.cbc_low_player
import pock_kotlin.app.generated.resources.cbc_showing
import shapes.PaddleCarousel
import shapes.SkinCarousel
import shapes.TailCarousel
import shapes.ScrollSnapCarousel
import utility.PaintBucket
import utility.Storage
import utility.edgeSwipeBack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val ID_SKIN   = 0
private const val ID_TAIL   = 1
private const val ID_PADDLE = 2

private const val GESTURE_IDLE       = 0
private const val GESTURE_DETECTING  = 1
private const val GESTURE_HORIZONTAL = 2
private const val GESTURE_VERTICAL   = 3

// 3 seconds at 16 ms/frame ≈ 188 frames
private const val LONG_PRESS_FRAMES = 188

// Preview state
private const val STATE_NORMAL = 0
private const val STATE_SHIELD = 1
private const val STATE_INERT  = 2

private data class SavedBall(val storageIndex: Int, val config: CustomBallConfig)

@Composable
fun CustomBallCreatorScreen(onBack: () -> Unit, onNavigateToCcp: () -> Unit) {
    val isDark  = LocalDarkMode.current
    val density = LocalDensity.current
    val bgColor = if (isDark) PaintBucket.menuBackgroundDark else PaintBucket.menuBackgroundLight

    var frame by remember { mutableIntStateOf(0) }
    var canvasW by remember { mutableIntStateOf(0) }
    var canvasH by remember { mutableIntStateOf(0) }
    var initDone by remember { mutableStateOf(false) }

    val skinCarousel   = remember { SkinCarousel() }
    val tailCarousel   = remember { TailCarousel() }
    val paddleCarousel = remember { PaddleCarousel() }

    fun carousel(id: Int): shapes.ComponentCarousel = when (id) {
        ID_SKIN -> skinCarousel
        ID_TAIL -> tailCarousel
        else    -> paddleCarousel
    }

    fun loadBalls(): List<SavedBall> = (0 until 5).mapNotNull { i ->
        Storage.loadCustomBall(i)?.let { SavedBall(i, it) }
    }

    var savedBalls by remember { mutableStateOf(loadBalls()) }
    var selectedStorageIdx by remember { mutableIntStateOf(savedBalls.firstOrNull()?.storageIndex ?: -1) }

    // Color theme and state toggles for preview
    var previewTheme by remember { mutableStateOf(ColorTheme.Cold) }
    var previewState by remember { mutableIntStateOf(STATE_NORMAL) }
    var previewOrbitAngle by remember { mutableFloatStateOf(0f) }

    // Slot mini-preview renderers (one per storage slot 0–4)
    val slotRenderers = remember { arrayOfNulls<PuckRenderer>(5) }
    var slotVersion by remember { mutableIntStateOf(0) }

    // carouselRanks: skin=.first, tail=.second, paddle=.third  (0=back, 2=front)
    var carouselRanks by remember { mutableStateOf(Triple(0, 1, 2)) }

    fun rankOf(id: Int) = when (id) {
        ID_SKIN -> carouselRanks.first
        ID_TAIL -> carouselRanks.second
        else    -> carouselRanks.third
    }

    fun withNewRank(movingId: Int, newRank: Int): Triple<Int, Int, Int> {
        val others = (0..2).filter { it != movingId }.sortedBy { rankOf(it) }
        val free = (0..2).filter { it != newRank }.sorted()
        val newSkin = when {
            movingId == ID_SKIN -> newRank
            others.indexOf(ID_SKIN) >= 0 -> free[others.indexOf(ID_SKIN)]
            else -> carouselRanks.first
        }
        val newTail = when {
            movingId == ID_TAIL -> newRank
            others.indexOf(ID_TAIL) >= 0 -> free[others.indexOf(ID_TAIL)]
            else -> carouselRanks.second
        }
        val newPaddle = when {
            movingId == ID_PADDLE -> newRank
            others.indexOf(ID_PADDLE) >= 0 -> free[others.indexOf(ID_PADDLE)]
            else -> carouselRanks.third
        }
        return Triple(newSkin, newTail, newPaddle)
    }

    fun currentConfig() = CustomBallConfig(
        skinType    = skinCarousel.selectedType,
        tailType    = tailCarousel.selectedType,
        paddleType  = paddleCarousel.selectedType,
        skinZRank   = carouselRanks.first,
        tailZRank   = carouselRanks.second,
        paddleZRank = carouselRanks.third
    )

    fun allUnlocked(config: CustomBallConfig) =
        BallStyleFactory.isUnlocked(config.skinType, Settings.unlockProgress) &&
        BallStyleFactory.isUnlocked(config.tailType, Settings.unlockProgress) &&
        BallStyleFactory.isUnlocked(config.paddleType, Settings.unlockProgress)

    var lastSavedConfig by remember { mutableStateOf<CustomBallConfig?>(null) }

    fun trySave() {
        if (selectedStorageIdx < 0) return
        val cfg = currentConfig()
        if (cfg == lastSavedConfig || !allUnlocked(cfg)) return
        Storage.saveCustomBall(selectedStorageIdx, cfg)
        lastSavedConfig = cfg
        savedBalls = loadBalls()
    }

    var previewRenderer by remember { mutableStateOf<PuckRenderer?>(null) }
    var lastPreviewConfig by remember { mutableStateOf<CustomBallConfig?>(null) }

    fun rebuildPreview(cfg: CustomBallConfig) {
        previewRenderer?.tail?.clear()
        val r = BallStyleFactory.buildCustomRenderer(cfg, previewTheme)
        r.isHigh = previewTheme.isWarm
        previewRenderer = r
        lastPreviewConfig = cfg
    }

    fun rebuildSlotRenderers() {
        for (i in 0..4) {
            slotRenderers[i]?.tail?.clear()
            val cfg = Storage.loadCustomBall(i)
            slotRenderers[i] = if (cfg != null) {
                val r = BallStyleFactory.buildCustomRenderer(cfg, previewTheme)
                r.isHigh = previewTheme.isWarm
                r
            } else null
        }
        slotVersion++
    }

    fun loadSlot(config: CustomBallConfig) {
        skinCarousel.scrollToIndex(skinCarousel.availableTypes.indexOf(config.skinType).coerceAtLeast(0))
        tailCarousel.scrollToIndex(tailCarousel.availableTypes.indexOf(config.tailType).coerceAtLeast(0))
        paddleCarousel.scrollToIndex(paddleCarousel.availableTypes.indexOf(config.paddleType).coerceAtLeast(0))
        carouselRanks = Triple(config.skinZRank, config.tailZRank, config.paddleZRank)
        for (id in 0..2) carousel(id).clearTailAt(carousel(id).snapIndex)
    }

    // Animated Y positions per carousel (indexed by carousel id)
    val carouselAnimY = remember { FloatArray(3) }
    var animVersion   by remember { mutableIntStateOf(0) }

    // Reorder drag state
    var reorderingId by remember { mutableIntStateOf(-1) }
    var reorderDragY by remember { mutableFloatStateOf(0f) }

    // Gesture state
    var gesturePhase      by remember { mutableIntStateOf(GESTURE_IDLE) }
    var gestureTouchedId  by remember { mutableIntStateOf(-1) }
    var gestureStartX     by remember { mutableFloatStateOf(0f) }
    var gestureStartY     by remember { mutableFloatStateOf(0f) }
    var gesturePrevX      by remember { mutableFloatStateOf(0f) }
    var gesturePrevY      by remember { mutableFloatStateOf(0f) }

    // Long press
    var longPressIdx      by remember { mutableIntStateOf(-1) }
    var longPressStart    by remember { mutableIntStateOf(-1) }
    var longPressProgress by remember { mutableFloatStateOf(0f) }

    // ── Layout helpers ────────────────────────────────────────────────────────
    fun carouselH()       = if (Settings.screenRatio > 0f) Settings.screenRatio * 5f else 80f
    // Carousels sit just above the toggle button area (estimated at ratio*4 from bottom)
    fun carouselAreaTop() = canvasH - Settings.screenRatio * 5f - carouselH() * 3f
    fun slotTopY()        = carouselAreaTop() - Settings.screenRatio * 0.5f - Settings.screenRatio * 3f
    fun previewCenterY()  = slotTopY() / 2f
    fun targetYForRank(rank: Int) = carouselAreaTop() + (2 - rank) * carouselH() + carouselH() / 2f
    fun targetY(id: Int)  = if (id == reorderingId) reorderDragY else targetYForRank(rankOf(id))
    fun carouselIdAtY(y: Float): Int {
        val aTop = carouselAreaTop()
        val aBot = aTop + 3f * carouselH()
        if (y < aTop || y > aBot) return -1
        for (id in 0..2) {
            if (abs(y - targetYForRank(rankOf(id))) < carouselH() / 2f) return id
        }
        return -1
    }
    fun rankAtY(y: Float): Int {
        val raw = ((carouselAreaTop() + carouselH() / 2f - y) / carouselH() + 2f).roundToInt()
        return raw.coerceIn(0, 2)
    }

    // ── Animation loop ────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        while (true) {
            delay(16L)
            frame++
            previewOrbitAngle = (previewOrbitAngle + 1.2f) % 360f

            if (carouselH() > 0f) {
                var anyMoved = false
                for (id in 0..2) {
                    val t = targetY(id)
                    val prev = carouselAnimY[id]
                    carouselAnimY[id] = prev + (t - prev) * 0.22f
                    if (abs(carouselAnimY[id] - prev) > 0.5f) anyMoved = true
                }
                if (anyMoved) animVersion++
            }

            if (longPressIdx >= 0 && longPressStart >= 0) {
                val elapsed = frame - longPressStart
                longPressProgress = (elapsed.toFloat() / LONG_PRESS_FRAMES).coerceIn(0f, 1f)
                if (elapsed >= LONG_PRESS_FRAMES) {
                    val del = longPressIdx
                    Storage.deleteCustomBall(del)
                    savedBalls = loadBalls()
                    if (selectedStorageIdx == del) {
                        selectedStorageIdx = savedBalls.firstOrNull()?.storageIndex ?: -1
                        lastSavedConfig = null
                    }
                    longPressIdx = -1; longPressStart = -1; longPressProgress = 0f
                }
            }

            val cfg = currentConfig()
            if (cfg != lastPreviewConfig && initDone) {
                rebuildPreview(cfg)
                trySave()
            }
        }
    }

    // Rebuild slot mini-renderers whenever saved balls or preview theme change
    LaunchedEffect(savedBalls, previewTheme, initDone) {
        if (!initDone) return@LaunchedEffect
        rebuildSlotRenderers()
    }

    // ── Init ──────────────────────────────────────────────────────────────────
    LaunchedEffect(canvasW, canvasH) {
        if (!initDone && canvasW > 0 && canvasH > 0) {
            val ratio = max(1f, min(canvasW.toFloat(), canvasH.toFloat()) / 18f)
            if (Settings.screenRatio == 0f) {
                Settings.screenRatio = ratio
                Settings.strokeWidth = ratio / 4f
                Settings.screenWidth = canvasW.toFloat()
                Settings.screenHeight = canvasH.toFloat()
                Settings.middleX = canvasW / 2f
                Settings.middleY = canvasH / 2f
                PaintBucket.initialize(ratio)
                PaintBucket.applyPlayerHues()
            }
            Settings.unlockProgress = Storage.unlockProgress

            skinCarousel.initRenderers()
            tailCarousel.initRenderers()
            paddleCarousel.initRenderers()

            val initial = savedBalls.firstOrNull { it.storageIndex == selectedStorageIdx }
            if (initial != null) loadSlot(initial.config)

            for (id in 0..2) carouselAnimY[id] = targetYForRank(rankOf(id))
            animVersion++
            rebuildPreview(currentConfig())
            initDone = true
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .edgeSwipeBack(onBack)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasW = it.width; canvasH = it.height }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event  = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val x = change.position.x
                            val y = change.position.y

                            when (event.type) {
                                PointerEventType.Press -> {
                                    gestureStartX = x; gestureStartY = y
                                    gesturePrevX  = x; gesturePrevY  = y
                                    gesturePhase     = GESTURE_DETECTING
                                    gestureTouchedId = carouselIdAtY(y)

                                    val slotHit = cbcSlotHitTest(x, y, canvasW.toFloat(), canvasH.toFloat())
                                    if (slotHit >= 0 && savedBalls.any { it.storageIndex == slotHit }) {
                                        longPressIdx   = slotHit
                                        longPressStart = frame
                                        longPressProgress = 0f
                                    } else {
                                        longPressIdx = -1; longPressStart = -1; longPressProgress = 0f
                                    }
                                    change.consume()
                                }

                                PointerEventType.Move -> {
                                    val dy   = y - gesturePrevY
                                    val cumX = abs(x - gestureStartX)
                                    val cumY = abs(y - gestureStartY)

                                    if (gesturePhase == GESTURE_DETECTING) {
                                        val slop = viewConfiguration.touchSlop
                                        if (cumX > slop || cumY > slop) {
                                            gesturePhase = if (cumX >= cumY) GESTURE_HORIZONTAL else GESTURE_VERTICAL
                                            if (gesturePhase == GESTURE_HORIZONTAL && gestureTouchedId >= 0) {
                                                // Send ACTION_DOWN so the carousel starts tracking the drag
                                                carousel(gestureTouchedId).handleScrollTouchEvent(
                                                    ScrollSnapCarousel.ACTION_DOWN, gestureStartX, gestureStartY
                                                )
                                            } else if (gesturePhase == GESTURE_VERTICAL && gestureTouchedId >= 0) {
                                                reorderingId = gestureTouchedId
                                                reorderDragY = carouselAnimY[reorderingId]
                                            }
                                            longPressIdx = -1; longPressStart = -1; longPressProgress = 0f
                                        }
                                    }

                                    when (gesturePhase) {
                                        GESTURE_HORIZONTAL -> {
                                            if (gestureTouchedId >= 0)
                                                carousel(gestureTouchedId).handleScrollTouchEvent(
                                                    ScrollSnapCarousel.ACTION_MOVE, x, y
                                                )
                                            longPressIdx = -1; longPressStart = -1; longPressProgress = 0f
                                        }
                                        GESTURE_VERTICAL -> {
                                            if (reorderingId >= 0) {
                                                reorderDragY += dy
                                                val newRank = rankAtY(reorderDragY)
                                                if (newRank != rankOf(reorderingId))
                                                    carouselRanks = withNewRank(reorderingId, newRank)
                                            }
                                        }
                                    }
                                    gesturePrevX = x; gesturePrevY = y
                                    change.consume()
                                }

                                PointerEventType.Release -> {
                                    when (gesturePhase) {
                                        GESTURE_HORIZONTAL -> {
                                            if (gestureTouchedId >= 0)
                                                carousel(gestureTouchedId).handleScrollTouchEvent(
                                                    ScrollSnapCarousel.ACTION_UP, x, y
                                                )
                                        }
                                        GESTURE_VERTICAL -> { reorderingId = -1 }
                                        GESTURE_DETECTING -> {
                                            val slotHit = cbcSlotHitTest(x, y, canvasW.toFloat(), canvasH.toFloat())
                                            if (slotHit >= 0 && longPressProgress < 0.05f) {
                                                val existing = savedBalls.firstOrNull { it.storageIndex == slotHit }
                                                if (existing != null) {
                                                    selectedStorageIdx = slotHit
                                                    lastSavedConfig    = null
                                                    loadSlot(existing.config)
                                                    rebuildPreview(currentConfig())
                                                } else {
                                                    val cfg = currentConfig()
                                                    if (allUnlocked(cfg)) {
                                                        Storage.saveCustomBall(slotHit, cfg)
                                                        savedBalls = loadBalls()
                                                        selectedStorageIdx = slotHit
                                                        lastSavedConfig    = cfg
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    gesturePhase = GESTURE_IDLE
                                    longPressIdx = -1; longPressStart = -1; longPressProgress = 0f
                                    change.consume()
                                }
                            }
                        }
                    }
                }
        ) {
            if (!initDone || Settings.screenRatio == 0f) return@Canvas
            @Suppress("UNUSED_EXPRESSION") animVersion
            @Suppress("UNUSED_EXPRESSION") slotVersion

            val ratio = Settings.screenRatio
            val cw    = size.width

            // Apply current state to carousel renderers every frame
            val shielded = previewState == STATE_SHIELD
            val inert    = previewState == STATE_INERT
            skinCarousel.updateStateFlags(shielded, inert)
            tailCarousel.updateStateFlags(shielded, inert)
            paddleCarousel.updateStateFlags(shielded, inert)

            // Slot mini-renderers: apply state flags
            for (r in slotRenderers) {
                r?.shielded = shielded
                r?.inertLocked = inert
            }

            // ── Preview ball ─────────────────────────────────────────────
            val previewCy        = previewCenterY()
            val previewAmplitude = ratio * 1.5f
            val previewY         = previewCy + previewAmplitude * sin(2f * PI.toFloat() * frame / 70f)
            val pr = previewRenderer
            if (pr != null) {
                pr.x = cw / 2f
                pr.y = previewY
                pr.radius      = Settings.ballRadius
                pr.strokeWidth = Settings.strokeWidth
                pr.frame       = frame
                pr.effectEnabled = true
                pr.shielded    = shielded
                pr.inertLocked = inert
                pr.fillColor   = previewTheme.main.primary
                pr.strokeColor = previewTheme.main.secondary
                pr.baseFillColor = previewTheme.main.primary
                pr.effect.increaseCharge()
                if (pr.effect.phase == ChargePhase.Inert) pr.effect.reset()
                (pr.effect as? PaddleLaunchEffect)?.cbcOrbitAngleDeg = previewOrbitAngle
                with(pr) { draw() }
            }

            // ── Slot header ───────────────────────────────────────────
            drawCbcSlotHeader(
                savedBalls        = savedBalls,
                selectedIdx       = selectedStorageIdx,
                longPressIdx      = longPressIdx,
                longPressProgress = longPressProgress,
                slotRenderers     = slotRenderers,
                canvasW           = cw,
                slotTopY          = slotTopY(),
                ratio             = ratio,
                frame             = frame,
                previewTheme      = previewTheme,
                isDark            = isDark
            )

            // ── Three carousels ───────────────────────────────────────
            for (id in 0..2) {
                val car = carousel(id)
                val cy  = carouselAnimY[id]
                with(car) { drawTo(cy, frame) }
            }
        }

        // ── PALETTE button — pinned to right side, level with the preset slot row ──
        val overlayBtnColors = ButtonDefaults.buttonColors(
            containerColor = if (isDark) PaintBucket.menuButtonDark else PaintBucket.menuButtonLight,
            contentColor   = if (isDark) PaintBucket.white else PaintBucket.menuBackgroundDark
        )
        val paletteBtnTopDp = with(density) {
            if (initDone && Settings.screenRatio > 0f) {
                val slotTopPx = cbcSlotTopY(canvasH.toFloat(), Settings.screenRatio)
                (slotTopPx - 48.dp.toPx()).toDp().coerceAtLeast(8.dp)
            } else 0.dp
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = paletteBtnTopDp, end = 12.dp)
        ) {
            Button(onClick = onNavigateToCcp, colors = overlayBtnColors) {
                Text(text = "PALETTE", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── Toggle buttons ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text       = stringResource(Res.string.cbc_showing).uppercase(),
                color      = if (isDark) PaintBucket.white else Color(0xFF333333.toInt()),
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = {
                    val newTheme = if (previewTheme.isWarm) ColorTheme.Cold else ColorTheme.Warm
                    previewTheme = newTheme
                    skinCarousel.updateTheme(newTheme)
                    tailCarousel.updateTheme(newTheme)
                    paddleCarousel.updateTheme(newTheme)
                    rebuildPreview(currentConfig())
                },
                colors = overlayBtnColors
            ) {
                Text(
                    text       = if (previewTheme.isWarm) stringResource(Res.string.cbc_high_player).uppercase()
                                 else stringResource(Res.string.cbc_low_player).uppercase(),
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(
                onClick = { previewState = (previewState + 1) % 3 },
                colors = overlayBtnColors
            ) {
                Text(
                    text = when (previewState) {
                        STATE_SHIELD -> "SHIELD"
                        STATE_INERT  -> "INERT"
                        else         -> "NORMAL"
                    },
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Hit testing ───────────────────────────────────────────────────────────────

/** Always tests all 5 fixed slot positions; returns slot index 0–4, or -1 if miss. */
private fun cbcSlotHitTest(x: Float, y: Float, canvasW: Float, canvasH: Float): Int {
    val ratio    = Settings.screenRatio
    val slotSize = ratio * 3f
    val gap      = ratio * 0.5f
    val totalW   = 5 * (slotSize + gap) - gap
    var sx       = (canvasW - totalW) / 2f
    val topY     = cbcSlotTopY(canvasH, ratio)
    for (i in 0..4) {
        if (x in sx..(sx + slotSize) && y in topY..(topY + slotSize)) return i
        sx += slotSize + gap
    }
    return -1
}

private fun cbcSlotTopY(canvasH: Float, ratio: Float): Float {
    if (ratio == 0f) return 0f
    val carouselH = ratio * 5f
    val carouselAreaTop = canvasH - ratio * 6f - carouselH * 3f
    return carouselAreaTop - ratio * 0.5f - ratio * 3f
}

// ── Drawing helpers ───────────────────────────────────────────────────────────

private fun DrawScope.drawCbcSlotHeader(
    savedBalls: List<SavedBall>,
    selectedIdx: Int,
    longPressIdx: Int,
    longPressProgress: Float,
    slotRenderers: Array<PuckRenderer?>,
    canvasW: Float,
    slotTopY: Float,
    ratio: Float,
    frame: Int,
    previewTheme: ColorTheme,
    isDark: Boolean
) {
    val slotSize = ratio * 3f
    val gap      = ratio * 0.5f
    val totalW   = 5 * (slotSize + gap) - gap
    var sx       = (canvasW - totalW) / 2f
    val topY     = slotTopY
    val cr       = CornerRadius(ratio * 0.35f)

    for (i in 0..4) {
        val populated = savedBalls.any { it.storageIndex == i }
        val isSelected    = i == selectedIdx
        val isLongPressed = i == longPressIdx

        // Background
        val bgColor     = if (isDark) Color(0xFF1A2A3E.toInt()) else Color(0xFFE8E8F0.toInt())
        val borderColor = if (isSelected) Color(0xFF52B6F2.toInt()) else Color(0xFFF25252.toInt())
        val strokeW     = if (isSelected) ratio * 0.22f else ratio * 0.12f

        drawRoundRect(
            color       = bgColor,
            topLeft     = Offset(sx, topY),
            size        = Size(slotSize, slotSize),
            cornerRadius = cr
        )
        drawRoundRect(
            color       = borderColor,
            topLeft     = Offset(sx, topY),
            size        = Size(slotSize, slotSize),
            cornerRadius = cr,
            style       = Stroke(strokeW)
        )

        if (populated) {
            // Draw mini ball preview
            val miniR = slotRenderers[i]
            if (miniR != null) {
                miniR.x = sx + slotSize / 2f
                miniR.y = topY + slotSize / 2f
                miniR.radius = Settings.ballRadius
                miniR.strokeWidth = Settings.strokeWidth
                miniR.frame = frame
                miniR.effectEnabled = false
                miniR.fillColor = previewTheme.main.primary
                miniR.strokeColor = previewTheme.main.secondary
                miniR.baseFillColor = previewTheme.main.primary
                with(miniR) { draw() }
            } else {
                // Fallback: simple colored dot
                drawCircle(
                    color  = Color(previewTheme.main.primary).copy(alpha = 0.7f),
                    radius = slotSize * 0.27f,
                    center = Offset(sx + slotSize / 2f, topY + slotSize / 2f)
                )
            }
        } else {
            // Empty slot indicator
            drawCircle(
                color  = Color(if (isDark) 0x22FFFFFF else 0x22000000.toInt()),
                radius = slotSize * 0.18f,
                center = Offset(sx + slotSize / 2f, topY + slotSize / 2f)
            )
            val bLen = slotSize * 0.32f
            val cxp  = sx + slotSize / 2f
            val cyp  = topY + slotSize / 2f
            val plusColor = Color(if (isDark) 0x55FFFFFF else 0x55000000.toInt())
            drawLine(plusColor, Offset(cxp - bLen / 2f, cyp), Offset(cxp + bLen / 2f, cyp), ratio * 0.2f, StrokeCap.Round)
            drawLine(plusColor, Offset(cxp, cyp - bLen / 2f), Offset(cxp, cyp + bLen / 2f), ratio * 0.2f, StrokeCap.Round)
        }

        // Long press deletion ring
        if (isLongPressed && longPressProgress > 0f) {
            val ringR = slotSize * 0.42f
            val cxp   = sx + slotSize / 2f
            val cyp   = topY + slotSize / 2f
            drawArc(
                color      = Color(0xFFFF4444.toInt()),
                startAngle = -90f,
                sweepAngle = longPressProgress * 360f,
                useCenter  = false,
                topLeft    = Offset(cxp - ringR, cyp - ringR),
                size       = Size(ringR * 2f, ringR * 2f),
                style      = Stroke(ratio * 0.25f, cap = StrokeCap.Round)
            )
        }

        sx += slotSize + gap
    }
}

