package gameobjects.puckstyle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.LayoutDirection
import gameobjects.Settings
import gameobjects.puckstyle.TailRenderer.Companion.previewLayerPaint
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

abstract class PaddleLaunchEffect(override val renderer: PuckRenderer) : LaunchEffect {

    override val zIndex: Int get() = 1

    open val alwaysVisible: Boolean = false

    protected var frame = 0
        private set

    val theme: ColorTheme
        get() = renderer.theme

    val responsivePrimary: Int
        get() = if (phase == ChargePhase.Inert) renderer.theme.inert.primary else renderer.responsiveColorGroup.primary

    val responsiveSecondary: Int
        get() = if (phase == ChargePhase.Inert) renderer.theme.inert.secondary else renderer.responsiveColorGroup.secondary

    private var _currentCharge = 0f
    @Deprecated("Superseded by Draining/Inert phases — retained for interface compat")
    private var _chargePowerLocked = false

    private var _sweetSpotFramesRemaining: Int = 0
    private var _drainHoldFrames: Int = 0

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

    protected var aimX = 0f
        private set
    protected var aimY = 0f
        private set

    protected var paddleDistance = 0f
        private set

    protected var paddleX = 0f
        private set
    protected var paddleY = 0f
        private set

    private var _chargeFillRatio = 0f
    override val chargeFillRatio: Float get() = _chargeFillRatio

    private var releaseFrames = 0
    private var releaseFromX = 0f
    private var releaseFromY = 0f
    private var releaseAimX = 0f
    private var releaseAimY = 0f
    private var releaseSweet = false
    private var releaseFatigued = false

    protected var maxDist = renderer.radius * 5f
    protected open var minDist = renderer.radius

    private var strikeCallback: (() -> Unit)? = null

    override fun registerStrikeCallback(onStrike: () -> Unit) {
        strikeCallback = onStrike
    }

    override fun draw(scope: DrawScope) {
        frame++
        updateState()

        if (releaseFrames > 0) {
            val t = 1f - (releaseFrames.toFloat() / RELEASE_DURATION)
            val cx = lerp(releaseFromX, renderer.x, t)
            val cy = lerp(releaseFromY, renderer.y, t)
            drawStrikingPaddle(scope, cx, cy, releaseAimX, releaseAimY, releaseSweet, releaseFatigued, t)
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
            drawChargingPaddle(scope)
        } else if (phase == ChargePhase.Idle) {
            drawIdlePaddle(scope)
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

    private fun updateState() {
        minDist = renderer.radius
        maxDist = renderer.radius * 5

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

        if (_phaseAfterClear == null) {
            when (_phase) {
                ChargePhase.Building -> {
                    if (_currentCharge >= Settings.sweetSpotMax) {
                        _currentCharge = Settings.sweetSpotMax.toFloat()
                        _sweetSpotFramesRemaining = Settings.sweetSpotWindowFrames
                        _phase = ChargePhase.SweetSpot
                        renderer.playSweetSpotSound()
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
                ChargePhase.Inert -> {}
                ChargePhase.Idle -> {}
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

        val dragT = if (dist > 1f) min(dist, maxDrag) / maxDrag else 0f
        val blendedT = (dragT * 0.5f + _chargeFillRatio * 0.5f).coerceIn(0f, 1f)
        paddleDistance = minDist + (maxDist - minDist) * blendedT

        paddleX = renderer.x - aimX * paddleDistance
        paddleY = renderer.y - aimY * paddleDistance
    }

    protected open fun drawChargingPaddle(scope: DrawScope) {
        drawPaddleBar(scope, paddleX, paddleY, aimX, aimY, _chargeFillRatio, _phase, false)
    }

    protected open fun drawStrikingPaddle(
        scope: DrawScope,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        val fill = if (fatigued) 0f else 1f
        val fakePhase = when {
            sweet    -> ChargePhase.SweetSpot
            fatigued -> ChargePhase.Inert
            else     -> ChargePhase.Building
        }
        drawPaddleBar(scope, cx, cy, aX, aY, fill, fakePhase, true)
    }

    protected open fun drawIdlePaddle(scope: DrawScope) {}

    fun renderWithPreview(scope: DrawScope) {
        if (!renderer.preview) {
            draw(scope)
            return
        }
        val self = this
        scope.drawIntoCanvas { composeCanvas ->
            composeCanvas.withSaveLayer(Rect(0f, 0f, scope.size.width, scope.size.height), previewLayerPaint) {
                helperScope.draw(scope, scope.layoutDirection, composeCanvas, scope.size) {
                    self.draw(this)
                }
            }
        }
    }

    protected fun drawPaddleBar(
        scope: DrawScope,
        cx: Float, cy: Float, aX: Float, aY: Float,
        fillRatio: Float, ph: ChargePhase, striking: Boolean
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
            else -> stateColors.secondary
        }
        val chargeColor = if (hitStunBlend)
            blendColor(theme.shield.primary, theme.inert.primary, hitStunR)
        else
            theme.shield.primary
        val pulse = if (ph == ChargePhase.SweetSpot) 0.7f + 0.3f * sin(frame * 0.35f) else 1f

        scope.drawLine(
            color = Color(baseColor),
            start = Offset(cx - perpX * half, cy - perpY * half),
            end = Offset(cx + perpX * half, cy + perpY * half),
            strokeWidth = thickness,
            cap = StrokeCap.Round
        )

        if (fillRatio > 0f && !isInert) {
            val fillHalf = half * fillRatio
            scope.drawLine(
                color = Color(Palette.withAlpha(chargeColor, (255 * pulse).toInt().coerceIn(0, 255))),
                start = Offset(cx - perpX * fillHalf, cy - perpY * fillHalf),
                end = Offset(cx + perpX * fillHalf, cy + perpY * fillHalf),
                strokeWidth = thickness,
                cap = StrokeCap.Round
            )
        }
    }

    protected open fun onReleaseSpawn(x: Float, y: Float, radius: Float, sweet: Boolean, fatigued: Boolean) {}
    protected open fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {}

    protected open fun paddleHalfLength(): Float = Settings.screenRatio
    protected open fun paddleThickness(): Float = Settings.strokeWidth * 1.4f

    protected fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    protected fun withAlpha(color: Int, alpha: Int): Int = Palette.withAlpha(color, alpha)

    protected fun blendColor(from: Int, to: Int, t: Float): Int {
        val r = (Palette.red(from)   + (Palette.red(to)   - Palette.red(from))   * t).toInt()
        val g = (Palette.green(from) + (Palette.green(to) - Palette.green(from)) * t).toInt()
        val b = (Palette.blue(from)  + (Palette.blue(to)  - Palette.blue(from))  * t).toInt()
        val a = (Palette.alpha(from) + (Palette.alpha(to) - Palette.alpha(from)) * t).toInt()
        return Palette.argb(a, r, g, b)
    }

    companion object {
        const val RELEASE_DURATION = 5
        private val helperScope = CanvasDrawScope()
    }
}
