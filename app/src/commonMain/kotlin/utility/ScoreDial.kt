package utility

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import enums.GameState
import enums.ScoreMenuSide
import gameobjects.Settings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import physics.Ticker

/**
 * The mid-screen **score dial** (Score Redesign, Plan 1).
 *
 * A half-disc parked at the vertical middle of one side edge, split by the screen midline into two
 * quarter-sections: the **top** quarter shows the high (warm) player's score, the **bottom** quarter
 * the low (cold) player's. Each section fills with that player's `theme.main.primary`
 * (`PaintBucket.highBallFill` / `lowBallFill`) at rest, flipping to `secondary` (the stroke colour)
 * while its number is updating. The white numerals are rotated ±45° so their baseline faces the
 * outer screen edge.
 *
 * On a score the old numeral spins **out** toward the screen edge along the arc and the new one spins
 * **in** from the midline (both around the disc centre). Plan 1 fires this spin the moment the score
 * commits; Plan 3 will move the trigger to the paddle's arrival.
 *
 * Performance: the section [Path]s and numeral geometry are cached and rebuilt only when the screen
 * dimensions or the chosen side change; the [TextLayoutResult]s are cached per displayed digit. Idle
 * frames (steady score) allocate nothing on the heap (Compose `Offset`/`Color` are free value types).
 */
object ScoreDial {

    // Disc radius as a multiple of screenRatio — each quarter ends up roughly a goal-zone tall.
    private const val DIAL_RADIUS_RATIO = 3.2f
    // Numeral centre distance from the disc centre, as a fraction of the radius.
    private const val NUMERAL_RADIUS_FACTOR = 0.55f
    // Frames a score spin (old out + new in) runs over. Slow enough (~1s at 60fps) that the old
    // numeral's exit and the new numeral's mechanical ratchet-in both read clearly.
    private const val SPIN_FRAMES = 60
    // Fraction of the spin spent flinging the old numeral out; the rest is the new numeral ratcheting
    // into place. The settle gets the larger share since it's the focal motion.
    private const val SPIN_OUT_FRACTION = 0.4f
    // Per-frame step for the section's primary↔secondary colour cross-fade — a 16-frame fade each way.
    private const val COLOR_FADE_STEP = 1f / 16f

    private var measurer: TextMeasurer? = null
    private var fontFamily: FontFamily? = null

    /** Called once from GameScreen (composable scope) so the dial can measure/style its numerals. */
    fun initialize(textMeasurer: TextMeasurer, family: FontFamily) {
        measurer = textMeasurer
        fontFamily = family
        // Force a re-measure/restyle against the (possibly new) screen on the next frame.
        scoreStyle = null; scoreStyleKey = Float.NaN
        highLayout = null; highLayoutKey = null
        lowLayout = null; lowLayoutKey = null
        builtSide = null
    }

    // -------------------------------------------------------------------------
    // Cached geometry (rebuilt only on screen-size or side change)
    // -------------------------------------------------------------------------

    private var builtSide: ScoreMenuSide? = null
    private var builtWidth = -1f
    private var builtHeight = -1f
    private var builtRatio = -1f

    private var discCenter = Offset.Zero
    var radius = 0f
        private set

    private val highSectionPath = Path()
    private val lowSectionPath = Path()

    private var highNumPos = Offset.Zero
    private var lowNumPos = Offset.Zero
    private var numRadius = 0f

    // Per-section sweep angles (degrees, y-down): rest = numeral's resting bisector; outer = the
    // screen-edge extreme the old numeral exits toward; inner = the midline the new numeral enters
    // from. baseRot (the numeral's own rotation) equals restAngle.
    private var highRestAngle = 0f; private var highOuterAngle = 0f; private var highInnerAngle = 0f
    private var lowRestAngle = 0f;  private var lowOuterAngle = 0f;  private var lowInnerAngle = 0f

