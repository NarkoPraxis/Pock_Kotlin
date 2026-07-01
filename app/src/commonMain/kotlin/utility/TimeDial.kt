package utility

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
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
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import physics.Ticker

/**
 * The mid-screen **time dial** — the match countdown rendered with the [ScoreDial]'s visual language
 * but mirrored to the **opposite** side edge and run "backwards".
 *
 * It is a half-disc parked at the vertical middle of the edge *across from* the score dial
 * ([Settings.scoreMenuSide]'s opposite), split by the screen midline into a top (high/warm) and a
 * bottom (low/cold) quarter-section — exactly like the score dial, so the two read as a matched pair
 * on the two side edges. Both sections show the **same** remaining time; the section colours and
 * numeral styling are pulled straight from the score dial.
 *
 * Two deliberate tweaks distinguish it from the score dial:
 *
 *  1. **Reversed ratchet.** On an update the old numeral spins *toward the centre line* (not out to
 *     the screen edge) and the new one ratchets in from the edge. The top and bottom sections animate
 *     **together**, so both old numerals converge on the midline and vanish into each other — the
 *     numbers look "sucked" into the centre.
 *  2. **Coarse display cadence.** The match clock counts down once per second internally, but the
 *     dial only re-reads (and therefore only spins) once every [BUCKET_SECONDS] seconds, so it isn't
 *     in a perpetual spin.
 *
 * Hidden when the time limit is Infinite ([Settings.timeLimitMinutes] == 0), before the timer starts,
 * once it has expired, during ball selection, and in demo mode.
 */
object TimeDial {

    // Geometry constants mirror ScoreDial so the two dials match.
    private const val DIAL_RADIUS_RATIO = 3.2f
    private const val NUMERAL_RADIUS_FACTOR = 0.55f
    // Numerals here can be three digits (e.g. "600"), so the time font is a fraction of the score
    // font, which is sized to a one/two-digit score.
    private const val FONT_SCALE = 0.55f
    private const val SPIN_FRAMES = 60
    // Snappier spin used once the clock enters the per-second suspense stretch, so each of the final
    // seconds gets a crisp tick instead of a full slow ~1s spin that would never come to rest.
    private const val FINE_SPIN_FRAMES = 24
    // Fraction of the spin spent sucking the old numeral into (and now past) the centre line; the rest
    // ratchets the new numeral in from the screen edge. The suck-in gets the larger share — it's the
    // focal motion.
    private const val SPIN_SUCK_FRACTION = 0.45f
    private const val COLOR_FADE_STEP = 1f / 16f
    // How many real seconds each *coarse* displayed step covers. Above the fine threshold the dial only
    // updates (and spins) when the remaining time crosses one of these boundaries.
    private const val BUCKET_SECONDS = 10
    // The final stretch: at or below this many seconds the dial abandons the coarse bucket cadence and
    // counts down one second at a time to build suspense (…10, 9, 8 … 1, 0).
    private const val FINE_THRESHOLD_SECONDS = 10
    // A hair of extra travel past the exact vanishing point so the sucked-in numeral is unambiguously
    // fully clipped by the midline before the new numeral arrives.
    private const val CROSS_MARGIN = 1.12f
    // Big centred "0" shown at expiry in a close game: font size as a multiple of the score font (so
    // it's clearly larger than the section numerals), and its centre as a fraction of the disc radius
    // in from the screen edge (roughly the half-disc's centroid).
    private const val BIG_ZERO_FONT_SCALE = 1.1f
    private const val BIG_ZERO_CENTER_FACTOR = 0.5f
    // The close-game final-zero animation: the last small numeral is sucked out through the centre,
    // then the big 0 slides in from off the screen edge and settles. One ticker drives both beats;
    // FINAL_ZERO_SUCK_FRACTION is the share spent sucking the old numeral out before the slide begins.
    private const val FINAL_ZERO_FRAMES = 34
    private const val FINAL_ZERO_SUCK_FRACTION = 0.4f

    private var measurer: TextMeasurer? = null
    private var fontFamily: FontFamily? = null

    /** Called once from GameScreen (composable scope) so the dial can measure/style its numerals. */
    fun initialize(textMeasurer: TextMeasurer, family: FontFamily) {
        measurer = textMeasurer
        fontFamily = family
        timeStyle = null; timeStyleKey = Float.NaN
        highLayout = null; highLayoutKey = null
        lowLayout = null; lowLayoutKey = null
        bigZeroStyle = null; bigZeroStyleKey = Float.NaN; bigZeroLayout = null
        finalZeroPhase = FinalZeroPhase.None
        builtSide = null
    }

