package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.*
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
import enums.BallType
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.CustomBallConfig
import gameobjects.puckstyle.PuckRenderer
import kotlinx.coroutines.delay
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

private const val GESTURE_IDLE      = 0
private const val GESTURE_DETECTING = 1
private const val GESTURE_HORIZONTAL = 2
private const val GESTURE_VERTICAL  = 3

// 3 seconds at 16 ms/frame ≈ 188 frames
private const val LONG_PRESS_FRAMES = 188

private data class SavedBall(val storageIndex: Int, val config: CustomBallConfig)

@Composable
fun CustomBallCreatorScreen(onBack: () -> Unit) {
    val isDark = LocalDarkMode.current
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

    // carouselRanks: skin=.first, tail=.second, paddle=.third  (0=back, 2=front)
    var carouselRanks by remember { mutableStateOf(Triple(0, 1, 2)) }

    fun rankOf(id: Int) = when (id) {
        ID_SKIN -> carouselRanks.first
        ID_TAIL -> carouselRanks.second
        else    -> carouselRanks.third
    }

    fun withNewRank(movingId: Int, newRank: Int): Triple<Int, Int, Int> {
        // Others maintain relative order and fill remaining ranks
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
        previewRenderer = BallStyleFactory.buildCustomRenderer(cfg, ColorTheme.Warm)
        lastPreviewConfig = cfg
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
    var reorderingId  by remember { mutableIntStateOf(-1) }
    var reorderDragY  by remember { mutableFloatStateOf(0f) }

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

    // ── Layout helpers (local funs read live state at call time) ─────────────
    fun carouselH()       = if (Settings.screenRatio > 0f) Settings.screenRatio * 5f else 80f
    fun carouselAreaTop() = canvasH * 0.38f
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

    // ── Animation loop ───────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        while (true) {
            delay(16L)
            frame++

            // Lerp carousel Y toward target
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

            // Long press countdown
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

            // Rebuild preview and auto-save when config changes
            val cfg = currentConfig()
            if (cfg != lastPreviewConfig && initDone) {
                rebuildPreview(cfg)
                trySave()
            }
        }
    }

    // ── Init ─────────────────────────────────────────────────────────────────
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

    // ── UI ───────────────────────────────────────────────────────────────────
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

                                    val slotHit = slotHitTest(x, y, savedBalls.map { it.storageIndex }, canvasW.toFloat())
                                    if (slotHit >= 0) {
                                        longPressIdx   = slotHit
                                        longPressStart = frame
                                        longPressProgress = 0f
                                    } else {
                                        longPressIdx = -1; longPressStart = -1; longPressProgress = 0f
                                    }
                                    change.consume()
                                }

                                PointerEventType.Move -> {
                                    val dx   = x - gesturePrevX
                                    val dy   = y - gesturePrevY
                                    val cumX = abs(x - gestureStartX)
                                    val cumY = abs(y - gestureStartY)

                                    if (gesturePhase == GESTURE_DETECTING) {
                                        val slop = viewConfiguration.touchSlop
                                        if (cumX > slop || cumY > slop) {
                                            gesturePhase = if (cumX >= cumY) GESTURE_HORIZONTAL else GESTURE_VERTICAL
                                            if (gesturePhase == GESTURE_VERTICAL && gestureTouchedId >= 0) {
                                                reorderingId = gestureTouchedId
                                                reorderDragY = carouselAnimY[reorderingId]
                                            }
                                            longPressIdx = -1; longPressStart = -1; longPressProgress = 0f
                                        }
                                    }

                                    when (gesturePhase) {
                                        GESTURE_HORIZONTAL -> {
                                            if (gestureTouchedId >= 0)
                                                carousel(gestureTouchedId).handleScrollTouchEvent(ScrollSnapCarousel.ACTION_MOVE, x, y)
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
                                                carousel(gestureTouchedId).handleScrollTouchEvent(ScrollSnapCarousel.ACTION_UP, x, y)
                                        }
                                        GESTURE_VERTICAL -> { reorderingId = -1 }
                                        GESTURE_DETECTING -> {
                                            // Tap
                                            val slotHit = slotHitTest(x, y, savedBalls.map { it.storageIndex }, canvasW.toFloat())
                                            if (slotHit >= 0 && longPressProgress < 0.05f) {
                                                selectedStorageIdx = slotHit
                                                lastSavedConfig    = null
                                                savedBalls.firstOrNull { it.storageIndex == slotHit }?.let { loadSlot(it.config) }
                                                rebuildPreview(currentConfig())
                                            } else if (slotHit < 0 && isAddButtonHit(x, y, savedBalls.size, canvasW.toFloat())) {
                                                val newIdx = (0 until 5).firstOrNull { i -> savedBalls.none { it.storageIndex == i } }
                                                if (newIdx != null) {
                                                    val cfg = currentConfig()
                                                    if (allUnlocked(cfg)) {
                                                        Storage.saveCustomBall(newIdx, cfg)
                                                        savedBalls = loadBalls()
                                                        selectedStorageIdx = newIdx
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
            @Suppress("UNUSED_EXPRESSION") animVersion // recompose when positions update

            val ratio = Settings.screenRatio
            val cw    = size.width

            // ── Slot header ──────────────────────────────────────────────
            drawSlotHeader(
                savedBalls      = savedBalls.map { it.storageIndex },
                selectedIdx     = selectedStorageIdx,
                longPressIdx    = longPressIdx,
                longPressProgress = longPressProgress,
                canvasW         = cw,
                ratio           = ratio,
                isDark          = isDark
            )

            // ── Preview ball ─────────────────────────────────────────────
            val previewCy        = canvasH * 0.22f
            val previewAmplitude = ratio * 1.5f
            val previewY         = previewCy + previewAmplitude * sin(2f * PI.toFloat() * frame / 70f)
            val pr = previewRenderer
            if (pr != null) {
                pr.x = cw / 2f
                pr.y = previewY
                pr.radius      = Settings.ballRadius * 1.3f
                pr.strokeWidth = ratio / 4f
                pr.frame       = frame
                pr.effectEnabled = true
                pr.fillColor   = ColorTheme.Warm.main.primary
                pr.strokeColor = ColorTheme.Warm.main.secondary
                pr.baseFillColor = ColorTheme.Warm.main.primary
                pr.effect.increaseCharge()
                if (pr.effect.phase == ChargePhase.Inert) pr.effect.reset()
                with(pr) { draw() }
            }

            // ── Three carousels ──────────────────────────────────────────
            for (id in 0..2) {
                val car = carousel(id)
                val cy  = carouselAnimY[id]
                with(car) { drawTo(cy, frame) }
                drawCarouselLabel(
                    label  = when (id) { ID_SKIN -> "BALL"; ID_TAIL -> "TAIL"; else -> "PADDLE" },
                    carouselTopY = cy - carouselH() / 2f,
                    ratio  = ratio,
                    isDark = isDark
                )
            }
        }
    }
}

// ── Hit testing ──────────────────────────────────────────────────────────────

private fun slotHitTest(x: Float, y: Float, storageIndices: List<Int>, canvasW: Float): Int {
    val ratio    = Settings.screenRatio
    val slotSize = ratio * 3f
    val gap      = ratio * 0.5f
    val totalW   = storageIndices.size * (slotSize + gap) - gap
    var sx       = (canvasW - totalW) / 2f
    val topY     = ratio * 0.4f
    for (idx in storageIndices) {
        if (x in sx..(sx + slotSize) && y in topY..(topY + slotSize)) return idx
        sx += slotSize + gap
    }
    return -1
}

private fun isAddButtonHit(x: Float, y: Float, count: Int, canvasW: Float): Boolean {
    if (count >= 5) return false
    val ratio    = Settings.screenRatio
    val slotSize = ratio * 3f
    val gap      = ratio * 0.5f
    val totalW   = count * (slotSize + gap) + slotSize
    val sx       = (canvasW - totalW) / 2f + count * (slotSize + gap)
    val topY     = ratio * 0.4f
    return x in sx..(sx + slotSize) && y in topY..(topY + slotSize)
}

// ── Drawing helpers ───────────────────────────────────────────────────────────

private fun DrawScope.drawSlotHeader(
    savedBalls: List<Int>,
    selectedIdx: Int,
    longPressIdx: Int,
    longPressProgress: Float,
    canvasW: Float,
    ratio: Float,
    isDark: Boolean
) {
    val slotSize = ratio * 3f
    val gap      = ratio * 0.5f
    val count    = savedBalls.size
    val addCount = if (count < 5) 1 else 0
    val totalW   = (count + addCount) * (slotSize + gap) - gap
    var sx       = (canvasW - totalW) / 2f
    val topY     = ratio * 0.4f
    val cr       = CornerRadius(ratio * 0.35f)

    for ((uiIdx, storageIdx) in savedBalls.withIndex()) {
        val isSelected    = storageIdx == selectedIdx
        val isLongPressed = storageIdx == longPressIdx
        val borderColor   = if (isSelected) Color(ColorTheme.Warm.main.primary) else Color(0x66FFFFFF)
        val bgColor       = if (isSelected) Color(0x44FFAA44.toInt()) else if (isDark) Color(0xFF1A1A2E.toInt()) else Color(0xFFF0EEFF.toInt())

        drawRoundRect(color = bgColor,       topLeft = Offset(sx, topY), size = Size(slotSize, slotSize), cornerRadius = cr)
        drawRoundRect(color = borderColor,   topLeft = Offset(sx, topY), size = Size(slotSize, slotSize), cornerRadius = cr, style = Stroke(ratio * 0.18f))

        // Slot index dot
        drawCircle(
            color  = if (isSelected) Color(ColorTheme.Warm.main.primary).copy(alpha = 0.6f) else Color(0x33FFFFFF),
            radius = slotSize * 0.18f,
            center = Offset(sx + slotSize / 2f, topY + slotSize / 2f)
        )

        // Long press ring
        if (isLongPressed && longPressProgress > 0f) {
            val ringR = slotSize * 0.42f
            val cx2   = sx + slotSize / 2f
            val cy2   = topY + slotSize / 2f
            drawArc(
                color      = Color(0xFFFF4444.toInt()),
                startAngle = -90f,
                sweepAngle = longPressProgress * 360f,
                useCenter  = false,
                topLeft    = Offset(cx2 - ringR, cy2 - ringR),
                size       = Size(ringR * 2f, ringR * 2f),
                style      = Stroke(ratio * 0.25f, cap = StrokeCap.Round)
            )
        }

        sx += slotSize + gap
    }

    // "+" button
    if (count < 5) {
        drawRoundRect(color = Color(0x22FFFFFF), topLeft = Offset(sx, topY), size = Size(slotSize, slotSize), cornerRadius = cr)
        drawRoundRect(color = Color(0x66FFFFFF), topLeft = Offset(sx, topY), size = Size(slotSize, slotSize), cornerRadius = cr, style = Stroke(ratio * 0.15f))
        val bLen = slotSize * 0.4f
        val cx2  = sx + slotSize / 2f
        val cy2  = topY + slotSize / 2f
        drawLine(Color(0xAAFFFFFF.toInt()), Offset(cx2 - bLen / 2f, cy2), Offset(cx2 + bLen / 2f, cy2), ratio * 0.22f, StrokeCap.Round)
        drawLine(Color(0xAAFFFFFF.toInt()), Offset(cx2, cy2 - bLen / 2f), Offset(cx2, cy2 + bLen / 2f), ratio * 0.22f, StrokeCap.Round)
    }
}

private fun DrawScope.drawCarouselLabel(label: String, carouselTopY: Float, ratio: Float, isDark: Boolean) {
    val tabW = ratio * 3.5f
    val tabH = ratio * 1.0f
    val tabX = ratio * 0.3f
    val tabY = carouselTopY - tabH - ratio * 0.15f
    drawRoundRect(
        color       = Color(ColorTheme.Warm.main.primary).copy(alpha = 0.25f),
        topLeft     = Offset(tabX, tabY),
        size        = Size(tabW, tabH),
        cornerRadius = CornerRadius(ratio * 0.2f)
    )
}