    private fun ensureGeometry() {
        val side = Settings.scoreMenuSide
        val w = Settings.screenWidth
        val h = Settings.screenHeight
        val ratio = Settings.screenRatio
        if (side == builtSide && w == builtWidth && h == builtHeight && ratio == builtRatio) return
        builtSide = side; builtWidth = w; builtHeight = h; builtRatio = ratio

        val isLeft = side == ScoreMenuSide.Left
        val cx = if (isLeft) 0f else w
        val cy = Settings.middleY
        discCenter = Offset(cx, cy)
        radius = ratio * DIAL_RADIUS_RATIO
        val rect = Rect(cx - radius, cy - radius, cx + radius, cy + radius)

        // The visible half is the side facing the play area. Each pie wedge runs from the disc centre
        // out along a 90° arc. Left: top = [-90°..0°], bottom = [0°..90°]. Right mirrors to the left
        // half: top = [180°..270°], bottom = [90°..180°].
        highSectionPath.rewind()
        highSectionPath.moveTo(cx, cy)
        highSectionPath.arcTo(rect, if (isLeft) -90f else 180f, 90f, false)
        highSectionPath.close()

        lowSectionPath.rewind()
        lowSectionPath.moveTo(cx, cy)
        lowSectionPath.arcTo(rect, if (isLeft) 0f else 90f, 90f, false)
        lowSectionPath.close()

        if (isLeft) {
            highRestAngle = -45f; highOuterAngle = -90f; highInnerAngle = 0f
            lowRestAngle = 45f;   lowOuterAngle = 90f;   lowInnerAngle = 0f
        } else {
            highRestAngle = 225f; highOuterAngle = 270f; highInnerAngle = 180f
            lowRestAngle = 135f;  lowOuterAngle = 90f;   lowInnerAngle = 180f
        }

        numRadius = radius * NUMERAL_RADIUS_FACTOR
        highNumPos = polar(cx, cy, numRadius, highRestAngle)
        lowNumPos = polar(cx, cy, numRadius, lowRestAngle)
    }

    private fun polar(cx: Float, cy: Float, r: Float, deg: Float): Offset {
        val rad = deg * (PI.toFloat() / 180f)
        return Offset(cx + r * cos(rad), cy + r * sin(rad))
    }

    // -------------------------------------------------------------------------
    // Displayed score + spin state (one pair of fields per section)
    // -------------------------------------------------------------------------

    private var highDisplayed = 0
    private var lowDisplayed = 0
    private var highOldValue = 0
    private var lowOldValue = 0
    private var highSpinning = false
    private var lowSpinning = false
    // "Pending" means a score has committed for this section but the number hasn't spun yet (the toss
    // is still in flight). The section already shows its secondary "updating" colour during this gap,
    // so the colour flip lands the instant the score happens — not when the toss/spin begins.
    private var highScorePending = false
    private var lowScorePending = false
    // Current primary→secondary blend per section (0 = primary at rest, 1 = secondary while updating).
    // Eased toward its target each frame so the colour change reads as a fade, not a snap.
    private var highColorFade = 0f
    private var lowColorFade = 0f
    private val highSpinTicker = Ticker(SPIN_FRAMES, accending = true)
    private val lowSpinTicker = Ticker(SPIN_FRAMES, accending = true)

    /** Snap both displayed scores to the live player scores with no animation (new game / reset). */
    fun syncFromPlayers() {
        highDisplayed = Logic.highPlayer.score
        lowDisplayed = Logic.lowPlayer.score
        highOldValue = highDisplayed
        lowOldValue = lowDisplayed
        highSpinning = false
        lowSpinning = false
        highScorePending = false
        lowScorePending = false
        highColorFade = 0f
        lowColorFade = 0f
    }

    /**
     * Flag that a score just committed for one section so it flips to its secondary "updating" colour
     * immediately — before the paddle toss arrives and the number actually spins. Cleared when the
     * subsequent spin finishes (or by [syncFromPlayers]).
     */
    fun markScorePending(isHigh: Boolean) {
        if (isHigh) highScorePending = true else lowScorePending = true
    }