    /** The edge opposite the score dial. */
    private val side: ScoreMenuSide
        get() = if (Settings.scoreMenuSide == ScoreMenuSide.Left) ScoreMenuSide.Right else ScoreMenuSide.Left

    // -------------------------------------------------------------------------
    // Cached geometry (rebuilt only on screen-size or side change)
    // -------------------------------------------------------------------------

    private var builtSide: ScoreMenuSide? = null
    private var builtWidth = -1f
    private var builtHeight = -1f
    private var builtRatio = -1f

    private var discCenter = Offset.Zero
    private var radius = 0f

    private val highSectionPath = Path()
    private val lowSectionPath = Path()

    private var highNumPos = Offset.Zero
    private var lowNumPos = Offset.Zero
    private var numRadius = 0f

    // Per-section sweep angles (degrees, y-down). rest = numeral's resting bisector; inner = the
    // midline both old numerals get sucked toward; outer = the screen-edge extreme the new numeral
    // ratchets in from. (ScoreDial uses the same three angles but swaps inner/outer roles.)
    private var highRestAngle = 0f; private var highOuterAngle = 0f; private var highInnerAngle = 0f
    private var lowRestAngle = 0f;  private var lowOuterAngle = 0f;  private var lowInnerAngle = 0f

    private fun ensureGeometry() {
        val s = side
        val w = Settings.screenWidth
        val h = Settings.screenHeight
        val ratio = Settings.screenRatio
        if (s == builtSide && w == builtWidth && h == builtHeight && ratio == builtRatio) return
        builtSide = s; builtWidth = w; builtHeight = h; builtRatio = ratio

        val isLeft = s == ScoreMenuSide.Left
        val cx = if (isLeft) 0f else w
        val cy = Settings.middleY
        discCenter = Offset(cx, cy)
        radius = ratio * DIAL_RADIUS_RATIO
        val rect = Rect(cx - radius, cy - radius, cx + radius, cy + radius)

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
    // Displayed value + (synchronized) spin state
    // -------------------------------------------------------------------------

    // The currently shown step value (always a multiple of BUCKET_SECONDS) and the one we're spinning
    // away from. Both sections share these — the whole dial updates as one unit.
    private var displayedValue = 0
    private var oldValue = 0
    private var initialized = false
    private var spinning = false
    private val spinTicker = Ticker(SPIN_FRAMES, accending = true)
    private var highColorFade = 0f
    private var lowColorFade = 0f

    /**
     * The value the dial shows for a given remaining-seconds reading. Above [FINE_THRESHOLD_SECONDS] it
     * snaps up to the next coarse [BUCKET_SECONDS] step (…40, 30, 20) so the dial isn't in a perpetual
     * spin; at or below the threshold it shows the exact second (…10, 9, 8 … 0) for the per-second
     * suspense countdown.
     */
    private fun displayValueFor(seconds: Int): Int =
        if (seconds > FINE_THRESHOLD_SECONDS)
            ((seconds + BUCKET_SECONDS - 1) / BUCKET_SECONDS) * BUCKET_SECONDS
        else
            seconds.coerceAtLeast(0)

    /** Snap the dial to the live remaining time with no animation (new match / reset). */
    fun syncFromTimer() {
        displayedValue = displayValueFor(Logic.timerSecondsRemaining)
        oldValue = displayedValue
        initialized = true
        spinning = false
        finalZeroPhase = FinalZeroPhase.None
        highColorFade = 0f
        lowColorFade = 0f
    }

    /**
     * Close-game expiry: the countdown has just reached 0 while the score is within one. Suck the last
     * small numeral out through the centre and slide the big 0 in — driven now, the instant the clock
     * hits 0, so nothing small ratchets in first. Called once from [Logic.updateTimer]. Idempotent.
     */
    fun beginFinalZero() {
        if (finalZeroPhase != FinalZeroPhase.None) return
        finalZeroSuckValue = if (displayedValue != 0) displayedValue else oldValue
        displayedValue = 0
        spinning = false
        finalZeroTicker.reset(FINAL_ZERO_FRAMES)
        finalZeroPhase = FinalZeroPhase.Running
    }

    /**
     * Feed the dial the latest internal countdown ([Logic.timerSecondsRemaining]). When the displayed
     * value changes, both sections start a single synchronized spin toward the new value. Called once
     * per frame from [Logic.updateTimer].
     */
    fun update(secondsRemaining: Int) {
        val target = displayValueFor(secondsRemaining)
        if (!initialized) {                 // first read — snap, don't spin
            initialized = true
            displayedValue = target
            oldValue = target
            return
        }
        if (target == displayedValue) return
        oldValue = displayedValue
        displayedValue = target
        // Crisp per-second ticks in the final stretch; the slower spin for the coarse buckets before it.
        spinTicker.reset(if (target <= FINE_THRESHOLD_SECONDS) FINE_SPIN_FRAMES else SPIN_FRAMES)
        spinning = true
    }

    // -------------------------------------------------------------------------
    // Numeral text caches
    // -------------------------------------------------------------------------

    private var timeStyle: TextStyle? = null
    private var timeStyleKey = Float.NaN
    private var highLayout: TextLayoutResult? = null
    private var highLayoutKey: String? = null
    private var lowLayout: TextLayoutResult? = null
    private var lowLayoutKey: String? = null

    // The large centred "0" shown at close-game expiry (its own bigger style + cached layout).
    private var bigZeroStyle: TextStyle? = null
    private var bigZeroStyleKey = Float.NaN
    private var bigZeroLayout: TextLayoutResult? = null

    // Close-game final-zero sequence. Running spans two beats off one ticker: the last small numeral
    // sucks out through the centre (first FINAL_ZERO_SUCK_FRACTION), then the big 0 slides in from off
    // the screen edge. Rest = the big 0 parked at the centre until the point resolves.
    private enum class FinalZeroPhase { None, Running, Rest }
    private var finalZeroPhase = FinalZeroPhase.None
    private var finalZeroSuckValue = 0
    private val finalZeroTicker = Ticker(FINAL_ZERO_FRAMES, accending = true)

    // -------------------------------------------------------------------------
    // Draw
    // -------------------------------------------------------------------------

    fun DrawScope.drawTimeDial() {
        if (Settings.isDemoMode) return
        if (Settings.timeLimitMinutes == 0) return                 // Infinite — no dial
        if (!Logic.timerStarted) return
        if (Settings.gameState == GameState.BallSelection) return
        if (measurer == null) return

        ensureGeometry()

        if (Logic.timerShowFinalZero) {
            // Close game: the clock reached 0 but the point plays on. The last numeral sucks out and a
            // large 0 slides in from off screen, then parks centred. Drawn only during live Play — it's
            // hidden through the score/game-over cinematic.
            if (Settings.gameState == GameState.Play) {
                ensureStyle(digitsOf(finalZeroSuckValue))
                ensureBigZeroStyle()
                drawFinalZeroSequence()
            }
            return
        }
        // A decided game (winner already clear) hid the dial at expiry.
        if (Logic.timerExpired) return

        // Shrink the numerals once the value runs past 3 digits (e.g. ≥ 1000s) so they keep fitting.
        ensureStyle(maxOf(digitsOf(displayedValue), digitsOf(oldValue)))

        drawSection(isHigh = true)
        drawSection(isHigh = false)

        if (spinning && spinTicker.tick) spinning = false
    }

    /** Base-10 digit count of a non-negative-ish value, without allocating a String each frame. */
    private fun digitsOf(value: Int): Int {
        var n = if (value < 0) -value else value
        var d = 1
        while (n >= 10) { n /= 10; d++ }
        return d
    }

    private fun DrawScope.ensureStyle(digits: Int) {
        val scale = if (digits > 3) 3f / digits else 1f
        val fontSizeSp = (PaintBucket.scoreFontSize * FONT_SCALE * scale) / drawContext.density.density
        if (timeStyle != null && timeStyleKey == fontSizeSp) return
        timeStyle = TextStyle(
            fontSize = TextUnit(fontSizeSp, TextUnitType.Sp),
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            color = PaintBucket.white
        )
        timeStyleKey = fontSizeSp
        highLayout = null; lowLayout = null
    }

    private fun DrawScope.drawSection(isHigh: Boolean) {
        val ratio = if (spinning) spinTicker.ratio.coerceIn(0f, 1f) else 0f

        // Section fill matches the score dial: rest colour, easing to the "updating" colour during a
        // spin. Both are dark-mode-aware (see PaintBucket.dialRestColor / dialActiveColor).
        val fade = advanceColorFade(isHigh, target = if (spinning) 1f else 0f)
        val fill = lerpColor(sectionPrimary(isHigh), sectionSecondary(isHigh), fade)
        val sectionPath = if (isHigh) highSectionPath else lowSectionPath
        drawPath(sectionPath, color = fill)

        val restAngle = if (isHigh) highRestAngle else lowRestAngle

        // Which numeral shows this frame: the old one while it's being sucked in, the new one once it
        // ratchets back in from the edge.
        val shownValue = when {
            !spinning -> displayedValue
            ratio < SPIN_SUCK_FRACTION -> oldValue
            else -> displayedValue
        }

        // The numeral slides along the arc but stays square: upright for the low (bottom) player,
        // flipped 180° for the high (top) player so each reads it right-side-up. No tilt.
        val layout = layoutFor(isHigh, shownValue) ?: return

        // Two beats: the old numeral is sucked *past* the centre line — far enough that the section
        // clip (below) has fully swallowed it, so both old numerals appear to merge into the midline and
        // shrink to nothing — then the new numeral ratchets in from the screen edge and settles like a
        // gear clicking into place.
        val posAngle: Float = when {
            !spinning -> restAngle
            ratio < SPIN_SUCK_FRACTION -> {
                val t = ratio / SPIN_SUCK_FRACTION
                lerp(restAngle, crossAngleFor(isHigh, layout.size.height / 2f), easeIn(t))
            }
            else -> {
                val t = (ratio - SPIN_SUCK_FRACTION) / (1f - SPIN_SUCK_FRACTION)
                lerp(if (isHigh) highOuterAngle else lowOuterAngle, restAngle, ratchet(t))
            }
        }

        val pos = polar(discCenter.x, discCenter.y, numRadius, posAngle)
        val topLeft = Offset(pos.x - layout.size.width / 2f, pos.y - layout.size.height / 2f)

        // While spinning, mask the numeral to this section's quadrant so the two old numbers slide
        // into the centre line and "merge" there (clipped exactly at the midline) rather than fading.
        // At rest the full number is drawn unclipped so it is never cut off.
        if (spinning) {
            clipPath(sectionPath) { drawNumeral(isHigh, layout, pos, topLeft) }
        } else {
            drawNumeral(isHigh, layout, pos, topLeft)
        }
    }

    private fun DrawScope.drawNumeral(isHigh: Boolean, layout: TextLayoutResult, pivot: Offset, topLeft: Offset) {
        if (isHigh) {
            rotate(180f, pivot) { drawText(layout, color = PaintBucket.white, topLeft = topLeft) }
        } else {
            drawText(layout, color = PaintBucket.white, topLeft = topLeft)
        }
    }

    /**
     * The suck-in target angle for a section: the midline bisector, plus just enough extra travel — in
     * the direction that carries the numeral into the *opposite* section — for a numeral of the given
     * half-height to be entirely clipped past the centre line. Derived from the arc geometry:
     * `sin(delta) = halfHeight / numRadius` (times a small margin), clamped so a tall numeral simply
     * sweeps to the far edge of its quadrant.
     */
    private fun crossAngleFor(isHigh: Boolean, halfHeight: Float): Float {
        val innerAngle = if (isHigh) highInnerAngle else lowInnerAngle
        val isLeft = side == ScoreMenuSide.Left
        val crossDir = if (isLeft == isHigh) 1f else -1f
        val sinArg = ((halfHeight * CROSS_MARGIN) / numRadius).coerceIn(0f, 1f)
        val deltaDeg = asin(sinArg) * (180f / PI.toFloat())
        return innerAngle + crossDir * deltaDeg
    }

    private fun DrawScope.ensureBigZeroStyle() {
        val fontSizeSp = (PaintBucket.scoreFontSize * BIG_ZERO_FONT_SCALE) / drawContext.density.density
        if (bigZeroStyle != null && bigZeroStyleKey == fontSizeSp) return
        bigZeroStyle = TextStyle(
            fontSize = TextUnit(fontSizeSp, TextUnitType.Sp),
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            color = PaintBucket.white
        )
        bigZeroStyleKey = fontSizeSp
        bigZeroLayout = null
    }

    /**
     * The close-game expiry sequence: both quarter-sections stay filled while the last small numeral
     * sucks out through the centre, then the big "0" slides in from off the screen edge and parks.
     */
    private fun DrawScope.drawFinalZeroSequence() {
        // Section fills stay put behind the whole sequence.
        drawPath(highSectionPath, color = sectionPrimary(true))
        drawPath(lowSectionPath, color = sectionPrimary(false))

        if (finalZeroPhase == FinalZeroPhase.Rest) {
            drawBigZero(slideProgress = 1f)
            return
        }

        val t = finalZeroTicker.ratio.coerceIn(0f, 1f)
        if (t < FINAL_ZERO_SUCK_FRACTION) {
            // Beat one: suck the last small numeral out through the midline (nothing ratchets in).
            val st = t / FINAL_ZERO_SUCK_FRACTION
            drawSuckingNumeral(isHigh = true, value = finalZeroSuckValue, progress = st)
            drawSuckingNumeral(isHigh = false, value = finalZeroSuckValue, progress = st)
        } else {
            // Beat two: the big 0 slides in from off the screen edge and settles with a mechanical click.
            val slideT = (t - FINAL_ZERO_SUCK_FRACTION) / (1f - FINAL_ZERO_SUCK_FRACTION)
            drawBigZero(slideProgress = slideT)
        }

        if (finalZeroTicker.tick) finalZeroPhase = FinalZeroPhase.Rest
    }

    /** One section's small numeral sliding from rest out *past* the midline (clipped away there), the
     *  same suck used by a normal tick — but here it exits with nothing following it. */
    private fun DrawScope.drawSuckingNumeral(isHigh: Boolean, value: Int, progress: Float) {
        val layout = layoutFor(isHigh, value) ?: return
        val restAngle = if (isHigh) highRestAngle else lowRestAngle
        val posAngle = lerp(restAngle, crossAngleFor(isHigh, layout.size.height / 2f), easeIn(progress))
        val pos = polar(discCenter.x, discCenter.y, numRadius, posAngle)
        val topLeft = Offset(pos.x - layout.size.width / 2f, pos.y - layout.size.height / 2f)
        val sectionPath = if (isHigh) highSectionPath else lowSectionPath
        clipPath(sectionPath) { drawNumeral(isHigh, layout, pos, topLeft) }
    }

    /**
     * The big centred "0", drawn once (not mirrored) — "0" is vertically symmetric, so it reads
     * right-side-up for both players. [slideProgress] 0→1 carries it from off the screen edge to its
     * resting centre with a ratcheting settle; 1f draws it parked.
     */
    private fun DrawScope.drawBigZero(slideProgress: Float) {
        val tm = measurer ?: return
        val style = bigZeroStyle ?: return
        if (bigZeroLayout == null) bigZeroLayout = tm.measure("0", style)
        val layout = bigZeroLayout ?: return

        val isLeft = side == ScoreMenuSide.Left
        val restCx = if (isLeft) radius * BIG_ZERO_CENTER_FACTOR
                     else Settings.screenWidth - radius * BIG_ZERO_CENTER_FACTOR
        // Start fully off the screen edge so it slides in from nothing (canvas clips the off-screen part).
        val startCx = if (isLeft) -layout.size.width.toFloat()
                      else Settings.screenWidth + layout.size.width
        val cx = lerp(startCx, restCx, ratchet(slideProgress.coerceIn(0f, 1f)))
        val cy = Settings.middleY
        val topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f)
        drawText(layout, color = PaintBucket.white, topLeft = topLeft)
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

    // Shared dark-mode-aware section colours (identical to ScoreDial's, so the two panels match).
    private fun sectionPrimary(isHigh: Boolean): Color = PaintBucket.dialRestColor(isHigh)

    private fun sectionSecondary(isHigh: Boolean): Color = PaintBucket.dialActiveColor(isHigh)

    private fun layoutFor(isHigh: Boolean, value: Int): TextLayoutResult? {
        val tm = measurer ?: return null
        val style = timeStyle ?: return null
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

    /** Quadratic ease-in — the old numeral hangs a beat, then accelerates into the centre. */
    private fun easeIn(t: Float): Float = t * t

    /** Damped-oscillation "ratchet" for the new numeral settling in (see ScoreDial.ratchet). */
    private fun ratchet(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        val omega = 3.5f * PI.toFloat()
        val decay = 4.2f
        return 1f - exp(-decay * t) * cos(omega * t)
    }
}
