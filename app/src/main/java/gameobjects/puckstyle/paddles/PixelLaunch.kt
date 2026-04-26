package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer
import utility.Effects
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Chunky 8-bit brick paddle. Charge fills as discrete pixel segments. */
class PixelLaunch(theme: ColorTheme, renderer: PuckRenderer) : PaddleLaunchEffect(theme, renderer) {
    private val block = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private val rect = RectF()

    override fun drawChargingPaddle(canvas: Canvas) =
        drawPixelBar(canvas, paddleX, paddleY, aimX, aimY, phase, chargeFillRatio)

    override fun drawStrikingPaddle(
        canvas: Canvas,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, overcharged: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (overcharged) ChargePhase.Inert else ChargePhase.Building
        drawPixelBar(canvas, cx, cy, aX, aY, ph, if (sweet) 1f else if (overcharged) 0f else 1f)
    }

    private fun drawPixelBar(
        canvas: Canvas, cx: Float, cy: Float, aX: Float, aY: Float,
        ph: ChargePhase, fill: Float
    ) {
        canvas.save()
        val angle = Math.toDegrees(kotlin.math.atan2(aY, aX).toDouble()).toFloat()
        canvas.rotate(angle + 90f, cx, cy)

        val totalLen = paddleHalfLength() * 2f
        val thick = renderer.radius * 0.45f
        val cells = 6
        val cellW = totalLen / cells
        val startX = cx - totalLen / 2f

        val base = responsiveSecondary
        val fillColor = theme.effect.primary
        val filledCells = (cells * fill).toInt()
        val center = cells / 2

        for (i in 0 until cells) {
            rect.set(startX + i * cellW + 1f, cy - thick, startX + (i + 1) * cellW - 1f, cy + thick)
            val dist = kotlin.math.abs(i - (center - 0.5f)).toInt()
            val isFilled = ph == ChargePhase.SweetSpot || dist < filledCells
            block.color = if (isFilled && ph != ChargePhase.Inert) fillColor else base
            canvas.drawRect(rect, block)
        }
        canvas.restore()
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(PixelDebris(rx, ry, renderer.radius, theme.effect.primary, theme.main.secondary))
    }

    override fun paddleThickness(): Float = Settings.strokeWidth * 1.6f

    private class PixelDebris(
        cx: Float, cy: Float,
        radius: Float,
        private val accentColor: Int,
        private val secondaryColor: Int
    ) : Effects.PersistentEffect {
        private val paint = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
        private var frame = 0
        override val isDone = false

        private class Fragment(val x: Float, val y: Float, val size: Float, val color: Int, val winkFrame: Int)
        private val frags: List<Fragment>

        init {
            val rand = Random(cx.toLong() xor cy.toLong())
            frags = List(10) {
                val angle = rand.nextFloat() * 2f * Math.PI.toFloat()
                val dist = rand.nextFloat() * radius * 2.5f
                val fx = cx + cos(angle) * dist
                val fy = cy + sin(angle) * dist
                val sz = radius * (0.1f + rand.nextFloat() * 0.14f)
                val c = if (rand.nextBoolean()) accentColor else secondaryColor
                val wink = 60 + rand.nextInt(180)
                Fragment(fx, fy, sz, c, wink)
            }
        }

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            for (frag in frags) {
                if (frame >= frag.winkFrame) continue
                val alpha = if (frame > frag.winkFrame - 20) {
                    (255 * (frag.winkFrame - frame) / 20).coerceIn(0, 255)
                } else 200
                paint.color = frag.color
                paint.alpha = alpha
                canvas.drawRect(frag.x - frag.size, frag.y - frag.size, frag.x + frag.size, frag.y + frag.size, paint)
            }
        }
    }
}
