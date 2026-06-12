package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import org.jetbrains.compose.resources.painterResource
import pock_kotlin.app.generated.resources.*
import utility.AdUnlock
import utility.PaintBucket
import utility.Storage
import utility.edgeSwipeBack
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val BD_SKIN = 0
private const val BD_TAIL = 1
private const val BD_PADDLE = 2

private val BD_COMPONENT_TYPES: List<BallType> = BallType.entries.filter { it != BallType.Random }
private val BD_PADDLE_ALIAS_TYPES = setOf(BallType.Dragon, BallType.Cat)
private val BD_PADDLE_TYPES: List<BallType> = BD_COMPONENT_TYPES.filter { it !in BD_PADDLE_ALIAS_TYPES }

private fun bdTypesFor(component: Int): List<BallType> =
    if (component == BD_PADDLE) BD_PADDLE_TYPES else BD_COMPONENT_TYPES

private fun bdBuildPartRenderer(component: Int, type: BallType, theme: ColorTheme): PuckRenderer {
    val r = when (component) {
        BD_SKIN -> BallStyleFactory.buildSkinOnlyRenderer(type, theme)
        BD_TAIL -> BallStyleFactory.buildTailOnlyRenderer(type, theme)
        else -> BallStyleFactory.buildPaddleOnlyRenderer(type, theme)
    }
    r.isHigh = theme.isWarm
    r.staticUiMode = true
    r.effect.frozen = true
    return r
}

private fun bdIsUnlocked(component: Int, type: BallType): Boolean = when (component) {
    BD_SKIN -> Storage.isSkinUnlocked(type)
    BD_TAIL -> Storage.isTailUnlocked(type)
    else -> Storage.isPaddleUnlocked(type)
}

private fun bdUnlock(component: Int, type: BallType) = when (component) {
    BD_SKIN -> Storage.unlockSkin(type)
    BD_TAIL -> Storage.unlockTail(type)
    else -> Storage.unlockPaddle(type)
}

// Soft contact-shadow (inert tint), proportioned from customization.svg's shadow ellipses
// (rx≈0.85r, ry≈0.23r, sitting at the part's bottom edge ≈ +1r below centre).
internal fun DrawScope.bdDrawShadow(cx: Float, cy: Float, r: Float) {
    val sw = r * 1.7f
    val sh = r * 0.47f
    drawOval(
        color = Color(Palette.withAlpha(ColorTheme.Cold.inert.primary, 150)),
        topLeft = Offset(cx - sw / 2f, (cy + r) - sh / 2f),
        size = Size(sw, sh)
    )
}

private fun DrawScope.bdDrawPart(
    renderer: PuckRenderer, theme: ColorTheme, component: Int,
    cx: Float, cy: Float, r: Float, drawShadow: Boolean = true
) {
    if (drawShadow) bdDrawShadow(cx, cy, r)
    renderer.x = cx
    renderer.y = cy
    renderer.radius = r
    renderer.strokeWidth = Settings.strokeWidth * (r / Settings.ballRadius)
    // The paddle is only part of an option preview when the PADDLE type is being chosen.
    renderer.effectEnabled = component == BD_PADDLE
    renderer.fillColor = theme.main.primary
    renderer.strokeColor = theme.main.secondary
    renderer.baseFillColor = theme.main.primary
    with(renderer) { draw() }
}

// Icon art aspect ratios (height / width) so they aren't stretched when drawn into a square.
internal const val BD_ADLOCK_ASPECT = 106.46f / 89.37f   // ic_menu_adlock
internal const val BD_LOCK_ASPECT = 95.17f / 81.84f       // ic_menu_lock

private fun darken(c: Color, f: Float) = Color(c.red * f, c.green * f, c.blue * f, c.alpha)

/**
 * AdLock glyph signalling "this design uses a cosmetic that must be unlocked, so it can't be saved."
 * Drawn the exact same way the carousel ad-buttons draw their glyph (`painter.draw`) — the `Image`
 * composable path was cropping the vector, this one shows it whole. [h] is the glyph height; it is
 * centred on ([cx],[cy]).
 */
