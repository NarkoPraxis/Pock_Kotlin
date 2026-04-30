package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import utility.Effects
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Warp-star paddle: three five-pointed stars sit behind the puck along the aim vector.
 * The smallest (index 2) mirrors the base paddle position exactly; the larger two follow
 * the same motion but are capped at their respective orbitRadius from the puck center.
 * All three are always visible, even in Idle.
 */
class GalaxyLaunch(theme: ColorTheme, renderer: PuckRenderer) : PaddleLaunchEffect(theme, renderer) {

    // ── Drawing ──────────────────────────────────────────────────────────────
    private val starPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
    private val starPaintFill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val starPath  = Path()

    // Cached stroke width — only updated when radius changes
    private var cachedStrokeWidth = -1f

    override var minDist: Float = 0f
        get() = 0f

    override val alwaysVisible: Boolean = true
    override val zIndex: Int
        get() = -1
    // ── Star descriptors (index 0 = largest/closest, 2 = smallest/furthest) ─
    //   orbitRadius: how far from the puck center the star sits while orbiting
    //   starRadius:  the "radius" of the five-pointed star itself
    private data class StarDesc(val orbitRadius: Float, val starRadius: Float)

    // Cached by radius — rebuilt only when renderer.radius changes (rare)
    private var cachedStarDescsRadius = -1f
    private val starDescs = arrayOf(StarDesc(0f, 0f), StarDesc(0f, 0f), StarDesc(0f, 0f))

    private fun ensureStarDescs() {
        if (renderer.radius == cachedStarDescsRadius) return
        cachedStarDescsRadius = renderer.radius
        val r = renderer.radius
        starDescs[0] = StarDesc(r * 2f, r)
        starDescs[1] = StarDesc(r * 3f, r * 0.9f)
        starDescs[2] = StarDesc(r * 4f, r * 0.6f)
        cachedStrokeWidth = r * 0.2f
    }

    // ── Orbit ─────────────────────────────────────────────────────────────────
    // Each star orbits at a slightly different angular phase so they look spread out
    private val orbitPhaseOffset = floatArrayOf(0f, (PI / 5f).toFloat(), (2 * PI / 5f).toFloat())
    private val ORBIT_SPEED = 0.022f  // radians per frame

    // ── Strike launch state ───────────────────────────────────────────────────
    // Tracks whether each star has been "fired" during the current strike animation.
    // Stars fire in reverse order: index 2 (smallest) first, then 1, then 0.
    // progress thresholds at which each star begins returning home:
    //   star 2 fires at progress >= 0.0 (immediately)
    //   star 1 fires when star 2 has returned to star 1's pull-back position (progress >= 0.33)
    //   star 0 fires when star 1 has returned to star 0's pull-back position (progress >= 0.66)
    // The puck launches only after all three are back (progress = 1.0 is handled by base class).
    // We track per-star "return progress" in [0,1].
    private val starReturnProgress = FloatArray(1) { 1f }  // 1 = fully home
    private var lastStrikeProgress = 0f

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Build a five-pointed star Path centered at (cx, cy) with outer radius [outer].
     * Uses cubic beziers so the tips are softly rounded rather than sharp points.
     */
    private fun buildStar(cx: Float, cy: Float, outer: Float, rotation: Float) {
        val inner = outer * 0.40f               // inner (valley) radius
        val roundness = outer * 0.04f           // control-point offset for rounded tips
        val angleStep = ANGLE_STEP_5
        val halfStep  = HALF_STEP_5

        starPath.reset()
        for (i in 0 until 5) {
            // Outer tip
            val outerAngle = rotation + i * angleStep - HALF_PI
            val tipX = cx + cos(outerAngle) * outer
            val tipY = cy + sin(outerAngle) * outer

            // Tangent direction perpendicular to the tip radius (for rounding)
            val perpX = -sin(outerAngle)
            val perpY =  cos(outerAngle)

            // Two inner valleys flanking this tip
            val prevInnerAngle = outerAngle - halfStep
            val nextInnerAngle = outerAngle + halfStep
            val prevValX = cx + cos(prevInnerAngle) * inner
            val prevValY = cy + sin(prevInnerAngle) * inner
            val nextValX = cx + cos(nextInnerAngle) * inner
            val nextValY = cy + sin(nextInnerAngle) * inner

            if (i == 0) {
                starPath.moveTo(prevValX, prevValY)
            }
            // Cubic bezier: valley → (control near tip) → (control near tip) → valley
            val cp1X = tipX - perpX * roundness
            val cp1Y = tipY - perpY * roundness
            val cp2X = tipX + perpX * roundness
            val cp2Y = tipY + perpY * roundness
            starPath.cubicTo(cp1X, cp1Y, tipX, tipY, tipX, tipY)    // approach tip
            starPath.cubicTo(tipX, tipY, cp2X, cp2Y, nextValX, nextValY) // leave tip
        }
        starPath.close()
    }

