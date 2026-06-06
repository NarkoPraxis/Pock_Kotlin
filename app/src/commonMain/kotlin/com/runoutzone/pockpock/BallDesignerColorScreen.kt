package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runoutzone.pockpock.components.AdLimitPopup
import com.runoutzone.pockpock.components.MeterLockedPopup
import com.runoutzone.pockpock.components.VerticalOptionCarousel
import com.runoutzone.pockpock.menu.EdgePill
import com.runoutzone.pockpock.menu.PillSide
import com.runoutzone.pockpock.menu.poppinsFamily
import enums.BallType
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.CustomBallConfig
import gameobjects.puckstyle.PuckRenderer
import org.jetbrains.compose.resources.painterResource
import pock_kotlin.app.generated.resources.*
import shapes.ColorCarousel
import utility.AdUnlock
import utility.CcpPreset
import utility.PaintBucket
import utility.Storage
import utility.edgeSwipeBack
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val ROW_NORMAL = 0
private const val ROW_SHIELD = 1

/**
 * "The Ball Designer" — color screen (replaces the deprecated CustomColorPickerScreen).
 * Same composition language as [BallDesignerScreen]; one player edited at a time.
 */
@Composable
fun BallDesignerColorScreen(onBack: () -> Unit, onNavigateToStyle: () -> Unit) {
    val isDark = LocalDarkMode.current
    val density = LocalDensity.current
    val poppins = poppinsFamily()
    val lockPainter = painterResource(Res.drawable.ic_menu_lock)

    val bgColor = if (isDark) PaintBucket.menuBackgroundDark else PaintBucket.menuBackgroundLight
    val fgColor = if (isDark) PaintBucket.white else Color(0xFF222222)
    val wrapperBg = if (isDark) Color(0xFF1E1E2A) else Color(ColorTheme.Cold.inert.primary)
    val controlBg = if (isDark) Color(0xFF2A3A4E) else PaintBucket.white
    val accentBlue = PaintBucket.menuAccentBlue

    var rootW by remember { mutableIntStateOf(0) }
    var rootH by remember { mutableIntStateOf(0) }
    var initDone by remember { mutableStateOf(false) }

    var highHue by remember { mutableFloatStateOf(0f) }
    var highShieldHue by remember { mutableFloatStateOf(0f) }
    var lowHue by remember { mutableFloatStateOf(0f) }
    var lowShieldHue by remember { mutableFloatStateOf(0f) }

    var activePlayerHigh by remember { mutableStateOf(false) }
    var activeRow by remember { mutableIntStateOf(ROW_NORMAL) }
    var customSliderActive by remember { mutableStateOf(false) }

    var meterPopupVisible by remember { mutableStateOf(false) }
    var adLimitPopupVisible by remember { mutableStateOf(false) }

    val presets = remember { arrayOfNulls<CcpPreset>(5) }
    var selectedPreset by remember { mutableIntStateOf(-1) }
    var presetsVersion by remember { mutableIntStateOf(0) }
    fun loadPresets() { for (i in 0..4) presets[i] = Storage.loadCcpPreset(i); presetsVersion++ }

    fun currentTargetHue(): Float = when {
        activePlayerHigh && activeRow == ROW_SHIELD -> highShieldHue
        activePlayerHigh -> highHue
        activeRow == ROW_SHIELD -> lowShieldHue
        else -> lowHue
    }

    fun applyHueToPaint(high: Boolean, shield: Boolean, hue: Float) {
        val pri = Color.hsv(hue, 0.359f, 0.961f)
        val sec = Color.hsv(hue, 0.661f, 0.961f)
        when {
            high && !shield -> { PaintBucket.highPlayerPrimary = pri; PaintBucket.highPlayerSecondary = sec }
            high && shield -> { PaintBucket.highShieldPrimary = pri; PaintBucket.highShieldSecondary = sec }
            !high && !shield -> { PaintBucket.lowPlayerPrimary = pri; PaintBucket.lowPlayerSecondary = sec }
            else -> { PaintBucket.lowShieldPrimary = pri; PaintBucket.lowShieldSecondary = sec }
        }
    }

    fun applyAllHues() {
        applyHueToPaint(true, false, highHue)
        applyHueToPaint(true, true, highShieldHue)
        applyHueToPaint(false, false, lowHue)
        applyHueToPaint(false, true, lowShieldHue)
    }

    fun persistHues() {
        Storage.saveHighPlayerColorHue(highHue); Storage.saveHighShieldColorHue(highShieldHue)
        Storage.saveLowPlayerColorHue(lowHue); Storage.saveLowShieldColorHue(lowShieldHue)
    }

    fun updateSelectedPresetInMemory() {
        if (selectedPreset in 0..4) { presets[selectedPreset] = CcpPreset(highHue, highShieldHue, lowHue, lowShieldHue); presetsVersion++ }
    }

    fun saveSelectedPreset() {
        if (selectedPreset in 0..4) Storage.saveCcpPreset(selectedPreset, CcpPreset(highHue, highShieldHue, lowHue, lowShieldHue))
    }

    fun setTargetHue(hue: Float) {
        when {
            activePlayerHigh && activeRow == ROW_SHIELD -> highShieldHue = hue
            activePlayerHigh -> highHue = hue
            activeRow == ROW_SHIELD -> lowShieldHue = hue
            else -> lowHue = hue
        }
        applyHueToPaint(activePlayerHigh, activeRow == ROW_SHIELD, hue)
        persistHues(); updateSelectedPresetInMemory(); saveSelectedPreset()
    }

    var previewRenderer by remember { mutableStateOf<PuckRenderer?>(null) }
    fun previewConfig(): CustomBallConfig {
        val idx = if (activePlayerHigh) Settings.highCustomBallIndex else Settings.lowCustomBallIndex
        return (idx?.let { Storage.loadCustomBall(it) })
            ?: (0 until Storage.SLOT_COUNT).firstNotNullOfOrNull { Storage.loadCustomBall(it) }
            ?: CustomBallConfig(BallType.Classic, BallType.Classic, BallType.Classic, 0, 1, 2)
    }
    fun rebuildPreview() {
        val theme = if (activePlayerHigh) ColorTheme.Warm else ColorTheme.Cold
        val r = BallStyleFactory.buildCustomRenderer(previewConfig(), theme)
        r.isHigh = activePlayerHigh; r.staticUiMode = true; r.effect.frozen = true
        previewRenderer = r
    }

    fun selectRow(row: Int) { activeRow = row; customSliderActive = false }
    fun togglePlayer() { activePlayerHigh = !activePlayerHigh; customSliderActive = false; rebuildPreview() }

    fun handleLockedColor(index: Int) {
        if (index == ColorCarousel.CUSTOM_IDX) meterPopupVisible = true
        else if (Storage.canWatchAdNow()) AdUnlock.watchAdToUnlock(grant = { Storage.unlockColor(index) }) { }
        else adLimitPopupVisible = true
    }

    LaunchedEffect(rootW, rootH) {
        if (!initDone && rootW > 0 && rootH > 0) {
            val ratio = max(1f, min(rootW.toFloat(), rootH.toFloat()) / 18f)
            if (Settings.screenRatio == 0f) {
                Settings.screenRatio = ratio
                Settings.strokeWidth = ratio / 4f
                Settings.screenWidth = rootW.toFloat()
                Settings.screenHeight = rootH.toFloat()
                Settings.middleX = rootW / 2f
                Settings.middleY = rootH / 2f
                PaintBucket.initialize(ratio)
                PaintBucket.applyPlayerHues()
            }
            if (Settings.ballRadius == 0f) Settings.ballRadius = Settings.screenRatio
            Settings.unlockProgress = Storage.unlockProgress
            highHue = Storage.highPlayerColorHue; highShieldHue = Storage.highShieldColorHue
            lowHue = Storage.lowPlayerColorHue; lowShieldHue = Storage.lowShieldColorHue
            loadPresets(); selectedPreset = Storage.ccpSelectedSlot
            applyAllHues(); rebuildPreview()
            initDone = true
        }
    }

    val presetSlotHues = ColorCarousel.PRESETS

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .onSizeChanged { rootW = it.width; rootH = it.height }
            .edgeSwipeBack(onBack)
    ) {
        if (initDone) {
            val slotDp = with(density) { (Settings.ballRadius * 3.4f).toDp() }

            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (Storage.unlockProgress < 100) {
                    UnlockProgressBar(
                        progress = Storage.unlockProgress,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(32.dp)
                    )
                    Box(Modifier.height(8.dp))
                }

                // Preview — selected custom ball, active player theme, normal/shield color + shadow.
                Box(modifier = Modifier.fillMaxWidth().weight(0.9f).clipToBounds()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val pr = previewRenderer ?: return@Canvas
                        val theme = if (activePlayerHigh) ColorTheme.Warm else ColorTheme.Cold
                        val grp = if (activeRow == ROW_SHIELD) theme.shield else theme.main
                        val cx = size.width / 2f
                        val r = Settings.ballRadius
                        val cy = size.height / 2f - r * 0.3f
                        bdDrawShadow(cx, cy, r)
                        pr.theme = theme
                        pr.x = cx; pr.y = cy; pr.radius = r
                        pr.strokeWidth = Settings.strokeWidth
                        pr.effectEnabled = true
                        pr.shielded = activeRow == ROW_SHIELD
                        pr.inertLocked = false
                        pr.fillColor = grp.primary; pr.strokeColor = grp.secondary; pr.baseFillColor = grp.primary
                        with(pr) { draw() }
                    }
                }
                Box(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(2.3f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(wrapperBg)
                        .padding(10.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Left: Normal / Shield / Player (not draggable).
                            Column(modifier = Modifier.fillMaxHeight().weight(0.58f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ColorTargetBox(
                                    label = "Normal", hue = if (activePlayerHigh) highHue else lowHue,
                                    active = activeRow == ROW_NORMAL, controlBg = controlBg, accent = accentBlue, fg = fgColor, poppins = poppins,
                                    modifier = Modifier.fillMaxWidth().weight(1f), onTap = { selectRow(ROW_NORMAL) }
                                )
                                ColorTargetBox(
                                    label = "Shield", hue = if (activePlayerHigh) highShieldHue else lowShieldHue,
                                    active = activeRow == ROW_SHIELD, controlBg = controlBg, accent = accentBlue, fg = fgColor, poppins = poppins,
                                    modifier = Modifier.fillMaxWidth().weight(1f), onTap = { selectRow(ROW_SHIELD) }
                                )
                                PlayerToggleBox(
                                    high = activePlayerHigh, controlBg = controlBg, fg = fgColor, poppins = poppins,
                                    modifier = Modifier.fillMaxWidth().weight(1f), onTap = { togglePlayer() }
                                )
                            }

                            // Right: vertical color carousel (+ optional custom hue slider).
                            val selIdx = run {
                                val hue = currentTargetHue()
                                var found = ColorCarousel.CUSTOM_IDX
                                for (i in 0 until ColorCarousel.CUSTOM_IDX) {
                                    if (abs((presetSlotHues[i]?.hue ?: -999f) - hue) < 1f) { found = i; break }
                                }
                                found
                            }
                            Column(modifier = Modifier.fillMaxHeight().weight(0.42f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                        .clip(RoundedCornerShape(16.dp)).background(controlBg)
                                ) {
                                    VerticalOptionCarousel(
                                        itemCount = 10, selectedIndex = selIdx, modifier = Modifier.fillMaxSize(),
                                        onTap = { i ->
                                            if (i == ColorCarousel.CUSTOM_IDX) {
                                                if (Storage.isColorUnlocked(i)) customSliderActive = true else handleLockedColor(i)
                                            } else if (Storage.isColorUnlocked(i)) {
                                                customSliderActive = false; presetSlotHues[i]?.hue?.let { setTargetHue(it) }
                                            } else handleLockedColor(i)
                                        },
                                        onSnap = { i ->
                                            if (i == ColorCarousel.CUSTOM_IDX) {
                                                if (Storage.isColorUnlocked(i)) customSliderActive = true
                                            } else if (Storage.isColorUnlocked(i)) {
                                                customSliderActive = false; presetSlotHues[i]?.hue?.let { setTargetHue(it) }
                                            }
                                        }
                                    ) { index, cx, cy, r, isCenter ->
                                        val unlocked = Storage.isColorUnlocked(index)
                                        bdDrawShadow(cx, cy, r)
                                        val strokeW = if (isCenter) r * 0.30f else r * 0.16f
                                        if (index == ColorCarousel.CUSTOM_IDX) {
                                            val hue = currentTargetHue()
                                            drawCircle(Color.hsv(hue, 0.359f, 0.961f), radius = r, center = Offset(cx, cy))
                                            drawCircle(Color.hsv(hue, 0.661f, 0.961f), radius = r, center = Offset(cx, cy), style = Stroke(strokeW))
                                            val arcR = r * 0.6f
                                            drawArc(Color.White, 300f, 300f, false,
                                                topLeft = Offset(cx - arcR, cy - arcR), size = Size(arcR * 2f, arcR * 2f),
                                                style = Stroke(r * 0.18f, cap = StrokeCap.Round))
                                        } else {
                                            val hue = presetSlotHues[index]?.hue ?: return@VerticalOptionCarousel
                                            val a = if (unlocked) 1f else 0.35f
                                            drawCircle(Color.hsv(hue, 0.359f, 0.961f).copy(alpha = a), radius = r, center = Offset(cx, cy))
                                            drawCircle(Color.hsv(hue, 0.661f, 0.961f).copy(alpha = a), radius = r, center = Offset(cx, cy), style = Stroke(strokeW))
                                        }
                                        if (!unlocked) {
                                            drawCircle(Color.Black.copy(alpha = 0.42f), radius = r * 1.05f, center = Offset(cx, cy))
                                            val ls = r * 0.95f
                                            translate(cx - ls / 2f, cy - (ls * 1.16f) / 2f) {
                                                with(lockPainter) { draw(Size(ls, ls * 1.16f), colorFilter = ColorFilter.tint(Color.White)) }
                                            }
                                        }
                                    }
                                }
                                if (customSliderActive && Storage.isColorUnlocked(ColorCarousel.CUSTOM_IDX)) {
                                    HueSliderRow(
                                        hue = currentTargetHue(), onHue = { setTargetHue(it) },
                                        modifier = Modifier.fillMaxWidth().height(36.dp)
                                    )
                                }
                            }
                        }

                        // Save-slot strip — 5 four-square palette presets, inside the wrapper.
                        @Suppress("UNUSED_EXPRESSION") presetsVersion
                        Box(
                            modifier = Modifier.fillMaxWidth().height(slotDp + 16.dp)
                                .clip(RoundedCornerShape(16.dp)).background(controlBg)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())
                                    .padding(start = 8.dp, end = 84.dp, top = 8.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (i in 0..4) {
                                    CcpPresetSlot(
                                        preset = presets[i], selected = i == selectedPreset, isDark = isDark,
                                        accent = accentBlue, fg = fgColor, sizeDp = slotDp,
                                        onTap = {
                                            val existing = presets[i]
                                            if (existing != null) {
                                                highHue = existing.highHue; highShieldHue = existing.highShieldHue
                                                lowHue = existing.lowHue; lowShieldHue = existing.lowShieldHue
                                                selectedPreset = i; Storage.saveCcpSelectedSlot(i)
                                                applyAllHues(); persistHues(); rebuildPreview()
                                            } else {
                                                selectedPreset = i; Storage.saveCcpSelectedSlot(i)
                                                saveSelectedPreset(); loadPresets()
                                            }
                                        },
                                        onLongPress = {
                                            Storage.deleteCcpPreset(i)
                                            if (selectedPreset == i) { selectedPreset = -1; Storage.saveCcpSelectedSlot(-1) }
                                            loadPresets()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 14.dp),
                horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EdgePill(side = PillSide.End, color = accentBlue, modifier = Modifier.height(42.dp).clickable(onClick = onNavigateToStyle)) {
                    Image(painterResource(Res.drawable.ic_menu_customize), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(PaintBucket.white))
                }
                EdgePill(side = PillSide.End, color = PaintBucket.menuAccentRed, modifier = Modifier.height(42.dp).clickable(onClick = onBack)) {
                    Image(painterResource(Res.drawable.ic_menu_check), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(PaintBucket.white))
                }
            }
        }

        if (meterPopupVisible) MeterLockedPopup(Storage.unlockProgress) { meterPopupVisible = false }
        if (adLimitPopupVisible) AdLimitPopup(Storage.minutesUntilNextAd()) { adLimitPopupVisible = false }
    }
}

@Composable
private fun ColorTargetBox(
    label: String,
    hue: Float,
    active: Boolean,
    controlBg: Color,
    accent: Color,
    fg: Color,
    poppins: androidx.compose.ui.text.font.FontFamily,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier.clip(shape).background(controlBg)
            .then(if (active) Modifier.border(4.dp, accent, shape) else Modifier)
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = Settings.ballRadius
            val cx = size.width / 2f
            val cy = size.height / 2f + r * 0.2f
            bdDrawShadow(cx, cy, r)
            drawCircle(Color.hsv(hue, 0.359f, 0.961f), radius = r, center = Offset(cx, cy))
            drawCircle(Color.hsv(hue, 0.661f, 0.961f), radius = r, center = Offset(cx, cy), style = Stroke(r * 0.16f))
        }
        Text(label, color = fg, fontFamily = poppins, fontSize = 13.sp, fontWeight = FontWeight.Light, fontStyle = FontStyle.Italic,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 4.dp))
    }
}

