package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import utility.Effects
import kotlin.math.sin
import kotlin.math.sqrt

/** Tethered mini-ghost orb: a scaled-down copy of the GhostSkin with a ghost tail. Sweet-spot leaves a
 *  persistent spirit that animates back to the host puck when a goal is scored. */
class GhostLaunch(theme: ColorTheme, renderer: PuckRenderer) : PaddleLaunchEffect(theme, renderer) {

    private data class AuraConfig(val baseMult: Float, val amp: Float, val phase: Float, val alpha: Int, val strokeMult: Float)

    override var minDist: Float = 0f
        get() = 0f

    override val zIndex: Int
        get() = 2
    private val auraRings = listOf(
        AuraConfig(1.10f, 0.06f, 0.0f, 70, 1.6f),
        AuraConfig(1.20f, 0.08f, 1.0f, 45, 1.2f),
        AuraConfig(1.35f, 0.10f, 2.2f, 25, 2.0f),
        AuraConfig(1.55f, 0.12f, 3.7f, 12, 2.8f)
    )

    private val bodyPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val glowPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }

    private val tailPositions = ArrayDeque<Pair<Float, Float>>()
    private val tailCapacity = 9
    private val tailFillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val tailGlowPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }

    private val orbRadius: Float
        get() = renderer.radius * .6f

    override fun drawChargingPaddle(canvas: Canvas) {
        tailPositions.addLast(paddleX to paddleY)
        if (tailPositions.size > tailCapacity) tailPositions.removeFirst()
        drawGhostOrb(canvas, paddleX, paddleY)
        drawGhostTail(canvas, tailPositions,  orbRadius, responsivePrimary, tailGlowPaint, tailFillPaint, chargeFillRatio, theme.shield.primary)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas, cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        drawGhostOrb(canvas, cx, cy)
    }

    override fun drawIdlePaddle(canvas: Canvas) {
       if (tailPositions.size > 0) tailPositions.removeFirst()
    }

    private fun drawGhostOrb(canvas: Canvas, cx: Float, cy: Float) {
        val pulse = 0.88f + 0.12f * sin(frame * 0.18f)
        val r = orbRadius * pulse
        val glowColor = if (phase == ChargePhase.SweetSpot) theme.shield.primary else responsivePrimary
        val sw = Settings.strokeWidth * 0.7f

        for (ring in auraRings) {
            val auraR = r * ring.baseMult + r * ring.amp * sin(frame * 0.04f + ring.phase)
            glowPaint.color = Palette.withAlpha(glowColor, ring.alpha)
            glowPaint.strokeWidth = sw * ring.strokeMult
            canvas.drawCircle(cx, cy, auraR, glowPaint)
        }

        bodyPaint.color = Color.argb(120, 255, 255, 255)
        canvas.drawCircle(cx, cy, r, bodyPaint)

        glowPaint.color = Color.argb(200, 255, 255, 255)
        glowPaint.strokeWidth = sw
        canvas.drawCircle(cx, cy, r, glowPaint)

        val innerR = r * 0.75f + r * 0.1f * sin(frame * 0.025f + 5f)
        glowPaint.strokeWidth = sw * 0.7f
        glowPaint.color = Color.argb(160, 255, 255, 255)
        canvas.drawCircle(cx, cy, innerR, glowPaint)

        if (chargeFillRatio > 0f) {
            bodyPaint.alpha = 255
            bodyPaint.color = Palette.lerpColor(theme.shield.primary, theme.shield.secondary, sin(frame * 0.25f) * 0.5f + 0.5f)
            canvas.drawCircle(cx, cy, r * chargeFillRatio, bodyPaint)
        }
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(GhostSpirit(rx, ry, renderer.radius * 0.5f, theme.shield.primary, renderer))
    }

    companion object {
        fun spawnImpact(cx: Float, cy: Float, radius: Float, color: Int, renderer: PuckRenderer) {
            Effects.addPersistentEffect(GhostSpirit(cx, cy, radius, color, renderer))
        }

        /** Ghost tail: two-pass (glow rings then white fills) matching GhostTail's style.
         *  positions[0] = oldest (most faded), positions.last() = newest (closest to body). */
        fun drawGhostTail(
            canvas: Canvas,
            positions: List<Pair<Float, Float>>,
            baseR: Float,
            glowColor: Int,
            glowPaint: Paint,
            fillPaint: Paint,
            chargeFill: Float,
            chargeColor: Int
        ) {
            if (positions.size < 2) return
            val sw = Settings.strokeWidth * 0.7f
            val outlineR = baseR * 1.15f
            for (i in positions.indices) {
                val ratio = (positions.size - 1 - i).toFloat() / positions.size
                val r = outlineR * (1f - ratio * 0.9f)
                glowPaint.color = glowColor
                glowPaint.strokeWidth = sw * 1.2f
                canvas.drawCircle(positions[i].first, positions[i].second, r, glowPaint)
            }
            for (i in positions.indices) {
                val ratio = (positions.size - 1 - i).toFloat() / positions.size
                val r = baseR * (1f - ratio * 0.9f)
                fillPaint.color = Color.WHITE
                fillPaint.alpha = 255
                canvas.drawCircle(positions[i].first, positions[i].second, r, fillPaint)
                if (chargeFill > 0f) {
                    fillPaint.alpha = 120
                    fillPaint.color = chargeColor
                    canvas.drawCircle(positions[i].first, positions[i].second, r * chargeFill, fillPaint)
                }
            }
        }
    }

    private class GhostSpirit(
        private var cx: Float,
        private var cy: Float,
        private val baseRadius: Float,
        private val color: Int,
        private val renderer: PuckRenderer
    ) : Effects.PersistentEffect {

        private data class AuraConfig(val baseMult: Float, val amp: Float, val phase: Float, val alpha: Int, val strokeMult: Float)
        private val auraRings = listOf(
            AuraConfig(1.10f, 0.06f, 0.0f, 70, 1.6f),
            AuraConfig(1.20f, 0.08f, 1.0f, 45, 1.2f),
            AuraConfig(1.35f, 0.10f, 2.2f, 25, 2.0f),
            AuraConfig(1.55f, 0.12f, 3.7f, 12, 2.8f)
        )

        private val bodyPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val glowPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
        private val tailFillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val tailGlowPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }

        private var frame = 0
        private var returning = false
        private var returnFrames = 0
        private val maxReturnFrames = 300

        private val tailPositions = ArrayDeque<Pair<Float, Float>>()
        private val tailCapacity = 20

        private var _isDone = false
        override val isDone: Boolean get() = _isDone

        override fun onScoreSignal(): Boolean {
            returning = true
            return true
        }

        override fun step() {
            frame++
            if (!returning) return
            returnFrames++
            if (returnFrames > maxReturnFrames) { _isDone = true; return }
            val tx = renderer.x
            val ty = renderer.y
            val dx = tx - cx
            val dy = ty - cy
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < baseRadius * 0.2f) {
                if (tailPositions.isNotEmpty()) {
                    cx = renderer.x
                    cy = renderer.y
                    tailPositions.removeFirst()
                } else {
                    _isDone = true;
                    return
                }
            } else {
                tailPositions.addLast(cx to cy)

                if (tailPositions.size > tailCapacity) tailPositions.removeFirst()
                // Proportional speed with floor/cap for smooth pursuit
                val speed = (dist * 0.08f)
                    .coerceAtLeast(Settings.screenRatio * 0.12f)
                    .coerceAtMost(Settings.screenRatio * 0.5f)
                cx += (dx / dist) * speed
                cy += (dy / dist) * speed
            }
            // Record current position in tail before moving so trail lags behind

        }

        override fun draw(canvas: Canvas) {
            // Oscillate between 1.25x (peak) and 0.75x (trough) of the paddle radius
            val sizePulse = 1.0f + 0.25f * sin(frame * 0.052f)
            val r = baseRadius * sizePulse
            val glowColor = color
            val sw = Settings.strokeWidth * 0.7f

            for (ring in auraRings) {
                val auraR = r * ring.baseMult + r * ring.amp * sin(frame * 0.04f + ring.phase)
                glowPaint.color = Palette.withAlpha(glowColor, ring.alpha)
                glowPaint.strokeWidth = sw * ring.strokeMult
                canvas.drawCircle(cx, cy, auraR, glowPaint)
            }

            bodyPaint.color = Color.argb(120, 255, 255, 255)
            canvas.drawCircle(cx, cy, r, bodyPaint)

            glowPaint.color = Color.argb(200, 255, 255, 255)
            glowPaint.strokeWidth = sw
            canvas.drawCircle(cx, cy, r, glowPaint)

            val innerR = r * 0.75f + r * 0.1f * sin(frame * 0.025f + 5f)
            glowPaint.strokeWidth = sw * 0.7f
            glowPaint.color = Color.argb(160, 255, 255, 255)
            canvas.drawCircle(cx, cy, innerR, glowPaint)

            if (returning && tailPositions.size > 1) {
                drawGhostTail(canvas, tailPositions, r, glowColor, tailGlowPaint, tailFillPaint, 0f, bodyPaint.color)
            }
        }
    }
}
