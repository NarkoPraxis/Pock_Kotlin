package gameobjects.puckstyle

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
 *  - Distance from puck center ranges from `radius` (touching outside) to
 *    `radius + maxPullback` (roughly 1.5 puck radii).
 *  - Distance is driven by the finger-drag magnitude (base power), NOT by charge.
 *  - Charge fills the paddle center-out with the shared effect color (purple).
 *  - SweetSpot phase = paddle fully filled + gentle alpha pulse.
 *  - Overcharged phase = paddle drops to its 50%-distance position and renders in
 *    the player's theme color (no purple).
 *  - On release, paddle slides to puck center along the aim vector in a fixed
 *    number of frames (travel time constant regardless of distance).
 *  - On a sweet-spot release only, onSpawnResidual() is called once the strike lands,
 *    allowing subclasses to add persistent effects to Effects.
 *
 * Subclasses override only the visual primitives; the kinematics stay fixed.
 * Subclasses access per-frame puck state via [currentRenderer].
 */
abstract class PaddleLaunchEffect(override val theme: ColorTheme) : LaunchEffect {

    override val zIndex: Int get() = 1

    /** Populated at the start of every [draw] call; valid for the entire frame. */
    protected lateinit var currentRenderer: PuckRenderer
        private set

    protected var frame = 0
        private set

    // --- charge state (SSoT owned here) ---
    private var _currentCharge = 0f
    private var _chargePowerLocked = false

    override val currentCharge: Float get() = _currentCharge
    override val chargePowerLocked: Boolean get() = _chargePowerLocked

    override fun increaseCharge() {
        if (!_chargePowerLocked) {
            if (_currentCharge < Settings.chargeStart) {
                _currentCharge = Settings.chargeStart
            } else if (_currentCharge >= Settings.sweetSpotMax) {
                _currentCharge = Settings.sweetSpotMax * .5f
                _chargePowerLocked = true
            } else {
                _currentCharge += Settings.chargeIncreaseRate
            }
        }
    }

    override fun clearCharge() {
        _currentCharge = 0f
        _chargePowerLocked = false
    }

    // --- phase with listener dispatch ---
    private val phaseListeners = mutableListOf<(ChargePhase) -> Unit>()
    private var _phase: ChargePhase = ChargePhase.Idle
        set(value) {
            if (field != value) {
                field = value
                phaseListeners.forEach { it(value) }
            }
        }

    override val phase: ChargePhase get() = _phase

    override fun registerPhaseCallback(onPhaseChanged: (ChargePhase) -> Unit) {
        phaseListeners.add(onPhaseChanged)
    }

    override fun unregisterAllPhaseCallbacks() {
        phaseListeners.clear()
    }

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
    private var _chargeFillRatio = 0f
    override val chargeFillRatio: Float get() = _chargeFillRatio

    // ----- release anim state -----
    private var releaseFrames = 0
    private var releaseFromX = 0f
    private var releaseFromY = 0f
    private var releaseAimX = 0f
    private var releaseAimY = 0f
    private var releaseSweet = false
    private var releaseOvercharged = false

    private var maxDist = 0f

    // ----- strike callback -----
    private var strikeCallback: (() -> Unit)? = null

    // ----- paint scratch -----
    protected val paddlePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun registerStrikeCallback(onStrike: () -> Unit) {
        strikeCallback = onStrike
    }

    override fun draw(canvas: Canvas, renderer: PuckRenderer) {
        currentRenderer = renderer
        frame++
        updateState(renderer)

        if (releaseFrames > 0) {
            val t = 1f - (releaseFrames.toFloat() / RELEASE_DURATION)
            val cx = lerp(releaseFromX, renderer.x, t)
            val cy = lerp(releaseFromY, renderer.y, t)
            drawStrikingPaddle(canvas, cx, cy, releaseAimX, releaseAimY, releaseSweet, releaseOvercharged, t)
            releaseFrames--
            if (releaseFrames == 0) {
                strikeCallback?.invoke()
                strikeCallback = null
                if (releaseSweet) {
                    onSpawnResidual(renderer.x, renderer.y, releaseAimX, releaseAimY)
                }
            }
        } else if (_phase != ChargePhase.Idle) {
            drawChargingPaddle(canvas)
        }
    }

