package gameobjects.puckstyle.tails

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.withTransform
import gameobjects.Settings
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class AxolotlTail(override val renderer: PuckRenderer) : TailRenderer {

    // --- Spine geometry ------------------------------------------------------
    // Fixed 15 segments; tail length is tuned via SEGMENT_SPACING_K only.
    // (Want a longer tail later? Increase spacing — do not add segments.)
    private val SEGMENT_COUNT = 15
    private val SEGMENT_SPACING_K = 0.20f
    private val BASE_FOLLOW = 0.22f
    private val TIP_FOLLOW = 0.12f
    private val MAX_ANGLE_DIFF = 28f * (PI.toFloat() / 180f)

    // --- Idle motion: dragon-style traveling wave + cat-style amplitude -----
    private val WAVE_SPEED     = 0.025f
    private val WAVE_AMPLITUDE = 0.18f
    private val WAVE_FREQUENCY = 1.0f
    private val MOTION_DAMP_THRESHOLD = 0.4f
    private val RESTORE_K = 1.0f

    // --- Tail (on top, secondary): smooth gourd/banana silhouette -----------
    // Width K-factors at evenly-spaced ratios 0..1. Wide root, gentle taper,
    // smooth approach to 0 at tip. Last entry stays > 0 so the rounded-tip
    // bezier has a finite half-width to curl off of.
    private val TAIL_KEYS = floatArrayOf(
        1.40f, 1.40f, 1.38f, 1.32f, 1.22f,
        1.08f, 0.92f, 0.76f, 0.62f, 0.50f,
        0.40f, 0.30f, 0.22f, 0.14f, 0.06f
    )

    // --- Fin (behind, primary): uniform-width spline with sinusoidal lobes --
    // No taper — base width stays constant along the whole length. Width
    // variation comes only from the lobe oscillation (FIN_LOBE_CYCLES sets
    // how many full sine cycles fit along the spline). Peaks frame the Tail
    // like the dragon spikes frame the dragon body.
    private val FIN_BASE_WIDTH_K = 1.4f
    private val FIN_LOBE_AMPLITUDE = 0.3f
    private val FIN_LOBE_CYCLES = 3.0f
    // Speed at which the fin's sine lobes travel toward the tip (radians/frame).
    // Larger = faster ripple. Negative would reverse direction (tip → root).
    private val FIN_WAVE_SPEED = 0.08f
    // Total fin width (base + oscillation together) is held at 100% until
    // FIN_TAPER_START_T, then smoothstep-eased down to FIN_TIP_WIDTH_FRACTION
    // at the fin tip. Because the whole width is multiplied uniformly, the
    // sine-phase shape of the lobes is preserved — the fin just shrinks
    // toward the end.
    private val FIN_TAPER_START_T = 0.80f
    private val FIN_TIP_WIDTH_FRACTION = 0.20f

    // --- Rounded-tip shape parameters ---------------------------------------
    // Extra forward extension beyond the last segment for the bezier tip apex,
    // in r units. Larger = pointier nose; smaller = blunter round.
    private val TIP_APEX_EXTEND_K = 0.10f

    // --- Shadow (lit-window matches part silhouette, AxolotlSkin convention) ------
    // Fin and tail body each get their OWN three-layer shadow pass: their
    // own silhouette serves as the lit-erase shape, translated toward the
    // paddle. They share shadowDx/Dy (the puck→paddle vector) but each
    // clamps the magnitude against its own root half-width so the spine
    // center of each part always stays lit.
    //  SHADOW_MAX_DISTANCE_K — uncapped lit shift, in r units (clamped per-part).
    //  SHADOW_CLAMP_K        — fraction of each part's root half-width used as
    //   the clamp limit. 1.0 = lit edge can reach the spine center at max
    //   offset (half the part in shadow). Lower keeps a wider center strip lit.
    //  SHADOW_BOUNDS_K       — half-extent of each saveLayer rect, in r units.
    //   If you see the fin or tail clipped at large wags, bump this up.
    private val SHADOW_MAX_DISTANCE_K = 0.6f
    private val SHADOW_CLAMP_K        = 0.3f
    private val SHADOW_ALPHA          = 0.244f
    private val SHADOW_FOLLOW_RATE    = 0.12f
    private val SHADOW_BOUNDS_K       = 6f

    // Hardcoded toggle: swap the fin's wash color from black to white. The
    // lit-window position and shape stay exactly the same; only the wash
    // color inverts, so the side that was darkened is now brightened
    // instead. Set to false to restore the normal dark shadow wash.
    private val FIN_SHADOW_INVERTED = true
    // Multiplier applied to SHADOW_ALPHA when the wash is inverted (white).
    // Equal alpha produces unequal perceived contrast — white over a
    // saturated fill desaturates softly, while black darkens it crisply —
    // so the white wash gets more alpha to match the tail's shadow weight.
    private val INVERTED_ALPHA_GAIN = 2f

    // Fin's lit-erase shape is scaled along the head's perpendicular axis
    // (local x), keeping the spine direction (local y) untouched. With the
    // lit window narrower than the fin, the wash is visible on both edges
    // at rest; shadowDx/Dy then biases how much wash shows on each side.
    private val FIN_LIT_SCALE_X = 0.9f

    private var shadowDx = 0f
    private var shadowDy = 0f

    // --- Persistent state ---------------------------------------------------
    private val spineX = FloatArray(SEGMENT_COUNT)
    private val spineY = FloatArray(SEGMENT_COUNT)
    private val segAngle = FloatArray(SEGMENT_COUNT)
    private var initialized = false

    private var wavePhase = 0f
    private var finWavePhase = 0f
    private var lastHeadX = 0f
    private var lastHeadY = 0f
    private var smoothedSpeed = 0f

    // Per-segment widths recomputed each frame (one set per spline).
    private val finHalfW = FloatArray(SEGMENT_COUNT)
    private val tailHalfW = FloatArray(SEGMENT_COUNT)

    private val finPath = Path()
    private val tailPath = Path()

    override val zIndex: Int get() = -1

    override fun render(scope: DrawScope) {
        val r = renderer.radius
        val colors = responsiveGroup
        val primaryColor = Color(colors.primary)
        val secondaryColor = Color(colors.secondary)

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
        wavePhase += WAVE_SPEED
        finWavePhase += FIN_WAVE_SPEED

        // --- Step 1: integrate the shared spine -----------------------------
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
                val wagAngle = sin(wavePhase - t * WAVE_FREQUENCY) * WAVE_AMPLITUDE
                val restoreAngle = -RESTORE_K * driftFromNeutral
                val curlPerSeg = (wagAngle + restoreAngle) * motionGain * (0.2f + t * 0.8f)
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

        // --- Step 2: compute per-segment half-widths for both splines -------
        val widthMultiplier = Settings.tailLengthMultiplier.coerceAtMost(1.5f)
        val edgeHalfFactor = 0.5f
        for (i in 0 until SEGMENT_COUNT) {
            val t = (i + 1f) / SEGMENT_COUNT
            tailHalfW[i] = widthAtRatioTail(t) * r * edgeHalfFactor * widthMultiplier
            finHalfW[i] = widthAtRatioFin(t) * r * edgeHalfFactor * widthMultiplier
        }
        val tailHeadHalf = widthAtRatioTail(0f) * r * edgeHalfFactor * widthMultiplier
        val finHeadHalf = widthAtRatioFin(0f) * r * edgeHalfFactor * widthMultiplier

        // --- Step 3: shadow position update — lit slides toward paddle -------
        val effect = renderer.effect as? PaddleLaunchEffect
        val targetDx: Float
        val targetDy: Float
        if (effect != null) {
            val wx = effect.paddleX - headX
            val wy = effect.paddleY - headY
            val dist = hypot(wx, wy).coerceAtLeast(0.001f)
            targetDx = wx / dist * r * SHADOW_MAX_DISTANCE_K
            targetDy = wy / dist * r * SHADOW_MAX_DISTANCE_K
        } else { targetDx = 0f; targetDy = 0f }
        shadowDx = lerp(shadowDx, targetDx, SHADOW_FOLLOW_RATE)
        shadowDy = lerp(shadowDy, targetDy, SHADOW_FOLLOW_RATE)

        // --- Step 4: build both body paths ----------------------------------
        buildBodyPath(finPath, finHalfW, headX, headY, finHeadHalf, r)
        buildBodyPath(tailPath, tailHalfW, headX, headY, tailHeadHalf, r)

        // --- Step 5: draw fin and tail body each in their own shadow pass ----
        // Each part gets an independent three-layer stack (outer content,
        // SrcAtop dark wash, DstOut lit erase). They share shadowDx/Dy but
        // each clamps the magnitude against its own root half-width.
        val shadowBounds = Rect(
            headX - r * SHADOW_BOUNDS_K, headY - r * SHADOW_BOUNDS_K,
            headX + r * SHADOW_BOUNDS_K, headY + r * SHADOW_BOUNDS_K
        )
        drawPartWithShadow(scope, finPath, primaryColor, finHeadHalf, shadowBounds, invertShadow = FIN_SHADOW_INVERTED, litScaleX = FIN_LIT_SCALE_X)
        drawPartWithShadow(scope, tailPath, secondaryColor, tailHeadHalf, shadowBounds)
    }

    /**
     * Draw one body path with the lit-window shadow treatment. The path is
     * filled into an outer layer, a dark wash is composited onto that layer
     * via SrcAtop (clipped to part pixels), then a copy of the same path
     * shifted by the clamped puck→paddle vector erases a hole = the lit
     * region. The clamp keeps the spine center of this part always lit.
     */
    private fun drawPartWithShadow(
        scope: DrawScope,
        path: Path,
        fillColor: Color,
        rootHalfWidth: Float,
        bounds: Rect,
        invertShadow: Boolean = false,
        litScaleX: Float = 1f
    ) {
        val clampLimit = rootHalfWidth * SHADOW_CLAMP_K
        val mag = hypot(shadowDx, shadowDy)
        val rawLitDx: Float
        val rawLitDy: Float
        if (mag > clampLimit) {
            val s = clampLimit / mag
            rawLitDx = shadowDx * s; rawLitDy = shadowDy * s
        } else {
            rawLitDx = shadowDx; rawLitDy = shadowDy
        }

        // Project onto the tail's perpendicular axis so the lit window only
        // slides left/right across the part, never up/down along its length —
        // keeps the fin tip and tail rounded apex consistently shaded.
        val perpAngle = segAngle[0] + PI.toFloat() / 2f
        val perpCos = cos(perpAngle)
        val perpSin = sin(perpAngle)
        val perpProj = rawLitDx * perpCos + rawLitDy * perpSin
        val litDx = perpProj * perpCos
        val litDy = perpProj * perpSin

        val canvas = scope.drawContext.canvas
        canvas.saveLayer(bounds, Paint())
        scope.drawPath(path, fillColor, style = Fill)
        val srcAtopPaint = Paint().apply { blendMode = BlendMode.SrcAtop }
        canvas.saveLayer(bounds, srcAtopPaint)
        // A white wash at the same alpha as the black wash reads softer:
        // pushing a saturated fill toward white desaturates it rather than
        // dimming, so the contrast comes out weaker. Compensate by scaling
        // the wash alpha when inverted so the fin's highlight matches the
        // tail's shadow in perceived strength.
        val washChannel = if (invertShadow) 1f else 0f
        val washAlpha = if (invertShadow) (SHADOW_ALPHA * INVERTED_ALPHA_GAIN).coerceAtMost(1f) else SHADOW_ALPHA
        with(scope) {
            drawRect(
                color = Color(washChannel, washChannel, washChannel, washAlpha),
                topLeft = bounds.topLeft,
                size = bounds.size
            )
            val dstOutPaint = Paint().apply { blendMode = BlendMode.DstOut }
            canvas.saveLayer(bounds, dstOutPaint)
            withTransform({
                translate(litDx, litDy)
                if (litScaleX != 1f) {
                    val perpDeg = perpAngle * (180f / PI.toFloat())
                    val pivot = Offset(renderer.x, renderer.y)
                    rotate(perpDeg, pivot = pivot)
                    scale(litScaleX, 1f, pivot = pivot)
                    rotate(-perpDeg, pivot = pivot)
                }
            }) {
                drawPath(path, Color.Black, style = Fill)
            }
            canvas.restore()  // close lit erase layer
        }
        canvas.restore()  // close shadow wash layer
        canvas.restore()  // close outer part layer
    }

    /**
     * Build a tail body path with a rounded tip. Left edge runs head → tip,
     * a pair of quadratic beziers curves through a forward apex to round the
     * point, then the right edge runs tip → head.
     */
    private fun buildBodyPath(
        path: Path,
        halfWidths: FloatArray,
        headX: Float, headY: Float,
        headHalf: Float,
        r: Float
    ) {
        path.reset()

        val headPerp = segAngle[0] + PI.toFloat() / 2f
        val headPerpX = cos(headPerp) * headHalf
        val headPerpY = sin(headPerp) * headHalf
        path.moveTo(headX + headPerpX, headY + headPerpY)

        val lastIdx = SEGMENT_COUNT - 1
        // Left edge to one short of the tip.
        for (i in 0 until lastIdx) {
            val perp = segAngle[i] + PI.toFloat() / 2f
            path.lineTo(spineX[i] + cos(perp) * halfWidths[i], spineY[i] + sin(perp) * halfWidths[i])
        }

        // Round the tip: forward apex extended past the last segment along
        // its heading; quadTo via the last segment's left edge, then quadTo
        // via the last segment's right edge back to the right outline.
        val tipForward = segAngle[lastIdx]
        val tipApexX = spineX[lastIdx] + cos(tipForward) * r * TIP_APEX_EXTEND_K
        val tipApexY = spineY[lastIdx] + sin(tipForward) * r * TIP_APEX_EXTEND_K
        val tipPerp = segAngle[lastIdx] + PI.toFloat() / 2f
        val tipLX = spineX[lastIdx] + cos(tipPerp) * halfWidths[lastIdx]
        val tipLY = spineY[lastIdx] + sin(tipPerp) * halfWidths[lastIdx]
        val tipRX = spineX[lastIdx] - cos(tipPerp) * halfWidths[lastIdx]
        val tipRY = spineY[lastIdx] - sin(tipPerp) * halfWidths[lastIdx]
        val prevPerp = segAngle[lastIdx - 1] + PI.toFloat() / 2f
        val rightHandoffX = spineX[lastIdx - 1] - cos(prevPerp) * halfWidths[lastIdx - 1]
        val rightHandoffY = spineY[lastIdx - 1] - sin(prevPerp) * halfWidths[lastIdx - 1]
        path.quadraticTo(tipLX, tipLY, tipApexX, tipApexY)
        path.quadraticTo(tipRX, tipRY, rightHandoffX, rightHandoffY)

        // Right edge back to the head.
        for (i in lastIdx - 2 downTo 0) {
            val perp = segAngle[i] + PI.toFloat() / 2f
            path.lineTo(spineX[i] - cos(perp) * halfWidths[i], spineY[i] - sin(perp) * halfWidths[i])
        }
        path.lineTo(headX - headPerpX, headY - headPerpY)
        path.close()
    }

    /** Smooth gourd taper interpolated from TAIL_KEYS. */
    private fun widthAtRatioTail(t: Float): Float {
        val n = TAIL_KEYS.size
        val scaled = t.coerceIn(0f, 1f) * (n - 1)
        val i = scaled.toInt().coerceAtMost(n - 2)
        val frac = scaled - i
        return lerp(TAIL_KEYS[i], TAIL_KEYS[i + 1], frac)
    }

    /**
     * Fin = constant base width + sinusoidal lobe oscillation. Base width
     * and sine phase/frequency are both uniform along the length, so the
     * lobe shape never changes. Past FIN_TAPER_START_T the entire computed
     * width is smoothstep-multiplied down to FIN_TIP_WIDTH_FRACTION at the
     * tip, so the fin appears to shrink uniformly as it approaches the end
     * while the lobe shape stays intact.
     */
    private fun widthAtRatioFin(t: Float): Float {
        // Subtracting finWavePhase shifts the sine peaks in the +t direction
        // (toward the tip) as phase grows, producing a traveling ripple.
        val lobeWave = sin(t * PI.toFloat() * 2f * FIN_LOBE_CYCLES - finWavePhase)
        val rawWidth = FIN_BASE_WIDTH_K + lobeWave * FIN_LOBE_AMPLITUDE
        val tipGain = if (t < FIN_TAPER_START_T) {
            1f
        } else {
            val u = ((t - FIN_TAPER_START_T) /
                (1f - FIN_TAPER_START_T)).coerceIn(0f, 1f)
            val eased = u * u * (3f - 2f * u)
            lerp(1f, FIN_TIP_WIDTH_FRACTION, eased)
        }
        return (rawWidth * tipGain).coerceAtLeast(0f)
    }

    override fun clear() {
        initialized = false
        smoothedSpeed = 0f
    }

    override fun fillTo(x: Float, y: Float) {
        val spacing = renderer.radius * SEGMENT_SPACING_K
        val dirY = if (renderer.isHigh) -1f else 1f
        val angle = if (renderer.isHigh) (-PI / 2.0).toFloat() else (PI / 2.0).toFloat()
        for (i in 0 until SEGMENT_COUNT) {
            spineX[i] = x
            spineY[i] = y + dirY * spacing * (i + 1)
            segAngle[i] = angle
        }
        initialized = true
        lastHeadX = x
        lastHeadY = y
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
