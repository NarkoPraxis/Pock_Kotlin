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
    private val SEGMENT_COUNT = 10
    private val SEGMENT_SPACING_K = 0.32f
    private val BASE_FOLLOW = 0.22f
    private val TIP_FOLLOW = 0.12f
    private val MAX_ANGLE_DIFF = 28f * (PI.toFloat() / 180f)

    // --- Body width profile --------------------------------------------------
    private val ROOT_WIDTH_K = 0.70f
    private val BASE_WIDTH_K = 0.55f
    private val MID_WIDTH_K = 0.50f
    private val PRE_TIP_WIDTH_K = 0.30f
    private val TIP_WIDTH_K = 0.00f
    private val ROOT_TAPER_END = 0.50f

    // --- Idle flick ----------------------------------------------------------
    private val IDLE_FLICK_SPEED = 0.045f
    private val IDLE_FLICK_AMPLITUDE = 0.18f
    private val CURL_BIAS_RAD = 0.00f
    private val CURL_PHASE_SHIFT = 0.70f
    private val MOTION_DAMP_THRESHOLD = 0.4f
    private val RESTORE_K = 0.35f

    // --- Fur strands (clones of the main tail) ------------------------------
    // Each strand mirrors the main spine for the first part of its length,
    // then the remaining tip segments bend sideways with a constant angle
    // offset. Lateral travel is hard-capped against the corresponding main
    // spine point so a strand can never wander off the tail's shape.
    //
    // Per-strand arrays are all `strandCount` long; index k describes strand k.
    // Indices 0..3 are the four fur strands that hang off the main tail.
    // Index 4 (the LAST entry) is the "centerline strand" — it visually
    // replaces the old rigid main-tail body, using bend = 0 (no lateral
    // lean) and the full main-tail width profile. Mechanically it's just
    // another strand, so the whole tail now whips like the fur strands.
    // Tweak any of these to restyle the fur — see the comments above each.
    private val strandCount = 5

    // How many spine segments make up each strand. Compared against the main
    // tail's SEGMENT_COUNT: smaller = strand ends before the main tip
    // (shorter fur), equal = same length, larger = extends past main tip.
    // The centerline (index 4) matches SEGMENT_COUNT so it spans the full tail.
    private val strandSegmentCounts = intArrayOf(10, 10, 9, 9, 10)

    // What fraction of each strand's segments are locked to the main spine
    // (perfect overlap). The remaining fraction is the deviating tip that
    // bends sideways. 0.80 means the first 80% of the strand follows the
    // main exactly and the last 20% can drift; 0.50 means half the strand
    // is locked and half can drift.
    private val strandCloneFractions = floatArrayOf(0.70f, 0.70f, 0.80f, 0.70f, 0.80f)

    // How sharply each strand's deviating segments bend, in radians per
    // segment. Sign sets the bend direction (positive curls one way,
    // negative the other). Roughly: 0.05 is barely visible, 0.15 is a
    // gentle lean, 0.25 is a strong wisp. The centerline strand uses 0.0
    // so it has no lateral lean — only the whip lag, no curl.
    private val strandTipBendRadians = floatArrayOf(-0.10f, 0.30f, -0.30f, 0.20f, 0.30f)

    // Hard ceiling on how far the deviating tip can drift sideways from the
    // corresponding main spine point, measured in puck radii. Even with a
    // huge bend value, no strand can wander past this distance — guarantees
    // the strands always look attached to the main tail.
    private val strandMaximumDeviationFromMain = 0.90f

    // How quickly each strand's tip catches up to where the constant-bend
    // formula says it "should" be each frame. Lower values let the tip lag
    // behind, so when the main tail wags one way the strand tip trails
    // behind it and visibly whips back as the main reverses — like loose
    // fur catching wind. 1.0 = no lag (stiff hair-gel look, the previous
    // behavior); 0.5 = mild trail; 0.20 = pronounced whip; 0.10 = very
    // floppy and loose.
    private val strandTipChaseRates = floatArrayOf(0.20f, 0.25f, 0.22f, 0.28f, 0.25f)

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
    private val strandPath = Path()

    // Per-strand spine buffers. Cloned segments are overwritten from main
    // every frame; deviating segments persist across frames so they can lag
    // behind the main tail's motion (see strandTipChaseRates).
    private val strandSpineX = Array(strandCount) { FloatArray(strandSegmentCounts[it]) }
    private val strandSpineY = Array(strandCount) { FloatArray(strandSegmentCounts[it]) }
    private val strandSegAngle = Array(strandCount) { FloatArray(strandSegmentCounts[it]) }
    private val strandInitialized = BooleanArray(strandCount)

    override val zIndex: Int get() = -1

    override fun render(scope: DrawScope) {
        val r = renderer.radius
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

        val moveDx = headX - lastHeadX
        val moveDy = headY - lastHeadY
        val instantSpeed = hypot(moveDx, moveDy) / r.coerceAtLeast(0.001f)
        smoothedSpeed = smoothedSpeed * 0.85f + instantSpeed * 0.15f
        lastHeadX = headX
        lastHeadY = headY

        val motionGain = 1f - (smoothedSpeed / MOTION_DAMP_THRESHOLD).coerceIn(0f, 1f)
        flickPhase += IDLE_FLICK_SPEED

        // --- Step 1: integrate the main spine --------------------------------
        var prevX = headX
        var prevY = headY
        var prevAngle = atan2(spineY[0] - headY, spineX[0] - headX)

        for (i in 0 until SEGMENT_COUNT) {
            val t = i.toFloat() / (SEGMENT_COUNT - 1)
            val follow = lerp(BASE_FOLLOW, TIP_FOLLOW, t)

            spineX[i] = lerp(spineX[i], prevX, follow)
            spineY[i] = lerp(spineY[i], prevY, follow)

            val dx = spineX[i] - prevX
            val dy = spineY[i] - prevY
            val dist = hypot(dx, dy).coerceAtLeast(0.001f)
            spineX[i] = prevX + dx / dist * spacing
            spineY[i] = prevY + dy / dist * spacing

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

            if (motionGain > 0f) {
                val wagAngle = sin(flickPhase + t * CURL_PHASE_SHIFT) * IDLE_FLICK_AMPLITUDE
                val biasAngle = CURL_BIAS_RAD
                val restoreAngle = -RESTORE_K * driftFromNeutral
                val curlPerSeg = (wagAngle + biasAngle + restoreAngle) * motionGain * t
                val ox = spineX[i] - prevX
                val oy = spineY[i] - prevY
                val cosC = cos(curlPerSeg)
                val sinC = sin(curlPerSeg)
                spineX[i] = prevX + ox * cosC - oy * sinC
                spineY[i] = prevY + ox * sinC + oy * cosC
            }

            val finalAngle = atan2(spineY[i] - prevY, spineX[i] - prevX)
            segAngle[i] = finalAngle
            prevAngle = finalAngle
            prevX = spineX[i]
            prevY = spineY[i]
        }

        val fadeMultiplier = Settings.tailLengthMultiplier.coerceIn(0.1f, 2f)
        val bodyAlpha = (255f * fadeMultiplier.coerceAtMost(1f)).toInt()
        val bodyColor = Color(Palette.withAlpha(secondary, bodyAlpha))

        // --- Step 2: derive & draw the strands -------------------------------
        // The reference spine integrated above is only used internally as the
        // shape the strands clone from — it is never drawn directly.
        val maxLateralDeviation = r * strandMaximumDeviationFromMain
        for (k in 0 until strandCount) {
            computeStrandSpine(k, maxLateralDeviation, spacing)
            drawStrand(scope, k, headX, headY, r, bodyColor)
        }
    }

    /**
     * Derive one strand's spine from the main spine.
     *
     * The first `cloneCount` segments are copied verbatim (perfect overlap).
     *
     * The remaining segments compute a "stiff target" — where the segment
     * would sit if it instantly mirrored the constant tip-bend — then lerp
     * the previous frame's position toward that target by chaseRate. The
     * resulting position lag is converted to an angle lag by re-enforcing
     * constant spacing, which is what makes the tip visibly trail behind
     * the main tail's motion (the "whip"). Finally each segment is clamped
     * to within `maxLateralDeviation` of the corresponding main spine point.
     */
    private fun computeStrandSpine(strandIndex: Int, maxLateralDeviation: Float, spacing: Float) {
        val strandX = strandSpineX[strandIndex]
        val strandY = strandSpineY[strandIndex]
        val strandA = strandSegAngle[strandIndex]
        val segmentCount = strandSegmentCounts[strandIndex]
        val cloneCount = (segmentCount * strandCloneFractions[strandIndex])
            .toInt()
            .coerceIn(1, segmentCount.coerceAtMost(SEGMENT_COUNT))
        val tipBend = strandTipBendRadians[strandIndex]
        val chaseRate = strandTipChaseRates[strandIndex]
        val firstFrame = !strandInitialized[strandIndex]

        for (i in 0 until cloneCount) {
            strandX[i] = spineX[i]
            strandY[i] = spineY[i]
            strandA[i] = segAngle[i]
        }

        for (i in cloneCount until segmentCount) {
            // Stiff target: where this segment would sit with no lag.
            val bentAngle = strandA[i - 1] + tipBend
            val targetX = strandX[i - 1] + cos(bentAngle) * spacing
            val targetY = strandY[i - 1] + sin(bentAngle) * spacing

            // Lerp from last frame's stored position toward the target.
            // First frame snaps so the strand doesn't drift in from origin.
            val chasedX: Float
            val chasedY: Float
            if (firstFrame) {
                chasedX = targetX
                chasedY = targetY
            } else {
                chasedX = lerp(strandX[i], targetX, chaseRate)
                chasedY = lerp(strandY[i], targetY, chaseRate)
            }

            // Re-enforce constant spacing from the previous segment.
            // This turns the position lag into an angle lag — the segment
            // ends up at the right distance from its parent but pointing
            // behind where the "stiff" version would point.
            val dx = chasedX - strandX[i - 1]
            val dy = chasedY - strandY[i - 1]
            val dist = hypot(dx, dy).coerceAtLeast(0.001f)
            strandX[i] = strandX[i - 1] + dx / dist * spacing
            strandY[i] = strandY[i - 1] + dy / dist * spacing
            strandA[i] = atan2(strandY[i] - strandY[i - 1], strandX[i] - strandX[i - 1])

            // Cap lateral drift against the corresponding main spine point
            // (only when the main spine still has a sample at this index;
            // for strands longer than the main tail the tip is unconstrained).
            if (i < SEGMENT_COUNT) {
                val driftX = strandX[i] - spineX[i]
                val driftY = strandY[i] - spineY[i]
                val driftDistance = hypot(driftX, driftY)
                if (driftDistance > maxLateralDeviation) {
                    val pullBack = maxLateralDeviation / driftDistance
                    strandX[i] = spineX[i] + driftX * pullBack
                    strandY[i] = spineY[i] + driftY * pullBack
                    strandA[i] = atan2(strandY[i] - strandY[i - 1], strandX[i] - strandX[i - 1])
                }
            }
        }

        strandInitialized[strandIndex] = true
    }

    /**
     * Draw a strand body. Cloned segments use the main tail's width profile
     * at the matching index — making the strand visually identical to the
     * main where they overlap. The deviating tip tapers linearly from the
     * width at the departure point down to a sharp point at the strand tip.
     */
    private fun drawStrand(
        scope: DrawScope, strandIndex: Int,
        headX: Float, headY: Float, r: Float, color: Color
    ) {
        val strandX = strandSpineX[strandIndex]
        val strandY = strandSpineY[strandIndex]
        val strandA = strandSegAngle[strandIndex]
        val segmentCount = strandSegmentCounts[strandIndex]
        val cloneCount = (segmentCount * strandCloneFractions[strandIndex])
            .toInt()
            .coerceIn(1, segmentCount.coerceAtMost(SEGMENT_COUNT))
        val edgeHalfFactor = 0.5f

        val departureWidth = widthAtRatio(cloneCount.toFloat() / SEGMENT_COUNT)
        val deviatingCount = (segmentCount - cloneCount).coerceAtLeast(1).toFloat()

        val firstPerp = strandA[0] + PI.toFloat() / 2f
        val headHalf = r * ROOT_WIDTH_K * edgeHalfFactor
        val headPerpX = cos(firstPerp) * headHalf
        val headPerpY = sin(firstPerp) * headHalf

        strandPath.reset()
        strandPath.moveTo(headX + headPerpX, headY + headPerpY)
        for (i in 0 until segmentCount) {
            val widthK = strandWidthAt(i, cloneCount, segmentCount, departureWidth, deviatingCount)
            val halfWidth = widthK * r * edgeHalfFactor
            val perp = strandA[i] + PI.toFloat() / 2f
            strandPath.lineTo(strandX[i] + cos(perp) * halfWidth, strandY[i] + sin(perp) * halfWidth)
        }
        for (i in segmentCount - 1 downTo 0) {
            val widthK = strandWidthAt(i, cloneCount, segmentCount, departureWidth, deviatingCount)
            val halfWidth = widthK * r * edgeHalfFactor
            val perp = strandA[i] + PI.toFloat() / 2f
            strandPath.lineTo(strandX[i] - cos(perp) * halfWidth, strandY[i] - sin(perp) * halfWidth)
        }
        strandPath.lineTo(headX - headPerpX, headY - headPerpY)
        strandPath.close()

        scope.drawPath(strandPath, color, style = Fill)
    }

    /**
     * Width K-factor for a single strand segment.
     *  - Cloned segments use the main tail's width at the same absolute
     *    index (so the strand is visually identical to main there).
     *  - Deviating segments taper linearly from the width at the departure
     *    point down to 0 at the strand tip.
     */
    private fun strandWidthAt(
        segIndex: Int, cloneCount: Int, segmentCount: Int,
        departureWidth: Float, deviatingCount: Float
    ): Float {
        return if (segIndex < cloneCount) {
            widthAtRatio((segIndex + 1f) / SEGMENT_COUNT)
        } else {
            val deviatingProgress = (segIndex - cloneCount + 1f) / deviatingCount
            departureWidth * (1f - deviatingProgress)
        }
    }

    private fun widthAtRatio(t: Float): Float {
        val phase2End = 0.65f
        val phase3End = 0.80f
        return when {
            t < ROOT_TAPER_END -> lerp(ROOT_WIDTH_K, BASE_WIDTH_K, t / ROOT_TAPER_END)
            t < phase2End -> lerp(BASE_WIDTH_K, MID_WIDTH_K, (t - ROOT_TAPER_END) / (phase2End - ROOT_TAPER_END))
            t < phase3End -> lerp(MID_WIDTH_K, PRE_TIP_WIDTH_K, (t - phase2End) / (phase3End - phase2End))
            else -> lerp(PRE_TIP_WIDTH_K, TIP_WIDTH_K, (t - phase3End) / (1f - phase3End))
        }
    }

    override fun clear() {
        initialized = false
        smoothedSpeed = 0f
        for (k in 0 until strandCount) strandInitialized[k] = false
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
        for (k in 0 until strandCount) strandInitialized[k] = false
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
