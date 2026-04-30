package gameobjects.puckstyle.paddles

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.EggSplat
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.Palette
import utility.Effects
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import androidx.core.graphics.withRotation
import gameobjects.puckstyle.BallSize
import kotlin.random.Random

class ChickenLaunch(theme: ColorTheme, renderer: PuckRenderer) : PaddleLaunchEffect(theme, renderer) {

    private val eggPaint = Paint().apply { isAntiAlias = true }

    override fun drawChargingPaddle(canvas: Canvas) {
        drawEgg(canvas, paddleX, paddleY, chargeFillRatio, phase)
    }

    override fun drawStrikingPaddle(
        canvas: Canvas, cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        val ph = when {
            sweet       -> ChargePhase.SweetSpot
            fatigued -> ChargePhase.Inert
            else        -> ChargePhase.Building
        }
        drawEgg(canvas, cx, cy, if (sweet || !fatigued) 1f else 0f, ph)
    }

    private fun drawEgg(canvas: Canvas, cx: Float, cy: Float, fillRatio: Float, ph: ChargePhase) {
        val r = renderer.radius
        val eggW = renderer.r(BallSize.P050)
        val eggH = renderer.r(BallSize.P070)
        val pulse = if (ph == ChargePhase.SweetSpot) 0.7f + 0.3f * sin(frame * 0.35f) else 1f

        val angle = Math.toDegrees(atan2(aimY.toDouble(), aimX.toDouble())).toFloat()
        canvas.withRotation(angle + 90f, cx, cy) {
            eggPaint.style = Paint.Style.FILL
            eggPaint.color = if (renderer.isInert) responsivePrimary else android.graphics.Color.WHITE
            drawOval(cx - eggW, cy - eggH, cx + eggW, cy + eggH, eggPaint)

            if (fillRatio > 0f && ph != ChargePhase.Inert) {
                eggPaint.color = Palette.withAlpha(theme.shield.primary, (220 * pulse).toInt())
                drawOval(
                    cx - eggW * fillRatio, cy - eggH * fillRatio,
                    cx + eggW * fillRatio, cy + eggH * fillRatio, eggPaint
                )
            }

        }
    }

    val exposedPaddleX: Float get() = paddleX
    val exposedPaddleY: Float get() = paddleY
    val exposedAimX:    Float get() = aimX
    val exposedAimY:    Float get() = aimY

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(ChickenPersistentEffect(EggSplat(rx, ry, renderer.radius, theme)))
    }

    private class ChickenPersistentEffect(private val splat: EggSplat) : Effects.PersistentEffect {
        private val paint = Paint().apply { isAntiAlias = true }
        override val isDone get() = splat.isDone
        override fun step() { splat.step() }
        override fun draw(canvas: Canvas) { splat.draw(canvas, paint) }
    }

    companion object {
        fun spawnFeatherExplosion(cx: Float, cy: Float, radius: Float, primary: Int, secondary: Int) {
            Effects.addPersistentEffect(FeatherBurst(cx, cy, radius, primary, secondary))
        }
    }

    private class FeatherBurst(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        private val primary: Int,
        private val secondary: Int
    ) : Effects.PersistentEffect {
        private class Feather(
            var x: Float, var y: Float,
            val vx: Float, val vy: Float,
            var angle: Float, val spin: Float,
            var alpha: Int,
            val colorMix: Float
        )

        private val feathers: List<Feather>
        private val paint = Paint().apply { isAntiAlias = true }
        private var frame = 0
        override var isDone = false
            private set

        init {
            val count = 24
            feathers = List(count) { i ->
                val angle = (i.toFloat() / count) * 2f * PI.toFloat() + Random.nextFloat() * 0.4f
                val speed = radius * (0.05f + Random.nextFloat() * 0.1f)
                Feather(
                    x = cx, y = cy,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    angle = Random.nextFloat() * 360f,
                    spin = (Random.nextFloat() - 0.5f) * 6f,
                    alpha = 220,
                    colorMix = Random.nextFloat()
                )
            }
        }

        override fun step() {
            frame++
            if (frame > 42) isDone = true
        }

        val fw = radius * 0.2f
        val fh = radius * 0.55f
        override fun draw(canvas: Canvas) {

            for (f in feathers) {
                f.x += f.vx
                f.y += f.vy
                f.angle += f.spin
                f.alpha = (220f * (1f - frame / 42f).coerceAtLeast(0f)).toInt()
                if (f.alpha <= 0) continue

                val c = Palette.lerpColor(primary, secondary, f.colorMix)
                paint.style = Paint.Style.FILL
                paint.color = Palette.withAlpha(c, f.alpha)
                canvas.withRotation(f.angle, f.x, f.y) {
                    drawOval(f.x - fw, f.y - fh, f.x + fw, f.y + fh, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = fw * 0.35f
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.color = Palette.withAlpha(secondary, f.alpha)
                    drawLine(f.x, f.y + fh * 1.3f, f.x, f.y - fh * 0.7f, paint)
                }
            }
        }
    }
}
