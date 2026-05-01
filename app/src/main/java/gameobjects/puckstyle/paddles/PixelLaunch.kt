package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import utility.Effects
import androidx.core.graphics.withSave

/** 8-bit paddle: solid secondary base line; charge overlays discrete centered cells in 20% steps. */
class PixelLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {
    private val block = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private val rect = RectF()

    // Cached radius-derived values — recomputed only when radius changes
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
        cachedCellW    = cachedTotalLen / 5f  // steps = 5
    }

    override fun drawChargingPaddle(canvas: Canvas) {
        ensureCache()
        drawPixelBar(canvas, paddleX, paddleY, aimX, aimY, phase, chargeFillRatio)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        ensureCache()
        val ph = if (sweet) ChargePhase.SweetSpot else if (fatigued) ChargePhase.Inert else ChargePhase.Building
        drawPixelBar(canvas, cx, cy, aX, aY, ph, if (sweet) 1f else if (fatigued) 0f else 1f)
    }

    private fun drawPixelBar(
        canvas: Canvas, cx: Float, cy: Float, aX: Float, aY: Float,
        ph: ChargePhase, fill: Float
    ) {
        canvas.withSave {
            val angle = Math.toDegrees(kotlin.math.atan2(aY, aX).toDouble()).toFloat()
            rotate(angle + 90f, cx, cy)

            val totalLen = cachedTotalLen
            val thick    = cachedThick
            val half     = cachedHalf
            val cellW    = cachedCellW

            // Base line
            block.color = responsiveSecondary
            rect.set(cx - half, cy - thick, cx + half, cy + thick)
            drawRect(rect, block)

            // Charge overlay: cells growing outward from center in 20% steps
            val steps = 5
            val filledCount = when {
                ph == ChargePhase.Inert -> 0
                ph == ChargePhase.SweetSpot -> steps
                else -> (fill * steps).toInt()
            }
            if (filledCount > 0) {
                block.color = theme.shield.primary
                val startX = cx - filledCount * cellW / 2f
                for (i in 0 until filledCount) {
                    rect.set(
                        startX + i * cellW,
                        cy - thick,
                        startX + (i + 1) * cellW,
                        cy + thick
                    )
                    drawRect(rect, block)
                }
            }
        }
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        spawnSquare(rx, ry, renderer.radius, responsivePrimary, theme)
    }

    override fun paddleThickness(): Float = Settings.strokeWidth * 1.6f

    companion object {
        fun spawnSquare(cx: Float, cy: Float, puckRadius: Float, color: Int, theme: ColorTheme) {
            Effects.addPersistentEffect(PixelSquare(cx, cy, puckRadius, color, theme))
        }
    }

    private class PixelSquare(
        private val cx: Float,
        private val cy: Float,
        private val puckRadius: Float,
        private val color: Int,
        private val theme: ColorTheme
    ) : Effects.PersistentEffect {
        private val halfSize = puckRadius * 0.5f
        private val fillPaint = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
        // strokeWidth is constant for the life of this object — set once at construction
        private val ringPaint = Paint().apply {
            isAntiAlias = false
            style = Paint.Style.STROKE
            strokeWidth = puckRadius * 0.3f
        }

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

        override fun draw(canvas: Canvas) {
            if (!rippling) {
                fillPaint.color = color
                canvas.drawRect(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize, fillPaint)
            } else if (!_isDone) {
                val half = rippleSize / 2f
                ringPaint.color = Palette.withAlpha(theme.main.secondary, rippleAlpha.coerceIn(0, 255))
                canvas.drawRect(cx - half, cy - half, cx + half, cy + half, ringPaint)
            }
        }
    }
}
