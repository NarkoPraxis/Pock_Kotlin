package gameobjects.puckstyle.paddles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.EggSplat
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import utility.Effects
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ChickenLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    override fun drawChargingPaddle(scope: DrawScope) {
        drawEgg(scope, paddleX, paddleY, chargeFillRatio, phase)
    }

    override fun drawStrikingPaddle(
        scope: DrawScope, cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        val ph = when {
            sweet    -> ChargePhase.SweetSpot
            fatigued -> ChargePhase.Inert
            else     -> ChargePhase.Building
        }
        drawEgg(scope, cx, cy, if (sweet || !fatigued) 1f else 0f, ph)
    }

    val EGG_WIDTH get() = renderer.radius * .5f
    val EGG_HEIGHT get() = renderer.radius * .7f

    private fun drawEgg(scope: DrawScope, cx: Float, cy: Float, fillRatio: Float, ph: ChargePhase) {
        val pulse = if (ph == ChargePhase.SweetSpot) 0.7f + 0.3f * sin(frame * 0.35f) else 1f
        val angle = (atan2(aimY, aimX) * (180.0 / PI)).toFloat()
        val ew = EGG_WIDTH
        val eh = EGG_HEIGHT
        scope.withTransform({ rotate(angle + 90f, pivot = Offset(cx, cy)) }) {
            drawOval(
                color = Color(if (renderer.isInert) responsivePrimary else Palette.WHITE),
                topLeft = Offset(cx - ew, cy - eh),
                size = Size(ew * 2, eh * 2)
            )
            drawOval(
                color = Color(theme.inert.primary),
                topLeft = Offset(cx - ew, cy - eh),
                size = Size(ew * 2, eh * 2),
                style = Stroke(width = renderer.strokeWidth * 0.35f)
            )
            if (fillRatio > 0f && ph != ChargePhase.Inert) {
                drawOval(
                    color = Color(Palette.withAlpha(renderer.invertedChargeColor(theme.shield.primary), (220 * pulse).toInt())),
                    topLeft = Offset(cx - ew * fillRatio, cy - eh * fillRatio),
                    size = Size(ew * 2 * fillRatio, eh * 2 * fillRatio)
                )
            }
        }
    }



    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(ChickenPersistentEffect(EggSplat(rx, ry, renderer.radius, renderer.bakedPrimary(theme.main.primary))))
    }

    private class ChickenPersistentEffect(private val splat: EggSplat) : Effects.PersistentEffect {
        override val isDone get() = splat.isDone
        override fun step() { splat.step() }
        override fun draw(scope: DrawScope) { splat.draw(scope) }
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
        private var frame = 0
        override var isDone = false
            private set

        private val fw = radius * 0.2f
        private val fh = radius * 0.55f

        init {
            val count = 24
            feathers = List(count) { i ->
                val a = (i.toFloat() / count) * 2f * PI.toFloat() + Random.nextFloat() * 0.4f
                val speed = radius * (0.05f + Random.nextFloat() * 0.1f)
                Feather(
                    x = cx, y = cy,
                    vx = cos(a) * speed, vy = sin(a) * speed,
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

        override fun draw(scope: DrawScope) {
            for (f in feathers) {
                f.x += f.vx
                f.y += f.vy
                f.angle += f.spin
                f.alpha = (220f * (1f - frame / 42f).coerceAtLeast(0f)).toInt()
                if (f.alpha <= 0) continue
                val c = Palette.lerpColor(primary, secondary, f.colorMix)
                scope.withTransform({ rotate(f.angle, pivot = Offset(f.x, f.y)) }) {
                    drawOval(
                        color = Color(Palette.withAlpha(c, f.alpha)),
                        topLeft = Offset(f.x - fw, f.y - fh),
                        size = Size(fw * 2, fh * 2)
                    )
                    drawLine(
                        color = Color(Palette.withAlpha(secondary, f.alpha)),
                        start = Offset(f.x, f.y + fh * 1.3f),
                        end = Offset(f.x, f.y - fh * 0.7f),
                        strokeWidth = fw * 0.35f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