    /**
     * Resolve colors for both stroke and fill paints in a single pass.
     * Avoids calling renderer.shielded, renderer.isInert, and sin() multiple times
     * per draw call when both starColor and starColorFill are needed together.
     *
     * Returns a pair: (strokeColor, fillColor)
     */
    private fun resolveStarColors(starIndex: Int, ph: ChargePhase): Pair<Int, Int> {
        if (renderer.shielded && phase == ChargePhase.Idle) {
            return Pair(theme.shield.secondary, theme.shield.primary)
        }
        if (renderer.isInert) {
            return Pair(theme.inert.secondary, theme.inert.primary)
        }
        if (ph == ChargePhase.SweetSpot) {
            val t = sin(frame * 0.25f) * 0.5f + 0.5f
            return Pair(
                Palette.lerpColor(theme.shield.primary, theme.shield.secondary, t),
                Palette.lerpColor(theme.shield.secondary, theme.shield.primary, t)
            )
        }
        if (ph == ChargePhase.Inert) {
            return Pair(theme.inert.secondary, theme.inert.primary)
        }
        return if (chargeFillRatio > COLOR_THRESHOLDS[starIndex]) {
            Pair(theme.shield.secondary, theme.shield.primary)
        } else {
            Pair(theme.main.secondary, theme.main.primary)
        }
    }

    override fun drawIdlePaddle(canvas: Canvas) {
        ensureStarDescs()
        starPaint.strokeWidth = cachedStrokeWidth
        val descs = starDescs
        val sx = renderer.x
        val sy = renderer.y
        val baseRot = frame * ORBIT_SPEED * 1.5f + orbitPhaseOffset[0]

        val (stroke1, fill1) = resolveStarColors(1, phase)
        buildStar(sx, sy, descs[1].starRadius, -baseRot)
        starPaintFill.color = Palette.withAlpha(fill1, 255)
        starPaint.color = Palette.withAlpha(stroke1, 255)
        canvas.drawPath(starPath, starPaintFill)
        canvas.drawPath(starPath, starPaint)

        val (stroke0, fill0) = resolveStarColors(0, phase)
        buildStar(sx, sy, descs[0].starRadius, baseRot)
        starPaint.color = Palette.withAlpha(stroke0, 255)
        starPaintFill.color = Palette.withAlpha(fill0, 255)
        canvas.drawPath(starPath, starPaintFill)
        canvas.drawPath(starPath, starPaint)
    }


