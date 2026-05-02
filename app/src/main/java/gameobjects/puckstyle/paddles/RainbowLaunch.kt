package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.Palette
import utility.Effects
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Rainbow paddle — a pill-shaped bar whose base color cycles through the hue spectrum,
 * matching the RainbowSkin/RainbowTail hue animation. A standard charge fill grows from
 * the bar center on top of the base color.
 */
class RainbowLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    private val tailLinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val hueOffset = Palette.themeHue(theme)
    private val shieldHue = Palette.colorHue(theme.shield.primary)

    // ── radius-derived cache ──────────────────────────────────────────────────
    private var cachedRadius = -1f
    private var tailStrokeWidth = 0f   // renderer.radius * 0.44f
    private var barRadius = 0f         // renderer.radius * 0.5f

    private fun ensureCache() {
        if (cachedRadius != renderer.radius) {
            cachedRadius = renderer.radius
            tailStrokeWidth = renderer.radius * 0.44f
            barRadius = renderer.radius * 0.5f
        }
    }

    // ── tail history ─────────────────────────────────────────────────────────
    private var tailHistoryX = FloatArray(TAIL_COUNT)
    private var tailHistoryY = FloatArray(TAIL_COUNT)
    private var tailInitialized = false
    private var lastChargingFrame = -999

    // ── stale tail queue ──────────────────────────────────────────────────────
    private inner class StaleTail(x: FloatArray, y: FloatArray, val captureFrame: Int) {
        val histX = x.copyOf()
        val histY = y.copyOf()
        private var framesAlive = 0
        val globalAlpha: Float get() = (1f - framesAlive.toFloat() / STALE_FADE_FRAMES).coerceAtLeast(0f)
        val isDone: Boolean get() = framesAlive >= STALE_FADE_FRAMES
        fun tick() { framesAlive++ }
    }
    private val staleTails = ArrayDeque<StaleTail>()

    companion object {
        private const val TAIL_COUNT = 40
        private const val STALE_FADE_FRAMES = 25
        private const val TAIL_LAST = TAIL_COUNT - 1
        private const val TAIL_LAST_F = TAIL_LAST.toFloat()

        fun spawnRainbow(rx: Float, ry: Float, radius: Float) {
            Effects.addPersistentEffect(SpectralGleam(rx, ry, radius))
        }

        fun spawnCelebration(cx: Float, cy: Float, radius: Float): Effects.PersistentEffect {
            return SpectralGleam(cx, cy, radius, celebrationMaxAlpha = 255, fadeOut = true)
        }
    }

    override val zIndex: Int
        get() = -1

    /** Push the current paddle center into the front of the history ring. */
    private fun pushHistory(x: Float, y: Float) {
        if (!tailInitialized) {
            tailHistoryX.fill(x)
            tailHistoryY.fill(y)
            tailInitialized = true
            return
        }
        for (i in TAIL_LAST downTo 1) {
            tailHistoryX[i] = tailHistoryX[i - 1]
            tailHistoryY[i] = tailHistoryY[i - 1]
        }
        tailHistoryX[0] = x
        tailHistoryY[0] = y
    }

    /** Snapshot the working tail into the stale queue and reset working arrays. */
    private fun captureStaleTail() {
        if (!tailInitialized) return
        staleTails.addLast(StaleTail(tailHistoryX, tailHistoryY, frame))
        tailHistoryX = FloatArray(TAIL_COUNT)
        tailHistoryY = FloatArray(TAIL_COUNT)
        tailInitialized = false
    }

    /** Draw line segments between consecutive history positions with hue cycling and alpha fade. */
    private fun drawTailLines(canvas: Canvas) {
        if (!tailInitialized) return
        tailLinePaint.strokeWidth = tailStrokeWidth

        // Compute per-frame shielded oscillation once outside the loop.
        val isInert = renderer.isInert
        val isShielded = renderer.shielded
        val shieldBase = if (isShielded) shieldHue + sin(frame * 0.04f) * 30f else 0f
        val frameHue = frame * 4f + hueOffset

        for (i in 0 until TAIL_LAST) {
            val ratio = i.toFloat() / TAIL_LAST_F
            val cycleHue = frameHue - i * 15f
            val color = when {
                isInert    -> Palette.hsv(cycleHue, 0.10f, 0.90f)
                isShielded -> Palette.hsvThemed(shieldBase - i * 15f)
                else       -> Palette.hsvThemed(cycleHue)
            }
            tailLinePaint.color = Palette.withAlpha(color, 255)
            canvas.drawLine(
                tailHistoryX[i], tailHistoryY[i],
                tailHistoryX[i + 1], tailHistoryY[i + 1],
                tailLinePaint
            )
        }
    }

    /**
     * Draw a captured stale tail with frozen hue (from captureFrame) and a global alpha
     * multiplier so it fades independently of any active tail.
     */
    private fun drawStaleTailLines(canvas: Canvas, tail: StaleTail) {
        tailLinePaint.strokeWidth = tailStrokeWidth
        val globalAlpha = tail.globalAlpha
        val frozenFrameHue = tail.captureFrame * 4f + hueOffset
        for (i in 0 until TAIL_LAST) {
            val ratio = i.toFloat() / TAIL_LAST_F
            val frozenHue = frozenFrameHue - i * 15f
            val color = Palette.hsvThemed(frozenHue)
            val segAlpha = (255f * (1f - ratio)).toInt().coerceIn(0, 255)
            val finalAlpha = (segAlpha * globalAlpha).toInt().coerceIn(0, 255)
            tailLinePaint.color = Palette.withAlpha(color, finalAlpha)
            canvas.drawLine(
                tail.histX[i], tail.histY[i],
                tail.histX[i + 1], tail.histY[i + 1],
                tailLinePaint
            )
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Base hue-cycling color for the bar, mirroring RainbowSkin.drawBody logic. */
    private fun barColor(ph: ChargePhase, f: Int): Int {
        val baseHue = f * 4f + hueOffset
        val osc = sin(f * 0.04f) * 30f
        return when {
            ph == ChargePhase.Inert -> theme.inert.secondary
            renderer.shielded       -> Palette.hsvThemed(shieldHue + osc)
            else                    -> Palette.hsvThemed(baseHue)
        }
    }

    // ── draw helpers ─────────────────────────────────────────────────────────

    protected val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun drawBar(
        canvas: Canvas,
        cx: Float, cy: Float,
        aX: Float, aY: Float,
        ph: ChargePhase,
        fill: Float,
        f: Int
    ) {
        if (renderer.isInert) {
            fillPaint.color = responsivePrimary
            canvas.drawCircle(cx, cy, barRadius, fillPaint)
        } else {
            fillPaint.color = barColor(ph, f)
            canvas.drawCircle(cx, cy, barRadius, fillPaint)
        }

        if (fill > 0f && !renderer.isInert) {
            paddlePaint.color = theme.shield.primary
            canvas.drawCircle(cx, cy, barRadius * chargeFillRatio, paddlePaint)
        }

        paddlePaint.strokeWidth = Settings.strokeWidth
        paddlePaint.alpha = 255
        paddlePaint.color = if (phase == ChargePhase.Inert) theme.inert.secondary else responsiveSecondary
        canvas.drawCircle(cx, cy, barRadius, paddlePaint)
    }

    // ── overrides ─────────────────────────────────────────────────────────────

    override fun draw(canvas: Canvas) {
        ensureCache()
        // Tick all stale tails; remove expired ones; draw survivors — no iterator allocation.
        staleTails.removeAll { stale ->
            stale.tick()
            stale.isDone
        }
        for (stale in staleTails) {
            drawStaleTailLines(canvas, stale)
        }
        super.draw(canvas)
    }

    override fun drawChargingPaddle(canvas: Canvas) {
        lastChargingFrame = frame
        val dx = renderer.x - paddleX
        val dy = renderer.y - paddleY
        val dist = hypot(dx, dy).coerceAtLeast(0.001f)
        pushHistory(paddleX, paddleY)
        drawTailLines(canvas)
        drawBar(canvas, paddleX, paddleY, dx / dist, dy / dist, phase, chargeFillRatio, frame)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        lastChargingFrame = frame
        val ph = when {
            sweet    -> ChargePhase.SweetSpot
            fatigued -> ChargePhase.Inert
            else     -> ChargePhase.Building
        }
        val fill = when {
            sweet    -> 1f
            fatigued -> 0f
            else     -> 1f
        }
        pushHistory(cx, cy)
        drawTailLines(canvas)
        drawBar(canvas, cx, cy, aX, aY, ph, fill, frame)
    }

    override fun drawIdlePaddle(canvas: Canvas) {
        super.drawIdlePaddle(canvas)
        // Detect the first idle frame after charging/striking and snapshot the tail.
        // Draw the capture immediately this frame (at full alpha) to avoid a one-frame gap.
        if (tailInitialized && frame - lastChargingFrame == 1) {
            captureStaleTail()
            staleTails.lastOrNull()?.let { drawStaleTailLines(canvas, it) }
        }
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(SpectralGleam(rx, ry, renderer.radius))
    }


    private class SpectralGleam(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        private val celebrationMaxAlpha: Int = MAX_ALPHA,
        private val fadeOut: Boolean = false
    ) : Effects.PersistentEffect {
        private val paint = Paint().apply {
            isAntiAlias = true; style = Paint.Style.STROKE
        }
        private var frame = 0
        private val maxRadius = radius * 3f
        private val fixedStrokeWidth = Settings.strokeWidth * (2f / 3f)

        private var _isDone = false
        override val isDone: Boolean get() = _isDone

        companion object {
            private const val NUM_RINGS = 6
            private const val GROW_FRAMES = 30
            private const val HOLD_FRAMES = 5
            private const val FADE_FRAMES = 60
            private const val MAX_ALPHA = 100
            private const val PULSE_SPEED = 6 // frames per ring step; raise to 3, 4, 5… to slow further
        }

        override fun step() {
            frame++
            if (fadeOut && frame > GROW_FRAMES + HOLD_FRAMES + FADE_FRAMES) _isDone = true
        }

        override fun draw(canvas: Canvas) {
            val baseAlpha = when {
                frame < GROW_FRAMES -> ((frame.toFloat() / GROW_FRAMES) * celebrationMaxAlpha).toInt()
                !fadeOut -> celebrationMaxAlpha
                frame < GROW_FRAMES + HOLD_FRAMES -> celebrationMaxAlpha
                else -> {
                    val fadeProgress = (frame - GROW_FRAMES - HOLD_FRAMES).toFloat() / FADE_FRAMES
                    ((1f - fadeProgress) * celebrationMaxAlpha).toInt()
                }
            }
            if (baseAlpha <= 0) return

            val growT = (frame.toFloat() / GROW_FRAMES).coerceIn(0f, 1f)
            paint.strokeWidth = fixedStrokeWidth

            // Pulse wave: advances one ring every PULSE_SPEED frames, looping.
            val wavePos = (frame / PULSE_SPEED) % (NUM_RINGS + 4)

            for (i in 0 until NUM_RINGS) {
                val finalRingRadius = maxRadius - (NUM_RINGS - 1 - i) * fixedStrokeWidth
                val ringRadius = finalRingRadius * growT
                if (ringRadius <= 0f) continue

                val hue = i * (360f / NUM_RINGS) + frame * 0.5f
                paint.color = Palette.hsvThemed(hue)

                val dist = abs(i - wavePos)
                val alphaBonus = when (dist) { 0 -> 50; 1 -> 30; 2 -> 15; 3 -> 5; else -> 0 }
                paint.alpha = (baseAlpha + alphaBonus).coerceIn(0, 255)

                canvas.drawCircle(cx, cy, ringRadius, paint)
            }
        }
    }
}