private fun DrawScope.bdDrawAdLockGlyph(painter: Painter, tint: Color, cx: Float, cy: Float, h: Float) {
    val w = h * (89.37f / 106.46f)
    translate(cx - w / 2f, cy - h / 2f) {
        with(painter) { draw(Size(w, h), colorFilter = ColorFilter.tint(tint)) }
    }
}

/**
 * Draws a locked cosmetic option as a raised, square, tappable "ad button": a [faceColor] face
 * (the previewed player's primary) with a darker extruded lower lip + soft drop shadow so it reads
 * as clickable, the cosmetic ([drawCosmetic]) clipped onto the face, and [icon] (the combined
 * lock/ad glyph, or a plain lock for meter-gated items) drawn large on top. When [pressed] the
 * extrusion and shadow collapse so the button visibly depresses. No dimming/mask is used — the
 * cosmetic stays fully visible around the icon.
 *
 * The square is centred on ([cx],[cy]) and sized to the item's clip cell ([cellWidth]×[cellHeight]).
 */
internal fun DrawScope.bdDrawLockedOption(
    cx: Float, cy: Float,
    cellWidth: Float, cellHeight: Float,
    faceColor: Color,
    pressed: Boolean,
    icon: Painter,
    iconAspectHW: Float,
    drawCosmetic: DrawScope.() -> Unit,
) {
    val side = min(cellWidth, cellHeight) * 0.9f
    val half = side / 2f
    val left = cx - half
    val top = cy - half
    val corner = side * 0.16f
    val depthRest = side * 0.09f
    val depth = if (pressed) depthRest * 0.2f else depthRest

    // Soft drop shadow beneath the button (shrinks + fades when pressed).
    val shAlpha = if (pressed) 0.10f else 0.20f
    val shOff = if (pressed) depth + side * 0.01f else depthRest + side * 0.05f
    drawRoundRect(
        color = Color.Black.copy(alpha = shAlpha),
        topLeft = Offset(left, top + shOff),
        size = Size(side, side),
        cornerRadius = CornerRadius(corner)
    )
    // Extruded lower lip (darker), sits `depth` below the face to give the raised 3D edge.
    drawRoundRect(
        color = darken(faceColor, 0.6f),
        topLeft = Offset(left, top + depth),
        size = Size(side, side),
        cornerRadius = CornerRadius(corner)
    )
    // Button face.
    drawRoundRect(
        color = faceColor,
        topLeft = Offset(left, top),
        size = Size(side, side),
        cornerRadius = CornerRadius(corner)
    )
    // Cosmetic, clipped onto the face so it can't spill past the button edges.
    clipRect(left, top, left + side, top + side) { drawCosmetic() }
    // Lock/ad glyph on top — large, with padding on all sides.
    val iconW = side * 0.58f
    val iconH = iconW * iconAspectHW
    translate(cx - iconW / 2f, cy - iconH / 2f) {
        with(icon) { draw(Size(iconW, iconH), colorFilter = ColorFilter.tint(Color.White)) }
    }
}

/**
 * "The Ball Designer" — style screen (replaces the deprecated CustomBallCreatorScreen).
 * Translated from Plans/UIOverhaul/Screens/customization.svg. Cosmetic re-presentation only.
 */