    /**
     * Start the out→in spin for one section toward [newValue] (the already-incremented logical
     * score). Plan 1 calls this the instant the score commits; Plan 3 moves the call to the paddle's
     * arrival on the dial.
     */
    fun triggerScoreSpin(isHigh: Boolean, newValue: Int) {
        if (isHigh) {
            if (newValue == highDisplayed && !highSpinning) return
            highOldValue = highDisplayed
            highDisplayed = newValue
            highSpinTicker.reset(SPIN_FRAMES)
            highSpinning = true
        } else {
            if (newValue == lowDisplayed && !lowSpinning) return
            lowOldValue = lowDisplayed
            lowDisplayed = newValue
            lowSpinTicker.reset(SPIN_FRAMES)
            lowSpinning = true
        }
    }

    /** True while either section is mid-spin — used by Plan 3's resume gate. */
    val isSpinning: Boolean get() = highSpinning || lowSpinning

    // -------------------------------------------------------------------------
    // Numeral text caches
    // -------------------------------------------------------------------------

    private var scoreStyle: TextStyle? = null
    private var scoreStyleKey = Float.NaN
    private var highLayout: TextLayoutResult? = null
    private var highLayoutKey: String? = null
    private var lowLayout: TextLayoutResult? = null
    private var lowLayoutKey: String? = null

    // -------------------------------------------------------------------------
    // Draw
    // -------------------------------------------------------------------------

    /**
     * Whether the dial belongs in the **lifted** HUD layer this frame (above the score cinematic's
     * dim wash) rather than the normal behind-the-pucks pass. drawFrame calls drawScoreDial twice —
     * once per layer — and each call only renders when [lifted] matches this, so the dial draws
     * exactly once per frame.
     */
    fun shouldLift(): Boolean = Logic.scoreCinematicActive

    fun DrawScope.drawScoreDial(lifted: Boolean) {
        if (Settings.isDemoMode) return
        // Hide the score entirely during ball selection (the goals/popups own the screen then).
        if (Settings.gameState == GameState.BallSelection) return
        // While the pause menu is open/animating, the menu pass owns the dial (drawn top-most).
        if (menuActive) return
        if (measurer == null) return
        if (lifted != shouldLift()) return

        ensureGeometry()
        ensureStyle()

        // Layer order within the dial: section backgrounds → tossed paddle + its landing burst →
        // numerals. So a paddle flying into a number passes in front of the disc face but behind the
        // digit it feeds (same for the celebration burst it spawns on arrival). Both follow whichever
        // pass (lifted/normal) the dial is in, since they draw from inside this single render pass.
        drawSectionBackground(isHigh = true)
        drawSectionBackground(isHigh = false)

        with(Logic.tossHigh) { draw() }
        with(Logic.tossLow) { draw() }
        with(Effects) { drawScoreEffects() }

        drawSectionNumeral(isHigh = true)
        drawSectionNumeral(isHigh = false)

        // Advance the spin tickers once per frame (only the rendering pass runs this). The number has
        // landed when the spin ends, so the section drops back to its primary colour here.
        if (highSpinning && highSpinTicker.tick) { highSpinning = false; highScorePending = false }
        if (lowSpinning && lowSpinTicker.tick) { lowSpinning = false; lowScorePending = false }
    }

    private fun DrawScope.ensureStyle() {
        val fontSizeSp = PaintBucket.scoreFontSize / drawContext.density.density
        if (scoreStyle != null && scoreStyleKey == fontSizeSp) return
        scoreStyle = TextStyle(
            fontSize = TextUnit(fontSizeSp, TextUnitType.Sp),
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            color = PaintBucket.white
        )
        scoreStyleKey = fontSizeSp
        // Style changed → measured layouts are stale.
        highLayout = null; lowLayout = null
    }