@Composable
private fun PlayerToggleBox(
    high: Boolean,
    controlBg: Color,
    fg: Color,
    poppins: androidx.compose.ui.text.font.FontFamily,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier.clip(shape).background(controlBg)
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
        contentAlignment = Alignment.Center
    ) {
        Text(text = if (high) "High\nPlayer" else "Low\nPlayer", color = fg, fontFamily = poppins,
            fontSize = 14.sp, fontWeight = FontWeight.Light, fontStyle = FontStyle.Italic)
    }
}

@Composable
private fun HueSliderRow(hue: Float, onHue: (Float) -> Unit, modifier: Modifier = Modifier) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    Canvas(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures(onTap = { offset -> onHue((offset.x / widthPx).coerceIn(0f, 1f) * 360f) }) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> change.consume(); onHue((change.position.x / widthPx).coerceIn(0f, 1f) * 360f) }
            }
    ) {
        val barH = size.height * 0.5f
        val rainbow = (0..36).map { Color.hsv(it * 10f, 0.9f, 1f) }
        drawRoundRect(
            brush = Brush.linearGradient(rainbow, start = Offset(0f, 0f), end = Offset(size.width, 0f)),
            topLeft = Offset(0f, size.height / 2f - barH / 2f), size = Size(size.width, barH),
            cornerRadius = CornerRadius(barH / 2f)
        )
        val thumbR = size.height * 0.42f
        val thumbX = ((hue / 360f) * size.width).coerceIn(thumbR, size.width - thumbR)
        drawCircle(Color.hsv(hue, 0.661f, 0.961f), radius = thumbR, center = Offset(thumbX, size.height / 2f))
        drawCircle(Color.White, radius = thumbR, center = Offset(thumbX, size.height / 2f), style = Stroke(size.height * 0.08f))
    }
}

