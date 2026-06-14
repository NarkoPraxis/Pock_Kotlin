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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
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
import kotlin.math.roundToInt

private const val ROW_NORMAL = 0
private const val ROW_SHIELD = 1

// Degrees of hue advanced per animation frame for the custom "any color" carousel button's
// rainbow strobe (it has no single hue, so its face cycles like the Rainbow skin).
private const val CCP_STROBE_SPEED = 4f

// Localized display name for a colour-carousel index (parallels ColorCarousel.PRESETS). The status
// label shows the browsed colour's name when unlocked, or "Watch Ad To Own" when locked — mirrors
// BallDesigner (see bdStyleName). Index 9 is the custom "any color" slot.
@Composable
private fun ccpColorName(index: Int): String = when (index) {
    0 -> stringResource(Res.string.color_name_red)
    1 -> stringResource(Res.string.color_name_orange)
    2 -> stringResource(Res.string.color_name_yellow)
    3 -> stringResource(Res.string.color_name_green)
    4 -> stringResource(Res.string.color_name_sky_blue)
    5 -> stringResource(Res.string.color_name_deep_purple)
    6 -> stringResource(Res.string.color_name_purple)
    7 -> stringResource(Res.string.color_name_magenta)
    8 -> stringResource(Res.string.color_name_pink)
    else -> stringResource(Res.string.color_name_custom)
}

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
    val adLockPainter = painterResource(Res.drawable.ic_menu_adlock)
    // A VectorPainter holds one size at a time, so the preview's lock glyph (drawn at a different
    // size than the carousel's) needs its own instance or it renders clipped. See BallDesignerScreen.
    val previewAdLockPainter = painterResource(Res.drawable.ic_menu_adlock)
    // Plain meter-lock glyph for the preview when the chosen colour is the Rainbow (its own instance
    // for the same VectorPainter size-sharing reason as previewAdLockPainter).
    val previewLockPainter = painterResource(Res.drawable.ic_menu_lock)

    val bgColor = if (isDark) PaintBucket.menuBackgroundDark else PaintBucket.menuBackgroundLight
    val fgColor = if (isDark) PaintBucket.white else Color(0xFF222222)
    val wrapperBg = if (isDark) BD_WRAPPER_DARK else BD_WRAPPER_LIGHT
    val controlBg = if (isDark) Color(0xFF2A3A4E) else PaintBucket.white
    val accentBlue = PaintBucket.menuAccentBlue

    var rootW by remember { mutableIntStateOf(0) }
    var rootH by remember { mutableIntStateOf(0) }
    var initDone by remember { mutableStateOf(false) }

    // Demo-only animation clock for the composition preview (mirrors BallDesignerScreen). `frame`
    // forces the Canvas to redraw each tick; `previewStep` advances one motion-step per draw.
    var frame by remember { mutableIntStateOf(0) }
    val previewStep = remember { floatArrayOf(0f) }

    var highHue by remember { mutableFloatStateOf(0f) }
    var highShieldHue by remember { mutableFloatStateOf(0f) }
    var lowHue by remember { mutableFloatStateOf(0f) }
    var lowShieldHue by remember { mutableFloatStateOf(0f) }

    var activePlayerHigh by remember { mutableStateOf(false) }
    var activeRow by remember { mutableIntStateOf(ROW_NORMAL) }
    var customSliderActive by remember { mutableStateOf(false) }

    var meterPopupVisible by remember { mutableStateOf(false) }
    var adLimitPopupVisible by remember { mutableStateOf(false) }

    // Geometry for centering the action pills between the progress bar and preview (see
    // BallDesignerScreen). All values are in root coords.
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var progressBottomPx by remember { mutableFloatStateOf(0f) }
    var previewTopPx by remember { mutableFloatStateOf(0f) }
    var previewBottomPx by remember { mutableFloatStateOf(0f) }
    var pillColHeightPx by remember { mutableIntStateOf(0) }

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

    val presetSlotHues = ColorCarousel.PRESETS

    // Which carousel index a hue corresponds to (CUSTOM_IDX if it isn't one of the presets), and
    // whether that color is still locked. Used both to centre the carousel and to decide when the
    // lock glyph shows on the left control + preview.
    fun colorIndexForHue(hue: Float): Int {
        for (i in 0 until ColorCarousel.CUSTOM_IDX) {
            if (abs((presetSlotHues[i]?.hue ?: -999f) - hue) < 1f) return i
        }
        return ColorCarousel.CUSTOM_IDX
    }
    fun hueLocked(hue: Float): Boolean = !Storage.isColorUnlocked(colorIndexForHue(hue))

    // The Rainbow (index CUSTOM_IDX) unlocks at 100% meter rather than by watching an ad, so its
    // "won't be saved" markers use the plain lock glyph (like the Rainbow carousel button) instead
    // of the ad-lock the preset colours use.
    fun hueUsesMeterLock(hue: Float): Boolean = colorIndexForHue(hue) == ColorCarousel.CUSTOM_IDX

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

    // [persist] = false is the fully-isolated "show but don't save" path for a locked colour: it only
    // updates this screen's local hue state (which drives the preview + left control entirely on its
    // own, via Color.hsv / bdThemeFromHues). It never touches PaintBucket or Storage, so the live
    // gameplay palette is untouched until the colour is actually unlocked and persisted.
    fun setTargetHue(hue: Float, persist: Boolean) {
        when {
            activePlayerHigh && activeRow == ROW_SHIELD -> highShieldHue = hue
            activePlayerHigh -> highHue = hue
            activeRow == ROW_SHIELD -> lowShieldHue = hue
            else -> lowHue = hue
        }
        if (persist) {
            applyHueToPaint(activePlayerHigh, activeRow == ROW_SHIELD, hue)
            persistHues(); updateSelectedPresetInMemory(); saveSelectedPreset()
        }
    }

    var previewRenderer by remember { mutableStateOf<PuckRenderer?>(null) }
    fun previewConfig(): CustomBallConfig {
        val idx = if (activePlayerHigh) Settings.highCustomBallIndex else Settings.lowCustomBallIndex
        return (idx?.let { Storage.loadCustomBall(it) })
            ?: (0 until Storage.SLOT_COUNT).firstNotNullOfOrNull { Storage.loadCustomBall(it) }
            ?: CustomBallConfig(BallType.Classic, BallType.Classic, BallType.Classic, 0, 1, 2)
    }
    fun rebuildPreview() {
        previewRenderer?.tail?.clear()
        // Isolated theme built from the local hues (never from PaintBucket) so a locked-colour preview
        // can't leak into gameplay. Live motion (not a static pose): see bdDrawAnimatedPreview.
        val nHue = if (activePlayerHigh) highHue else lowHue
        val sHue = if (activePlayerHigh) highShieldHue else lowShieldHue
        val r = BallStyleFactory.buildCustomRenderer(previewConfig(), bdThemeFromHues(activePlayerHigh, nHue, sHue))
        r.isHigh = activePlayerHigh; r.staticUiMode = false; r.effect.frozen = false
        r.suppressSounds = true   // cosmetic preview — never play the charge/sweet-spot SFX
        previewRenderer = r
    }

    fun selectRow(row: Int) { activeRow = row; customSliderActive = false }

    // The saved hue for a given player/row — the value persisted in the selected preset, or in
    // Storage when no preset is selected. Locked previews (persist = false) never reach either, so
    // this is always an already-unlocked color: the safe value to fall back to.
    fun savedHueFor(high: Boolean, shield: Boolean): Float {
        val p = if (selectedPreset in 0..4) presets[selectedPreset] else null
        return if (p != null) when {
            high && !shield -> p.highHue
            high && shield -> p.highShieldHue
            !high && !shield -> p.lowHue
            else -> p.lowShieldHue
        } else when {
            high && !shield -> Storage.highPlayerColorHue
            high && shield -> Storage.highShieldColorHue
            !high && !shield -> Storage.lowPlayerColorHue
            else -> Storage.lowShieldColorHue
        }
    }

    fun togglePlayer() {
        // The player we're leaving is about to be hidden. If either of its colors is a still-locked
        // preview (shown but never saved), discard the preview and restore the saved value —
        // otherwise the save slot would keep reading "locked" over a color the user can no longer
        // see or unlock (its carousel/ad button isn't visible for the hidden player).
        val leaving = activePlayerHigh
        if (leaving) {
            if (hueLocked(highHue)) highHue = savedHueFor(true, false)
            if (hueLocked(highShieldHue)) highShieldHue = savedHueFor(true, true)
        } else {
            if (hueLocked(lowHue)) lowHue = savedHueFor(false, false)
            if (hueLocked(lowShieldHue)) lowShieldHue = savedHueFor(false, true)
        }
        activePlayerHigh = !activePlayerHigh
        customSliderActive = false
        rebuildPreview()
    }

    fun handleLockedColor(index: Int) {
        if (index == ColorCarousel.CUSTOM_IDX) meterPopupVisible = true
        else if (Storage.canWatchAdNow()) AdUnlock.watchAdToUnlock(grant = { Storage.unlockColor(index) }) { }
        else adLimitPopupVisible = true
    }

    // Drives the preview ball's demo motion (~60fps). Purely cosmetic — no physics involved.
    LaunchedEffect(Unit) { while (true) { delay(16L); frame++ } }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .onSizeChanged { rootW = it.width; rootH = it.height }
            .onGloballyPositioned { rootCoords = it }
            .edgeSwipeBack(onBack)
    ) {
        if (initDone) {
            val slotDp = with(density) { (Settings.ballRadius * 3.4f).toDp() }

            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (Storage.unlockProgress < 100) {
                    UnlockProgressBar(
                        progress = Storage.unlockProgress,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(32.dp)
                            .onGloballyPositioned { pc ->
                                rootCoords?.let { progressBottomPx = it.localPositionOf(pc, Offset.Zero).y + pc.size.height }
                            }
                    )
                    Box(Modifier.height(8.dp))
                }

                // Preview — selected custom ball, active player theme, normal/shield color. When the
                // chosen color is still locked it mirrors BallDesigner: the design shows but a lock
                // glyph marks it as un-saved.
                val previewColorLocked = hueLocked(currentTargetHue())
                // Rainbow (meter-unlocked) → plain lock glyph; preset colours (ad-unlocked) → ad-lock.
                val previewLockIsMeter = previewColorLocked && hueUsesMeterLock(currentTargetHue())
                // Any of the four colors still locked → the current selection can't be saved into a
                // slot. The selected slot reflects this (lock + desaturated colors).
                val saveDisabled = hueLocked(highHue) || hueLocked(highShieldHue) ||
                    hueLocked(lowHue) || hueLocked(lowShieldHue)
                // If a blocking locked colour is the Rainbow (meter-unlocked), the slot's "can't save"
                // stamp uses the plain lock instead of the ad-lock.
                val saveBlockedByMeter =
                    (hueLocked(highHue) && hueUsesMeterLock(highHue)) ||
                    (hueLocked(highShieldHue) && hueUsesMeterLock(highShieldHue)) ||
                    (hueLocked(lowHue) && hueUsesMeterLock(lowHue)) ||
                    (hueLocked(lowShieldHue) && hueUsesMeterLock(lowShieldHue))
                Box(
                    modifier = Modifier.fillMaxWidth().weight(0.9f).clipToBounds()
                        .onGloballyPositioned { pc ->
                            rootCoords?.let {
                                val y = it.localPositionOf(pc, Offset.Zero).y
                                previewTopPx = y; previewBottomPx = y + pc.size.height
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val pr = previewRenderer ?: return@Canvas
                        // Isolated theme from the local hues — previewing (even a locked colour) never
                        // touches the live PaintBucket palette.
                        val theme = bdThemeFromHues(
                            activePlayerHigh,
                            if (activePlayerHigh) highHue else lowHue,
                            if (activePlayerHigh) highShieldHue else lowShieldHue
                        )
                        previewStep[0] += 1f
                        bdDrawAnimatedPreview(
                            pr, theme, shielded = activeRow == ROW_SHIELD, step = previewStep[0],
                            frame = frame, locked = previewColorLocked,
                            lockPainter = if (previewLockIsMeter) previewLockPainter else previewAdLockPainter,
                            lockWidthOverHeight = if (previewLockIsMeter) 1f / BD_LOCK_ASPECT else 1f / BD_ADLOCK_ASPECT
                        )
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
                                val normalHue = if (activePlayerHigh) highHue else lowHue
                                val shieldHue = if (activePlayerHigh) highShieldHue else lowShieldHue
                                ColorTargetBox(
                                    label = "Normal", hue = normalHue, locked = hueLocked(normalHue),
                                    meterLock = hueUsesMeterLock(normalHue), high = activePlayerHigh,
                                    active = activeRow == ROW_NORMAL, controlBg = controlBg, accent = accentBlue, fg = fgColor, poppins = poppins,
                                    modifier = Modifier.fillMaxWidth().weight(1f), onTap = { selectRow(ROW_NORMAL) }
                                )
                                ColorTargetBox(
                                    label = "Shield", hue = shieldHue, locked = hueLocked(shieldHue),
                                    meterLock = hueUsesMeterLock(shieldHue), high = activePlayerHigh,
                                    active = activeRow == ROW_SHIELD, controlBg = controlBg, accent = accentBlue, fg = fgColor, poppins = poppins,
                                    modifier = Modifier.fillMaxWidth().weight(1f), onTap = { selectRow(ROW_SHIELD) }
                                )
                                PlayerToggleBox(
                                    high = activePlayerHigh,
                                    theme = bdThemeFromHues(activePlayerHigh, normalHue, shieldHue),
                                    poppins = poppins,
                                    modifier = Modifier.fillMaxWidth().weight(1f), onTap = { togglePlayer() }
                                )
                            }

                            // Right: vertical color carousel (+ optional custom hue slider).
                            val selIdx = colorIndexForHue(currentTargetHue())
                            // The colour under the carousel's centre drives the status label. It resets to
                            // the equipped selection whenever that selection changes.
                            var browsedColorIndex by remember(selIdx) { mutableIntStateOf(selIdx) }
                            var carouselWidthPx by remember { mutableIntStateOf(0) }
                            Column(modifier = Modifier.fillMaxHeight().weight(0.42f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                        .clip(RoundedCornerShape(16.dp)).background(controlBg)
                                        .onSizeChanged { carouselWidthPx = it.width }
                                ) {
                                    VerticalOptionCarousel(
                                        itemCount = 10, selectedIndex = selIdx, modifier = Modifier.fillMaxSize(),
                                        onCenterChanged = { browsedColorIndex = it },
                                        onTap = { i ->
                                            if (i == ColorCarousel.CUSTOM_IDX) {
                                                if (Storage.isColorUnlocked(i)) customSliderActive = true else handleLockedColor(i)
                                            } else if (Storage.isColorUnlocked(i)) {
                                                customSliderActive = false; presetSlotHues[i]?.hue?.let { setTargetHue(it, persist = true) }
                                            } else {
                                                // Locked: preview it (no save) AND fire the unlock ad — mirrors BallDesigner.
                                                customSliderActive = false; presetSlotHues[i]?.hue?.let { setTargetHue(it, persist = false) }
                                                handleLockedColor(i)
                                            }
                                        },
                                        onSnap = { i ->
                                            if (i == ColorCarousel.CUSTOM_IDX) {
                                                if (Storage.isColorUnlocked(i)) customSliderActive = true
                                            } else if (Storage.isColorUnlocked(i)) {
                                                customSliderActive = false; presetSlotHues[i]?.hue?.let { setTargetHue(it, persist = true) }
                                            } else {
                                                // Locked: scrolling onto it just previews the design (no save, no ad).
                                                customSliderActive = false; presetSlotHues[i]?.hue?.let { setTargetHue(it, persist = false) }
                                            }
                                        }
                                    ) { index, cx, cy, r, isCenter, isPressed, cellW, cellH ->
                                        // Every color is the same raised square button (no swatch ball). Reading
                                        // `frame` here keeps the carousel redrawing so the custom button can strobe.
                                        val strobeHue = (frame * CCP_STROBE_SPEED) % 360f
                                        val unlocked = Storage.isColorUnlocked(index)
                                        val isCustom = index == ColorCarousel.CUSTOM_IDX
                                        // Custom has no fixed hue → its face cycles the rainbow (like the Rainbow
                                        // skin); presets show the exact colour they unlock.
                                        val hue = if (isCustom) strobeHue else (presetSlotHues[index]?.hue ?: 0f)
                                        val faceColor = Color.hsv(hue, 0.359f, 0.961f)
                                        val faceStroke = Color.hsv(hue, 0.661f, 0.961f)
                                        if (unlocked) {
                                            // Unlocked → flat (always-pressed) rectangle, no lock glyph.
                                            bdDrawLockedOption(
                                                cx, cy, cellW, cellH, faceColor = faceColor, pressed = true,
                                                icon = null, iconAspectHW = 0f, faceStroke = faceStroke
                                            ) { }
                                        } else {
                                            // Locked → raised ad-button. Custom unlocks only at 100% meter (plain
                                            // lock); presets are individually ad-unlockable (ad-lock).
                                            bdDrawLockedOption(
                                                cx, cy, cellW, cellH, faceColor = faceColor, pressed = isPressed,
                                                icon = if (isCustom) lockPainter else adLockPainter,
                                                iconAspectHW = if (isCustom) BD_LOCK_ASPECT else BD_ADLOCK_ASPECT,
                                                faceStroke = faceStroke, centerIcon = true
                                            ) { }
                                        }
                                    }

                                    // Status label for the browsed colour — "Watch Ad To Own" when locked,
                                    // else its name. Same chip/typography as BallDesignerScreen: left-aligned,
                                    // word-wrapped to 3 lines, opaque rounded chip so it stays readable as the
                                    // ad-buttons scroll behind it. Colours unified across dark/light.
                                    val carouselLabel =
                                        if (Storage.isColorUnlocked(browsedColorIndex))
                                            ccpColorName(browsedColorIndex.coerceIn(0, 9))
                                        else stringResource(Res.string.style_ad_to_own)
                                    val labelShape = RoundedCornerShape(8.dp)
                                    Text(
                                        text = carouselLabel,
                                        color = fgColor,
                                        fontFamily = poppins,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Light,
                                        fontStyle = FontStyle.Italic,
                                        maxLines = 3,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(6.dp)
                                            .then(
                                                if (carouselWidthPx > 0)
                                                    Modifier.widthIn(max = with(density) { carouselWidthPx.toDp() } - 12.dp)
                                                else Modifier
                                            )
                                            .clip(labelShape)
                                            .background(controlBg)
                                            .border(2.dp, wrapperBg, labelShape)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                if (customSliderActive && Storage.isColorUnlocked(ColorCarousel.CUSTOM_IDX)) {
                                    HueSliderRow(
                                        hue = currentTargetHue(), onHue = { setTargetHue(it, persist = true) },
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
                                        index = i, preset = presets[i], selected = i == selectedPreset, isDark = isDark,
                                        unlocked = Storage.isCcpSlotUnlocked(i),
                                        disabled = i == selectedPreset && saveDisabled,
                                        meterLock = saveBlockedByMeter,
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

            // Action pills — vertically centered between the progress bar and the preview's lock
            // glyph region (matches BallDesignerScreen).
            val lockReservePx = with(density) { (12.dp + 36.dp).toPx() }
            val topAnchorPx = if (Storage.unlockProgress < 100) progressBottomPx else previewTopPx
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .onSizeChanged { pillColHeightPx = it.height }
                    .offset {
                        val lockTop = previewBottomPx - lockReservePx
                        val center = (topAnchorPx + lockTop) / 2f
                        IntOffset(0, (center - pillColHeightPx / 2f).roundToInt().coerceAtLeast(0))
                    },
                horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EdgePill(side = PillSide.End, color = accentBlue, modifier = Modifier.height(52.dp).clickable(onClick = onNavigateToStyle)) {
                    Image(painterResource(Res.drawable.ic_menu_customize), null, Modifier.size(28.dp), colorFilter = ColorFilter.tint(PaintBucket.white))
                }
                EdgePill(side = PillSide.End, color = PaintBucket.menuAccentRed, modifier = Modifier.height(52.dp).clickable(onClick = onBack)) {
                    Image(painterResource(Res.drawable.ic_menu_check), null, Modifier.size(28.dp), colorFilter = ColorFilter.tint(PaintBucket.white))
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
    locked: Boolean,
    meterLock: Boolean,
    active: Boolean,
    high: Boolean,
    controlBg: Color,
    accent: Color,
    fg: Color,
    poppins: androidx.compose.ui.text.font.FontFamily,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val isDark = LocalDarkMode.current
    val shape = RoundedCornerShape(14.dp)
    // Own painter instances (see note at previewAdLockPainter) so they aren't resized by other draws.
    // adLockPainter = ad-unlockable preset colours; meterLockPainter = the Rainbow (100% meter).
    val lockPainter = painterResource(Res.drawable.ic_menu_adlock)
    val meterLockPainter = painterResource(Res.drawable.ic_menu_lock)
    // The swatch is a real Classic skin posed in static UI mode (closer to 1:1 with gameplay than a
    // bare circle). Built once; its theme/hue is set per-draw below. Theme drives the body colours.
    val renderer = remember {
        BallStyleFactory.buildSkinOnlyRenderer(BallType.Classic, ColorTheme.Cold)
            .also { it.staticUiMode = true; it.effect.frozen = true }
    }
    Box(
        modifier = modifier.clip(shape).background(controlBg)
            .then(if (active) Modifier.border(4.dp, accent, shape) else Modifier)
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = Settings.ballRadius
            val cx = size.width / 2f
            val cy = size.height / 2f + r * 0.2f
            val theme = bdThemeFromHues(high, hue, hue)
            bdDrawShadow(cx, cy, r, isDark)
            renderer.theme = theme
            renderer.x = cx; renderer.y = cy; renderer.radius = r
            renderer.strokeWidth = Settings.strokeWidth
            renderer.shielded = false; renderer.inertLocked = false
            renderer.fillColor = theme.main.primary
            renderer.strokeColor = theme.main.secondary
            renderer.baseFillColor = theme.main.primary
            with(renderer) { draw() }
            // Chosen color is locked → right-centred lock glyph (plain meter lock for the Rainbow,
            // ad-lock for ad-unlockable preset colours), matching the BallDesigner.
            if (locked) {
                val ratio = if (meterLock) 1f / BD_LOCK_ASPECT else 1f / BD_ADLOCK_ASPECT
                val h = 24.dp.toPx(); val w = h * ratio; val pad = 10.dp.toPx()
                bdDrawAdLockGlyph(
                    if (meterLock) meterLockPainter else lockPainter, PaintBucket.menuAccentRed,
                    size.width - pad - w / 2f, size.height / 2f, h, ratio
                )
            }
        }
        Text(label, color = fg, fontFamily = poppins, fontSize = 13.sp, fontWeight = FontWeight.Light, fontStyle = FontStyle.Italic,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 4.dp))
    }
}

// The fraction of the button's height taken by the shield "goal indicator" band (top edge for the
// Top player, bottom edge for Bottom), and the press-depth fraction (matches bdDrawLockedOption).
private const val TOGGLE_BAND_FRACTION = 0.2f
private const val TOGGLE_DEPTH_K = 0.09f

@Composable
private fun PlayerToggleBox(
    high: Boolean,
    theme: ColorTheme,
    poppins: androidx.compose.ui.text.font.FontFamily,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    var boxHeightPx by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier
            .onSizeChanged { boxHeightPx = it.height.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onTap() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) { drawPlayerToggleButton(high, theme, pressed) }
        // The face sits `depth` above the cell at rest and slides down onto its shadow lip when
        // pressed; the label rides with it so it stays centred on the face in both states.
        val depthPx = boxHeightPx / (1f + TOGGLE_DEPTH_K) * TOGGLE_DEPTH_K
        val faceOffPx = if (pressed) depthPx else 0f
        val labelOffset = (faceOffPx - depthPx / 2f).roundToInt()
        // high = Top, low = Bottom (user-facing wording).
        Text(text = if (high) stringResource(Res.string.ccp_top_colors) else stringResource(Res.string.ccp_bottom_colors),
            color = PaintBucket.white, fontFamily = poppins, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic,
            modifier = Modifier.offset { IntOffset(0, labelOffset) })
    }
}

// Draws the Top/Bottom toggle as a raised carousel-style button: a theme.main.primary fill ringed by
// a theme.main.secondary outline, sitting on a darker shadow lip. The top (Top player) or bottom
// (Bottom player) [TOGGLE_BAND_FRACTION] of the button is overlaid with the shield colours
// (theme.shield.primary fill + theme.shield.secondary outline) to mark which goal that player's
// colors edit. When [pressed] the face slides straight down onto the lip (matches bdDrawLockedOption).
private fun DrawScope.drawPlayerToggleButton(high: Boolean, theme: ColorTheme, pressed: Boolean) {
    val w = size.width
    val h = size.height
    val faceH = h / (1f + TOGGLE_DEPTH_K)
    val depth = faceH * TOGGLE_DEPTH_K
    val corner = faceH * 0.18f
    val strokeW = faceH * 0.09f
    val bandH = faceH * TOGGLE_BAND_FRACTION
    val faceOff = if (pressed) depth else 0f

    val mainP = Color(theme.main.primary)
    val mainS = Color(theme.main.secondary)
    val shieldP = Color(theme.shield.primary)
    val shieldS = Color(theme.shield.secondary)

    // Band sits on the goal edge: top for Top player, bottom for Bottom player.
    fun bandTop(faceTop: Float) = if (high) faceTop else faceTop + faceH - bandH
    fun facePath(faceTop: Float): Path = Path().apply {
        addRoundRect(RoundRect(Rect(Offset(0f, faceTop), Size(w, faceH)), CornerRadius(corner)))
    }
    // Inset stroke so its outer edge lands on the face edge (no fill slivers past the corners).
    val sInset = strokeW / 2f
    fun DrawScope.outline(faceTop: Float, color: Color) = drawRoundRect(
        color, Offset(sInset, faceTop + sInset), Size(w - strokeW, faceH - strokeW),
        CornerRadius((corner - sInset).coerceAtLeast(0f)), style = Stroke(strokeW)
    )

    // Shadow lip — a darker duplicate of the (two-tone) face, fixed `depth` below the resting face.
    drawRoundRect(bdShadowOver(mainP), Offset(0f, depth), Size(w, faceH), CornerRadius(corner))
    clipPath(facePath(depth)) {
        drawRect(bdShadowOver(shieldP), Offset(0f, bandTop(depth)), Size(w, bandH))
    }

    // Face — main fill, shield band overlaid (clipped to the rounded face so the band keeps its
    // rounded goal-edge corners), then the continuous outline that switches colour across the band.
    drawRoundRect(mainP, Offset(0f, faceOff), Size(w, faceH), CornerRadius(corner))
    clipPath(facePath(faceOff)) {
        drawRect(shieldP, Offset(0f, bandTop(faceOff)), Size(w, bandH))
    }
    outline(faceOff, mainS)
    val bt = bandTop(faceOff)
    clipRect(0f, bt, w, bt + bandH) { outline(faceOff, shieldS) }
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
    index: Int,
    preset: CcpPreset?,
    selected: Boolean,
    isDark: Boolean,
    unlocked: Boolean,
    disabled: Boolean,
    meterLock: Boolean,
    accent: Color,
    fg: Color,
    sizeDp: androidx.compose.ui.unit.Dp,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (isDark) Color(0xFF1A2A3E) else Color(0xFFEDEDF4)
    // Own painter instances (see note at previewAdLockPainter) so they aren't resized by other draws.
    val lockPainter = painterResource(Res.drawable.ic_menu_adlock)
    val meterLockPainter = painterResource(Res.drawable.ic_menu_lock)
    Box(
        modifier = Modifier.size(sizeDp).clip(shape).background(bg)
            .then(if (unlocked && selected) Modifier.border(4.dp, accent, shape) else Modifier)
            .pointerInput(unlocked, preset) {
                // Locked slots can't be selected — they just show the % needed to unlock them
                // (mirrors the Ball Designer's DesignerSlotCell).
                if (unlocked) detectTapGestures(onTap = { onTap() }, onLongPress = { if (preset != null) onLongPress() })
            },
        contentAlignment = Alignment.Center
    ) {
        if (!unlocked) {
            Text("${Storage.ccpSlotRequiredPercent(index)}%", color = fg.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        } else if (preset != null) {
            // Four horizontal bands in the order an arena draws top→bottom, so the slot reads as a
            // miniature of real gameplay: High shield (1/6) · High color (1/3) · Low color (1/3) ·
            // Low shield (1/6). The whole formed square keeps rounded corners (clip below). When
            // [disabled] (a locked colour is selected so saving is off) the bands desaturate and a
            // lock is stamped over the slot.
            val sat = if (disabled) 0.12f else 0.661f
            val value = if (disabled) 0.74f else 0.961f
            Box(modifier = Modifier.fillMaxSize().padding(6.dp).clip(RoundedCornerShape(8.dp))) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val sH = size.height / 6f   // shield band
                    val mH = size.height / 3f   // main band
                    fun band(top: Float, h: Float, hue: Float) =
                        drawRect(Color.hsv(hue, sat, value), topLeft = Offset(0f, top), size = Size(w, h))
                    band(0f, sH, preset.highShieldHue)        // High shield (top)
                    band(sH, mH, preset.highHue)               // High color
                    band(sH + mH, mH, preset.lowHue)           // Low color
                    band(sH + mH * 2f, sH, preset.lowShieldHue) // Low shield (bottom)
                }
            }
            if (disabled) Canvas(modifier = Modifier.fillMaxSize()) {
                bdDrawAdLockGlyph(
                    if (meterLock) meterLockPainter else lockPainter, PaintBucket.menuAccentRed,
                    size.width / 2f, size.height / 2f, size.minDimension * 0.4f,
                    if (meterLock) 1f / BD_LOCK_ASPECT else 1f / BD_ADLOCK_ASPECT
                )
            }
        } else {
            Text("+", color = fg.copy(alpha = 0.5f), fontSize = 22.sp)
        }
    }
}
