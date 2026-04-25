package gameobjects.puckstyle

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Settings
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Base class for the tethered-paddle launch visual shared by every ball type.
 *
 * Kinematics (identical for every ball type):
 *  - Paddle sits behind the puck along the aim vector.
 *  - Distance from puck center = drag contribution (0..50%) + charge fill contribution (0..50%).
 *  - Charge state machine: Idle → Building → SweetSpot (timed window) → Draining → Inert.
 *  - Releasing during SweetSpot grants shield + full launch power.
 *  - Releasing during Draining launches at current reduced charge, no shield.
 *  - Releasing during Inert plays an impotent strike animation with no launch.
 *  - On a sweet-spot release only, onSpawnResidual() is called once the strike lands.
 *
 * Subclasses override only the visual primitives; the kinematics stay fixed.
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
    @Deprecated("Superseded by Draining/Inert phases — retained for interface compat")
    private var _chargePowerLocked = false

    private var _sweetSpotFramesRemaining: Int = 0
    private var _drainHoldFrames: Int = 0

    /** When set, the phase transition to Idle is deferred until the strike animation ends. */
    private var _phaseAfterClear: ChargePhase? = null

    override val currentCharge: Float get() = _currentCharge
    @Suppress("DEPRECATION")
    override val chargePowerLocked: Boolean get() = _chargePowerLocked

    val isInSweetSpotWindow: Boolean get() = _phase == ChargePhase.SweetSpot

    override fun increaseCharge() {
        if (_phase != ChargePhase.Idle && _phase != ChargePhase.Building) return
        if (_currentCharge < Settings.chargeStart) {
            _currentCharge = Settings.chargeStart
        } else {
            _currentCharge = (_currentCharge + Settings.chargeIncreaseRate)
                .coerceAtMost(Settings.sweetSpotMax.toFloat())
        }
        if (_phase == ChargePhase.Idle) _phase = ChargePhase.Building
    }

    override fun clearCharge() {
        _currentCharge = 0f
        @Suppress("DEPRECATION")
        _chargePowerLocked = false
        _sweetSpotFramesRemaining = 0
        _drainHoldFrames = 0
        if (releaseFrames > 0) {
            _phaseAfterClear = ChargePhase.Idle
        } else {
            _phase = ChargePhase.Idle
        }
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

    /** 0..1 — how far the center-out charge fill has travelled. 1.0 in SweetSpot. */
    private var _chargeFillRatio = 0f
    override val chargeFillRatio: Float get() = _chargeFillRatio

    // ----- release anim state -----
    private var releaseFrames = 0
    private var releaseFromX = 0f
    private var releaseFromY = 0f
    private var releaseAimX = 0f
    private var releaseAimY = 0f
    private var releaseSweet = false
    private var releaseFatigued = false

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
            drawStrikingPaddle(canvas, cx, cy, releaseAimX, releaseAimY, releaseSweet, releaseFatigued, t)
            releaseFrames--
            if (releaseFrames == 0) {
                strikeCallback?.invoke()
                strikeCallback = null
                if (releaseSweet) {
                    onSpawnResidual(renderer.x, renderer.y, releaseAimX, releaseAimY)
                }
                _phaseAfterClear?.let { _phase = it; _phaseAfterClear = null }
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
        releaseFatigued = _phase == ChargePhase.Inert
        releaseFrames = RELEASE_DURATION
        onReleaseSpawn(x, y, radius, releaseSweet, releaseFatigued)
    }

    override fun reset() {
        releaseFrames = 0
        _phase = ChargePhase.Idle
        _currentCharge = 0f
        @Suppress("DEPRECATION")
        _chargePowerLocked = false
        _sweetSpotFramesRemaining = 0
        _drainHoldFrames = 0
        _phaseAfterClear = null
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
        } else {
            if (aimX == 0f && aimY == 0f) {
                aimY = if (renderer.isHigh) 1f else -1f
            }
        }

        // Only tick the state machine while not frozen mid-strike
        if (_phaseAfterClear == null) {
            when (_phase) {
                ChargePhase.Building -> {
                    if (_currentCharge >= Settings.sweetSpotMax) {
                        _currentCharge = Settings.sweetSpotMax.toFloat()
                        _sweetSpotFramesRemaining = Settings.sweetSpotWindowFrames
                        _phase = ChargePhase.SweetSpot
                    }
                }
                ChargePhase.SweetSpot -> {
                    _sweetSpotFramesRemaining--
                    if (_sweetSpotFramesRemaining <= 0) {
                        _drainHoldFrames = 0
                        _phase = ChargePhase.Draining
                    }
                }
                ChargePhase.Draining -> {
                    _currentCharge = (_currentCharge - Settings.chargeDrainRate)
                        .coerceAtLeast(Settings.drainFloor)
                    if (_currentCharge <= Settings.drainFloor) {
                        _drainHoldFrames++
                        if (_drainHoldFrames >= Settings.inertHoldFrames) {
                            _phase = ChargePhase.Inert
                        }
                    }
                }
                ChargePhase.Inert -> { /* paddle is dead */ }
                ChargePhase.Idle -> { /* awaiting increaseCharge() */ }
            }
        }

        _chargeFillRatio = when (_phase) {
            ChargePhase.SweetSpot -> 1f
            ChargePhase.Draining  -> ((_currentCharge - Settings.drainFloor) /
                                       (Settings.sweetSpotMax - Settings.drainFloor)).coerceIn(0f, 1f)
            ChargePhase.Inert     -> 0f
            ChargePhase.Building  -> ((_currentCharge - Settings.chargeStart) /
                                       (Settings.sweetSpotMax - Settings.chargeStart)).coerceIn(0f, 1f)
            ChargePhase.Idle      -> 0f
        }

        // Drag informs first 50% of paddle extension; charge fill informs the second 50%.
        // Both reach their respective maxes simultaneously at max drag + full charge.
        val dragT = if (dist > 1f) min(dist, maxDrag) / maxDrag else 0f
        val blendedT = (dragT * 0.5f + _chargeFillRatio * 0.5f).coerceIn(0f, 1f)
        paddleDistance = minDist + (maxDist - minDist) * blendedT

        paddleX = renderer.x - aimX * paddleDistance
        paddleY = renderer.y - aimY * paddleDistance
    }

    // ---------- drawing primitives (overridable) ----------

    /** Called each frame during Building / SweetSpot / Draining / Inert. */
    protected open fun drawChargingPaddle(canvas: Canvas) {
        drawPaddleBar(canvas, paddleX, paddleY, aimX, aimY, _chargeFillRatio, _phase, false)
    }

    /** Called each frame during the release-strike animation. */
    protected open fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        val fill = if (fatigued) 0f else 1f
        val fakePhase = when {
            sweet    -> ChargePhase.SweetSpot
            fatigued -> ChargePhase.Inert
            else     -> ChargePhase.Building
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

        val isInert = currentRenderer.inertLocked || ph == ChargePhase.Inert
        val baseColor = if (isInert) theme.inert.secondary else theme.main.secondary
        val chargeColor = theme.accent.primary
        val pulse = if (ph == ChargePhase.SweetSpot) 0.7f + 0.3f * sin(frame * 0.35f) else 1f

        paddlePaint.strokeWidth = thickness

        paddlePaint.color = baseColor
        paddlePaint.alpha = 255
        canvas.drawLine(
            cx - perpX * half, cy - perpY * half,
            cx + perpX * half, cy + perpY * half,
            paddlePaint
        )

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
    protected open fun onReleaseSpawn(x: Float, y: Float, radius: Float, sweet: Boolean, fatigued: Boolean) {}

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
