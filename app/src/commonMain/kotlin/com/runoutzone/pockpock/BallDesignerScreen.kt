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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
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
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorGroup
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.CustomBallConfig
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pock_kotlin.app.generated.resources.*
import utility.AdUnlock
import utility.PaintBucket
import utility.Storage
import utility.UiStrobeClock
import utility.edgeSwipeBack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

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

// Left-control prefix: "Ball" for the skin row, "Tail"/"Paddle" for the others.
@Composable
private fun bdTypeLabel(component: Int): String = when (component) {
    BD_SKIN -> stringResource(Res.string.style_type_ball)
    BD_TAIL -> stringResource(Res.string.style_type_tail)
    else -> stringResource(Res.string.style_type_paddle)
}

// Display name of a skin/tail style (reuses the existing ball-type strings where they exist).
@Composable
private fun bdSkinTailName(type: BallType): String = when (type) {
    BallType.Classic -> stringResource(Res.string.style_name_classic)
    BallType.PokPok -> stringResource(Res.string.ball_type_pokpok)
    BallType.Neon -> stringResource(Res.string.style_name_neon)
    BallType.Ghost -> stringResource(Res.string.style_name_ghost)
    BallType.Fire -> stringResource(Res.string.style_name_fire)
    BallType.Ice -> stringResource(Res.string.style_name_ice)
    BallType.Galaxy -> stringResource(Res.string.style_name_galaxy)
    BallType.Spinner -> stringResource(Res.string.style_name_spinner)
    BallType.Metal -> stringResource(Res.string.style_name_metal)
    BallType.Pixel -> stringResource(Res.string.style_name_pixel)
    BallType.Rainbow -> stringResource(Res.string.style_name_rainbow)
    BallType.Prism -> stringResource(Res.string.style_name_prism)
    BallType.Plasma -> stringResource(Res.string.style_name_plasma)
    BallType.Dragon -> stringResource(Res.string.ball_type_dragon)
    BallType.Axolotl -> stringResource(Res.string.ball_type_axolotl)
    BallType.Cat -> stringResource(Res.string.ball_type_cat)
    BallType.Random -> stringResource(Res.string.style_name_random)
}

// Display name of a component's style. Paddles whose visual differs from the parent ball name
// get a design-based name (PokPok's egg paddle = "Egg", Galaxy = "Star", Axolotl = "Bubble").
@Composable
private fun bdStyleName(component: Int, type: BallType): String =
    if (component == BD_PADDLE) when (type) {
        BallType.PokPok -> stringResource(Res.string.style_paddle_egg)
        BallType.Galaxy -> stringResource(Res.string.style_paddle_star)
        BallType.Axolotl -> stringResource(Res.string.style_paddle_bubble)
        BallType.Metal -> stringResource(Res.string.style_paddle_dynamite)
        else -> bdSkinTailName(type)
    } else bdSkinTailName(type)

// Left-control caption, e.g. "Ball | Metal", "Tail | Rainbow", "Paddle | Egg".
@Composable
private fun bdControlLabel(component: Int, type: BallType): String =
    "${bdTypeLabel(component)} | ${bdStyleName(component, type)}"

// Soft contact-shadow (inert tint), proportioned from customization.svg's shadow ellipses
// (rx≈0.85r, ry≈0.23r, sitting at the part's bottom edge ≈ +1r below centre). Light mode keeps the
// soft cold-inert tint; dark mode uses the deep navy control-box tone (BD_WRAPPER_DARK) so the oval
// reads as a shadow against the dark backdrop instead of a pale smudge.
internal fun DrawScope.bdDrawShadow(cx: Float, cy: Float, r: Float, isDark: Boolean) {
    val sw = r * 1.7f
    val sh = r * 0.47f
    drawOval(
        color = if (isDark) BD_WRAPPER_DARK else Color(Palette.withAlpha(ColorTheme.Cold.inert.primary, 150)),
        topLeft = Offset(cx - sw / 2f, (cy + r) - sh / 2f),
        size = Size(sw, sh)
    )
}