    // The dial face for one section: the quarter-disc fill. Split out from the numeral so the tossed
    // paddle + landing burst can draw between the two (in front of the face, behind the digit). Also
    // advances the section's colour fade — call once per frame per section (the background pass does).
    private fun DrawScope.drawSectionBackground(isHigh: Boolean) {
        val spinning = if (isHigh) highSpinning else lowSpinning
        val pending = if (isHigh) highScorePending else lowScorePending

        // Section fill: primary at rest, secondary while this number is "updating" — which begins the
        // moment the score commits (pending) and runs through the end of the spin, not just the spin.
        // The blend is eased toward its target each frame so the change fades in/out over ~16 frames.
        val fade = advanceColorFade(isHigh, target = if (spinning || pending) 1f else 0f)
        val fill = lerpColor(sectionPrimary(isHigh), sectionSecondary(isHigh), fade)
        drawPath(if (isHigh) highSectionPath else lowSectionPath, color = fill)
    }

    // The numeral for one section, posed at its current arc position (rest, or mid-spin). Drawn after
    // the toss/burst layer so the digit is always the top-most part of the dial.
    private fun DrawScope.drawSectionNumeral(isHigh: Boolean) {
        val spinning = if (isHigh) highSpinning else lowSpinning
        val ratio = if (spinning) {
            (if (isHigh) highSpinTicker else lowSpinTicker).ratio.coerceIn(0f, 1f)
        } else 0f

        val restAngle = if (isHigh) highRestAngle else lowRestAngle

        // Which digit shows, and where along the arc it sits this frame. The spin is two beats: the old
        // numeral eases out to the screen edge (SPIN_OUT_FRACTION of the spin), then the new numeral
        // ratchets in from the midline — overshooting its rest spot and settling like a gear clicking
        // into place.
        val shownValue: Int
        val posAngle: Float
        when {
            !spinning -> { shownValue = if (isHigh) highDisplayed else lowDisplayed; posAngle = restAngle }
            ratio < SPIN_OUT_FRACTION -> {
                shownValue = if (isHigh) highOldValue else lowOldValue
                val t = ratio / SPIN_OUT_FRACTION
                posAngle = lerp(restAngle, if (isHigh) highOuterAngle else lowOuterAngle, easeIn(t))
            }
            else -> {
                shownValue = if (isHigh) highDisplayed else lowDisplayed
                val t = (ratio - SPIN_OUT_FRACTION) / (1f - SPIN_OUT_FRACTION)
                posAngle = lerp(if (isHigh) highInnerAngle else lowInnerAngle, restAngle, ratchet(t))
            }
        }

        // The numeral slides along the arc (its centre tracks posAngle) but keeps a square, readable
        // orientation: upright for the low (bottom) player, flipped 180° for the high (top) player so
        // each reads it right-side-up from their end. No ±45° tilt.
        val layout = layoutFor(isHigh, shownValue) ?: return
        val pos = polar(discCenter.x, discCenter.y, numRadius, posAngle)
        val topLeft = Offset(pos.x - layout.size.width / 2f, pos.y - layout.size.height / 2f)
        if (isHigh) {
            rotate(180f, pos) { drawText(layout, color = PaintBucket.white, topLeft = topLeft) }
        } else {
            drawText(layout, color = PaintBucket.white, topLeft = topLeft)
        }
    }

    /** Step the section's colour blend one frame toward [target] (0 or 1) and return the new value. */
    private fun advanceColorFade(isHigh: Boolean, target: Float): Float {
        val cur = if (isHigh) highColorFade else lowColorFade
        val next = when {
            cur < target -> (cur + COLOR_FADE_STEP).coerceAtMost(target)
            cur > target -> (cur - COLOR_FADE_STEP).coerceAtLeast(target)
            else -> cur
        }
        if (isHigh) highColorFade = next else lowColorFade = next
        return next
    }

