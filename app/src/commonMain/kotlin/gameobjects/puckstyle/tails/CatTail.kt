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

    // --- Extra fur strands ---------------------------------------------------
    // Four clones of the main tail. Each strand's first 80% of segments is
    // copied verbatim from the main spine (perfect overlap, same width
    // profile). The remaining ~20% deviates with a small constant tip-bias
    // that pulls its tip toward one side. Lateral deviation is hard-capped
    // to MAX_DEVIATION_K × radius from the corresponding main spine point,
    // so a strand can never wander off the main tail's shape.
    // All four strands inherit the main spine's wag/curl through the cloned
    // portion — they only differ in which way the last two segments lean.
    private val STRAND_COUNT = 4
    private val CLONE_FRACTION = 0.80f
    private val MAX_DEVIATION_K = 0.18f
    private val strandTipBiasRad = floatArrayOf(0.20f, -0.22f, 0.14f, -0.16f)

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

    // Per-strand scratch buffers — recomputed from the main spine every frame,
    // no persistent state of their own.
    private val strandSpineX = Array(STRAND_COUNT) { FloatArray(SEGMENT_COUNT) }
    private val strandSpineY = Array(STRAND_COUNT) { FloatArray(SEGMENT_COUNT) }
    private val strandSegAngle = Array(STRAND_COUNT) { FloatArray(SEGMENT_COUNT) }

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

        // --- Step 2: derive & draw the four strand clones (behind main) -----
        val cloneCount = (SEGMENT_COUNT * CLONE_FRACTION).toInt().coerceAtLeast(1)
        val maxDev = r * MAX_DEVIATION_K
        for (k in 0 until STRAND_COUNT) {
            computeStrandSpine(k, cloneCount, maxDev, spacing)
            drawStrand(scope, k, headX, headY, r, bodyColor)
        }

        // --- Step 3: build & draw the main tail body ------------------------
        val edgeCount = SEGMENT_COUNT + 1
        val leftX = FloatArray(edgeCount)
        val leftY = FloatArray(edgeCount)
        val rightX = FloatArray(edgeCount)
        val rightY = FloatArray(edgeCount)

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
        scope.drawPath(bodyPath, bodyColor, style = Fill)

        // --- Step 4: subtle highlight along the upper edge ------------------
        val highlightAlpha = (bodyAlpha * 0.45f).toInt()
        val highlightColor = Color(Palette.withAlpha(primary, highlightAlpha))
        val highlightPath = Path()
        highlightPath.moveTo(leftX[0], leftY[0])
        for (i in 1 until edgeCount) highlightPath.lineTo(leftX[i], leftY[i])
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
     * Derive a single strand spine from the main spine. The first cloneCount
     * segments are copied verbatim — the strand can't drift there. The
     * remaining segments chase forward with a small constant tip-bias added
     * to each angle, then are hard-clamped to within maxDev of the
     * corresponding main spine point so the strand stays on the tail's shape.
     */
    private fun computeStrandSpine(k: Int, cloneCount: Int, maxDev: Float, spacing: Float) {
        val sX = strandSpineX[k]
        val sY = strandSpineY[k]
        val sA = strandSegAngle[k]
        val bias = strandTipBiasRad[k]

        for (i in 0 until cloneCount) {
            sX[i] = spineX[i]
            sY[i] = spineY[i]
            sA[i] = segAngle[i]
        }

        for (i in cloneCount until SEGMENT_COUNT) {
            val biasedAngle = sA[i - 1] + bias
            sX[i] = sX[i - 1] + cos(biasedAngle) * spacing
            sY[i] = sY[i - 1] + sin(biasedAngle) * spacing
            sA[i] = biasedAngle

            val dx = sX[i] - spineX[i]
            val dy = sY[i] - spineY[i]
            val dist = hypot(dx, dy)
            if (dist > maxDev) {
                val scale = maxDev / dist
                sX[i] = spineX[i] + dx * scale
                sY[i] = spineY[i] + dy * scale
                sA[i] = atan2(sY[i] - sY[i - 1], sX[i] - sX[i - 1])
            }
        }
    }

    /**
     * Draw a strand body using the same width profile as the main tail.
     * Cloned segments produce a body shape identical to the main; deviating
     * segments produce a tapered tip that branches off slightly.
     */
    private fun drawStrand(
        scope: DrawScope, k: Int,
        headX: Float, headY: Float, r: Float, color: Color
    ) {
        val sX = strandSpineX[k]
        val sY = strandSpineY[k]
        val sA = strandSegAngle[k]
        val edgeHalfFactor = 0.5f

        val firstPerp = sA[0] + PI.toFloat() / 2f
        val headHalf = r * ROOT_WIDTH_K * edgeHalfFactor
        val hpx = cos(firstPerp) * headHalf
        val hpy = sin(firstPerp) * headHalf

        strandPath.reset()
        strandPath.moveTo(headX + hpx, headY + hpy)
        for (i in 0 until SEGMENT_COUNT) {
            val t = (i + 1f) / SEGMENT_COUNT
            val halfWidth = widthAtRatio(t) * r * edgeHalfFactor
            val perp = sA[i] + PI.toFloat() / 2f
            strandPath.lineTo(sX[i] + cos(perp) * halfWidth, sY[i] + sin(perp) * halfWidth)
        }
        for (i in SEGMENT_COUNT - 1 downTo 0) {
            val t = (i + 1f) / SEGMENT_COUNT
            val halfWidth = widthAtRatio(t) * r * edgeHalfFactor
            val perp = sA[i] + PI.toFloat() / 2f
            strandPath.lineTo(sX[i] - cos(perp) * halfWidth, sY[i] - sin(perp) * halfWidth)
        }
        strandPath.lineTo(headX - hpx, headY - hpy)
        strandPath.close()

        scope.drawPath(strandPath, color, style = Fill)
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
