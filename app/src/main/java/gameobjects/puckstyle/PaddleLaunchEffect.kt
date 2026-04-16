package gameobjects.puckstyle

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Player
import gameobjects.Puck
import gameobjects.Settings
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Base class for the tethered-paddle launch visual shared by every ball type.
 *
 * Kinematics (identical for every ball type):
 *  - Paddle sits behind the puck along the aim vector.
 *  - Distance from puck center ranges from `puck.radius` (touching outside) to
 *    `puck.radius + maxPullback` (roughly 1.5 puck radii).
 *  - Distance is driven by the finger-drag magnitude (base power), NOT by charge.
 *  - Charge fills the paddle center-out with the shared effect color (purple).
 *  - SweetSpot phase = paddle fully filled + gentle alpha pulse.
 *  - Overcharged phase = paddle drops to its 50%-distance position and renders in
 *    the player's theme color (no purple).
 *  - On release, paddle slides to puck center along the aim vector in a fixed
 *    number of frames (travel time constant regardless of distance).
 *  - On a sweet-spot release only, a themed residual lingers briefly.
 *
 * Subclasses override only the visual primitives; the kinematics stay fixed.
 */
abstract class PaddleLaunchEffect(override val theme: ColorTheme) : LaunchEffect {

    protected var frame = 0
        private set

    protected var phase: ChargePhase = ChargePhase.Idle
        private set

    /** Unit vector pointing in the direction the puck will launch. */
    protected var aimX = 0f
        private set
    protected var aimY = 0f
        private set

    /** Distance from puck center to paddle center along -aim (opposite launch dir). */
    protected var paddleDistance = 0f
        private set

    /** Paddle center in world coords. */
    protected var paddleX = 0f
        private set
    protected var paddleY = 0f
        private set

    /** 0..1 — how far the center-out charge fill has travelled. 1 in SweetSpot. */
    protected var chargeFillRatio = 0f
        private set

    // ----- release anim state -----
    private var releaseFrames = 0
    private var releaseFromX = 0f
    private var releaseFromY = 0f
    private var releaseAimX = 0f
    private var releaseAimY = 0f
    private var releaseSweet = false
    private var releaseOvercharged = false

    // ----- residual state -----
    private var residualFrames = 0
    private var residualX = 0f
    private var residualY = 0f

    // ----- paint scratch -----
    protected val paddlePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    protected val residualPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun draw(canvas: Canvas, player: Player) {
        frame++
        updateState(player)

        if (releaseFrames > 0) {
            val t = 1f - (releaseFrames.toFloat() / RELEASE_DURATION)
            val cx = lerp(releaseFromX, player.puck.x, t)
            val cy = lerp(releaseFromY, player.puck.y, t)
            drawStrikingPaddle(canvas, player.puck, cx, cy, releaseAimX, releaseAimY, releaseSweet, releaseOvercharged, t)
            releaseFrames--
            if (releaseFrames == 0 && releaseSweet) {
                residualX = player.puck.x
                residualY = player.puck.y
                residualFrames = RESIDUAL_DURATION
                onSpawnResidual(player.puck, residualX, residualY, releaseAimX, releaseAimY)
            }
        } else if (phase != ChargePhase.Idle) {
            drawChargingPaddle(canvas, player.puck)
        }

        if (residualFrames > 0) {
            val r = residualFrames.toFloat() / RESIDUAL_DURATION
            drawResidual(canvas, player.puck, residualX, residualY, r)
            residualFrames--
        }
    }

    override fun onRelease(player: Player, sweetSpotHit: Boolean) {
        if (phase == ChargePhase.Idle) {
            reset(); return
        }
        releaseFromX = paddleX
        releaseFromY = paddleY
        releaseAimX = aimX
        releaseAimY = aimY
        releaseSweet = sweetSpotHit
        releaseOvercharged = phase == ChargePhase.Overcharged
        releaseFrames = RELEASE_DURATION
        onReleaseSpawn(player.puck, releaseSweet, releaseOvercharged)
    }

    override fun reset() {
        releaseFrames = 0
        residualFrames = 0
        phase = ChargePhase.Idle
    }

    // ---------- state update ----------