// Builds a ColorTheme straight from hue values WITHOUT touching PaintBucket, so a colour preview can
// show any hue — including a still-locked one — without ever mutating the live gameplay palette. The
// inert (dead) group is hue-independent, so it's read from the live theme (constant either way).
internal fun bdThemeFromHues(isHigh: Boolean, normalHue: Float, shieldHue: Float): ColorTheme {
    fun grp(hue: Float) = ColorGroup(
        Color.hsv(hue, 0.359f, 0.961f).toArgb(),
        Color.hsv(hue, 0.661f, 0.961f).toArgb(),
        Color.hsv(hue, 0.10f, 0.99f).toArgb()
    )
    return ColorTheme(
        main = grp(normalHue),
        shield = grp(shieldHue),
        inert = (if (isHigh) ColorTheme.Warm else ColorTheme.Cold).inert,
        isWarm = isHigh
    )
}

// Shared demo-motion preview used by both the Ball Designer (style) and the Color Picker: the ball
// orbits a circle (so the tail trails its real path) and the paddle runs its real charge cycle.
// Purely cosmetic — no physics. [step] advances one motion-step per draw so position and tail history
// stay in lockstep (the caller bumps its own counter and passes it); [frame] keeps the canvas
// redrawing each tick. [theme]/[shielded] drive the colours; pass an isolated theme (see
// bdThemeFromHues) to preview without affecting gameplay. When [locked] it stamps the ad-lock glyph
// bottom-right to mark the design/colour as un-saveable.
internal fun DrawScope.bdDrawAnimatedPreview(
    renderer: PuckRenderer, theme: ColorTheme, shielded: Boolean,
    step: Float, frame: Int, locked: Boolean, lockPainter: Painter,
) {
    val r = Settings.ballRadius
    val cx = size.width / 2f
    val cy = size.height / 2f
    val orbitR = r * 3.0f
    val theta = 2f * PI.toFloat() * step / 100f
    val grp = if (shielded) theme.shield else theme.main
    renderer.theme = theme
    renderer.x = cx + orbitR * cos(theta)
    renderer.y = cy + orbitR * sin(theta)
    renderer.radius = r
    renderer.strokeWidth = Settings.strokeWidth
    renderer.frame = frame
    renderer.effectEnabled = true
    renderer.shielded = shielded
    renderer.fillColor = grp.primary
    renderer.strokeColor = grp.secondary
    renderer.baseFillColor = grp.primary
    // Paddle: run the charge → sweet-spot → drain → inert cycle on a loop and orbit the ball.
    renderer.effect.increaseCharge()
    if (renderer.effect.phase == ChargePhase.Inert) renderer.effect.reset()
    renderer.effect.cbcOrbitAngleDeg = (step * 1.2f) % 360f
    // No contact shadow on the moving preview — that's only for static displays.
    with(renderer) { draw() }
    if (locked) {
        val h = 36.dp.toPx(); val w = h * (89.37f / 106.46f); val pad = 12.dp.toPx()
        bdDrawAdLockGlyph(lockPainter, PaintBucket.menuAccentRed,
            size.width - pad - w / 2f, size.height - pad - h / 2f, h)
    }
}

