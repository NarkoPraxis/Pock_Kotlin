package gameobjects.puckstyle.tails

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import gameobjects.Settings
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class CatTail(override val renderer: PuckRenderer) : TailRenderer {

    // --- Spine geometry ------------------------------------------------------
    // Fewer, larger segments → shorter & wider tail that better matches the
    // SVG's body-to-tail proportions (tail length ~= body diameter).
    private val SEGMENT_COUNT = 10

    // Distance between segments expressed as a fraction of puck radius.
    private val SEGMENT_SPACING_K = 0.32f

    // How aggressively each segment chases the previous one (head → tip).
    private val BASE_FOLLOW = 0.22f
    private val TIP_FOLLOW = 0.12f

    // Max bend between two consecutive segments — keeps the spine smooth.
    private val MAX_ANGLE_DIFF = 28f * (PI.toFloat() / 180f)

    // --- Body width profile --------------------------------------------------
    // Width values are multiples of the puck radius. The cat tail in the SVG
    // is thick at the base, stays thick through the upper two thirds, then
    // bulges in the lower third where the fur tufts live.
    // ROOT_WIDTH_K is the very-base width; the tail tapers from ROOT to BASE
    // across the first ROOT_TAPER_END fraction of its length, then follows
    // the existing profile.
    private val ROOT_WIDTH_K = 0.70f
    private val BASE_WIDTH_K = 0.55f
    private val MID_WIDTH_K = 0.50f
    private val PRE_TUFT_WIDTH_K = 0.42f
    private val TIP_WIDTH_K = 0.30f
    private val ROOT_TAPER_END = 0.50f         // width hits BASE_WIDTH_K at this t

    // --- Fur tufts -----------------------------------------------------------
    // Each tuft is a POINTED fur clump (triangular flame shape) that LIES
    // ALONG the spine — its apex points TOWARD THE TIP of the tail, following
    // the spine's forward direction at that segment. As the tail curls, the
    // fur curls with it. A perpendicular outward nudge alternates left/right
    // per tuft so the fur visibly slants away from the tail edge while still
    // leaning toward the tip.
    //
    // Iteration 6: doubled to 16 tufts for denser fur. Tufts now lay flatter
    // against the tail edge (smaller perp offset) and have widened ±~45%
    // variation in both size and length, with neighbours intentionally not
    // following a regular alternating pattern.
    private val TUFT_COUNT = 16
    private val TUFT_FIRST_SEG_RATIO = 0.20f       // tufts start at 20% along the tail
    private val TUFT_LAST_SEG_RATIO = 0.92f        // last tuft anchor is at most ~92% along the tail
    private val TUFT_LENGTH_K = 0.22f              // baseline apex projection (kept short so even longest tuft stays close to tip)
    private val TUFT_BASE_HALFWIDTH_K = 0.14f      // baseline half-width of the tuft attachment (across the spine)
    private val TUFT_OFFSET_K = 0.10f              // perpendicular spine attachment offset
    private val TUFT_TIP_BONUS_K = 0.06f           // tip-side tufts reach a little further (tightened to respect 30% cap)
    // Slant: how much each tuft's apex is pushed outward (perpendicular to
    // the spine) compared to its forward projection. Smaller → fur lays
    // flatter along the tail edge instead of sticking out perpendicular.
    private val TUFT_APEX_PERP_OFFSET_K = 0.20f    // outward perpendicular shove of the apex (alternates per tuft)

    // Per-tuft deterministic size variation — each entry multiplies the
    // base half-width for that tuft index. Spread across ~±45% with
    // intentionally mixed ordering so neighbours never follow a regular
    // alternating pattern.
    private val TUFT_SIZE_MULTIPLIERS = floatArrayOf(
        0.70f, 1.35f, 0.95f, 1.45f, 0.60f, 1.15f, 0.85f, 1.40f,
        1.05f, 0.55f, 1.30f, 0.75f, 1.20f, 0.90f, 1.50f, 0.65f
    )
    // Per-tuft length multipliers, varied independently of width so size and
    // length are both visibly irregular between neighbours. Spread across
    // ~±45% with a different irregular ordering than the size multipliers.
    private val TUFT_LENGTH_MULTIPLIERS = floatArrayOf(
        1.20f, 0.65f, 1.40f, 0.80f, 1.05f, 1.45f, 0.55f, 1.15f,
        0.95f, 1.30f, 0.70f, 1.50f, 0.85f, 1.25f, 0.60f, 1.10f
    )
    // Per-tuft phase offset (radians) for the "lay along spine" oscillation.
    // Spread roughly evenly across [0, 2π) for a non-uniform breath pattern,
    // in a non-monotonic order so neighbouring tufts don't breathe in sync.
    private val TUFT_LAY_PHASE_OFFSETS = floatArrayOf(
        0.00f, 2.36f, 0.79f, 3.14f, 1.57f, 4.71f, 2.75f, 5.50f,
        0.39f, 3.53f, 1.18f, 4.32f, 1.96f, 5.10f, 3.93f, 5.89f
    )
    // How much each tuft's forward-projection magnitude oscillates over time.
    // Multiplies the per-tuft tuftLength to produce a gentle breathing motion.
    private val TUFT_LAY_OSC_SPEED = 0.06f         // radians per frame for the breath
    private val TUFT_LAY_OSC_AMPLITUDE = 0.18f     // ±18% of the apex length oscillation

    // Hard cap on how far past the tail's tip endpoint any tuft apex may
    // extend, expressed as a fraction of the tip-segment spacing.
    private val TUFT_MAX_OVERSHOOT_K = 0.30f       // ~30% of tip-segment length

    // --- Idle flick ----------------------------------------------------------
    // When the puck is at rest the tail performs a soft wagging motion with a
    // hint of curl. Iteration 4 adds a per-segment restoring spring so the
    // tail doesn't get pinned to one side, and drops CURL_BIAS_RAD to zero so
    // the wag passes cleanly through the neutral straight position.
    private val IDLE_FLICK_SPEED = 0.045f          // radians per frame phase advance
    private val IDLE_FLICK_AMPLITUDE = 0.18f       // peak per-segment angle offset (rad) — wag size
    private val CURL_BIAS_RAD = 0.00f              // removed steady curl bias — was pinning to one side
    private val CURL_PHASE_SHIFT = 0.70f           // radians of phase shift accumulated tip-ward (curl wave)
    private val MOTION_DAMP_THRESHOLD = 0.4f       // speed (in r/frame) above which flick fades
    // Spring constant pulling each segment's current angle back toward the
    // neutral (previous-segment) direction. Keeps wag centered.
    private val RESTORE_K = 0.35f

    // --- Persistent state ----------------------------------------------------
    private val spineX = FloatArray(SEGMENT_COUNT)
    private val spineY = FloatArray(SEGMENT_COUNT)
    private val segAngle = FloatArray(SEGMENT_COUNT)
    private var initialized = false

    private var flickPhase = 0f
    private var lastHeadX = 0f
    private var lastHeadY = 0f
    private var smoothedSpeed = 0f

    private val bodyPath = Path()
    private val tuftPath = Path()

    override val zIndex: Int get() = -1

    override fun render(scope: DrawScope) {
        val r = renderer.radius
        // Hoist color resolution out of every per-segment / per-tuft loop.
        val colors = responsiveGroup
        val primary = colors.primary
        val secondary = colors.secondary

        val spacing = r * SEGMENT_SPACING_K
        val headX = renderer.x
        val headY = renderer.y

        if (!initialized) {
            fillTo(headX, headY)
            initialized = true
            lastHeadX = headX
            lastHeadY = headY
        }

        // Track head motion to know whether the ball is "at rest".
        val moveDx = headX - lastHeadX
        val moveDy = headY - lastHeadY
        val instantSpeed = hypot(moveDx, moveDy) / r.coerceAtLeast(0.001f)
        // Low-pass filter the speed so direction reversals don't flash the flick.
        smoothedSpeed = smoothedSpeed * 0.85f + instantSpeed * 0.15f
        lastHeadX = headX
        lastHeadY = headY

        // Idle flick gain: 1.0 when stopped, 0.0 when moving briskly.
        val motionGain = 1f - (smoothedSpeed / MOTION_DAMP_THRESHOLD).coerceIn(0f, 1f)
        flickPhase += IDLE_FLICK_SPEED

        // --- Step 1: integrate the spine -------------------------------------
        // Each segment chases the previous one; clamp distance and max bend.
        var prevX = headX
        var prevY = headY
        var prevAngle = atan2(spineY[0] - headY, spineX[0] - headX)

        for (i in 0 until SEGMENT_COUNT) {
            val t = i.toFloat() / (SEGMENT_COUNT - 1)
            val follow = lerp(BASE_FOLLOW, TIP_FOLLOW, t)

            // Soft chase
            spineX[i] = lerp(spineX[i], prevX, follow)
            spineY[i] = lerp(spineY[i], prevY, follow)

            // Hard distance clamp to keep segment length constant
            val dx = spineX[i] - prevX
            val dy = spineY[i] - prevY
            val dist = hypot(dx, dy).coerceAtLeast(0.001f)
            spineX[i] = prevX + dx / dist * spacing
            spineY[i] = prevY + dy / dist * spacing

            // Max-angle clamp against previous segment direction (smooth curve)
            // Also capture the post-clamp deviation from "neutral" (the parent
            // segment's direction) so the idle flick can apply a restoring
            // spring toward the straight pose.
            var driftFromNeutral = 0f
            if (i > 0) {
                val curAngle = atan2(spineY[i] - prevY, spineX[i] - prevX)
                var angleDiff = curAngle - prevAngle
                while (angleDiff > PI) angleDiff -= 2f * PI.toFloat()
                while (angleDiff < -PI) angleDiff += 2f * PI.toFloat()
                if (angleDiff > MAX_ANGLE_DIFF) {
                    val clamped = prevAngle + MAX_ANGLE_DIFF
                    spineX[i] = prevX + cos(clamped) * spacing
                    spineY[i] = prevY + sin(clamped) * spacing
                    driftFromNeutral = MAX_ANGLE_DIFF
                } else if (angleDiff < -MAX_ANGLE_DIFF) {
                    val clamped = prevAngle - MAX_ANGLE_DIFF
                    spineX[i] = prevX + cos(clamped) * spacing
                    spineY[i] = prevY + sin(clamped) * spacing
                    driftFromNeutral = -MAX_ANGLE_DIFF
                } else {
                    driftFromNeutral = angleDiff
                }
            }

            // --- Step 2: apply idle flick as a curling wave -----------------
            // Three contributions, all weighted by t so the head stays put:
            //   1. Oscillating wag (sin) with a phase shift that grows along
            //      the spine — this creates a traveling curl wave rather than
            //      a rigid metronome swing.
            //   2. A steady CURL_BIAS (now 0) that used to push the tail into
            //      a consistent coiled direction — disabled because it pinned
            //      the wag to one side.
            //   3. A restoring spring proportional to the segment's current
            //      drift from neutral; this prevents the tail from getting
            //      stuck fully curled and pulls every cycle back through the
            //      straight pose.
            // All fade with motionGain so a moving puck shows a clean trail.
            if (motionGain > 0f) {
                val wagAngle = sin(flickPhase + t * CURL_PHASE_SHIFT) * IDLE_FLICK_AMPLITUDE
                val biasAngle = CURL_BIAS_RAD
                val restoreAngle = -RESTORE_K * driftFromNeutral
                val curlPerSeg = (wagAngle + biasAngle + restoreAngle) * motionGain * t
                // Rotate this segment's offset from prev by curlPerSeg
                val ox = spineX[i] - prevX
                val oy = spineY[i] - prevY
                val cosC = cos(curlPerSeg)
                val sinC = sin(curlPerSeg)
                spineX[i] = prevX + ox * cosC - oy * sinC
                spineY[i] = prevY + ox * sinC + oy * cosC
            }

            // Record final segment angle
            val finalAngle = atan2(spineY[i] - prevY, spineX[i] - prevX)
            segAngle[i] = finalAngle
            prevAngle = finalAngle
            prevX = spineX[i]
            prevY = spineY[i]
        }

        // --- Step 3: build the main tail body --------------------------------
        // Walk the spine and emit left/right edge vertices using the width
        // profile. Width profile is piecewise to give the SVG-style "thick
        // middle, slight pinch before the fur cluster".
        val edgeCount = SEGMENT_COUNT + 1
        val leftX = FloatArray(edgeCount)
        val leftY = FloatArray(edgeCount)
        val rightX = FloatArray(edgeCount)
        val rightY = FloatArray(edgeCount)

        // Edge 0 sits at the head — uses the new wider root width.
        val firstAngle = segAngle[0]
        val firstPerp = firstAngle + PI.toFloat() / 2f
        val edgeHalfFactor = 0.5f
        val headHalf = r * ROOT_WIDTH_K * edgeHalfFactor
        val hpx = cos(firstPerp) * headHalf
        val hpy = sin(firstPerp) * headHalf
        leftX[0] = headX + hpx
        leftY[0] = headY + hpy
        rightX[0] = headX - hpx
        rightY[0] = headY - hpy

        for (i in 0 until SEGMENT_COUNT) {
            val t = (i + 1f) / SEGMENT_COUNT
            val halfWidth = widthAtRatio(t) * r * edgeHalfFactor
            val perp = segAngle[i] + PI.toFloat() / 2f
            val px = cos(perp) * halfWidth
            val py = sin(perp) * halfWidth
            leftX[i + 1] = spineX[i] + px
            leftY[i + 1] = spineY[i] + py
            rightX[i + 1] = spineX[i] - px
            rightY[i + 1] = spineY[i] - py
        }

        bodyPath.reset()
        bodyPath.moveTo(leftX[0], leftY[0])
        for (i in 1 until edgeCount) bodyPath.lineTo(leftX[i], leftY[i])
        for (i in edgeCount - 1 downTo 0) bodyPath.lineTo(rightX[i], rightY[i])
        bodyPath.close()

        val fadeMultiplier = Settings.tailLengthMultiplier.coerceIn(0.1f, 2f)
        val bodyAlpha = (255f * fadeMultiplier.coerceAtMost(1f)).toInt()
        val bodyColor = Color(Palette.withAlpha(secondary, bodyAlpha))
        scope.drawPath(bodyPath, bodyColor, style = Fill)

        // --- Step 4: build pointed fur tufts ---------------------------------
        // Each tuft is a triangular flame shape that LIES ALONG the spine —
        // its apex points TOWARD THE TIP of the tail using the spine's local
        // forward direction (from this segment to the next), plus a clear
        // outward perpendicular slant so the fur visibly angles away from
        // the tail edge. The tail just ends at the spine — no special tip
        // geometry.
        tuftPath.reset()
        // Tip endpoint (last spine sample) and tip-segment forward axis are
        // used to compute the hard cap on how far any tuft apex may extend
        // past the tail's actual tip.
        val lastIdx = SEGMENT_COUNT - 1
        val tipEndX = spineX[lastIdx]
        val tipEndY = spineY[lastIdx]
        val tipFwdAngle = segAngle[lastIdx]
        val tipFwdAxisX = cos(tipFwdAngle)
        val tipFwdAxisY = sin(tipFwdAngle)
        val maxOvershoot = spacing * TUFT_MAX_OVERSHOOT_K

        val firstTuftIdxF = (SEGMENT_COUNT - 1).toFloat() * TUFT_FIRST_SEG_RATIO
        val lastTuftIdxF = (SEGMENT_COUNT - 1).toFloat() * TUFT_LAST_SEG_RATIO
        val tuftSegSpan = (lastTuftIdxF - firstTuftIdxF).coerceAtLeast(0.001f)
        val tuftCountDenom = (TUFT_COUNT - 1).coerceAtLeast(1).toFloat()
        for (k in 0 until TUFT_COUNT) {
            // Even distribution across [firstTuftIdxF, lastTuftIdxF] so the
            // first tuft sits at 20% and the last at ~92% along the spine.
            val tuftAlongTail = k.toFloat() / tuftCountDenom
            val segFloat = firstTuftIdxF + tuftAlongTail * tuftSegSpan
            val segI = segFloat.toInt().coerceIn(0, SEGMENT_COUNT - 1)
            val segFrac = segFloat - segI
            // Position along the spine (interpolate between segI and segI+1)
            val nextI = (segI + 1).coerceAtMost(SEGMENT_COUNT - 1)
            val baseX = spineX[segI] + (spineX[nextI] - spineX[segI]) * segFrac
            val baseY = spineY[segI] + (spineY[nextI] - spineY[segI]) * segFrac

            // Forward unit vector at this anchor = direction from segI to nextI
            // along the spine. This is the "fur grows toward the tip" axis.
            val fwdDx = spineX[nextI] - spineX[segI]
            val fwdDy = spineY[nextI] - spineY[segI]
            val fwdLen = hypot(fwdDx, fwdDy).coerceAtLeast(0.001f)
            val fwdX = fwdDx / fwdLen
            val fwdY = fwdDy / fwdLen
            // Perpendicular unit vector (left of forward).
            val perpX = -fwdY
            val perpY = fwdX

            // Alternate sides — even tufts on +perp, odd tufts on -perp.
            val side = if (k % 2 == 0) 1f else -1f

            // Per-tuft deterministic size & length variation so neighbours
            // look visibly irregular ("varied fur" rather than uniform combs).
            val sizeMul = TUFT_SIZE_MULTIPLIERS[k % TUFT_SIZE_MULTIPLIERS.size]
            val lengthMul = TUFT_LENGTH_MULTIPLIERS[k % TUFT_LENGTH_MULTIPLIERS.size]

            // Per-tuft, per-frame oscillation of the "lay along spine" amount.
            // Modulates the apex's forward-projection magnitude only — the
            // direction itself is still driven by the spline. Some tufts lie
            // flatter, others lift slightly, and the pattern slowly breathes.
            val layPhase = TUFT_LAY_PHASE_OFFSETS[k % TUFT_LAY_PHASE_OFFSETS.size]
            val layOsc = 1f + TUFT_LAY_OSC_AMPLITUDE * sin(flickPhase * (TUFT_LAY_OSC_SPEED / IDLE_FLICK_SPEED) + layPhase)

            // Tip-ward tufts reach a bit further, then per-tuft length
            // multiplier and the breathing oscillation are folded in. Width
            // is varied independently of length.
            val baselineLength = r * TUFT_LENGTH_K + r * TUFT_TIP_BONUS_K * tuftAlongTail
            val tuftLength = baselineLength * lengthMul * layOsc
            val tuftBaseHalfWidth = r * TUFT_BASE_HALFWIDTH_K * sizeMul

            // Attachment anchor sits slightly off the spine on the chosen side.
            val anchorX = baseX + perpX * r * TUFT_OFFSET_K * side
            val anchorY = baseY + perpY * r * TUFT_OFFSET_K * side

            // Apex points forward toward the tip with a strong perpendicular
            // outward slant — the tuft visibly angles away from the tail edge
            // while still leaning toward the tip.
            val apexPerpOffset = r * TUFT_APEX_PERP_OFFSET_K * side
            var apexX = anchorX + fwdX * tuftLength + perpX * apexPerpOffset
            var apexY = anchorY + fwdY * tuftLength + perpY * apexPerpOffset

            // Cap apex protrusion past the tail's tip endpoint. If the apex
            // sits further along the tip-forward axis than (tip + maxOvershoot),
            // pull it back along that axis to the cap. This preserves the
            // outward slant while limiting how far past the tip any tuft can
            // poke out.
            val apexFwdProjection = (apexX - tipEndX) * tipFwdAxisX + (apexY - tipEndY) * tipFwdAxisY
            if (apexFwdProjection > maxOvershoot) {
                val pullBack = apexFwdProjection - maxOvershoot
                apexX -= tipFwdAxisX * pullBack
                apexY -= tipFwdAxisY * pullBack
            }

            // Base corners straddle the anchor across the spine (perpendicular
            // to the forward direction) so the triangle's base is set across
            // the tail and the point flows toward the tip.
            val baseAX = anchorX + perpX * tuftBaseHalfWidth
            val baseAY = anchorY + perpY * tuftBaseHalfWidth
            val baseBX = anchorX - perpX * tuftBaseHalfWidth
            val baseBY = anchorY - perpY * tuftBaseHalfWidth

            tuftPath.moveTo(baseAX, baseAY)
            tuftPath.lineTo(apexX, apexY)
            tuftPath.lineTo(baseBX, baseBY)
            tuftPath.close()
        }

        val tuftColor = Color(Palette.withAlpha(secondary, bodyAlpha))
        scope.drawPath(tuftPath, tuftColor, style = Fill)

        // --- Step 5: subtle highlight along the upper edge of the body ------
        // A thin primary-color stripe along one side adds depth so the tail
        // doesn't look like a flat blob. Hoisted color, no per-pixel work.
        val highlightAlpha = (bodyAlpha * 0.45f).toInt()
        val highlightColor = Color(Palette.withAlpha(primary, highlightAlpha))
        val highlightPath = Path()
        highlightPath.moveTo(leftX[0], leftY[0])
        for (i in 1 until edgeCount) highlightPath.lineTo(leftX[i], leftY[i])
        // Inner curve offset back toward the spine to make a stripe
        val highlightInsetK = 0.65f
        for (i in edgeCount - 1 downTo 0) {
            val sx: Float
            val sy: Float
            if (i == 0) { sx = headX; sy = headY } else { sx = spineX[i - 1]; sy = spineY[i - 1] }
            val ix = sx + (leftX[i] - sx) * highlightInsetK
            val iy = sy + (leftY[i] - sy) * highlightInsetK
            highlightPath.lineTo(ix, iy)
        }
        highlightPath.close()
        scope.drawPath(highlightPath, highlightColor, style = Fill)
    }

    /**
     * Piecewise width along the tail.
     *  - 0.0 .. ROOT_TAPER_END : taper ROOT down to BASE (new wider base)
     *  - ROOT_TAPER_END .. phase2End : ease from BASE to MID width
     *  - phase2End .. phase3End : pinch slightly before the fur cluster
     *  - phase3End .. 1.00 : taper into tip (fur tufts take over visually)
     */
    private fun widthAtRatio(t: Float): Float {
        val phase2End = 0.75f
        val phase3End = 0.90f
        return when {
            t < ROOT_TAPER_END -> lerp(ROOT_WIDTH_K, BASE_WIDTH_K, t / ROOT_TAPER_END)
            t < phase2End -> lerp(BASE_WIDTH_K, MID_WIDTH_K, (t - ROOT_TAPER_END) / (phase2End - ROOT_TAPER_END))
            t < phase3End -> lerp(MID_WIDTH_K, PRE_TUFT_WIDTH_K, (t - phase2End) / (phase3End - phase2End))
            else -> lerp(PRE_TUFT_WIDTH_K, TIP_WIDTH_K, (t - phase3End) / (1f - phase3End))
        }
    }

    override fun clear() {
        initialized = false
        smoothedSpeed = 0f
    }

    override fun fillTo(x: Float, y: Float) {
        val spacing = renderer.radius * SEGMENT_SPACING_K
        for (i in 0 until SEGMENT_COUNT) {
            spineX[i] = x
            spineY[i] = y + spacing * (i + 1)
            segAngle[i] = PI.toFloat() / 2f
        }
        initialized = true
        lastHeadX = x
        lastHeadY = y
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