@Composable
fun BallDesignerScreen(onBack: () -> Unit, onNavigateToColor: () -> Unit) {
    val isDark = LocalDarkMode.current
    val density = LocalDensity.current
    val poppins = poppinsFamily()
    // A VectorPainter holds one internal size at a time, so each distinct draw-size needs its own
    // instance — otherwise the carousel (large) and a badge (small) sharing one painter fight over
    // its size and the badge renders clipped. carouselLockPainter → the carousel ad-buttons,
    // previewLockPainter → the preview badge; the control boxes and slots make their own below.
    val carouselLockPainter = painterResource(Res.drawable.ic_menu_adlock)
    val previewLockPainter = painterResource(Res.drawable.ic_menu_adlock)

    val bgColor = if (isDark) PaintBucket.menuBackgroundDark else PaintBucket.menuBackgroundLight
    val fgColor = if (isDark) PaintBucket.white else Color(0xFF222222)
    val theme = ColorTheme.Cold // CBC parts/preview: cold/low, normal color, upright.
    val wrapperBg = if (isDark) Color(0xFF1E1E2A) else Color(theme.inert.primary)
    val controlBg = if (isDark) Color(0xFF2A3A4E) else PaintBucket.white
    val accentBlue = PaintBucket.menuAccentBlue

    var rootW by remember { mutableIntStateOf(0) }
    var rootH by remember { mutableIntStateOf(0) }
    var initDone by remember { mutableStateOf(false) }

    var skinType by remember { mutableStateOf(BallType.Classic) }
    var tailType by remember { mutableStateOf(BallType.Classic) }
    var paddleType by remember { mutableStateOf(BallType.Classic) }
    var ranks by remember { mutableStateOf(Triple(0, 1, 2)) }
    var activeComp by remember { mutableIntStateOf(BD_SKIN) }

    var draggingComp by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var boxHeightPx by remember { mutableFloatStateOf(0f) }

    var meterPopupVisible by remember { mutableStateOf(false) }
    var adLimitPopupVisible by remember { mutableStateOf(false) }

    fun loadBalls(): List<Pair<Int, CustomBallConfig>> =
        (0 until Storage.SLOT_COUNT).mapNotNull { i -> Storage.loadCustomBall(i)?.let { i to it } }

    var savedBalls by remember { mutableStateOf(loadBalls()) }
    var selectedSlot by remember { mutableIntStateOf(savedBalls.firstOrNull()?.first ?: 0) }

    fun currentConfig() = CustomBallConfig(skinType, tailType, paddleType, ranks.first, ranks.second, ranks.third)
    fun allUnlocked() =
        Storage.isSkinUnlocked(skinType) && Storage.isTailUnlocked(tailType) && Storage.isPaddleUnlocked(paddleType)

    var previewRenderer by remember { mutableStateOf<PuckRenderer?>(null) }
    fun rebuildPreview() {
        val r = BallStyleFactory.buildCustomRenderer(currentConfig(), theme)
        r.isHigh = false
        r.staticUiMode = true
        r.effect.frozen = true
        previewRenderer = r
    }

    fun trySave() {
        if (selectedSlot < 0 || !Storage.isSlotUnlocked(selectedSlot)) return
        if (!allUnlocked()) return
        Storage.saveCustomBall(selectedSlot, currentConfig())
        savedBalls = loadBalls()
    }

    fun loadSlotIntoColumn(config: CustomBallConfig) {
        skinType = config.skinType
        tailType = config.tailType
        paddleType = config.paddleType
        ranks = Triple(config.skinZRank, config.tailZRank, config.paddleZRank)
        rebuildPreview()
    }

    fun typeOf(id: Int) = when (id) { BD_SKIN -> skinType; BD_TAIL -> tailType; else -> paddleType }
    fun labelOf(id: Int) = when (id) { BD_SKIN -> "Skin"; BD_TAIL -> "Tail"; else -> "Paddle" }

    fun selectComponent(component: Int, type: BallType) {
        when (component) {
            BD_SKIN -> skinType = type
            BD_TAIL -> tailType = type
            else -> paddleType = type
        }
        rebuildPreview()
        trySave()
    }

    fun onComponentTapped(component: Int, type: BallType) {
        // Always update the live previews so the locked design is visible. trySave() (inside
        // selectComponent) silently refuses to persist while any selected piece is locked, so a
        // locked pick is shown but never saved.
        selectComponent(component, type)
        if (bdIsUnlocked(component, type)) return
        if (Storage.canWatchAdNow()) {
            AdUnlock.watchAdToUnlock(grant = { bdUnlock(component, type) }) { success ->
                if (success) selectComponent(component, type)
            }
        } else adLimitPopupVisible = true
    }

    fun rankOf(id: Int) = when (id) { BD_SKIN -> ranks.first; BD_TAIL -> ranks.second; else -> ranks.third }
    fun displayOrder(): List<Int> = (0..2).sortedByDescending { rankOf(it) }
    fun swapRanks(a: Int, b: Int) {
        val arr = intArrayOf(ranks.first, ranks.second, ranks.third)
        val tmp = arr[a]; arr[a] = arr[b]; arr[b] = tmp
        ranks = Triple(arr[0], arr[1], arr[2])
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
                Settings.ballRadius = ratio
                PaintBucket.initialize(ratio)
                PaintBucket.applyPlayerHues()
            }
            if (Settings.ballRadius == 0f) Settings.ballRadius = Settings.screenRatio
            val initial = savedBalls.firstOrNull { it.first == selectedSlot } ?: savedBalls.firstOrNull()
            if (initial != null) { selectedSlot = initial.first; loadSlotIntoColumn(initial.second) }
            else rebuildPreview()
            initDone = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .onSizeChanged { rootW = it.width; rootH = it.height }
            .edgeSwipeBack(onBack)
    ) {
        if (initDone) {
            val slotPx = Settings.ballRadius * 4f
            val slotDp = with(density) { slotPx.toDp() }

            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (Storage.unlockProgress < 100) {
                    UnlockProgressBar(
                        progress = Storage.unlockProgress,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(32.dp)
                    )
                    Box(Modifier.height(8.dp))
                }

                // Preview — static composed ball at play size, with contact shadow.
                val previewLocked = !allUnlocked()
                Box(modifier = Modifier.fillMaxWidth().weight(0.9f).clipToBounds()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val pr = previewRenderer ?: return@Canvas
                        val cx = size.width / 2f
                        val r = Settings.ballRadius
                        val cy = size.height / 2f - r * 0.3f
                        bdDrawShadow(cx, cy, r)
                        pr.x = cx; pr.y = cy; pr.radius = r
                        pr.strokeWidth = Settings.strokeWidth
                        pr.effectEnabled = true
                        pr.fillColor = theme.main.primary
                        pr.strokeColor = theme.main.secondary
                        pr.baseFillColor = theme.main.primary
                        with(pr) { draw() }
                        // Design uses a locked piece → mark the preview as unavailable (won't save).
                        // Bottom-right, inset so the whole glyph stays inside the box.
                        if (previewLocked) {
                            val h = 36.dp.toPx(); val w = h * (89.37f / 106.46f); val pad = 12.dp.toPx()
                            bdDrawAdLockGlyph(previewLockPainter, fgColor, size.width - pad - w / 2f, size.height - pad - h / 2f, h)
                        }
                    }
                }
                Box(Modifier.height(8.dp))

                // Grey wrapper holding all controls (no outline).
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
                            // Left: draggable Skin/Tail/Paddle column (wider than the carousel, as in the SVG).
                            Column(modifier = Modifier.fillMaxHeight().weight(0.58f)) {
                                for (id in displayOrder()) {
                                    key(id) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                                .offset { IntOffset(0, if (id == draggingComp) dragOffsetY.roundToInt() else 0) }
                                                .zIndex(if (id == draggingComp) 1f else 0f)
                                                .onSizeChanged { if (it.height > 0) boxHeightPx = it.height.toFloat() }
                                                .padding(vertical = 4.dp)
                                                .pointerInput(id) {
                                                    detectDragGestures(
                                                        onDragStart = { draggingComp = id; dragOffsetY = 0f },
                                                        onDragEnd = { draggingComp = -1; dragOffsetY = 0f; trySave() },
                                                        onDragCancel = { draggingComp = -1; dragOffsetY = 0f }
                                                    ) { change, drag ->
                                                        change.consume()
                                                        dragOffsetY += drag.y
                                                        val rh = if (boxHeightPx > 0f) boxHeightPx else 1f
                                                        val o = displayOrder()
                                                        val idx = o.indexOf(id)
                                                        if (dragOffsetY > rh / 2f && idx < o.lastIndex) {
                                                            swapRanks(id, o[idx + 1]); dragOffsetY -= rh; rebuildPreview()
                                                        } else if (dragOffsetY < -rh / 2f && idx > 0) {
                                                            swapRanks(id, o[idx - 1]); dragOffsetY += rh; rebuildPreview()
                                                        }
                                                    }
                                                }
                                                .pointerInput(id) { detectTapGestures(onTap = { activeComp = id }) }
                                        ) {
                                            TypeSelectorBox(
                                                component = id, type = typeOf(id), label = labelOf(id),
                                                active = id == activeComp, theme = theme,
                                                controlBg = controlBg, accent = accentBlue, fg = fgColor, poppins = poppins,
                                                locked = !bdIsUnlocked(id, typeOf(id))
                                            )
                                        }
                                    }
                                }
                            }

                            // Right: vertical option carousel for the active type (no outline).
                            val list = bdTypesFor(activeComp)
                            val selIdx = list.indexOf(typeOf(activeComp)).coerceAtLeast(0)
                            val rendererCache = remember(activeComp, isDark) {
                                list.associateWith { bdBuildPartRenderer(activeComp, it, theme) }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(0.42f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(controlBg)
                            ) {
                                VerticalOptionCarousel(
                                    itemCount = list.size,
                                    selectedIndex = selIdx,
                                    modifier = Modifier.fillMaxSize(),
                                    onTap = { i -> onComponentTapped(activeComp, list[i]) },
                                    // Browsing onto any item (locked or not) updates the previews so
                                    // its design is visible; locked picks just won't save (trySave guard).
                                    onSnap = { i -> selectComponent(activeComp, list[i]) }
                                ) { index, cx, cy, r, _, isPressed, cellW, cellH ->
                                    val type = list[index]
                                    val renderer = rendererCache[type] ?: return@VerticalOptionCarousel
                                    if (bdIsUnlocked(activeComp, type)) {
                                        bdDrawPart(renderer, theme, activeComp, cx, cy, r)
                                    } else {
                                        // All skins/tails/paddles are ad-unlockable → the AdLock glyph.
                                        bdDrawLockedOption(
                                            cx, cy, cellW, cellH, Color(theme.main.primary), isPressed,
                                            carouselLockPainter, BD_ADLOCK_ASPECT
                                        ) { bdDrawPart(renderer, theme, activeComp, cx, cy, r, drawShadow = false) }
                                    }
                                }
                            }
                        }

                        // Save-slot strip — inside the wrapper, themed sub-container, right space for pills.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(slotDp + 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(controlBg)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())
                                    .padding(start = 8.dp, end = 84.dp, top = 8.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (idx in 0 until Storage.SLOT_COUNT) {
                                    DesignerSlotCell(
                                        index = idx,
                                        config = savedBalls.firstOrNull { it.first == idx }?.second,
                                        unlocked = Storage.isSlotUnlocked(idx),
                                        selected = idx == selectedSlot,
                                        theme = theme, isDark = isDark, accent = accentBlue, fg = fgColor, sizeDp = slotDp,
                                        showAdLock = idx == selectedSlot && !allUnlocked(),
                                        onTap = {
                                            val existing = savedBalls.firstOrNull { it.first == idx }
                                            if (existing != null) { selectedSlot = idx; loadSlotIntoColumn(existing.second) }
                                            else if (allUnlocked()) {
                                                Storage.saveCustomBall(idx, currentConfig())
                                                savedBalls = loadBalls(); selectedSlot = idx
                                            }
                                        },
                                        onLongPress = {
                                            Storage.deleteCustomBall(idx)
                                            savedBalls = loadBalls()
                                            if (selectedSlot == idx) selectedSlot = savedBalls.firstOrNull()?.first ?: 0
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Action pills — flush to the screen's right edge, above the wrapper (z-order).
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 14.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EdgePill(
                    side = PillSide.End, color = accentBlue,
                    modifier = Modifier.height(42.dp).clickable(onClick = onNavigateToColor)
                ) {
                    Image(painterResource(Res.drawable.ic_menu_customize), null,
                        Modifier.size(22.dp), colorFilter = ColorFilter.tint(PaintBucket.white))
                }
                EdgePill(
                    side = PillSide.End, color = PaintBucket.menuAccentRed,
                    modifier = Modifier.height(42.dp).clickable(onClick = onBack)
                ) {
                    Image(painterResource(Res.drawable.ic_menu_check), null,
                        Modifier.size(22.dp), colorFilter = ColorFilter.tint(PaintBucket.white))
                }
            }
        }

        if (meterPopupVisible) MeterLockedPopup(Storage.unlockProgress) { meterPopupVisible = false }
        if (adLimitPopupVisible) AdLimitPopup(Storage.minutesUntilNextAd()) { adLimitPopupVisible = false }
    }
}