    private fun updateState(player: Player) {
        val puck = player.puck
        val minDist = puck.radius
        val maxDist = puck.radius * 2.5f

        val dx = player.flingStart.x - player.flingCurrent.x
        val dy = player.flingStart.y - player.flingCurrent.y
        val dist = sqrt(dx * dx + dy * dy)
        val maxDrag = Settings.screenRatio * 5f

        if (player.isFlingHeld && dist > 1f) {
            aimX = dx / dist
            aimY = dy / dist
            val t = min(dist, maxDrag) / maxDrag
            paddleDistance = minDist + (maxDist - minDist) * t
        } else {
            if (aimX == 0f && aimY == 0f) {
                aimY = if (player.isHigh) 1f else -1f
            }
            paddleDistance = minDist
        }

        phase = when {
            player.chargePowerLocked -> ChargePhase.Overcharged
            player.charge >= Settings.sweetSpotMin && player.charge <= Settings.sweetSpotMax -> ChargePhase.SweetSpot
            player.isFlingHeld || player.charge > 0f -> ChargePhase.Building
            else -> ChargePhase.Idle
        }

        if (phase == ChargePhase.Overcharged) {
            val capped = minDist + (maxDist - minDist) * 0.5f
            paddleDistance = min(paddleDistance, capped)
        }

        paddleX = puck.x - aimX * paddleDistance
        paddleY = puck.y - aimY * paddleDistance

        val range = max(1f, (Settings.sweetSpotMin - Settings.chargeStart))
        chargeFillRatio = when (phase) {
            ChargePhase.SweetSpot -> 1f
            ChargePhase.Overcharged -> 0f
            ChargePhase.Building -> ((player.charge - Settings.chargeStart) / range).coerceIn(0f, 1f)
            ChargePhase.Idle -> 0f
        }
    }

    // ---------- drawing primitives (overridable) ----------

    /** Called each frame during Building / SweetSpot / Overcharged. */
    protected open fun drawChargingPaddle(canvas: Canvas, puck: Puck) {
        drawPaddleBar(canvas, puck, paddleX, paddleY, aimX, aimY, chargeFillRatio, phase, false)
    }

    /** Called each frame during the release-strike animation. */
    protected open fun drawStrikingPaddle(
        canvas: Canvas, puck: Puck,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, overcharged: Boolean, progress: Float
    ) {
        val fill = when {
            sweet -> 1f
            overcharged -> 0f
            else -> 1f
        }
        val fakePhase = when {
            sweet -> ChargePhase.SweetSpot
            overcharged -> ChargePhase.Overcharged
            else -> ChargePhase.Building
        }
        drawPaddleBar(canvas, puck, cx, cy, aX, aY, fill, fakePhase, true)
    }

    /**
     * Paddle-bar primitive — line perpendicular to the aim vector, centered at (cx,cy).
     * Subclasses may ignore this and render a totally different shape via
     * `drawChargingPaddle` / `drawStrikingPaddle` overrides.
     */
    protected fun drawPaddleBar(
        canvas: Canvas, puck: Puck,
        cx: Float, cy: Float, aX: Float, aY: Float,
        fillRatio: Float, ph: ChargePhase, striking: Boolean
    ) {
        val half = paddleHalfLength(puck)
        val perpX = -aY
        val perpY = aX
        val thickness = paddleThickness(puck)

        val baseColor = theme.secondary
        val chargeColor = theme.accent
        val pulse = if (ph == ChargePhase.SweetSpot) 0.7f + 0.3f * sin(frame * 0.35f) else 1f

        paddlePaint.strokeWidth = thickness

        // Base (player color) — full paddle.
        paddlePaint.color = baseColor
        paddlePaint.alpha = 255
        canvas.drawLine(
            cx - perpX * half, cy - perpY * half,
            cx + perpX * half, cy + perpY * half,
            paddlePaint
        )

        // Center-out charge fill (purple).
        if (fillRatio > 0f) {
            val fillHalf = half * fillRatio
            paddlePaint.color = chargeColor
            paddlePaint.alpha = (255 * pulse).toInt().coerceIn(0, 255)
            canvas.drawLine(
                cx - perpX * fillHalf, cy - perpY * fillHalf,
                cx + perpX * fillHalf, cy + perpY * fillHalf,
                paddlePaint
            )
        }
    }

    protected open fun drawResidual(canvas: Canvas, puck: Puck, rx: Float, ry: Float, remaining: Float) {
        val a = (200 * remaining).toInt().coerceIn(0, 255)
        residualPaint.color = theme.accent
        residualPaint.alpha = a
        residualPaint.strokeWidth = Settings.strokeWidth * 0.6f
        canvas.drawCircle(rx, ry, puck.radius * (1.4f - remaining * 0.4f), residualPaint)
    }

    /** Hook: allows a subclass to spawn extra one-shot effects at release. */
    protected open fun onReleaseSpawn(puck: Puck, sweet: Boolean, overcharged: Boolean) {}

    /** Hook: allows a subclass to spawn extra effects once the strike lands. */
    protected open fun onSpawnResidual(puck: Puck, rx: Float, ry: Float, aX: Float, aY: Float) {}

    // ---------- tuning knobs ----------

    protected open fun paddleHalfLength(puck: Puck): Float = Settings.screenRatio
    protected open fun paddleThickness(puck: Puck): Float = Settings.strokeWidth * 1.4f

    protected fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    protected fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    companion object {
        const val RELEASE_DURATION = 5
        const val RESIDUAL_DURATION = 10
    }
}