    // Dark-mode-aware section colours, shared with TimeDial (light mode keeps theme.main
    // primary/secondary; dark mode lifts the touch-highlight tint slightly toward white).
    private fun sectionPrimary(isHigh: Boolean): Color = PaintBucket.dialRestColor(isHigh)

    private fun sectionSecondary(isHigh: Boolean): Color = PaintBucket.dialActiveColor(isHigh)

    private fun layoutFor(isHigh: Boolean, value: Int): TextLayoutResult? {
        val tm = measurer ?: return null
        val style = scoreStyle ?: return null
        val text = value.toString()
        return if (isHigh) {
            if (highLayout == null || highLayoutKey != text) {
                highLayout = tm.measure(text, style); highLayoutKey = text
            }
            highLayout
        } else {
            if (lowLayout == null || lowLayoutKey != text) {
                lowLayout = tm.measure(text, style); lowLayoutKey = text
            }
            lowLayout
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** Quadratic ease-in — the old numeral hangs a beat, then accelerates off the dial. */
    private fun easeIn(t: Float): Float = t * t

    /**
     * Damped-oscillation "ratchet" for the new numeral settling in. Eases toward the target, overshoots
     * past it, swings back, overshoots again (smaller), and lands exactly on the target at t=1 — the
     * mechanical, clock-gear feel. Returns values that briefly exceed 1f (the overshoot), which the
     * caller's lerp carries slightly past the rest angle toward the screen edge.
     */
    private fun ratchet(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        // omega = 3.5π → reach, over, under, over, settle within [0,1]; decay shrinks each swing.
        val omega = 3.5f * PI.toFloat()
        val decay = 4.2f
        return 1f - exp(-decay * t) * cos(omega * t)
    }

    // -------------------------------------------------------------------------
    // Queries (for Plan 3 — the paddle toss target)
    // -------------------------------------------------------------------------

    /** Screen centre of a section's resting numeral — the toss destination in Plan 3. */
    fun numberCenter(isHigh: Boolean): Offset {
        ensureGeometry()
        return if (isHigh) highNumPos else lowNumPos
    }

    // -------------------------------------------------------------------------
    // Pause menu (Score Redesign, Plan 2)
    //
    // A tap on the dial expands it into a vertical capsule: the two quarter-sections slide apart and
    // a white (dark-mode: dark) band carrying Return + Restart grows between them. Opening freezes the
    // sim immediately (Logic.paused); an off-menu tap collapses it and the sim resumes only once the
    // close animation completes. The expand/collapse is animated in the draw pass so it keeps moving
    // while the simulation is frozen.
    // -------------------------------------------------------------------------

    private const val MENU_EXPAND_FRAMES = 12
    // Half the open band's height, as a fraction of the disc radius (full band ≈ 1.8 × radius).
    private const val BAND_HALF_FULL_RATIO = 0.9f
    private const val ICON_SIZE_RATIO = 0.5f          // button glyph size, × radius
    // Button centres sit this fraction of the half-band above / below the screen midline.
    private const val BUTTON_OFFSET_RATIO = 0.45f

    private enum class MenuState { Closed, Opening, Open, Closing }
    private var menuState = MenuState.Closed
    private val menuTicker = Ticker(MENU_EXPAND_FRAMES, accending = true)

    /** Set by IosGameHost — the "Return to main menu" path (same as the edge-swipe back). */
    var returnCallback: (() -> Unit)? = null

    /** True while the menu is open or animating (the normal dial passes hand off to drawPauseMenu). */
    val menuActive: Boolean get() = menuState != MenuState.Closed
    val isMenuOpen: Boolean get() = menuState == MenuState.Open

    fun requestOpenMenu() {
        if (menuState != MenuState.Closed) return
        menuState = MenuState.Opening
        menuTicker.reset(MENU_EXPAND_FRAMES)
        Logic.paused = true
    }

    fun requestCloseMenu() {
        if (menuState == MenuState.Open || menuState == MenuState.Opening) {
            menuState = MenuState.Closing
            menuTicker.reset(MENU_EXPAND_FRAMES)
        }
    }

    /** Return button → leave to the main menu. Closes + resumes first so we never resume paused. */
    fun menuReturn() {
        menuState = MenuState.Closed
        Logic.paused = false
        returnCallback?.invoke()
    }

    /** Restart button → reset the match to ball-selection (where the dial is hidden), unfrozen. */
    fun menuRestart() {
        menuState = MenuState.Closed
        Logic.paused = false
        Logic.restartMatch()
    }

    private fun expandProgress(): Float = when (menuState) {
        MenuState.Closed -> 0f
        MenuState.Opening -> menuTicker.ratio.coerceIn(0f, 1f)
        MenuState.Open -> 1f
        MenuState.Closing -> (1f - menuTicker.ratio).coerceIn(0f, 1f)
    }

    private fun bandHalfFull(): Float = radius * BAND_HALF_FULL_RATIO
    private fun buttonCenterX(): Float =
        if (Settings.scoreMenuSide == ScoreMenuSide.Left) radius * 0.5f else Settings.screenWidth - radius * 0.5f
    private fun returnCenterY(): Float = Settings.middleY - bandHalfFull() * BUTTON_OFFSET_RATIO
    private fun restartCenterY(): Float = Settings.middleY + bandHalfFull() * BUTTON_OFFSET_RATIO

    // ---- Hit tests (consumed by Logic's touch routing) ----

    /** Whether (x,y) is inside the closed dial's half-disc — the tap-to-open region. */
    fun hitDial(x: Float, y: Float): Boolean {
        ensureGeometry()
        val dx = x - discCenter.x
        val dy = y - discCenter.y
        return dx * dx + dy * dy <= radius * radius
    }

    /** Whether (x,y) is anywhere within the open capsule (band + both domes). */
    fun hitMenu(x: Float, y: Float): Boolean {
        ensureGeometry()
        val isLeft = Settings.scoreMenuSide == ScoreMenuSide.Left
        val xIn = if (isLeft) x in 0f..radius else x in (Settings.screenWidth - radius)..Settings.screenWidth
        val half = bandHalfFull()
        val yIn = y in (Settings.middleY - half - radius)..(Settings.middleY + half + radius)
        return xIn && yIn
    }

    fun hitReturn(x: Float, y: Float): Boolean = hitButton(x, y, buttonCenterX(), returnCenterY())
    fun hitRestart(x: Float, y: Float): Boolean = hitButton(x, y, buttonCenterX(), restartCenterY())

    private fun hitButton(x: Float, y: Float, cx: Float, cy: Float): Boolean {
        ensureGeometry()
        val r = radius * ICON_SIZE_RATIO * 0.8f   // generous round hit target
        val dx = x - cx; val dy = y - cy
        return dx * dx + dy * dy <= r * r
    }

    // ---- Draw ----

    private var iconStroke: Stroke? = null
    private var iconStrokeWidth = -1f
    private fun iconStrokeFor(width: Float): Stroke {
        if (iconStroke == null || iconStrokeWidth != width) {
            iconStroke = Stroke(width = width, cap = StrokeCap.Round)
            iconStrokeWidth = width
        }
        return iconStroke!!
    }

    /** Top-most pass: the expanding/expanded pause menu. No-op unless the menu is active. */
    fun DrawScope.drawPauseMenu() {
        if (!menuActive) return
        if (Settings.isDemoMode || measurer == null) return
        ensureGeometry()
        ensureStyle()

        val p = expandProgress()
        val bandHalf = p * bandHalfFull()

        // The two quarter-sections, slid apart by the growing band. (No spin during pause, so the
        // numerals draw at rest — their fill stays primary.)
        translate(0f, -bandHalf) {
            drawPath(highSectionPath, color = sectionPrimary(true))
            drawRestNumeral(isHigh = true)
        }
        translate(0f, bandHalf) {
            drawPath(lowSectionPath, color = sectionPrimary(false))
            drawRestNumeral(isHigh = false)
        }

        // The band between them (themed for dark/light; scores stay white above).
        val isDark = Storage.darkMode
        val bandColor = if (isDark) PaintBucket.menuBackgroundDark else PaintBucket.menuBackgroundLight
        val isLeft = Settings.scoreMenuSide == ScoreMenuSide.Left
        if (bandHalf > 0f) {
            drawRect(
                color = bandColor,
                topLeft = Offset(if (isLeft) 0f else Settings.screenWidth - radius, Settings.middleY - bandHalf),
                size = Size(radius, bandHalf * 2f)
            )
        }

        // Buttons fade in with the expand and slide out with the band. Dark glyphs on the light band,
        // light glyphs on the dark.
        val iconColor = (if (isDark) PaintBucket.white else Color(0xFF222222)).copy(alpha = p)
        val iconSize = radius * ICON_SIZE_RATIO
        val cx = buttonCenterX()
        drawReturnIcon(cx, Settings.middleY - bandHalf * BUTTON_OFFSET_RATIO, iconSize, iconColor)
        drawRestartIcon(cx, Settings.middleY + bandHalf * BUTTON_OFFSET_RATIO, iconSize, iconColor)

        // Advance the open/close animation once per frame; resume the sim when fully closed.
        when (menuState) {
            MenuState.Opening -> if (menuTicker.tick) menuState = MenuState.Open
            MenuState.Closing -> if (menuTicker.tick) { menuState = MenuState.Closed; Logic.paused = false }
            else -> {}
        }
    }

    /** Draws a section's numeral at its resting position (used by the paused menu). Upright for the
     *  low player, flipped 180° for the high player — matching the live dial. */
    private fun DrawScope.drawRestNumeral(isHigh: Boolean) {
        val value = if (isHigh) highDisplayed else lowDisplayed
        val layout = layoutFor(isHigh, value) ?: return
        val restPos = if (isHigh) highNumPos else lowNumPos
        val topLeft = Offset(restPos.x - layout.size.width / 2f, restPos.y - layout.size.height / 2f)
        if (isHigh) {
            rotate(180f, restPos) { drawText(layout, color = PaintBucket.white, topLeft = topLeft) }
        } else {
            drawText(layout, color = PaintBucket.white, topLeft = topLeft)
        }
    }

    // Placeholder icons (drawn paths — swap these two functions for final vector assets later).

    /** Back arrow (←) — the Return button glyph. */
    private fun DrawScope.drawReturnIcon(cx: Float, cy: Float, size: Float, color: Color) {
        val s = size / 2f
        val w = size * 0.16f
        val stroke = w
        drawLine(color, Offset(cx + s, cy), Offset(cx - s * 0.4f, cy), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(cx - s * 0.4f, cy), Offset(cx + s * 0.05f, cy - s * 0.55f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(cx - s * 0.4f, cy), Offset(cx + s * 0.05f, cy + s * 0.55f), strokeWidth = stroke, cap = StrokeCap.Round)
    }

    /** Circular arrow (↻) — the Restart button glyph. */
    private fun DrawScope.drawRestartIcon(cx: Float, cy: Float, size: Float, color: Color) {
        val r = size * 0.42f
        val w = size * 0.16f
        drawArc(
            color = color,
            startAngle = 70f,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2f, r * 2f),
            style = iconStrokeFor(w)
        )
        // Arrowhead at the arc's start (70°), pointing roughly tangentially.
        val rad = 70f * (PI.toFloat() / 180f)
        val ex = cx + r * cos(rad)
        val ey = cy + r * sin(rad)
        val h = size * 0.22f
        drawLine(color, Offset(ex, ey), Offset(ex - h, ey - h * 0.2f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(ex, ey), Offset(ex + h * 0.2f, ey + h), strokeWidth = w, cap = StrokeCap.Round)
    }
}