@Composable
private fun TypeSelectorBox(
    component: Int,
    type: BallType,
    label: String,
    active: Boolean,
    theme: ColorTheme,
    controlBg: Color,
    accent: Color,
    fg: Color,
    poppins: androidx.compose.ui.text.font.FontFamily,
    locked: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    val renderer = remember(component, type, theme) { bdBuildPartRenderer(component, type, theme) }
    // Own painter instance (see note at carouselLockPainter) so the carousel can't resize it.
    val lockPainter = painterResource(Res.drawable.ic_menu_adlock)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(controlBg)
            .then(if (active) Modifier.border(4.dp, accent, shape) else Modifier)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            bdDrawPart(renderer, theme, component, size.width / 2f, size.height / 2f + Settings.ballRadius * 0.2f, Settings.ballRadius)
            // This piece needs an ad → mark it; the design shows but won't be used until unlocked.
            // Right-centred (inset) so the whole glyph stays inside the box, beside the ball.
            if (locked) {
                // Right inset matches the 10.dp gap between the two columns (the Row's spacedBy).
                val h = 24.dp.toPx(); val w = h * (89.37f / 106.46f); val pad = 10.dp.toPx()
                bdDrawAdLockGlyph(lockPainter, fg, size.width - pad - w / 2f, size.height / 2f, h)
            }
        }
        androidx.compose.material3.Text(
            text = label, color = fg, fontFamily = poppins, fontSize = 13.sp,
            fontWeight = FontWeight.Light, fontStyle = FontStyle.Italic,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 4.dp)
        )
    }
}

