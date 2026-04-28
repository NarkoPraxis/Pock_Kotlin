package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.Palette.HIGHLIGHT_SATURATION
import gameobjects.puckstyle.Palette.THEME_SATURATION
import gameobjects.puckstyle.Palette.THEME_VALUE
import utility.Effects
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Triangular prism. Sweet spot refracts rainbow streaks across the paddle. */
class PrismLaunch(theme: ColorTheme, renderer: PuckRenderer) : PaddleLaunchEffect(theme, renderer) {

    private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

    val defaultStrokeWidth = Settings.strokeWidth * .5f
    private val edge = Paint().apply {
        isAntiAlias = true; style = Paint.Style.STROKE
        strokeWidth = defaultStrokeWidth;
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    override fun drawChargingPaddle(canvas: Canvas) =
        drawPrism(canvas, paddleX, paddleY, aimX, aimY, phase, chargeFillRatio)

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (fatigued) ChargePhase.Inert else ChargePhase.Building
        drawPrism(canvas, cx, cy, aX, aY, ph, if (sweet) 1f else if (fatigued) 0f else 1f)
        if (sweet) drawRefraction(canvas, cx, cy, aX, aY, progress)
    }

    private fun drawPrism(
        canvas: Canvas, cx: Float, cy: Float, aX: Float, aY: Float,
        ph: ChargePhase, ratio: Float
    ) {
        val half = paddleHalfLength() * 0.85f
        val depth = renderer.radius * 0.5f
        val pX = -aY
        val pY = aX
        path.reset()
        path.moveTo(cx + pX * half, cy + pY * half)
        path.lineTo(cx - pX * half, cy - pY * half)
        path.lineTo(cx - aX * depth, cy - aY * depth)
        path.close()

        fill.color = if (ph == ChargePhase.Inert) theme.inert.primary else Color.WHITE

        fill.alpha = 200
        canvas.drawPath(path, fill)
        if (ratio > 0f) {
            fill.color = Palette.cyclingHue(frame, 4f)
            fill.alpha = (180 * ratio).toInt().coerceIn(0, 255)
            canvas.drawPath(path, fill)
        }
        fill.alpha = 255
        edge.color = if (renderer.isInert || ph == ChargePhase.Inert) theme.inert.secondary else responsivePrimary
        canvas.drawPath(path, edge)
    }

    private fun drawRefraction(canvas: Canvas, cx: Float, cy: Float, aX: Float, aY: Float, progress: Float) {
        val pX = -aY
        val pY = aX
        val half = paddleHalfLength()
        val px = renderer.x
        val py = renderer.y
        for (i in 0 until 6) {
            val offset = (i - 2.5f) / 2.5f * half
            edge.color = Palette.hsv(i * 60f + frame * 3f, THEME_SATURATION, THEME_VALUE)
            edge.alpha = (200 * (1f - progress)).toInt().coerceIn(0, 255)
            edge.strokeWidth = renderer.radius
            canvas.drawLine(
                cx + pX * offset, cy + pY * offset,
                px + pX * offset * 0.6f, py + pY * offset * 0.6f,
                edge
            )
            edge.strokeWidth = defaultStrokeWidth
        }
        edge.alpha = 255
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(PrismScatter(rx, ry, renderer.radius))
    }

    private class PrismScatter(
        cx: Float, cy: Float,
        private val radius: Float
    ) : Effects.PersistentEffect {
        private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private var frame = 0
        override val isDone = false

        private class Glint(val x: Float, val y: Float, val hue: Float, val fadeStart: Int, val fadeEnd: Int)
        private val glints: List<Glint>

        init {
            val rand = Random(cx.toLong() xor cy.toLong())
            glints = List(6) { i ->
                val hue = i * 60f
                val angle = rand.nextFloat() * 2f * Math.PI.toFloat()
                val dist = radius * (0.4f + rand.nextFloat() * 2f)
                val start = 30 + rand.nextInt(120)
                val end = start + 30 + rand.nextInt(60)
                Glint(cx + cos(angle) * dist, cy + sin(angle) * dist, hue, start, end)
            }
        }

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            for (g in glints) {
                val alpha = when {
                    frame < g.fadeStart -> 180
                    frame < g.fadeEnd -> (180 * (g.fadeEnd - frame) / (g.fadeEnd - g.fadeStart)).coerceIn(0, 255)
                    else -> 0
                }
                if (alpha <= 0) continue
            paint.color = Palette.hsv(g.hue, THEME_SATURATION, THEME_VALUE)
                paint.alpha = alpha
                canvas.drawCircle(g.x, g.y, radius * 0.2f, paint)
            }
            paint.alpha = 255
        }
    }
}