private fun DrawScope.bdDrawPart(
    renderer: PuckRenderer, theme: ColorTheme, component: Int,
    cx: Float, cy: Float, r: Float, isDark: Boolean, drawShadow: Boolean = true
) {
    // Tails get no contact shadow — it reads as accidental floating beneath a trail. The composed
    // ball/slot previews still draw their own shadow (they call bdDrawShadow directly).
    if (drawShadow && component != BD_TAIL) bdDrawShadow(cx, cy, r, isDark)
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

// The control-box (grey wrapper) background. Light is a soft lavender; dark is the deep navy that
// also doubles as the locked-option shadow lip in dark mode (see bdDrawLockedOption).
internal val BD_WRAPPER_LIGHT = Color(0xFFE3E2FE)
internal val BD_WRAPPER_DARK = Color(0xFF1E1E2A)

// The Ball Designer's carousel option buttons keep the default low-player blue regardless of any
// color the player has chosen on the Color screen (the balls themselves still follow the picker).
// The outline is the solid default primary; the face is that same primary at 20% alpha — the faded
// arena-background tint (see Drawing.drawTouchHighlights, which fills each half with
// lowBallFill.copy(alpha = .2f)) — so a ball whose body is the primary blue can't be swallowed by a
// solid button face. 202.5° is the Storage default for the low player's normal hue; the
// saturation/value pair matches applyHueToPaint / bdThemeFromHues.
internal const val BD_DEFAULT_LOW_HUE = 202.5f
internal val BD_BUTTON_OUTLINE = Color.hsv(BD_DEFAULT_LOW_HUE, 0.359f, 0.961f)
internal val BD_BUTTON_FILL = BD_BUTTON_OUTLINE.copy(alpha = 0.2f)

// The exact tone PokPok's skin produces by overlaying its black shadow mask (alpha 0.244, see
// PokPokSkin.SHADOW_ALPHA) over an opaque colour: c · (1 − alpha). Over the ad-button face
// (menuAccentBlue == #52B6F2, the same blue PokPok's body uses) this yields PokPok's body-vs-shadow
// tone, used for the button's shadow lip.
private const val BD_SHADOW_ALPHA = 0.244f
internal fun bdShadowOver(c: Color) = Color(
    c.red * (1f - BD_SHADOW_ALPHA), c.green * (1f - BD_SHADOW_ALPHA), c.blue * (1f - BD_SHADOW_ALPHA), c.alpha
)

/**
 * AdLock glyph signalling "this design uses a cosmetic that must be unlocked, so it can't be saved."
 * Drawn the exact same way the carousel ad-buttons draw their glyph (`painter.draw`) — the `Image`
 * composable path was cropping the vector, this one shows it whole. [h] is the glyph height; it is
 * centred on ([cx],[cy]).
 */
internal fun DrawScope.bdDrawAdLockGlyph(painter: Painter, tint: Color, cx: Float, cy: Float, h: Float) {
    val w = h * (89.37f / 106.46f)
    translate(cx - w / 2f, cy - h / 2f) {
        with(painter) { draw(Size(w, h), colorFilter = ColorFilter.tint(tint)) }
    }
}

/**
 * Draws a locked cosmetic option as a raised, square, tappable "ad button": a [faceColor] face
 * sitting on a single darker-blue shadow lip ([bdShadowOver] of the face — PokPok's body-vs-shadow
 * tone, an exact rounded duplicate of the face) so it reads as clickable, the cosmetic
 * ([drawCosmetic]) clipped onto the face, and [icon] (the lock/ad glyph) drawn small in the
 * top-right corner so the cosmetic design stays visible. When [pressed] the whole face (cosmetic +
 * icon) slides straight down onto its lip so the button visibly depresses — the shadow doesn't
 * vanish, the button covers it. No dimming/mask is used.
 *
 * The square is centred on ([cx],[cy]) and sized to the item's clip cell ([cellWidth]×[cellHeight]),
 * reserving room below for the lip so the carousel's hard cell-clip can't shave its rounded bottom.
 */
internal fun DrawScope.bdDrawLockedOption(
    cx: Float, cy: Float,
    cellWidth: Float, cellHeight: Float,
    faceColor: Color,
    pressed: Boolean,
    icon: Painter?,
    iconAspectHW: Float,
    faceStroke: Color? = null,
    centerIcon: Boolean = false,
    drawCosmetic: DrawScope.() -> Unit,
) {
    val depthK = 0.09f
    // Width has no lip; height must fit the face PLUS the lip (= side·(1 + depthK)) within the cell,
    // otherwise the carousel's per-cell clipRect shaves the lip's rounded bottom into a sharp edge.
    val side = min(cellWidth * 0.9f, cellHeight * 0.9f / (1f + depthK))
    val half = side / 2f
    val left = cx - half
    val corner = side * 0.16f
    val depth = side * depthK
    // Centre the whole assembly (face + lip) on cy so the lip has room below within the cell.
    val top = cy - (side + depth) / 2f
    // Pressing slides the face straight down by `depth` so it lands exactly on the lip, covering it.
    val faceOff = if (pressed) depth else 0f

    // Single shadow lip — fixed `depth` below the resting face, an exact rounded duplicate of the
    // face in PokPok's body-vs-shadow blue. Identical in light and dark mode.
    drawRoundRect(
        color = bdShadowOver(faceColor),
        topLeft = Offset(left, top + depth),
        size = Size(side, side),
        cornerRadius = CornerRadius(corner)
    )
    // Button face — slides down onto the lip when pressed.
    drawRoundRect(
        color = faceColor,
        topLeft = Offset(left, top + faceOff),
        size = Size(side, side),
        cornerRadius = CornerRadius(corner)
    )
    val sw = side * 0.09f
    // Cosmetic, clipped onto the face so it can't spill past the button edges; rides the face down.
    // The caller draws it centred on cy, but the face centre sits depth/2 above cy (assembly is
    // centred, lip hangs below), so nudge it up by depth/2 to centre it within the face. Drawn
    // BEFORE the face outline so the outline sits on top — the cosmetic is sandwiched between the
    // face fill and the outline (the outline reads as a rim around the cosmetic, not behind it).
    // The clip is a rounded rect inset to the outline's centreline (sw/2) with the matching reduced
    // corner radius, so wide tails tuck under the rounded border instead of poking past it — the
    // clip edge always lands beneath the outline stroke.
    val clipInset = sw / 2f
    val cosmeticClip = Path().apply {
        addRoundRect(
            RoundRect(
                left = left + clipInset,
                top = top + faceOff + clipInset,
                right = left + side - clipInset,
                bottom = top + faceOff + side - clipInset,
                cornerRadius = CornerRadius((corner - clipInset).coerceAtLeast(0f))
            )
        )
    }
    clipPath(cosmeticClip) {
        translate(0f, faceOff - depth / 2f) { drawCosmetic() }
    }
    // Optional outline on the face (CCP color buttons: the face IS the unlock colour, so it gets a
    // primary fill + secondary stroke just like a ball). The stroke is inset by half its width and
    // its corner radius reduced to match, so the stroke's OUTER edge coincides exactly with the
    // face edge (no fill slivers peeking past the corners).
    faceStroke?.let { sc ->
        drawRoundRect(
            color = sc,
            topLeft = Offset(left + sw / 2f, top + faceOff + sw / 2f),
            size = Size(side - sw, side - sw),
            cornerRadius = CornerRadius((corner - sw / 2f).coerceAtLeast(0f)),
            style = Stroke(sw)
        )
    }
    // Lock/ad glyph — always the menu accent red for consistency across light/dark, rides the face
    // down on press. Default: small, tucked top-right so it reads as "locked" without covering the
    // cosmetic. [centerIcon]: large and centred, for buttons that have no cosmetic to cover (e.g. the
    // CCP color buttons, where the face itself is the colour). [icon] null → no glyph at all (an
    // already-unlocked button, drawn flat/pressed).
    if (icon != null) {
        if (centerIcon) {
            val iconH = side * 0.5f
            val iconW = iconH / iconAspectHW
            translate(left + (side - iconW) / 2f, top + faceOff + (side - iconH) / 2f) {
                with(icon) { draw(Size(iconW, iconH), colorFilter = ColorFilter.tint(PaintBucket.menuAccentRed)) }
            }
        } else {
            val iconH = 24.dp.toPx()
            val iconW = iconH / iconAspectHW
            val iconPad = side * 0.1f
            translate(left + side - iconPad - iconW, top + faceOff + iconPad) {
                with(icon) { draw(Size(iconW, iconH), colorFilter = ColorFilter.tint(PaintBucket.menuAccentRed)) }
            }
        }
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
    val wrapperBg = if (isDark) BD_WRAPPER_DARK else BD_WRAPPER_LIGHT
    val controlBg = if (isDark) Color(0xFF2A3A4E) else PaintBucket.white
    val accentBlue = PaintBucket.menuAccentBlue

    var rootW by remember { mutableIntStateOf(0) }
    var rootH by remember { mutableIntStateOf(0) }
    var initDone by remember { mutableStateOf(false) }

    // Demo-only animation clock for the composition preview (read inside the preview Canvas).
    var frame by remember { mutableIntStateOf(0) }

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

    // Geometry for centering the action pills vertically between the unlock-progress bar and the
    // preview's locked-cosmetic glyph. All values are in root coords.
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var progressBottomPx by remember { mutableFloatStateOf(0f) }
    var previewTopPx by remember { mutableFloatStateOf(0f) }
    var previewBottomPx by remember { mutableFloatStateOf(0f) }
    var pillColHeightPx by remember { mutableIntStateOf(0) }

    fun loadBalls(): List<Pair<Int, CustomBallConfig>> =
        (0 until Storage.SLOT_COUNT).mapNotNull { i -> Storage.loadCustomBall(i)?.let { i to it } }

    var savedBalls by remember { mutableStateOf(loadBalls()) }
    var selectedSlot by remember { mutableIntStateOf(savedBalls.firstOrNull()?.first ?: 0) }

    fun currentConfig() = CustomBallConfig(skinType, tailType, paddleType, ranks.first, ranks.second, ranks.third)
    fun allUnlocked() =
        Storage.isSkinUnlocked(skinType) && Storage.isTailUnlocked(tailType) && Storage.isPaddleUnlocked(paddleType)

    var previewRenderer by remember { mutableStateOf<PuckRenderer?>(null) }
    // Motion clock advanced exactly once per preview draw (not per wall-clock tick). Plain holder,
    // not snapshot state, so mutating it in the draw phase can't trigger a redraw loop.
    val previewStep = remember { floatArrayOf(0f) }
    fun rebuildPreview() {
        previewRenderer?.tail?.clear()
        val r = BallStyleFactory.buildCustomRenderer(currentConfig(), theme)
        r.isHigh = false
        // Live motion (not the static "screenshot" pose): the ball orbits a circle so the tail
        // trails its real path and the paddle runs its real charge cycle. Driven in the Canvas below.
        r.staticUiMode = false
        r.effect.frozen = false
        r.suppressSounds = true   // cosmetic preview — never play the charge/sweet-spot SFX
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

    // Drives the preview ball's demo motion (~60fps). Purely cosmetic — no physics involved.
    // Also advances the shared strobe clock so the static rainbow/prism option thumbnails keep
    // cycling their colors (their geometry stays frozen; only the hue strobes).
    LaunchedEffect(Unit) {
        while (true) { delay(16L); frame++; UiStrobeClock.advance() }
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
            .onGloballyPositioned { rootCoords = it }
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
                            .onGloballyPositioned { pc ->
                                rootCoords?.let { progressBottomPx = it.localPositionOf(pc, Offset.Zero).y + pc.size.height }
                            }
                    )
                    Box(Modifier.height(8.dp))
                }

                // Preview — composed ball at play size, animated with demo-only motion: the ball
                // orbits a circle (real motion, so the tail trails live) and the paddle orbits +
                // cycles charge as the old CBC did. None of this touches the physics simulation.
                val previewLocked = !allUnlocked()
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
                        // Advance one motion-step per draw so position and tail history stay in
                        // lockstep — exactly one tail point per position step, like the live game.
                        previewStep[0] += 1f
                        bdDrawAnimatedPreview(
                            pr, theme, shielded = false, step = previewStep[0], frame = frame,
                            locked = previewLocked, lockPainter = previewLockPainter
                        )
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
                                                component = id, type = typeOf(id), label = bdControlLabel(id, typeOf(id)),
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
                            // The item under the carousel's centre drives the status label. It resets to
                            // the equipped selection whenever the active component or that selection changes.
                            var browsedIndex by remember(activeComp, selIdx) { mutableIntStateOf(selIdx) }
                            val browsedType = list[browsedIndex.coerceIn(0, list.lastIndex)]
                            val carouselLabel =
                                if (bdIsUnlocked(activeComp, browsedType)) bdStyleName(activeComp, browsedType)
                                else stringResource(Res.string.style_ad_to_own)
                            val rendererCache = remember(activeComp, isDark) {
                                list.associateWith { bdBuildPartRenderer(activeComp, it, theme) }
                            }
                            var carouselWidthPx by remember { mutableIntStateOf(0) }
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(0.42f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(controlBg)
                                    .onSizeChanged { carouselWidthPx = it.width }
                            ) {
                                VerticalOptionCarousel(
                                    itemCount = list.size,
                                    selectedIndex = selIdx,
                                    modifier = Modifier.fillMaxSize(),
                                    onTap = { i -> onComponentTapped(activeComp, list[i]) },
                                    // Browsing onto any item (locked or not) updates the previews so
                                    // its design is visible; locked picks just won't save (trySave guard).
                                    onSnap = { i -> selectComponent(activeComp, list[i]) },
                                    onCenterChanged = { browsedIndex = it }
                                ) { index, cx, cy, r, _, isPressed, cellW, cellH ->
                                    val type = list[index]
                                    val renderer = rendererCache[type] ?: return@VerticalOptionCarousel
                                    // Every option sits on a raised square button kept the fixed
                                    // default-low blue (faded primary face + solid primary outline)
                                    // regardless of the color picker, so a ball whose body is the primary
                                    // blue isn't swallowed by its button. The ball itself still follows the
                                    // picker (drawn with `theme`). Unlocked → no glyph; locked → AdLock.
                                    val unlocked = bdIsUnlocked(activeComp, type)
                                    bdDrawLockedOption(
                                        cx, cy, cellW, cellH,
                                        faceColor = BD_BUTTON_FILL, pressed = isPressed,
                                        icon = if (unlocked) null else carouselLockPainter,
                                        iconAspectHW = BD_ADLOCK_ASPECT,
                                        faceStroke = BD_BUTTON_OUTLINE
                                    ) { bdDrawPart(renderer, theme, activeComp, cx, cy, r, isDark, drawShadow = false) }
                                }

                                // Status label for the browsed style — "Watch Ad To Own" when locked, else
                                // its name. Left-aligned (like the type controls) with an opaque rounded chip
                                // so the text stays readable as designs/ad-buttons scroll behind it. Colors
                                // are unified across dark/light: carousel bg fill + whole-control bg border.
                                val labelShape = RoundedCornerShape(8.dp)
                                androidx.compose.material3.Text(
                                    text = carouselLabel,
                                    color = fgColor,
                                    fontFamily = poppins,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Light,
                                    fontStyle = FontStyle.Italic,
                                    // Word-wrap to up to 3 lines so long labels ("Watch Ad To Own")
                                    // flex down instead of overflowing the carousel width.
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

            // Action pills — flush to the screen's right edge, vertically centered between the
            // unlock-progress bar (its bottom; or the preview top when the bar is hidden at 100%)
            // and the preview's locked-cosmetic glyph (top edge ~48dp above the preview bottom).
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
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EdgePill(
                    side = PillSide.End, color = accentBlue,
                    modifier = Modifier.height(52.dp).clickable(onClick = onNavigateToColor)
                ) {
                    Image(painterResource(Res.drawable.ic_menu_customize), null,
                        Modifier.size(28.dp), colorFilter = ColorFilter.tint(PaintBucket.white))
                }
                EdgePill(
                    side = PillSide.End, color = PaintBucket.menuAccentRed,
                    modifier = Modifier.height(52.dp).clickable(onClick = onBack)
                ) {
                    Image(painterResource(Res.drawable.ic_menu_check), null,
                        Modifier.size(28.dp), colorFilter = ColorFilter.tint(PaintBucket.white))
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
    val isDark = LocalDarkMode.current
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
            bdDrawPart(renderer, theme, component, size.width / 2f, size.height / 2f + Settings.ballRadius * 0.2f, Settings.ballRadius, isDark)
            // This piece needs an ad → mark it; the design shows but won't be used until unlocked.
            // Right-centred (inset) so the whole glyph stays inside the box, beside the ball.
            if (locked) {
                // Right inset matches the 10.dp gap between the two columns (the Row's spacedBy).
                val h = 24.dp.toPx(); val w = h * (89.37f / 106.46f); val pad = 10.dp.toPx()
                bdDrawAdLockGlyph(lockPainter, PaintBucket.menuAccentRed, size.width - pad - w / 2f, size.height / 2f, h)
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
                    bdDrawShadow(cx, cy, Settings.ballRadius, isDark)
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
        // Current design uses a locked piece → it won't save into this (selected) slot. Fade the
        // design toward the cell background so it reads as "won't update" (mirrors the CCP preset
        // slot's desaturated disabled look), then stamp the ad-lock glyph centred over the ball.
        if (showAdLock) Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(bg.copy(alpha = 0.6f))
            bdDrawAdLockGlyph(lockPainter, PaintBucket.menuAccentRed, size.width / 2f, size.height / 2f, size.minDimension * 0.4f)
        }
    }
}
