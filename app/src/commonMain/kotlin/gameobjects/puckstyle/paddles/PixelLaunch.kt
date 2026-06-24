package gameobjects.puckstyle.paddles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import utility.Effects
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class PixelLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    private var cachedRadius  = -1f
    private var cachedTotalLen = 0f
    private var cachedThick   = 0f
    private var cachedHalf    = 0f
    private var cachedCellW   = 0f

    // Reusable Path for rotated rects — drawn in absolute screen coords to avoid the
    // per-frame capturing withTransform lambda (see CLAUDE.md rotation guidance).
    private val rectPath = Path()

    private fun ensureCache() {
        if (cachedRadius == renderer.radius) return
        cachedRadius   = renderer.radius
        cachedTotalLen = paddleHalfLength() * 2f
        cachedThick    = renderer.radius * 0.2f
        cachedHalf     = cachedTotalLen / 2f
        cachedCellW    = cachedTotalLen / 5f
    }

    /** Builds [rectPath] for an axis-aligned rect (in unrotated local space, origin at pivot
     *  cx/cy) rotated by [cosA]/[sinA] about (cx,cy), expressed in absolute screen coords. */
    private fun buildRotatedRect(
        cx: Float, cy: Float, cosA: Float, sinA: Float,
        localLeft: Float, localTop: Float, w: Float, h: Float
    ) {
        val lr = localLeft + w
        val lb = localTop + h
        rectPath.reset()
        rectPath.moveTo(cx + localLeft * cosA - localTop * sinA, cy + localLeft * sinA + localTop * cosA)
        rectPath.lineTo(cx + lr * cosA - localTop * sinA, cy + lr * sinA + localTop * cosA)
        rectPath.lineTo(cx + lr * cosA - lb * sinA, cy + lr * sinA + lb * cosA)
        rectPath.lineTo(cx + localLeft * cosA - lb * sinA, cy + localLeft * sinA + lb * cosA)
        rectPath.close()
    }

    override fun drawChargingPaddle(scope: DrawScope) {
        ensureCache()
        drawPixelBar(scope, paddleX, paddleY, aimX, aimY, phase, chargeFillRatio)
    }

    override fun drawStrikingPaddle(
        scope: DrawScope,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        ensureCache()
        val ph = if (sweet) ChargePhase.SweetSpot else if (fatigued) ChargePhase.Inert else ChargePhase.Building
        drawPixelBar(scope, cx, cy, aX, aY, ph, if (sweet) 1f else if (fatigued) 0f else 1f)
    }

    private fun drawPixelBar(
        scope: DrawScope, cx: Float, cy: Float, aX: Float, aY: Float,
        ph: ChargePhase, fill: Float
    ) {
        val totalLen = cachedTotalLen
        val thick    = cachedThick
        val half     = cachedHalf
        val cellW    = cachedCellW

        // rotate by angle + 90deg about (cx, cy); compute trig once, draw in absolute coords
        val rad = (atan2(aY, aX) + PI / 2.0).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)
        val twoThick = thick * 2f

        // background bar (local rect: left=-half, top=-thick, w=totalLen, h=2*thick)
        buildRotatedRect(cx, cy, cosA, sinA, -half, -thick, totalLen, twoThick)
        scope.drawPath(rectPath, color = Color(responsiveSecondary))

        val steps = 5
        val filledCount = when {
            ph == ChargePhase.Inert -> 0
            ph == ChargePhase.SweetSpot -> steps
            else -> (fill * steps).toInt()
        }
        if (filledCount > 0) {
            // local startX relative to pivot cx: original startX was cx - filledCount*cellW/2
            val localStart = -filledCount * cellW / 2f
            val chargeColor = Color(renderer.invertedChargeColor(theme.shield.primary))
            for (i in 0 until filledCount) {
                buildRotatedRect(cx, cy, cosA, sinA, localStart + i * cellW, -thick, cellW, twoThick)
                scope.drawPath(rectPath, color = chargeColor)
            }
        }
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        spawnSquare(rx, ry, renderer.radius, responsivePrimary, renderer.bakedSecondary(theme.main.secondary))
    }

    override fun paddleThickness(): Float = Settings.strokeWidth * 1.6f

    companion object {
        // color and rippleColor are baked at spawn (rainbow-resolved when the puck is strobing).
        fun spawnSquare(cx: Float, cy: Float, puckRadius: Float, color: Int, rippleColor: Int) {
            Effects.addPersistentEffect(PixelSquare(cx, cy, puckRadius, color, rippleColor))
        }
    }

    private class PixelSquare(
        private val cx: Float,
        private val cy: Float,
        private val puckRadius: Float,
        private val color: Int,
        private val rippleColor: Int
    ) : Effects.PersistentEffect {
        private val halfSize = puckRadius * 0.5f
        private val ringStrokeWidth = puckRadius * 0.3f
        // Stroke is a heap class; width is constant for this effect — build once
        private val ringStroke = Stroke(width = ringStrokeWidth)

        private var rippleSize = 0f
        private var rippleAlpha = 0
        private var rippling = false
        private var _isDone = false
        override val isDone: Boolean get() = _isDone

        override fun onScoreSignal(): Boolean {
            rippling = true
            rippleSize = puckRadius * 1.8f
            rippleAlpha = 200
            return true
        }

        override fun step() {
            if (!rippling) return
            rippleSize += puckRadius * 0.09f
            rippleAlpha -= 12
            if (rippleAlpha <= 0) _isDone = true
        }

        override fun draw(scope: DrawScope) {
            if (!rippling) {
                scope.drawRect(
                    color = Color(color),
                    topLeft = Offset(cx - halfSize, cy - halfSize),
                    size = Size(halfSize * 2, halfSize * 2)
                )
            } else if (!_isDone) {
                val half = rippleSize / 2f
                scope.drawRect(
                    color = Color(Palette.withAlpha(rippleColor, rippleAlpha.coerceIn(0, 255))),
                    topLeft = Offset(cx - half, cy - half),
                    size = Size(rippleSize, rippleSize),
                    style = ringStroke
                )
            }
        }
    }
}
