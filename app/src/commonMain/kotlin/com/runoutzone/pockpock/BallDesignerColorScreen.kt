package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.runoutzone.pockpock.components.AdLimitPopup
import com.runoutzone.pockpock.components.MeterLockedPopup
import com.runoutzone.pockpock.components.VerticalOptionCarousel
import com.runoutzone.pockpock.menu.EdgePill
import com.runoutzone.pockpock.menu.PillSide
import com.runoutzone.pockpock.menu.poppinsFamily
import enums.BallType
import enums.DesignerPane
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
import utility.UiStrobeClock
import utility.edgeSwipeBack
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val ROW_NORMAL = 0
private const val ROW_SHIELD = 1

// The game's "branding" default colours — red top, blue bottom, purple shields — the same preset
// seeded into slot 0 on first load (see Storage.ensureDefaultColorPreset). A newly filled save slot
// starts from these, and a long-pressed slot resets back to them.
private val CCP_BRANDING_DEFAULT = utility.CcpPreset(highHue = 0f, highShieldHue = 264f, lowHue = 202.5f, lowShieldHue = 264f)

// Localized display name for a colour-carousel index (parallels ColorCarousel.PRESETS). The status
// label shows the browsed colour's name when unlocked, or "Watch Ad To Own" when locked — mirrors
// BallDesigner (see bdStyleName). Index 13 is the custom "any color" slot.
@Composable
private fun ccpColorName(index: Int): String = when (index) {
    0 -> stringResource(Res.string.color_name_red)
    1 -> stringResource(Res.string.color_name_brown)
    2 -> stringResource(Res.string.color_name_orange)
    3 -> stringResource(Res.string.color_name_yellow)
    4 -> stringResource(Res.string.color_name_lime)
    5 -> stringResource(Res.string.color_name_green)
    6 -> stringResource(Res.string.color_name_forest_green)
    7 -> stringResource(Res.string.color_name_teal)
    8 -> stringResource(Res.string.color_name_sky_blue)
    9 -> stringResource(Res.string.color_name_deep_purple)
    10 -> stringResource(Res.string.color_name_purple)
    11 -> stringResource(Res.string.color_name_magenta)
    12 -> stringResource(Res.string.color_name_pink)
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

    // Rainbow colour overrides — local edit state mirroring the four hues above (see RainbowOverride).
    // The Rainbow carousel option toggles these per target instead of choosing a concrete colour.
    var highRainbow by remember { mutableStateOf(false) }
    var highShieldRainbow by remember { mutableStateOf(false) }
    var lowRainbow by remember { mutableStateOf(false) }
    var lowShieldRainbow by remember { mutableStateOf(false) }

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

    fun currentTargetRainbow(): Boolean = when {
        activePlayerHigh && activeRow == ROW_SHIELD -> highShieldRainbow
        activePlayerHigh -> highRainbow
        activeRow == ROW_SHIELD -> lowShieldRainbow
        else -> lowRainbow
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

    // The carousel index a given target currently sits on (CUSTOM_IDX when it's a rainbow override).
    // Used to hide conflicting colours so a shield is never the same colour as a player's ball.
    fun selectedColorIndexFor(high: Boolean, shield: Boolean): Int {
        val rainbow = when {
            shield && high -> highShieldRainbow
            shield -> lowShieldRainbow
            high -> highRainbow
            else -> lowRainbow
        }
        if (rainbow) return ColorCarousel.CUSTOM_IDX
        val hue = when {
            shield && high -> highShieldHue
            shield -> lowShieldHue
            high -> highHue
            else -> lowHue
        }
        return colorIndexForHue(hue)
    }

    fun applyHueToPaint(high: Boolean, shield: Boolean, hue: Float) {
        val pri = utility.SwatchPalette.primary(hue)
        val sec = utility.SwatchPalette.secondary(hue)
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

    // The live rainbow flags ride alongside the live hues (and the gameplay Settings flags, so a
    // game started from here strobes immediately without a reload).
    fun persistRainbow() {
        Storage.saveHighPlayerRainbow(highRainbow); Storage.saveHighPlayerRainbowShield(highShieldRainbow)
        Storage.saveLowPlayerRainbow(lowRainbow); Storage.saveLowPlayerRainbowShield(lowShieldRainbow)
        Settings.highPlayerRainbow = highRainbow; Settings.highPlayerRainbowShield = highShieldRainbow
        Settings.lowPlayerRainbow = lowRainbow; Settings.lowPlayerRainbowShield = lowShieldRainbow
    }

    fun currentCcpPreset() = CcpPreset(
        highHue, highShieldHue, lowHue, lowShieldHue,
        highRainbow, highShieldRainbow, lowRainbow, lowShieldRainbow
    )

    fun updateSelectedPresetInMemory() {
        if (selectedPreset in 0..4) { presets[selectedPreset] = currentCcpPreset(); presetsVersion++ }
    }

    fun saveSelectedPreset() {
        if (selectedPreset in 0..4) Storage.saveCcpPreset(selectedPreset, currentCcpPreset())
    }

    // [persist] = false is the fully-isolated "show but don't save" path for a locked colour: it only
    // updates this screen's local hue state (which drives the preview + left control entirely on its
    // own, via Color.hsv / bdThemeFromHues). It never touches PaintBucket or Storage, so the live
    // gameplay palette is untouched until the colour is actually unlocked and persisted.
    fun setTargetHue(hue: Float, persist: Boolean) {
        val shield = activeRow == ROW_SHIELD
        // The shield colour is shared by both players (so a goal isn't "owned" by either), but it's
        // still stored as two individual values — write both to keep them in lock-step.
        when {
            shield -> { highShieldHue = hue; lowShieldHue = hue }
            activePlayerHigh -> highHue = hue
            else -> lowHue = hue
        }
        if (persist) {
            // Choosing a concrete colour turns off the rainbow override for this target (the hue
            // takes over). Locked previews (persist = false) can't reach here with rainbow on.
            when {
                shield -> { highShieldRainbow = false; lowShieldRainbow = false }
                activePlayerHigh -> highRainbow = false
                else -> lowRainbow = false
            }
            if (shield) { applyHueToPaint(true, true, hue); applyHueToPaint(false, true, hue) }
            else applyHueToPaint(activePlayerHigh, false, hue)
            persistHues(); persistRainbow(); updateSelectedPresetInMemory(); saveSelectedPreset()
        }
    }

    // Select the rainbow override for the current target (player + Normal/Shield), like choosing any
    // other colour — it turns the override ON (picking a concrete colour turns it back off; see
    // setTargetHue). The stored hue underneath is preserved. Only reachable at 100% (the carousel
    // gates the Rainbow option below it). No-op if already on, so re-selecting is harmless.
    fun selectTargetRainbow() {
        val shield = activeRow == ROW_SHIELD
        if (currentTargetRainbow()) return
        // Shield rainbow is shared across both players (mirrors setTargetHue): set both.
        when {
            shield -> { highShieldRainbow = true; lowShieldRainbow = true }
            activePlayerHigh -> highRainbow = true
            else -> lowRainbow = true
        }
        persistRainbow(); updateSelectedPresetInMemory(); saveSelectedPreset()
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
        r.rainbowMain = if (activePlayerHigh) highRainbow else lowRainbow
        r.rainbowShield = if (activePlayerHigh) highShieldRainbow else lowShieldRainbow
        previewRenderer = r
    }

    fun selectRow(row: Int) { activeRow = row; customSliderActive = false }

    // Select a player's Normal colour (Top or Bottom) as the edit target. Unlike the old toggle,
    // both players' Normal boxes are always shown, so switching focus never hides a locked preview —
    // there's nothing to restore. Rebuild the preview only when the shown player actually changes.
    fun selectPlayer(high: Boolean) {
        activeRow = ROW_NORMAL
        customSliderActive = false
        if (activePlayerHigh != high) {
            activePlayerHigh = high
            rebuildPreview()
        }
    }

    fun handleLockedColor(index: Int) {
        if (index == ColorCarousel.CUSTOM_IDX) meterPopupVisible = true
        else if (Storage.canWatchAdNow()) AdUnlock.watchAdToUnlock(grant = { Storage.unlockColor(index) }) { }
        else adLimitPopupVisible = true
    }

    // Drives the preview ball's demo motion (~60fps). Purely cosmetic — no physics involved.
    // Also advances UiStrobeClock so the static Normal/Shield swatch boxes (staticUiMode) keep
    // cycling when their rainbow override is on.
    LaunchedEffect(Unit) { while (true) { delay(16L); frame++; UiStrobeClock.advance() } }

    // Remember that the Color pane was the last one open, so entering the designer from the main menu
    // reopens here (the Style screen records DesignerPane.Style the same way).
    LaunchedEffect(Unit) { Storage.ballDesignerPane = DesignerPane.Color }

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
            highRainbow = Storage.highPlayerRainbow; highShieldRainbow = Storage.highPlayerRainbowShield
            lowRainbow = Storage.lowPlayerRainbow; lowShieldRainbow = Storage.lowPlayerRainbowShield
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
                        // Refresh the rainbow overrides live so a toggle takes effect without a
                        // rebuild (mirrors how the theme is recomputed from local hues each frame).
                        pr.rainbowMain = if (activePlayerHigh) highRainbow else lowRainbow
                        pr.rainbowShield = if (activePlayerHigh) highShieldRainbow else lowShieldRainbow
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
                            // Left: Top / Shield / Bottom (not draggable). All three targets are
                            // always shown; tapping one selects it. The shared shield sits between the
                            // two players — there's no per-player shield colour, so a goal never reads
                            // as "owned" and its colour always matches the shield for both.
                            Column(modifier = Modifier.fillMaxHeight().weight(0.58f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val shieldHue = if (activePlayerHigh) highShieldHue else lowShieldHue
                                val shieldRainbow = if (activePlayerHigh) highShieldRainbow else lowShieldRainbow
                                ColorTargetBox(
                                    label = "Top", hue = highHue, locked = hueLocked(highHue),
                                    meterLock = hueUsesMeterLock(highHue), high = true,
                                    rainbow = highRainbow,
                                    active = activeRow == ROW_NORMAL && activePlayerHigh, controlBg = controlBg, accent = accentBlue, fg = fgColor, poppins = poppins,
                                    modifier = Modifier.fillMaxWidth().weight(1f), onTap = { selectPlayer(true) }
                                )
                                ColorTargetBox(
                                    label = "Shield", hue = shieldHue, locked = hueLocked(shieldHue),
                                    meterLock = hueUsesMeterLock(shieldHue), high = activePlayerHigh,
                                    rainbow = shieldRainbow,
                                    active = activeRow == ROW_SHIELD, controlBg = controlBg, accent = accentBlue, fg = fgColor, poppins = poppins,
                                    modifier = Modifier.fillMaxWidth().weight(1f), onTap = { selectRow(ROW_SHIELD) }
                                )
                                ColorTargetBox(
                                    label = "Bottom", hue = lowHue, locked = hueLocked(lowHue),
                                    meterLock = hueUsesMeterLock(lowHue), high = false,
                                    rainbow = lowRainbow,
                                    active = activeRow == ROW_NORMAL && !activePlayerHigh, controlBg = controlBg, accent = accentBlue, fg = fgColor, poppins = poppins,
                                    modifier = Modifier.fillMaxWidth().weight(1f), onTap = { selectPlayer(false) }
                                )
                            }

                            // Right: vertical color carousel (+ optional custom hue slider). When the
                            // current target is a rainbow override the carousel centres on the Rainbow
                            // option (like any other selected colour); otherwise on the stored hue.
                            val selIdx = if (currentTargetRainbow()) ColorCarousel.CUSTOM_IDX
                                         else colorIndexForHue(currentTargetHue())
                            // Hide colours that would make a shield indistinguishable from a player's
                            // ball: editing the shield hides whatever Top and Bottom currently use;
                            // editing a player hides whatever the shield uses. (Top and Bottom may still
                            // match each other.) The active target's own colour is always kept so the
                            // carousel can centre on it even in legacy same-colour data. Positions in the
                            // carousel map to these colour indices via [visibleIndices].
                            val excludedColors = if (activeRow == ROW_SHIELD)
                                setOf(selectedColorIndexFor(true, false), selectedColorIndexFor(false, false))
                            else setOf(selectedColorIndexFor(activePlayerHigh, true))
                            val visibleIndices = (0 until 14).filter { it == selIdx || it !in excludedColors }
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
                                        itemCount = visibleIndices.size,
                                        selectedIndex = visibleIndices.indexOf(selIdx).coerceAtLeast(0),
                                        modifier = Modifier.fillMaxSize(),
                                        onCenterChanged = { pos -> browsedColorIndex = visibleIndices.getOrElse(pos) { selIdx } },
                                        onTap = { pos -> visibleIndices.getOrNull(pos)?.let { i ->
                                            if (i == ColorCarousel.CUSTOM_IDX) {
                                                // Rainbow behaves like any other colour: selecting it turns the
                                                // override on (a concrete colour turns it back off). Gated at 100%.
                                                if (Storage.isColorUnlocked(i)) selectTargetRainbow() else handleLockedColor(i)
                                            } else if (Storage.isColorUnlocked(i)) {
                                                customSliderActive = false; presetSlotHues[i]?.hue?.let { setTargetHue(it, persist = true) }
                                            } else {
                                                // Locked: preview it (no save) AND fire the unlock ad — mirrors BallDesigner.
                                                customSliderActive = false; presetSlotHues[i]?.hue?.let { setTargetHue(it, persist = false) }
                                                handleLockedColor(i)
                                            }
                                        } },
                                        onSnap = { pos -> visibleIndices.getOrNull(pos)?.let { i ->
                                            if (i == ColorCarousel.CUSTOM_IDX) {
                                                // Auto-select like every other colour: snapping onto Rainbow turns
                                                // the override on. Below 100% it's locked — just browse (tap shows
                                                // the meter popup).
                                                customSliderActive = false
                                                if (Storage.isColorUnlocked(i)) selectTargetRainbow()
                                            } else if (Storage.isColorUnlocked(i)) {
                                                customSliderActive = false; presetSlotHues[i]?.hue?.let { setTargetHue(it, persist = true) }
                                            } else {
                                                // Locked: scrolling onto it just previews the design (no save, no ad).
                                                customSliderActive = false; presetSlotHues[i]?.hue?.let { setTargetHue(it, persist = false) }
                                            }
                                        } }
                                    ) { pos, cx, cy, r, isCenter, isPressed, cellW, cellH ->
                                        // Carousel position → colour index (conflicting colours filtered out).
                                        val index = visibleIndices.getOrElse(pos) { return@VerticalOptionCarousel }
                                        // Every color is the same raised square button (no swatch ball). Reading
                                        // `frame` here keeps the carousel redrawing so the custom button can strobe.
                                        val unlocked = Storage.isColorUnlocked(index)
                                        val isCustom = index == ColorCarousel.CUSTOM_IDX
                                        // Custom has no fixed hue → its face cycles the SAME continuous rainbow as the
                                        // override itself (via RainbowOverride, so the brown/forest-green exception
                                        // colours never appear); presets show the exact colour they unlock.
                                        val faceColor: Color
                                        val faceStroke: Color
                                        if (isCustom) {
                                            val strobeHue = gameobjects.puckstyle.RainbowOverride.hue(isHigh = false, strobe = frame)
                                            faceColor = gameobjects.puckstyle.RainbowOverride.primaryColor(strobeHue)
                                            faceStroke = gameobjects.puckstyle.RainbowOverride.secondaryColor(strobeHue)
                                        } else {
                                            val hue = presetSlotHues[index]?.hue ?: 0f
                                            faceColor = utility.SwatchPalette.primary(hue)
                                            faceStroke = utility.SwatchPalette.secondary(hue)
                                        }
                                        if (unlocked) {
                                            // Unlocked → flat (always-pressed) rectangle, no lock glyph. The Rainbow
                                            // option reads as selected (flat) when the current target's override is on,
                                            // raised (tappable) when off — same as any selectable colour.
                                            val pressedLook = if (isCustom) currentTargetRainbow() else true
                                            bdDrawLockedOption(
                                                cx, cy, cellW, cellH, faceColor = faceColor, pressed = pressedLook,
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
                                            ccpColorName(browsedColorIndex.coerceIn(0, 13))
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
                                                highRainbow = existing.highRainbow; highShieldRainbow = existing.highShieldRainbow
                                                lowRainbow = existing.lowRainbow; lowShieldRainbow = existing.lowShieldRainbow
                                                selectedPreset = i; Storage.saveCcpSelectedSlot(i)
                                                applyAllHues(); persistHues(); persistRainbow(); rebuildPreview()
                                            } else {
                                                // Filling an empty slot starts from the branding defaults
                                                // (red/blue/purple), not the previously-selected slot's colors.
                                                val d = CCP_BRANDING_DEFAULT
                                                highHue = d.highHue; highShieldHue = d.highShieldHue
                                                lowHue = d.lowHue; lowShieldHue = d.lowShieldHue
                                                highRainbow = d.highRainbow; highShieldRainbow = d.highShieldRainbow
                                                lowRainbow = d.lowRainbow; lowShieldRainbow = d.lowShieldRainbow
                                                selectedPreset = i; Storage.saveCcpSelectedSlot(i)
                                                applyAllHues(); persistHues(); persistRainbow()
                                                saveSelectedPreset(); loadPresets(); rebuildPreview()
                                            }
                                        },
                                        onLongPress = {
                                            // Holding a slot resets it to the branding defaults
                                            // (red/blue/purple) instead of emptying it.
                                            Storage.saveCcpPreset(i, CCP_BRANDING_DEFAULT)
                                            if (selectedPreset == i) {
                                                val d = CCP_BRANDING_DEFAULT
                                                highHue = d.highHue; highShieldHue = d.highShieldHue
                                                lowHue = d.lowHue; lowShieldHue = d.lowShieldHue
                                                highRainbow = d.highRainbow; highShieldRainbow = d.highShieldRainbow
                                                lowRainbow = d.lowRainbow; lowShieldRainbow = d.lowShieldRainbow
                                                applyAllHues(); persistHues(); persistRainbow(); rebuildPreview()
                                            }
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
                EdgePill(side = PillSide.End, color = accentBlue, modifier = Modifier.height(52.dp), onClick = onNavigateToStyle) {
                    Image(painterResource(Res.drawable.ic_menu_customize), null, Modifier.size(28.dp), colorFilter = ColorFilter.tint(PaintBucket.white))
                }
                EdgePill(side = PillSide.End, color = PaintBucket.menuAccentRed, modifier = Modifier.height(52.dp), onClick = onBack) {
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
    rainbow: Boolean,
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
            // The box draws the "main" state, so the normal-rainbow flag is what strobes it.
            renderer.rainbowMain = rainbow
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
                    // Rainbow colours don't fit a single swatch, so a rainbow-override band shows a
                    // full spectral gradient instead — the slot reads as "this colour strobes".
                    val rainbowStops = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map {
                        Color.hsv(it, utility.SwatchPalette.SECONDARY_SAT, utility.SwatchPalette.SWATCH_VALUE)
                    }
                    // Disabled (un-saveable) slots desaturate every band; otherwise each shows its
                    // true swatch colour (the secondary tone, incl. the Brown/Forest Green overrides),
                    // or a rainbow gradient when that colour is a rainbow override.
                    fun band(top: Float, h: Float, hue: Float, rainbow: Boolean) {
                        if (rainbow && !disabled) {
                            drawRect(
                                brush = Brush.horizontalGradient(rainbowStops, startX = 0f, endX = w),
                                topLeft = Offset(0f, top), size = Size(w, h)
                            )
                        } else {
                            drawRect(
                                if (disabled) Color.hsv(hue, sat, value) else utility.SwatchPalette.secondary(hue),
                                topLeft = Offset(0f, top), size = Size(w, h)
                            )
                        }
                    }
                    band(0f, sH, preset.highShieldHue, preset.highShieldRainbow)         // High shield (top)
                    band(sH, mH, preset.highHue, preset.highRainbow)                     // High color
                    band(sH + mH, mH, preset.lowHue, preset.lowRainbow)                  // Low color
                    band(sH + mH * 2f, sH, preset.lowShieldHue, preset.lowShieldRainbow) // Low shield (bottom)
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