    override fun drawChargingPaddle(canvas: Canvas) {
        ensureStarDescs()
        starPaint.strokeWidth = cachedStrokeWidth

        val desc = starDescs[0]

        // Star 2 (smallest) mirrors the paddle exactly; stars 0 and 1 follow the same
        // motion but are capped so they never exceed their natural orbit distance.
        val dist = (paddleDistance).coerceIn(0f, renderer.radius * 5f)

        val sx = renderer.x - aimX * dist
        val sy = renderer.y - aimY * dist

        val rot = (frame * ORBIT_SPEED * 1.5f + orbitPhaseOffset[0])

        val dipStart = 0.2f
        val dipEnd = 0.5f
        val halfSize = renderer.radius * 0.5f
        val halfRatio = halfSize / desc.starRadius
        val starSizeRatio = when {
            phase == ChargePhase.Inert       -> halfRatio
            phase == ChargePhase.Draining -> halfRatio + ((chargeFillRatio - 0.5f) / 0.5f).coerceIn(0f, 1f) * (1f - halfRatio)
            chargeFillRatio <= dipStart      -> 1f - (chargeFillRatio / dipStart) * (1f - halfRatio)
            chargeFillRatio <= dipEnd        -> halfRatio
            else                             -> halfRatio + ((chargeFillRatio - dipEnd) / (1f - dipEnd)) * (1f - halfRatio)
        }

        val (stroke2, fill2) = resolveStarColors(2, phase)
        buildStar(sx, sy, desc.starRadius * starSizeRatio, rot)
        starPaint.color = Palette.withAlpha(stroke2, 255)
        starPaintFill.color = Palette.withAlpha(fill2, 255)
        canvas.drawPath(starPath, starPaintFill)
        canvas.drawPath(starPath, starPaint)

        buildStar(sx, sy, desc.starRadius * chargeFillRatio * 0.8f, rot)
        starPaintFill.color = Palette.withAlpha(theme.shield.primary, 255)
        canvas.drawPath(starPath, starPaintFill)
    }

    // ── drawStrikingPaddle ────────────────────────────────────────────────────

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        ensureStarDescs()
        starPaint.strokeWidth = cachedStrokeWidth

        // Detect when progress resets (new strike started)
        if (progress < lastStrikeProgress) {
            starReturnProgress[0] = 0f
        }
        lastStrikeProgress = progress

        val desc = starDescs[0]
        val ret = starReturnProgress[0]

        val sx = cx + (renderer.x - cx) * ret
        val sy = cy + (renderer.y - cy) * ret

        val rot = frame * ORBIT_SPEED * 1.5f + orbitPhaseOffset[0]