@Composable
private fun DesignerSlotCell(
    index: Int,
    config: CustomBallConfig?,
    unlocked: Boolean,
    selected: Boolean,
    theme: ColorTheme,
    isDark: Boolean,
    accent: Color,
    fg: Color,
    sizeDp: androidx.compose.ui.unit.Dp,
    showAdLock: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (isDark) Color(0xFF1A2A3E) else Color(0xFFEDEDF4)
    // Own painter instance (see note at carouselLockPainter) so the carousel can't resize it.
    val lockPainter = painterResource(Res.drawable.ic_menu_adlock)
    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(shape)
            .background(bg)
            .then(if (unlocked && selected) Modifier.border(4.dp, accent, shape) else Modifier)
            .pointerInput(unlocked, config) {
                if (unlocked) detectTapGestures(onTap = { onTap() }, onLongPress = { if (config != null) onLongPress() })
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            !unlocked -> androidx.compose.material3.Text("${Storage.slotRequiredPercent(index)}%", color = fg.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            config != null -> {
                val renderer = remember(config, theme) {
                    BallStyleFactory.buildCustomRenderer(config, theme).also {
                        it.isHigh = theme.isWarm; it.staticUiMode = true; it.effect.frozen = true
                    }
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f - Settings.ballRadius * 0.2f
                    bdDrawShadow(cx, cy, Settings.ballRadius)
                    renderer.x = cx; renderer.y = cy; renderer.radius = Settings.ballRadius
                    renderer.strokeWidth = Settings.strokeWidth
                    renderer.effectEnabled = true
                    renderer.fillColor = theme.main.primary
                    renderer.strokeColor = theme.main.secondary
                    renderer.baseFillColor = theme.main.primary
                    with(renderer) { draw() }
                }
            }
            else -> androidx.compose.material3.Text("+", color = fg.copy(alpha = 0.5f), fontSize = 22.sp)
        }
        // Current design uses a locked piece → it won't save into this (selected) slot.
        // Centred over the ball.
        if (showAdLock) Canvas(modifier = Modifier.fillMaxSize()) {
            bdDrawAdLockGlyph(lockPainter, fg, size.width / 2f, size.height / 2f, size.minDimension * 0.4f)
        }
    }
}
