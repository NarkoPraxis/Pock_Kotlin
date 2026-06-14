package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gameobjects.Settings
import enums.BallType
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer
import kotlinx.coroutines.delay
import com.runoutzone.pockpock.components.AdLimitPopup
import com.runoutzone.pockpock.components.MeterLockedPopup
import shapes.ColorCarousel
import shapes.ScrollSnapCarousel
import utility.AdUnlock
import utility.CcpPreset
import utility.PaintBucket
import utility.Storage
import utility.edgeSwipeBack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val CCP_GESTURE_IDLE       = 0
private const val CCP_GESTURE_DETECTING  = 1
private const val CCP_GESTURE_HORIZONTAL = 2
private const val CCP_GESTURE_CUSTOM_HUE = 3

private const val CCP_LONG_PRESS_FRAMES = 188

// Carousel IDs
private const val CCP_HIGH_DEFAULT = 0
private const val CCP_HIGH_SHIELD  = 1
private const val CCP_LOW_DEFAULT  = 2
private const val CCP_LOW_SHIELD   = 3

@Composable
fun CustomColorPickerScreen(onBack: () -> Unit, onNavigateToCbc: () -> Unit) {
    val isDark        = LocalDarkMode.current
    val density       = LocalDensity.current
    val textMeasurer  = rememberTextMeasurer()
    val bgColor = if (isDark) PaintBucket.menuBackgroundDark else PaintBucket.menuBackgroundLight

    var frame     by remember { mutableIntStateOf(0) }
    var canvasW   by remember { mutableIntStateOf(0) }
    var canvasH   by remember { mutableIntStateOf(0) }
    var initDone  by remember { mutableStateOf(false) }

    val highDefaultCarousel = remember { ColorCarousel(Storage.highPlayerColorHue).also { it.isUpsideDown = true } }
    val highShieldCarousel  = remember { ColorCarousel(Storage.highShieldColorHue).also { it.isUpsideDown = true } }
    val lowDefaultCarousel  = remember { ColorCarousel(Storage.lowPlayerColorHue) }
    val lowShieldCarousel   = remember { ColorCarousel(Storage.lowShieldColorHue) }

    fun carousel(id: Int): ColorCarousel = when (id) {
        CCP_HIGH_DEFAULT -> highDefaultCarousel
        CCP_HIGH_SHIELD  -> highShieldCarousel
        CCP_LOW_DEFAULT  -> lowDefaultCarousel
        else             -> lowShieldCarousel
    }

    // Color hue state (used for slot drawing and preview)
    var highHue       by remember { mutableFloatStateOf(Storage.highPlayerColorHue) }
    var highShieldHue by remember { mutableFloatStateOf(Storage.highShieldColorHue) }
    var lowHue        by remember { mutableFloatStateOf(Storage.lowPlayerColorHue) }
    var lowShieldHue  by remember { mutableFloatStateOf(Storage.lowShieldColorHue) }

    // Preset slots
    val ccpPresets = remember { arrayOfNulls<CcpPreset>(5) }
    var selectedPresetIdx by remember { mutableIntStateOf(Storage.ccpSelectedSlot) }
    var presetsVersion    by remember { mutableIntStateOf(0) }

    fun loadPresets() {
        for (i in 0..4) ccpPresets[i] = Storage.loadCcpPreset(i)
        presetsVersion++
    }

    // Preview balls
    var previewOrbitAngle by remember { mutableFloatStateOf(0f) }
    val highPreviewRenderer = remember { mutableStateOf<PuckRenderer?>(null) }
    val lowPreviewRenderer  = remember { mutableStateOf<PuckRenderer?>(null) }

    // Which color state each preview ball shows (mirrors CBC's Shield/Normal button per player)
    var hiPreviewShield by remember { mutableStateOf(false) }
    var loPreviewShield by remember { mutableStateOf(false) }

    fun updatePreviewState(id: Int) {
        when (id) {
            CCP_HIGH_DEFAULT -> hiPreviewShield = false
            CCP_HIGH_SHIELD  -> hiPreviewShield = true
            CCP_LOW_DEFAULT  -> loPreviewShield = false
            CCP_LOW_SHIELD   -> loPreviewShield = true
        }
    }

    // Long press
    var longPressIdx      by remember { mutableIntStateOf(-1) }
    var longPressStart    by remember { mutableIntStateOf(-1) }
    var longPressProgress by remember { mutableFloatStateOf(0f) }

    // "Complete the meter" popup (custom color tapped below 100%) and ad-limit popup.
    var meterPopupVisible by remember { mutableStateOf(false) }
    var adLimitPopupVisible by remember { mutableStateOf(false) }

    // Locked color TAPPED (not merely scrolled to): custom → meter popup; ad-color → ad / limit popup.
    val handleLockedColor: (Int) -> Unit = { index ->
        if (index == ColorCarousel.CUSTOM_IDX) {
            meterPopupVisible = true
        } else if (Storage.canWatchAdNow()) {
            AdUnlock.watchAdToUnlock(grant = { Storage.unlockColor(index) }) { /* Storage notifies → recompose */ }
        } else {
            adLimitPopupVisible = true
        }
    }

    // ── Layout helpers ────────────────────────────────────────────────────────
    fun carouselH() = if (Settings.screenRatio > 0f) Settings.screenRatio * 5f else 80f
    fun slotRowCenterY()    = canvasH.toFloat() - carouselH() * 2.5f - Settings.screenRatio * 0.5f
    fun previewCenterY()    = (carouselH() * 2f + slotRowCenterY()) / 2f
    fun carouselCY(id: Int): Float {
        val ch = carouselH()
        val h  = canvasH.toFloat()
        return when (id) {
            CCP_HIGH_DEFAULT -> ch * 0.5f
            CCP_HIGH_SHIELD  -> ch * 1.5f
            CCP_LOW_SHIELD   -> h - ch * 1.5f
            else             -> h - ch * 0.5f  // CCP_LOW_DEFAULT
        }
    }

    fun carouselIdAtY(y: Float): Int {
        val ch = carouselH()
        for (id in 0..3) {
            if (abs(y - carouselCY(id)) < ch / 2f) return id
        }
        return -1
    }

    fun ccpSlotHitTest(x: Float, y: Float): Int {
        val ratio    = Settings.screenRatio
        val slotSize = ratio * 3f
        val gap      = ratio * 0.5f
        val totalW   = 5 * (slotSize + gap) - gap
        var sx       = (canvasW - totalW) / 2f
        val cy       = slotRowCenterY()
        val topY     = cy - slotSize / 2f
        for (i in 0..4) {
            if (x in sx..(sx + slotSize) && y in topY..(topY + slotSize)) return i
            sx += slotSize + gap
        }
        return -1
    }

    // ── Color application ─────────────────────────────────────────────────────
    fun applyColorsFromCarousels() {
        fun applyHue(slot: Int, hue: Float) {
            val pri = utility.SwatchPalette.primary(hue)
            val sec = utility.SwatchPalette.secondary(hue)
            when (slot) {
                0 -> { PaintBucket.highPlayerPrimary = pri; PaintBucket.highPlayerSecondary = sec }
                1 -> { PaintBucket.highShieldPrimary = pri; PaintBucket.highShieldSecondary = sec }
                2 -> { PaintBucket.lowPlayerPrimary  = pri; PaintBucket.lowPlayerSecondary  = sec }
                3 -> { PaintBucket.lowShieldPrimary  = pri; PaintBucket.lowShieldSecondary  = sec }
            }
        }
        applyHue(0, highDefaultCarousel.currentHue)
        applyHue(1, highShieldCarousel.currentHue)
        applyHue(2, lowDefaultCarousel.currentHue)
        applyHue(3, lowShieldCarousel.currentHue)
        highHue       = highDefaultCarousel.currentHue
        highShieldHue = highShieldCarousel.currentHue
        lowHue        = lowDefaultCarousel.currentHue
        lowShieldHue  = lowShieldCarousel.currentHue

        // Refresh renderer themes so skins read the new PaintBucket values
        highPreviewRenderer.value?.theme = ColorTheme.Warm
        lowPreviewRenderer.value?.theme  = ColorTheme.Cold

        // Auto-update selected slot in memory so the pie chart reflects current colors live
        if (selectedPresetIdx in 0..4) {
            ccpPresets[selectedPresetIdx] = CcpPreset(
                highHue       = highDefaultCarousel.currentHue,
                highShieldHue = highShieldCarousel.currentHue,
                lowHue        = lowDefaultCarousel.currentHue,
                lowShieldHue  = lowShieldCarousel.currentHue
            )
            presetsVersion++
        }
    }

    fun saveSelectedSlot() {
        if (selectedPresetIdx !in 0..4) return
        Storage.saveCcpPreset(selectedPresetIdx, CcpPreset(
            highHue       = highDefaultCarousel.currentHue,
            highShieldHue = highShieldCarousel.currentHue,
            lowHue        = lowDefaultCarousel.currentHue,
            lowShieldHue  = lowShieldCarousel.currentHue
        ))
    }

    fun saveCarouselHues() {
        Storage.saveHighPlayerColorHue(highDefaultCarousel.currentHue)
        Storage.saveHighShieldColorHue(highShieldCarousel.currentHue)
        Storage.saveLowPlayerColorHue(lowDefaultCarousel.currentHue)
        Storage.saveLowShieldColorHue(lowShieldCarousel.currentHue)
    }

    // ── Animation loop ────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        while (true) {
            delay(16L)
            frame++
            previewOrbitAngle = (previewOrbitAngle + 1.2f) % 360f

            if (initDone) applyColorsFromCarousels()

            if (longPressIdx >= 0 && longPressStart >= 0) {
                val elapsed = frame - longPressStart
                longPressProgress = (elapsed.toFloat() / CCP_LONG_PRESS_FRAMES).coerceIn(0f, 1f)
                if (elapsed >= CCP_LONG_PRESS_FRAMES) {
                    val del = longPressIdx
                    Storage.deleteCcpPreset(del)
                    if (selectedPresetIdx == del) {
                        Storage.saveCcpSelectedSlot(-1)
                        selectedPresetIdx = -1
                    }
                    loadPresets()
                    longPressIdx = -1; longPressStart = -1; longPressProgress = 0f
                }
            }
        }
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

            highDefaultCarousel.initializeToHue(Storage.highPlayerColorHue)
            highShieldCarousel.initializeToHue(Storage.highShieldColorHue)
            lowDefaultCarousel.initializeToHue(Storage.lowPlayerColorHue)
            lowShieldCarousel.initializeToHue(Storage.lowShieldColorHue)

            loadPresets()
            selectedPresetIdx = Storage.ccpSelectedSlot

            val hiR = BallStyleFactory.buildRenderer(BallType.Classic, ColorTheme.Warm)
            hiR.isHigh = true
            highPreviewRenderer.value = hiR

            val loR = BallStyleFactory.buildRenderer(BallType.Classic, ColorTheme.Cold)
            loR.isHigh = false
            lowPreviewRenderer.value = loR

            applyColorsFromCarousels()
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
                    data class PtrState(
                        var phase: Int,
                        val carouselId: Int,
                        val startX: Float,
                        val startY: Float
                    )
                    val ptrMap = mutableMapOf<Long, PtrState>()

                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (change in event.changes) {
                                val pid = change.id.value
                                val x   = change.position.x
                                val y   = change.position.y

                                when {
                                    // ── New finger down ───────────────────────────────────────
                                    change.pressed && !change.previousPressed -> {
                                        ptrMap[pid] = PtrState(
                                            phase      = CCP_GESTURE_DETECTING,
                                            carouselId = carouselIdAtY(y),
                                            startX     = x,
                                            startY     = y
                                        )
                                        // Long-press only tracks the first simultaneous pointer
                                        if (ptrMap.size == 1) {
                                            val slotHit = ccpSlotHitTest(x, y)
                                            if (slotHit >= 0 && ccpPresets[slotHit] != null) {
                                                longPressIdx = slotHit; longPressStart = frame; longPressProgress = 0f
                                            } else {
                                                longPressIdx = -1; longPressStart = -1; longPressProgress = 0f
                                            }
                                        }
                                        change.consume()
                                    }

                                    // ── Finger moved ──────────────────────────────────────────
                                    change.pressed -> {
                                        val state = ptrMap[pid] ?: continue
                                        val cumX  = abs(x - state.startX)
                                        val cumY  = abs(y - state.startY)

                                        if (state.phase == CCP_GESTURE_DETECTING) {
                                            val slop = viewConfiguration.touchSlop
                                            if (cumX > slop || cumY > slop) {
                                                longPressIdx = -1; longPressStart = -1; longPressProgress = 0f
                                                if (state.carouselId >= 0) {
                                                    val car = carousel(state.carouselId)
                                                    state.phase = if (car.isCustomSliderActive) {
                                                        CCP_GESTURE_CUSTOM_HUE
                                                    } else {
                                                        car.handleScrollTouchEvent(ScrollSnapCarousel.ACTION_DOWN, state.startX, state.startY)
                                                        CCP_GESTURE_HORIZONTAL
                                                    }
                                                } else {
                                                    state.phase = CCP_GESTURE_HORIZONTAL
                                                }
                                            }
                                        }

                                        when (state.phase) {
                                            CCP_GESTURE_HORIZONTAL -> {
                                                if (state.carouselId >= 0)
                                                    carousel(state.carouselId).handleScrollTouchEvent(ScrollSnapCarousel.ACTION_MOVE, x, y)
                                            }
                                            CCP_GESTURE_CUSTOM_HUE -> {
                                                if (state.carouselId >= 0) {
                                                    val car = carousel(state.carouselId)
                                                    val hue = car.getHueFromSliderX(x)
                                                    if (hue >= 0f) { car.setCustomHue(hue); applyColorsFromCarousels() }
                                                }
                                            }
                                        }
                                        change.consume()
                                    }

                                    // ── Finger lifted ─────────────────────────────────────────
                                    else -> {
                                        val state = ptrMap.remove(pid) ?: continue

                                        when (state.phase) {
                                            CCP_GESTURE_HORIZONTAL -> {
                                                if (state.carouselId >= 0) {
                                                    carousel(state.carouselId).handleScrollTouchEvent(ScrollSnapCarousel.ACTION_UP, x, y)
                                                    updatePreviewState(state.carouselId)
                                                }
                                                applyColorsFromCarousels(); saveCarouselHues(); saveSelectedSlot()
                                            }
                                            CCP_GESTURE_CUSTOM_HUE -> {
                                                if (state.carouselId >= 0) {
                                                    carousel(state.carouselId).deactivateCustomSlider()
                                                    updatePreviewState(state.carouselId)
                                                }
                                                applyColorsFromCarousels(); saveCarouselHues(); saveSelectedSlot()
                                            }
                                            CCP_GESTURE_DETECTING -> {
                                                val slotHit = ccpSlotHitTest(x, y)
                                                if (slotHit >= 0 && longPressProgress < 0.05f) {
                                                    val existing = ccpPresets[slotHit]
                                                    if (existing != null) {
                                                        highDefaultCarousel.initializeToHue(existing.highHue)
                                                        highShieldCarousel.initializeToHue(existing.highShieldHue)
                                                        lowDefaultCarousel.initializeToHue(existing.lowHue)
                                                        lowShieldCarousel.initializeToHue(existing.lowShieldHue)
                                                        selectedPresetIdx = slotHit
                                                        Storage.saveCcpSelectedSlot(slotHit)
                                                        applyColorsFromCarousels(); saveCarouselHues()
                                                    } else {
                                                        selectedPresetIdx = slotHit
                                                        Storage.saveCcpSelectedSlot(slotHit)
                                                        applyColorsFromCarousels(); saveCarouselHues(); saveSelectedSlot(); loadPresets()
                                                    }
                                                } else {
                                                    val carId = carouselIdAtY(y)
                                                    if (carId >= 0) {
                                                        val car = carousel(carId)
                                                        val idx = car.snapIndex
                                                        if (!car.isUnlocked(idx)) {
                                                            handleLockedColor(idx)
                                                        } else if (car.isCustomSelected && !car.isCustomSliderActive) {
                                                            car.tryActivateCustomSlider()
                                                            updatePreviewState(carId)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        if (ptrMap.isEmpty()) {
                                            longPressIdx = -1; longPressStart = -1; longPressProgress = 0f
                                        }
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            if (!initDone || Settings.screenRatio == 0f) return@Canvas
            @Suppress("UNUSED_EXPRESSION") presetsVersion

            val ratio  = Settings.screenRatio
            val cw     = size.width
            val ch     = size.height

            val carouselH = carouselH()

            // ── High carousels (upside-down) ─────────────────────────────────
            val highDefaultCY = carouselCY(CCP_HIGH_DEFAULT)
            val highShieldCY  = carouselCY(CCP_HIGH_SHIELD)

            withTransform({
                rotate(180f, pivot = Offset(cw / 2f, highDefaultCY))
            }) {
                with(highDefaultCarousel) { drawCarousel(highDefaultCY, frame, isDark, "Ball", textMeasurer) }
            }

            withTransform({
                rotate(180f, pivot = Offset(cw / 2f, highShieldCY))
            }) {
                with(highShieldCarousel) { drawCarousel(highShieldCY, frame, isDark, "Shield", textMeasurer) }
            }

            // ── Low carousels (right-side up) ─────────────────────────────────
            val lowShieldCY  = carouselCY(CCP_LOW_SHIELD)
            val lowDefaultCY = carouselCY(CCP_LOW_DEFAULT)

            with(lowShieldCarousel)  { drawCarousel(lowShieldCY,  frame, isDark, "Shield", textMeasurer) }
            with(lowDefaultCarousel) { drawCarousel(lowDefaultCY, frame, isDark, "Ball",   textMeasurer) }

            // ── Preset slot row ───────────────────────────────────────────────
            val slotCY = slotRowCenterY()
            drawCcpSlotRow(
                presets           = ccpPresets,
                selectedIdx       = selectedPresetIdx,
                longPressIdx      = longPressIdx,
                longPressProgress = longPressProgress,
                canvasW           = cw,
                centerY           = slotCY,
                ratio             = ratio,
                isDark            = isDark
            )

            // ── Preview balls ─────────────────────────────────────────────────
            val previewCy    = previewCenterY()
            val amplitude    = ratio * 1.5f
            val previewBobY  = previewCy + amplitude * sin(2f * PI.toFloat() * frame / 70f)

            val hiPr = highPreviewRenderer.value
            if (hiPr != null) {
                hiPr.x             = cw * 0.25f
                hiPr.y             = previewBobY
                hiPr.radius        = Settings.ballRadius
                hiPr.strokeWidth   = Settings.strokeWidth
                hiPr.frame         = frame
                hiPr.effectEnabled = true
                hiPr.shielded      = hiPreviewShield
                hiPr.inertLocked   = false
                hiPr.effect.increaseCharge()
                if (hiPr.effect.phase == ChargePhase.Inert) hiPr.effect.reset()
                (hiPr.effect as? PaddleLaunchEffect)?.cbcOrbitAngleDeg = previewOrbitAngle
                with(hiPr) { draw() }
            }

            val loPr = lowPreviewRenderer.value
            if (loPr != null) {
                loPr.x             = cw * 0.75f
                loPr.y             = previewBobY
                loPr.radius        = Settings.ballRadius
                loPr.strokeWidth   = Settings.strokeWidth
                loPr.frame         = frame
                loPr.effectEnabled = true
                loPr.shielded      = loPreviewShield
                loPr.inertLocked   = false
                loPr.effect.increaseCharge()
                if (loPr.effect.phase == ChargePhase.Inert) loPr.effect.reset()
                (loPr.effect as? PaddleLaunchEffect)?.cbcOrbitAngleDeg = previewOrbitAngle
                with(loPr) { draw() }
            }
        }

        // ── STYLE button — pinned to right side, level with the preset slot row ──
        val overlayBtnColors = ButtonDefaults.buttonColors(
            containerColor = if (isDark) PaintBucket.menuButtonDark else PaintBucket.menuButtonLight,
            contentColor   = if (isDark) PaintBucket.white else PaintBucket.menuBackgroundDark
        )
        val styleBtnTopDp = with(density) {
            if (initDone && Settings.screenRatio > 0f) {
                val slotTopPx = slotRowCenterY() - Settings.screenRatio * 1.5f
                (slotTopPx - 62.dp.toPx()).toDp().coerceAtLeast(8.dp)
            } else 0.dp
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = styleBtnTopDp, end = 12.dp)
        ) {
            Button(onClick = onNavigateToCbc, colors = overlayBtnColors) {
                Text(text = "STYLE", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (meterPopupVisible) {
            MeterLockedPopup(currentPercent = Storage.unlockProgress) { meterPopupVisible = false }
        }
        if (adLimitPopupVisible) {
            AdLimitPopup(Storage.minutesUntilNextAd()) { adLimitPopupVisible = false }
        }
    }
}

// ── Drawing helpers ───────────────────────────────────────────────────────────

private fun DrawScope.drawCcpSlotRow(
    presets: Array<utility.CcpPreset?>,
    selectedIdx: Int,
    longPressIdx: Int,
    longPressProgress: Float,
    canvasW: Float,
    centerY: Float,
    ratio: Float,
    isDark: Boolean
) {
    val slotSize = ratio * 3f
    val gap      = ratio * 0.5f
    val totalW   = 5 * (slotSize + gap) - gap
    var sx       = (canvasW - totalW) / 2f
    val topY     = centerY - slotSize / 2f
    val cr       = CornerRadius(ratio * 0.35f)

    for (i in 0..4) {
        val preset     = presets[i]
        val populated  = preset != null
        val isSelected = i == selectedIdx
        val isLong     = i == longPressIdx

        val bgColor     = if (isDark) Color(0xFF1A2A3E.toInt()) else Color(0xFFE8E8F0.toInt())
        val borderColor = if (isSelected) Color(0xFF52B6F2.toInt()) else Color(0xFFF25252.toInt())
        val strokeW     = if (isSelected) ratio * 0.22f else ratio * 0.12f

        drawRoundRect(bgColor,  topLeft = Offset(sx, topY), size = Size(slotSize, slotSize), cornerRadius = cr)
        drawRoundRect(borderColor, topLeft = Offset(sx, topY), size = Size(slotSize, slotSize),
            cornerRadius = cr, style = Stroke(strokeW))

        val slotCx = sx + slotSize / 2f
        val slotCy = topY + slotSize / 2f

        if (populated && preset != null) {
            // Pie chart — 4 quadrants
            val pieR     = slotSize * 0.35f
            val pieRect  = Size(pieR * 2f, pieR * 2f)
            val pieLeft  = Offset(slotCx - pieR, slotCy - pieR)
            // Top-right: highHue (startAngle=-90, sweep=90)
            drawArc(utility.SwatchPalette.secondary(preset.highHue),       -90f, 90f, true, pieLeft, pieRect)
            // Bottom-right: highShieldHue (startAngle=0, sweep=90)
            drawArc(utility.SwatchPalette.secondary(preset.highShieldHue),   0f, 90f, true, pieLeft, pieRect)
            // Bottom-left: lowHue (startAngle=90, sweep=90)
            drawArc(utility.SwatchPalette.secondary(preset.lowHue),         90f, 90f, true, pieLeft, pieRect)
            // Top-left: lowShieldHue (startAngle=180, sweep=90)
            drawArc(utility.SwatchPalette.secondary(preset.lowShieldHue),  180f, 90f, true, pieLeft, pieRect)
        } else {
            // Empty slot: dimmed circle + "+"
            drawCircle(Color(if (isDark) 0x22FFFFFF else 0x22000000.toInt()),
                radius = slotSize * 0.18f, center = Offset(slotCx, slotCy))
            val bLen = slotSize * 0.32f
            val plusColor = Color(if (isDark) 0x55FFFFFF else 0x55000000.toInt())
            drawLine(plusColor, Offset(slotCx - bLen / 2f, slotCy), Offset(slotCx + bLen / 2f, slotCy),
                ratio * 0.2f, StrokeCap.Round)
            drawLine(plusColor, Offset(slotCx, slotCy - bLen / 2f), Offset(slotCx, slotCy + bLen / 2f),
                ratio * 0.2f, StrokeCap.Round)
        }

        // Long-press deletion ring
        if (isLong && longPressProgress > 0f) {
            val ringR = slotSize * 0.42f
            drawArc(
                color      = Color(0xFFFF4444.toInt()),
                startAngle = -90f,
                sweepAngle = longPressProgress * 360f,
                useCenter  = false,
                topLeft    = Offset(slotCx - ringR, slotCy - ringR),
                size       = Size(ringR * 2f, ringR * 2f),
                style      = Stroke(ratio * 0.25f, cap = StrokeCap.Round)
            )
        }

        sx += slotSize + gap
    }
}