@Composable
private fun CcpPresetSlot(
    preset: CcpPreset?,
    selected: Boolean,
    isDark: Boolean,
    accent: Color,
    fg: Color,
    sizeDp: androidx.compose.ui.unit.Dp,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (isDark) Color(0xFF1A2A3E) else Color(0xFFEDEDF4)
    Box(
        modifier = Modifier.size(sizeDp).clip(shape).background(bg)
            .then(if (selected) Modifier.border(4.dp, accent, shape) else Modifier)
            .pointerInput(preset) { detectTapGestures(onTap = { onTap() }, onLongPress = { if (preset != null) onLongPress() }) },
        contentAlignment = Alignment.Center
    ) {
        if (preset != null) {
            // Four rounded-corner squares (2x2), matching the SVG's color slot swatch.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pad = size.minDimension * 0.16f
                val gap = size.minDimension * 0.08f
                val cell = (size.minDimension - pad * 2f - gap) / 2f
                val r = CornerRadius(cell * 0.28f)
                fun sq(col: Int, row: Int, hue: Float) {
                    val x = pad + col * (cell + gap)
                    val y = pad + row * (cell + gap)
                    drawRoundRect(Color.hsv(hue, 0.661f, 0.961f), topLeft = Offset(x, y), size = Size(cell, cell), cornerRadius = r)
                }
                sq(0, 0, preset.highHue)       // TL high
                sq(1, 0, preset.highShieldHue) // TR high shield
                sq(0, 1, preset.lowHue)        // BL low
                sq(1, 1, preset.lowShieldHue)  // BR low shield
            }
        } else {
            Text("+", color = fg.copy(alpha = 0.5f), fontSize = 22.sp)
        }
    }
}