        val (stroke0, fill0) = resolveStarColors(0, phase)
        buildStar(sx, sy, desc.starRadius, rot)
        starPaint.color = Palette.withAlpha(stroke0, 255)
        starPaintFill.color = Palette.withAlpha(fill0, 255)
        canvas.drawPath(starPath, starPaintFill)
        canvas.drawPath(starPath, starPaint)
    }

    // ── Residual ──────────────────────────────────────────────────────────────

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(NebulaMark(rx, ry, renderer.radius, theme.shield.primary, theme.shield.secondary))
    }

    companion object {
        // Thresholds at which each star (by index) lights up as charge fills
        private val COLOR_THRESHOLDS = floatArrayOf(0.33f, 0.66f, 1f)
        // Pre-computed 8-point star angles — saves 8 trig conversions per drawStar call
        private val STAR_ANGLES = FloatArray(8) { i -> (i * 45f - 90f) * Math.PI.toFloat() / 180f }

        // Pre-computed five-point star geometry constants
        private val ANGLE_STEP_5 = (2f * PI / 5f).toFloat()
        private val HALF_STEP_5  = ANGLE_STEP_5 / 2f
        private val HALF_PI      = (PI / 2f).toFloat()

        fun spawnStartImpact(cx: Float, cy: Float, radius: Float, primary: Int, secondary: Int) {
            Effects.addPersistentEffect(NebulaMark(cx, cy, radius, primary, secondary))
        }
    }

    private class NebulaMark(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        private val colorA: Int,
        private val colorB: Int
    ) : Effects.PersistentEffect {
        private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val starPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
        private val path = Path()
        private var frame = 0
        override val isDone = false

        // ── Star burst ──────────────────────────────────────────────────────────
        private class BurstStar(
            var x: Float, var y: Float,
            var vx: Float, var vy: Float,
            var life: Float,
            val twinkleSpeed: Float,
            val twinkleSeedRad: Float   // twinkleSeed * TAU, pre-multiplied
        )

        private val burstStars: List<BurstStar>
        private val burstPath = Path()
        private val burstPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val BURST_DURATION = 48

        // Cached constant: Settings.screenRatio * 0.5f — only needs to be read once
        private val screenRatioHalf = Settings.screenRatio * 0.5f

        // Pre-computed NebulaMark star geometry constants
        private val angleStep = (2f * PI / 5f).toFloat()
        private val halfStep  = angleStep / 2f
        private val halfPi    = (PI / 2f).toFloat()

        init {
            val tau = (PI * 2).toFloat()
            val count = 22
            burstStars = List(count) { i ->
                val angle = (i.toFloat() / count) * tau + Random.nextFloat() * 0.28f
                val speed = radius * (0.09f + Random.nextFloat() * 0.13f)
                BurstStar(
                    cx, cy,
                    cos(angle) * speed,
                    sin(angle) * speed,
                    1f,
                    0.12f + Random.nextFloat() * 0.18f,
                    Random.nextFloat() * tau   // pre-multiply twinkleSeed * TAU
                )
            }
        }

        private fun drawBurstStar(canvas: Canvas, bcx: Float, bcy: Float, outerR: Float) {
            val innerR = outerR * 0.4f
            burstPath.reset()
            for (i in 0 until 8) {
                val r = if (i % 2 == 0) outerR else innerR
                val px = bcx + cos(STAR_ANGLES[i]) * r
                val py = bcy + sin(STAR_ANGLES[i]) * r
                if (i == 0) burstPath.moveTo(px, py) else burstPath.lineTo(px, py)
            }
            burstPath.close()
            canvas.drawPath(burstPath, burstPaint)
        }

        // ── Core ─────────────────────────────────────────────────────────────────

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            val t = (frame.toFloat() / 150f).coerceIn(0f, 1f)
            val alpha = (255 * (1f - t)).toInt().coerceIn(135, 255)

            // Star burst — active only for the first BURST_DURATION frames
            if (frame <= BURST_DURATION) {
                val burstT = frame.toFloat() / BURST_DURATION
                // Compute lerped color once — it's the same for all burst stars this frame
                val burstColor = Palette.lerpColor(colorA, colorB, burstT)
                val frameF = frame.toFloat()
                for (s in burstStars) {
                    s.x += s.vx
                    s.y += s.vy
                    s.vx *= 0.96f
                    s.vy *= 0.96f
                    s.life = (1f - burstT).coerceAtLeast(0f)
                    // s.twinkleSeedRad already holds twinkleSeed * TAU
                    val twinkle = 0.75f + 0.25f * sin(frameF * s.twinkleSpeed + s.twinkleSeedRad)
                    burstPaint.color = Palette.withAlpha(burstColor, (255f * s.life * s.life).toInt().coerceIn(0, 255))
                    val outerR = screenRatioHalf * s.life * twinkle
                    if (outerR > 0.5f) drawBurstStar(canvas, s.x, s.y, outerR)
                }
            }

            // Fading mini-star at center
            val c = Palette.lerpColor(colorB, colorA, t)
            starPaint.color = Palette.withAlpha(c, alpha)
            val starR = radius * (1f - t * 0.3f).coerceAtLeast(0.5f)
            starPaint.strokeWidth = radius * 0.2f
            buildStar(path, cx, cy, starR, frame * 0.03f)
            canvas.drawPath(path, starPaint)
        }

        private fun buildStar(dst: Path, cx: Float, cy: Float, outer: Float, rotation: Float) {
            val inner = outer * 0.42f
            val roundness = outer * 0.14f
            dst.reset()
            for (i in 0 until 5) {
                val outerAngle = rotation + i * angleStep - halfPi
                val tipX = cx + cos(outerAngle) * outer
                val tipY = cy + sin(outerAngle) * outer
                val perpX = -sin(outerAngle)
                val perpY =  cos(outerAngle)
                val prevInnerAngle = outerAngle - halfStep
                val nextInnerAngle = outerAngle + halfStep
                val prevValX = cx + cos(prevInnerAngle) * inner
                val prevValY = cy + sin(prevInnerAngle) * inner
                val nextValX = cx + cos(nextInnerAngle) * inner
                val nextValY = cy + sin(nextInnerAngle) * inner
                if (i == 0) dst.moveTo(prevValX, prevValY)
                val cp1X = tipX - perpX * roundness
                val cp1Y = tipY - perpY * roundness
                val cp2X = tipX + perpX * roundness
                val cp2Y = tipY + perpY * roundness
                dst.cubicTo(cp1X, cp1Y, tipX, tipY, tipX, tipY)
                dst.cubicTo(tipX, tipY, cp2X, cp2Y, nextValX, nextValY)
            }
            dst.close()
        }
    }
}
