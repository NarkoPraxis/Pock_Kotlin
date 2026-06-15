package gameobjects.puckstyle.paddles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import utility.Effects
import kotlin.math.PI
import kotlin.math.atan2

class PixelLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    private var cachedRadius  = -1f
    private var cachedTotalLen = 0f
    private var cachedThick   = 0f
    private var cachedHalf    = 0f
    private var cachedCellW   = 0f

    private fun ensureCache() {
        if (cachedRadius == renderer.radius) return
        cachedRadius   = renderer.radius
        cachedTotalLen = paddleHalfLength() * 2f
        cachedThick    = renderer.radius * 0.2f
        cachedHalf     = cachedTotalLen / 2f
        cachedCellW    = cachedTotalLen / 5f
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
        val angle = (atan2(aY, aX) * (180.0 / PI)).toFloat()
        val totalLen = cachedTotalLen
        val thick    = cachedThick
        val half     = cachedHalf
        val cellW    = cachedCellW

        scope.withTransform({ rotate(angle + 90f, pivot = Offset(cx, cy)) }) {
            drawRect(
                color = Color(responsiveSecondary),
                topLeft = Offset(cx - half, cy - thick),
                size = Size(totalLen, thick * 2)
            )
            val steps = 5
            val filledCount = when {
                ph == ChargePhase.Inert -> 0
                ph == ChargePhase.SweetSpot -> steps
                else -> (fill * steps).toInt()
            }
            if (filledCount > 0) {
                val startX = cx - filledCount * cellW / 2f
                val chargeColor = renderer.invertedChargeColor(theme.shield.primary)
                for (i in 0 until filledCount) {
                    drawRect(
                        color = Color(chargeColor),
                        topLeft = Offset(startX + i * cellW, cy - thick),
                        size = Size(cellW, thick * 2)
                    )
                }
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
                    style = Stroke(width = ringStrokeWidth)
                )
            }
        }
    }
}