    override fun onRelease(x: Float, y: Float, radius: Float, sweetSpotHit: Boolean) {
        if (_phase == ChargePhase.Idle) {
            strikeCallback?.invoke()
            reset()
            return
        }
        releaseFromX = paddleX
        releaseFromY = paddleY
        releaseAimX = aimX
        releaseAimY = aimY
        releaseSweet = sweetSpotHit
        releaseOvercharged = _phase == ChargePhase.Overcharged
        releaseFrames = RELEASE_DURATION
        onReleaseSpawn(x, y, radius, releaseSweet, releaseOvercharged)
    }

    override fun reset() {
        releaseFrames = 0
        _phase = ChargePhase.Idle
        _currentCharge = 0f
        _chargePowerLocked = false
        strikeCallback = null
    }

    // ---------- state update ----------

    private fun updateState(renderer: PuckRenderer) {
        val minDist = renderer.radius
        maxDist = renderer.radius * 5f

        val dx = renderer.flingStartX - renderer.flingCurrentX
        val dy = renderer.flingStartY - renderer.flingCurrentY
        val dist = sqrt(dx * dx + dy * dy)
        val maxDrag = Settings.screenRatio * 5f

        if (renderer.isFlingHeld && dist > 1f) {
            aimX = dx / dist
            aimY = dy / dist
            val t = min(dist, maxDrag) / maxDrag
            paddleDistance = minDist + (maxDist - minDist) * t
        } else {
            if (aimX == 0f && aimY == 0f) {
                aimY = if (renderer.isHigh) 1f else -1f
            }
            paddleDistance = minDist
        }

        _phase = when {
            _chargePowerLocked -> ChargePhase.Overcharged
            _currentCharge >= Settings.sweetSpotMin && _currentCharge <= Settings.sweetSpotMax -> ChargePhase.SweetSpot
            renderer.isFlingHeld || _currentCharge > 0f -> ChargePhase.Building
            else -> ChargePhase.Idle
        }

        if (_phase == ChargePhase.Overcharged) {
            val capped = minDist + (maxDist - minDist) * 0.5f
            paddleDistance = min(paddleDistance, capped)
        }

        paddleX = renderer.x - aimX * paddleDistance
        paddleY = renderer.y - aimY * paddleDistance

        val range = max(1f, (Settings.sweetSpotMin - Settings.chargeStart))
        _chargeFillRatio = when (_phase) {
            ChargePhase.SweetSpot -> 1f
            ChargePhase.Overcharged -> 0f
            ChargePhase.Building -> ((_currentCharge - Settings.chargeStart) / range).coerceIn(0f, 1f)
            ChargePhase.Idle -> 0f
        }
    }

    // ---------- drawing primitives (overridable) ----------

    /** Called each frame during Building / SweetSpot / Overcharged. */
    protected open fun drawChargingPaddle(canvas: Canvas) {
        drawPaddleBar(canvas, paddleX, paddleY, aimX, aimY, _chargeFillRatio, _phase, false)
    }

    /** Called each frame during the release-strike animation. */
    protected open fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, overcharged: Boolean, progress: Float
    ) {
        val fill = if (overcharged) 0f else 1f
        val fakePhase = when {
            sweet -> ChargePhase.SweetSpot
            overcharged -> ChargePhase.Overcharged
            else -> ChargePhase.Building
        }
        drawPaddleBar(canvas, cx, cy, aX, aY, fill, fakePhase, true)
    }

    /**
     * Paddle-bar primitive — line perpendicular to the aim vector, centered at (cx,cy).
     * Subclasses may ignore this and render a totally different shape via
     * [drawChargingPaddle] / [drawStrikingPaddle] overrides.
     */
    protected fun drawPaddleBar(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        fillRatio: Float, ph: ChargePhase, striking: Boolean
    ) {
        val half = paddleHalfLength()
        val perpX = -aY
        val perpY = aX
        val thickness = paddleThickness()

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

    /** Hook: allows a subclass to spawn extra one-shot effects at release. */
    protected open fun onReleaseSpawn(x: Float, y: Float, radius: Float, sweet: Boolean, overcharged: Boolean) {}

    /** Hook: called once when the strike animation finishes on a sweet-spot release. Add to Effects.persistentEffects here. */
    protected open fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {}

    // ---------- tuning knobs ----------

    protected open fun paddleHalfLength(): Float = Settings.screenRatio
    protected open fun paddleThickness(): Float = Settings.strokeWidth * 1.4f

    protected fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    protected fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    companion object {
        const val RELEASE_DURATION = 5
    }
}
