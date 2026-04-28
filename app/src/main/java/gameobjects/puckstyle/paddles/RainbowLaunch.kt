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
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Rainbow paddle — a pill-shaped bar whose base color cycles through the hue spectrum,
 * matching the RainbowSkin/RainbowTail hue animation. A standard charge fill grows from
 * the bar center on top of the base color.
 */
class RainbowLaunch(theme: ColorTheme, renderer: PuckRenderer) : PaddleLaunchEffect(theme, renderer) {

    private val tailLinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val hueOffset = Palette.themeHue(theme)
    private val shieldHue = Palette.colorHue(theme.shield.primary)

    // ── tail history ─────────────────────────────────────────────────────────
    private var tailHistoryX = FloatArray(TAIL_COUNT)
    private var tailHistoryY = FloatArray(TAIL_COUNT)
    private var tailInitialized = false
    private var lastChargingFrame = -999

    companion object {
        private const val TAIL_COUNT = 40
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
        for (i in TAIL_COUNT - 1 downTo 1) {
            tailHistoryX[i] = tailHistoryX[i - 1]
            tailHistoryY[i] = tailHistoryY[i - 1]
        }
        tailHistoryX[0] = x
        tailHistoryY[0] = y
    }

    /** Draw line segments between consecutive history positions with hue cycling and alpha fade. */
    private fun drawTailLines(canvas: Canvas) {
        if (!tailInitialized) return
        tailLinePaint.strokeWidth = renderer.radius * 0.44f
        val last = TAIL_COUNT - 1
        for (i in 0 until last) {
            val ratio = i.toFloat() / last.toFloat()
            val cycleHue = frame * 4f + hueOffset - i * 15f
            val color = when {
                renderer.isInert -> Palette.hsv(cycleHue, 0.10f, 0.90f)
                renderer.shielded -> Palette.hsvThemed(shieldHue + kotlin.math.sin(frame * 0.04f) * 30f - i * 15f)
                else -> Palette.hsvThemed(cycleHue)
            }
            val alpha = (255f * (1f - ratio)).toInt().coerceIn(0, 255)
            tailLinePaint.color = Palette.withAlpha(color, alpha)
            canvas.drawLine(
                tailHistoryX[i], tailHistoryY[i],
                tailHistoryX[i + 1], tailHistoryY[i + 1],
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

    /**
     * Draw the pill bar at (cx, cy), oriented along the aim vector (aX, aY),
     * then overlay the charge fill growing from center.
     *
     * All dimensions mirror drawPaddleBar from the base class:
     *   half-length = paddleHalfLength()
     *   half-thickness = paddleThickness() / 2
     */
    private fun drawBar(
        canvas: Canvas,
        cx: Float, cy: Float,
        aX: Float, aY: Float,
        ph: ChargePhase,
        fill: Float,
        f: Int
    ) {
        val half = paddleHalfLength()
        val perpX = -aY
        val perpY = aX
        val thickness = paddleThickness()

        val isInert = renderer.isInert || ph == ChargePhase.Inert
        val stateColors = when {
            isInert -> theme.inert
            renderer.shielded -> theme.shield
            else -> theme.main
        }
        val hitStunBlend = renderer.hitStunned && !isInert
        val hitStunR = if (hitStunBlend) renderer.hitStunRatio else 0f
        val baseColor = when {
            hitStunBlend -> blendColor(stateColors.secondary, theme.inert.secondary, hitStunR)
            else -> barColor(ph, f)
        }
        val chargeColor = if (hitStunBlend)
            blendColor(theme.shield.primary, theme.inert.primary, hitStunR)
        else
            theme.shield.primary
        val pulse = if (ph == ChargePhase.SweetSpot) 0.7f + 0.3f * sin(frame * 0.35f) else 1f

        paddlePaint.strokeWidth = thickness

        paddlePaint.color = baseColor
        paddlePaint.alpha = 255
        canvas.drawCircle(cx, cy, renderer.radius * .25f, paddlePaint)

        if (fill > 0f && !isInert) {
            paddlePaint.color = chargeColor
            paddlePaint.alpha = (255 * pulse).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, renderer.radius * .20f * chargeFillRatio, paddlePaint)
        }
    }

    // ── overrides ─────────────────────────────────────────────────────────────

    override fun drawChargingPaddle(canvas: Canvas) {
        if (frame - lastChargingFrame > 1) {
            tailHistoryX = FloatArray(TAIL_COUNT)
            tailHistoryY = FloatArray(TAIL_COUNT)
            tailInitialized = false
        }
        lastChargingFrame = frame
        // Derive aim direction from the vector pointing from the paddle toward the puck.
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

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(SpectralGleam(rx, ry, aX, aY, renderer.radius))
    }

    override fun drawIdlePaddle(canvas: Canvas) {
        super.drawIdlePaddle(canvas)
        drawTailLines(canvas)
    }

    private class SpectralGleam(
        private val cx: Float, private val cy: Float,
        private val aX: Float, private val aY: Float,
        private val radius: Float
    ) : Effects.PersistentEffect {
        private val paint = Paint().apply {
            isAntiAlias = true; style = Paint.Style.STROKE
        }
        private var frame = 0
        override val isDone = false

        companion object {
            private const val NUM_RINGS = 6
            private const val GROW_FRAMES = 30
            private const val RAMP_FRAMES = 60
            private const val MAX_ALPHA = 50
        }

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            val sw = Settings.strokeWidth * (2f / 3f)
            val maxStartRadius = radius * 2f - (NUM_RINGS - 1) * sw
            val growT = (frame.toFloat() / GROW_FRAMES).coerceIn(0f, 1f)
            val alpha = if (frame < RAMP_FRAMES) {
                ((frame.toFloat() / RAMP_FRAMES) * MAX_ALPHA).toInt().coerceIn(0, MAX_ALPHA)
            } else {
                MAX_ALPHA
            }
            paint.strokeWidth = sw
            for (i in 0 until NUM_RINGS) {
                val finalRingRadius = maxStartRadius + i * sw
                val ringRadius = finalRingRadius * growT
                val hue = i * (360f / NUM_RINGS) + frame * 0.5f
                paint.color = Palette.hsvThemed(hue)
                paint.alpha = alpha
                canvas.drawCircle(cx, cy, ringRadius, paint)
            }
        }
    }
}
