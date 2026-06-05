package gameobjects.puckstyle.tails

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.withTransform
import gameobjects.Settings
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.StaticTailPath
import gameobjects.puckstyle.TailRenderer
import gameobjects.puckstyle.skins.DragonSkinPainters
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class DragonTail(override val renderer: PuckRenderer) : TailRenderer {

    // --- Spine geometry (5 more segments than CatTail) -----------------------
    private val SEGMENT_COUNT = 15
    private val SEGMENT_SPACING_K = 0.30f
    private val BASE_FOLLOW = 0.22f
    private val TIP_FOLLOW = 0.12f
    private val MAX_ANGLE_DIFF = 28f * (PI.toFloat() / 180f)

    // --- Body width profile: wide root (≈2× CatTail), quick taper, slow taper,
    //     thin rounded tip.
    private val ROOT_WIDTH_K = 1.5f
    private val MID_WIDTH_K = 0.55f
    private val TIP_WIDTH_K = 0.18f
    // ↓ Fraction of tail length held at full ROOT_WIDTH before any taper.
    //   Each +0.067 ≈ one extra "large" segment (with SEGMENT_COUNT = 15).
    private val FLAT_END_T = 0.10f        // 0f = taper starts immediately; 0.20f ≈ 3 flat segments
    // ↓ Fraction where the aggressive (root→mid) taper finishes; must be > FLAT_END_T.
    private val QUICK_TAPER_END_T = 0.38f // was 0.18f before the flat section was added

    // --- Traveling wave (idle animation) -------------------------------------
    // Wave travels from ball toward tip: sin(wavePhase - t * WAVE_FREQUENCY).
    // Negate WAVE_FREQUENCY to reverse direction (tip → ball).
    private val WAVE_SPEED     = 0.01f  // phase advance per frame; higher = faster wave
    private val WAVE_AMPLITUDE = 0.1f  // peak bend per segment (radians); higher = bigger bends
    private val WAVE_FREQUENCY = 1.0f   // spatial cycles × 2π; ~5 ≈ 0.8 crests along the tail
    private val MOTION_DAMP_THRESHOLD = 0.4f  // ball speed at which wave fades out (× radius)
    private val RESTORE_K = 1f       // how strongly segments snap back to straight; lower = floatier

    // --- Rigid section. The last segments don't whip — they extend straight
    //     along the final flexible segment's direction. This is the section the
    //     spikes are attached to; keeping it straight means the SVG never has
    //     to bend with the spine.
    private val RIGID_START_IDX = 13
    private val LAST_FLEXIBLE_IDX = RIGID_START_IDX - 1

    // --- Tail_1 SVG placement -------------------------------------------------
    // The fins are drawn flipped (y-axis mirrored) so the roots attach at the
    // rigid-section start and the fin tips trail toward the tail tip.
    // SPIKES_SQUISH is tuned so the inner edges of the fins align with the
    // tail body outline at the attachment point.
    private val SPIKES_NARROW_IDX = RIGID_START_IDX
    private val SPIKES_SQUISH = 0.9f
    private val SVG_ASPECT = 43.56f / 44.2f
    // ↓ Multiplies the spike SVG's drawn size uniformly. 1f = sized to the rigid
    //   section length; 3f = fins are 3× larger in both axes (centered in place).
    private val SPIKES_SCALE = 4f

    // --- Shadow (two independent passes, AxolotlTail convention) -------------
    // The body and the spike SVG each get their OWN three-layer shadow pass.
    // Both share the puck→paddle vector (shadowDx, shadowDy) but project it
    // onto different axes and clamp against different limits:
    //   • Body pass: perpendicular projection (lit window slides ACROSS the
    //     tail's width). Clamped to root half-width × SHADOW_CLAMP_K so the
    //     spine center stays lit. Drawn AFTER the spike pass, so the body's
    //     uninterrupted shadow runs the full length of the tail.
    //   • Spike pass: parallel projection along the rigid section heading
    //     (lit circle slides ALONG the spine, toward/away from the head). The
    //     lit-erase shape is a single small circle near the tip — exposes the
    //     spike SVG lobes on the paddle-facing side. Clamped to r × SPIKE_LIT_CLAMP_K.
    //  SHADOW_MAX_DISTANCE_K — uncapped lit shift, in r units. The per-pass
    //   clamps trim this; set this large enough that the clamps are what
    //   actually limit each slide.
    //  SHADOW_CLAMP_K        — fraction of body root half-width used as the
    //   body pass's clamp limit. 1.0 = lit edge can reach the spine center at
    //   max offset (half the body in shadow). Lower keeps a wider center
    //   strip lit: 0.7 ≈ ⅓ of the body in shadow, 0.5 ≈ ¼.
    //  SHADOW_BOUNDS_K       — half-extent of each saveLayer rect, in r units.
    //   If the tail or spikes clip at large wags, bump this up (e.g. 9–10).
    private val SHADOW_MAX_DISTANCE_K = 0.6f
    private val SHADOW_CLAMP_K        = 0.2f
    private val SHADOW_ALPHA          = 0.244f
    private val SHADOW_FOLLOW_RATE    = 0.12f
    private val SHADOW_BOUNDS_K       = 7f

    // --- Spike pass: tip lit-window circle -----------------------------------
    // A small round window near the tip is the spike's lit-erase shape. It
    // exposes a bright spot on the spike SVG lobes that peek past the body.
    //  LIT_CIRCLE_RADIUS_K — circle radius in r units.
    //  LIT_CIRCLE_OFFSET_K — base distance from the tip in r units, measured
    //   along the rigid section's heading. Positive pushes PAST the tip,
    //   negative pulls back toward the root.
    //  SPIKE_LIT_CLAMP_K   — max parallel slide of the lit circle from its
    //   base position, in r units. Independent of the body pass clamp.
    private val LIT_CIRCLE_RADIUS_K = 0.6f
    private val LIT_CIRCLE_OFFSET_K = 0.2f
    private val SPIKE_LIT_CLAMP_K   = 0.3f

    private var shadowDx = 0f
    private var shadowDy = 0f

    // --- Persistent state -----------------------------------------------------
    private val spineX = FloatArray(SEGMENT_COUNT)
    private val spineY = FloatArray(SEGMENT_COUNT)
    private val segAngle = FloatArray(SEGMENT_COUNT)
    private var initialized = false

    private var wavePhase  = 0f
    private var lastHeadX = 0f
    private var lastHeadY = 0f
    private var smoothedSpeed = 0f

    private val bodyPath = Path()

    override val zIndex: Int get() = -1

    override fun render(scope: DrawScope) {
        val r = renderer.radius
        val colors = responsiveGroup
        val bodyColor = Color(colors.primary)
        val spikesTint = ColorFilter.tint(Color(colors.secondary))

        val spacing = r * SEGMENT_SPACING_K
        val headX = renderer.x
        val headY = renderer.y

        if (!initialized) {
            fillTo(headX, headY)
            initialized = true
            lastHeadX = headX
            lastHeadY = headY
        }

        if (renderer.staticUiMode) {
            // Static screenshot: pose the whole spine along the shared swoosh; no wag.
            StaticTailPath.poseSpineAlong(spineX, spineY, segAngle, SEGMENT_COUNT, spacing, headX, headY, r)
        } else {
        val moveDx = headX - lastHeadX
        val moveDy = headY - lastHeadY
        val instantSpeed = hypot(moveDx, moveDy) / r.coerceAtLeast(0.001f)
        smoothedSpeed = smoothedSpeed * 0.85f + instantSpeed * 0.15f
        lastHeadX = headX
        lastHeadY = headY

        val motionGain = 1f - (smoothedSpeed / MOTION_DAMP_THRESHOLD).coerceIn(0f, 1f)
        wavePhase += WAVE_SPEED

        // --- Step 1: integrate the flexible portion of the spine ---------------
        var prevX = headX
        var prevY = headY
        var prevAngle = atan2(spineY[0] - headY, spineX[0] - headX)

        for (i in 0..LAST_FLEXIBLE_IDX) {
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

        // --- Step 2: extend the rigid portion in a straight line --------------
        val rigidAngle = segAngle[LAST_FLEXIBLE_IDX]
        val cosRigid = cos(rigidAngle)
        val sinRigid = sin(rigidAngle)
        for (i in RIGID_START_IDX until SEGMENT_COUNT) {
            spineX[i] = prevX + cosRigid * spacing
            spineY[i] = prevY + sinRigid * spacing
            segAngle[i] = rigidAngle
            prevX = spineX[i]
            prevY = spineY[i]
        }
        }

        // Rigid-section heading from the (now-final) spine — used by the spike shadow pass below.
        // Recomputed here (outside the integrate/pose branch) so both live and static modes have it.
        val rigidAngle = segAngle[LAST_FLEXIBLE_IDX]
        val cosRigid = cos(rigidAngle)
        val sinRigid = sin(rigidAngle)

        val widthMultiplier = Settings.tailLengthMultiplier.coerceAtMost(1.5f)
        val tipIdx = SEGMENT_COUNT - 1

        // --- Step 3: build the body path --------------------------------------
        val headPerp = segAngle[0] + PI.toFloat() / 2f
        val headHalfWidth = widthAtRatio(0f) * r * 0.5f * widthMultiplier
        val headPerpX = cos(headPerp) * headHalfWidth
        val headPerpY = sin(headPerp) * headHalfWidth

        bodyPath.reset()
        bodyPath.moveTo(headX + headPerpX, headY + headPerpY)
        for (i in 0 until SEGMENT_COUNT) {
            val perp = segAngle[i] + PI.toFloat() / 2f
            val halfWidth = widthAtRatio((i + 1f) / SEGMENT_COUNT) * r * 0.5f * widthMultiplier
            bodyPath.lineTo(spineX[i] + cos(perp) * halfWidth, spineY[i] + sin(perp) * halfWidth)
        }
        for (i in SEGMENT_COUNT - 1 downTo 0) {
            val perp = segAngle[i] + PI.toFloat() / 2f
            val halfWidth = widthAtRatio((i + 1f) / SEGMENT_COUNT) * r * 0.5f * widthMultiplier
            bodyPath.lineTo(spineX[i] - cos(perp) * halfWidth, spineY[i] - sin(perp) * halfWidth)
        }
        bodyPath.lineTo(headX - headPerpX, headY - headPerpY)
        bodyPath.close()

        // --- Shadow update: lit window slides toward the paddle ---------------
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

        val shadowBounds = Rect(
            headX - r * SHADOW_BOUNDS_K, headY - r * SHADOW_BOUNDS_K,
            headX + r * SHADOW_BOUNDS_K, headY + r * SHADOW_BOUNDS_K
        )
        val canvas = scope.drawContext.canvas
        val tipHalfWidth = widthAtRatio(1f) * r * 0.5f * widthMultiplier
        val tipCenter = Offset(spineX[tipIdx], spineY[tipIdx])

        // --- Step 4: spike SVG shadow pass (drawn FIRST, behind body) ---------
        // Independent three-layer stack on the spike SVG. The body fill in
        // step 5 covers the SVG's central body & dimple; only the two lobes
        // peek past the tail's lateral edges. The lit-erase circle near the
        // tip slides ALONG the spine (parallel projection), so it brightens
        // the spike lobes on the paddle-facing end without ever shifting
        // sideways out of the lobes.
        val spikePainter = DragonSkinPainters.tailSpikes
        if (spikePainter != null) {
            val wideX = spineX[tipIdx]
            val wideY = spineY[tipIdx]
            val narrowX = spineX[SPIKES_NARROW_IDX]
            val narrowY = spineY[SPIKES_NARROW_IDX]
            val axisDx = narrowX - wideX
            val axisDy = narrowY - wideY
            val axisLen = hypot(axisDx, axisDy).coerceAtLeast(0.001f)

            val spikeSvgH = axisLen * SPIKES_SCALE
            val spikeSvgW = spikeSvgH * SVG_ASPECT * SPIKES_SQUISH
            val cx = (wideX + narrowX) / 2f
            val cy = (wideY + narrowY) / 2f
            val spikeTx = cx - spikeSvgW / 2f
            val spikeTy = cy - spikeSvgH / 2f
            // SVG's natural +y (top→bottom = wide-lobe→narrow-tip) maps onto
            // the wide→narrow world vector.
            val spikeAngleDeg = atan2(-axisDx, axisDy) * (180f / PI.toFloat())

            // Draw the spikes painter at its constant intrinsic size and scale to the spike box via
            // the canvas (see DragonSkin.drawSvgPart). The carousel and in-game tails share one
            // VectorPainter; drawing it at two sizes per frame makes the spikes scale with the carousel.
            val spikeISize = spikePainter.intrinsicSize
            val spikeRefW = if (spikeISize.width.isFinite() && spikeISize.width > 0f) spikeISize.width else spikeSvgW
            val spikeRefH = if (spikeISize.height.isFinite() && spikeISize.height > 0f) spikeISize.height else spikeSvgH

            canvas.saveLayer(shadowBounds, Paint())
            with(scope) {
                withTransform({
                    translate(spikeTx, spikeTy)
                    val pivot = Offset(spikeSvgW / 2f, spikeSvgH / 2f)
                    if (spikeAngleDeg != 0f) rotate(spikeAngleDeg, pivot = pivot)
                    scale(1f, -1f, pivot = pivot)
                    scale(spikeSvgW / spikeRefW, spikeSvgH / spikeRefH, pivot = Offset.Zero)
                }) {
                    with(spikePainter) { draw(Size(spikeRefW, spikeRefH), colorFilter = spikesTint) }
                }
            }

            // Parallel projection of the puck→paddle vector onto the rigid
            // heading. The lit circle stays centered laterally on the spine
            // and only slides forward/back along it.
            val spikeRawProj = shadowDx * cosRigid + shadowDy * sinRigid
            val spikeClampLimit = r * SPIKE_LIT_CLAMP_K
            val spikeProj = spikeRawProj.coerceIn(-spikeClampLimit, spikeClampLimit)
            val spikeLitDx = -spikeProj * cosRigid
            val spikeLitDy = -spikeProj * sinRigid

            val spikeSrcAtop = Paint().apply { blendMode = BlendMode.SrcAtop }
            canvas.saveLayer(shadowBounds, spikeSrcAtop)
            with(scope) {
                drawRect(
                    color = Color(0f, 0f, 0f, SHADOW_ALPHA),
                    topLeft = shadowBounds.topLeft,
                    size = shadowBounds.size
                )
                val dstOutPaint = Paint().apply { blendMode = BlendMode.DstOut }
                canvas.saveLayer(shadowBounds, dstOutPaint)
                withTransform({ translate(spikeLitDx, spikeLitDy) }) {
                    val litCx = spineX[tipIdx] + cosRigid * r * LIT_CIRCLE_OFFSET_K
                    val litCy = spineY[tipIdx] + sinRigid * r * LIT_CIRCLE_OFFSET_K
                    drawCircle(Color.Black, r * LIT_CIRCLE_RADIUS_K, Offset(litCx, litCy))
                }
                canvas.restore()  // close lit erase layer
            }
            canvas.restore()  // close shadow wash layer
            canvas.restore()  // close spike content layer
        }

        // --- Step 5: body + tip cap shadow pass (drawn on top of spike pass) --
        // Body silhouette is the lit area. Perpendicular projection so the
        // bright strip slides across the tail's width; the shadow then runs
        // uninterrupted from root to rounded tip without any holes for the
        // spike circle (which is handled by the separate pass above).
        canvas.saveLayer(shadowBounds, Paint())
        scope.drawPath(bodyPath, bodyColor, style = Fill)
        scope.drawCircle(bodyColor, tipHalfWidth, tipCenter)

        val clampLimit = widthAtRatio(0f) * r * 0.5f * widthMultiplier * SHADOW_CLAMP_K
        val mag = hypot(shadowDx, shadowDy)
        val rawLitDx: Float
        val rawLitDy: Float
        if (mag > clampLimit) {
            val s = clampLimit / mag
            rawLitDx = shadowDx * s; rawLitDy = shadowDy * s
        } else {
            rawLitDx = shadowDx; rawLitDy = shadowDy
        }

        val perpAngle = segAngle[0] + PI.toFloat() / 2f
        val perpCos = cos(perpAngle)
        val perpSin = sin(perpAngle)
        val perpProj = rawLitDx * perpCos + rawLitDy * perpSin
        val litDx = perpProj * perpCos
        val litDy = perpProj * perpSin

        val bodySrcAtop = Paint().apply { blendMode = BlendMode.SrcAtop }
        canvas.saveLayer(shadowBounds, bodySrcAtop)
        with(scope) {
            drawRect(
                color = Color(0f, 0f, 0f, SHADOW_ALPHA),
                topLeft = shadowBounds.topLeft,
                size = shadowBounds.size
            )
            val dstOutPaint = Paint().apply { blendMode = BlendMode.DstOut }
            canvas.saveLayer(shadowBounds, dstOutPaint)
            withTransform({ translate(litDx, litDy) }) {
                drawPath(bodyPath, Color.Black, style = Fill)
                drawCircle(Color.Black, tipHalfWidth, tipCenter)
            }
            canvas.restore()  // close lit erase layer
        }
        canvas.restore()  // close shadow wash layer
        canvas.restore()  // close body content layer
    }

    private fun widthAtRatio(t: Float): Float {
        return when {
            t < FLAT_END_T -> ROOT_WIDTH_K
            t < QUICK_TAPER_END_T -> lerp(ROOT_WIDTH_K, MID_WIDTH_K, (t - FLAT_END_T) / (QUICK_TAPER_END_T - FLAT_END_T))
            else -> lerp(MID_WIDTH_K, TIP_WIDTH_K, (t - QUICK_TAPER_END_T) / (1f - QUICK_TAPER_END_T))
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
