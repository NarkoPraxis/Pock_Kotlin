package gameobjects.puckstyle.paddles

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MetalLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    private val fuseColor = Palette.argb(255, 70, 50, 30)
    private val fuseStrokeWidth = Settings.strokeWidth * 0.4f
    private val explosionOuter = Palette.argb(255, 255, 180, 40)
    private val explosionInner = Palette.argb(255, 255, 240, 150)

    override fun drawChargingPaddle(scope: DrawScope) {
        drawStick(scope, paddleX, paddleY, aimX, aimY, phase, chargeFillRatio)
    }

    override fun drawStrikingPaddle(
        scope: DrawScope,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        if (sweet) {
            drawExplosion(scope, progress)
        } else {
            val ph = if (fatigued) ChargePhase.Inert else ChargePhase.Building
            drawStick(scope, cx, cy, aX, aY, ph, if (fatigued) 0f else 1f)
        }
    }

    private fun drawStick(
        scope: DrawScope, cx: Float, cy: Float, aX: Float, aY: Float,
        ph: ChargePhase, fill: Float
    ) {
        val angle = (atan2(aY, aX) * (180.0 / PI)).toFloat()
        val halfLen = paddleHalfLength() * 0.8f
        val halfThick = renderer.radius * 0.25f

        scope.withTransform({ rotate(angle + 90f, pivot = Offset(cx, cy)) }) {
            val stickColor = if (ph == ChargePhase.Inert) responsivePrimary else responsiveSecondary
            drawRoundRect(
                color = Color(stickColor),
                topLeft = Offset(cx - halfLen, cy - halfThick),
                size = Size(halfLen * 2, halfThick * 2),
                cornerRadius = CornerRadius(halfThick * 0.4f, halfThick * 0.4f)
            )

            if (fill > 0f) {
                val bandHalf = (halfLen * fill).coerceAtMost(halfLen - halfLen * .1f)
                drawRoundRect(
                    color = Color(theme.shield.primary),
                    topLeft = Offset(cx - bandHalf, cy - halfThick * 0.6f),
                    size = Size(bandHalf * 2, halfThick * 1.2f),
                    cornerRadius = CornerRadius(halfThick, halfThick)
                )
            }

            val fuseBaseX = cx + halfLen
            val fuseBaseY = cy
            val fuseTipX = fuseBaseX + halfThick * 1.4f
            val fuseTipY = cy + halfThick * 1.2f
            drawLine(
                color = Color(fuseColor),
                start = Offset(fuseBaseX, fuseBaseY),
                end = Offset(fuseTipX, fuseTipY),
                strokeWidth = fuseStrokeWidth,
                cap = StrokeCap.Round
            )

            if (ph == ChargePhase.SweetSpot) {
                val flicker = 0.6f + 0.4f * sin(frame * 0.9f)
                drawCircle(
                    color = Color(Palette.withAlpha(responsivePrimary, (255 * flicker).toInt().coerceIn(0, 255))),
                    radius = halfThick,
                    center = Offset(fuseTipX, fuseTipY)
                )
            }
        }
    }

    private fun drawExplosion(scope: DrawScope, progress: Float) {
        val cx = renderer.x; val cy = renderer.y
        val r = renderer.radius * (1f + progress * 5f)
        scope.drawCircle(
            Color(Palette.withAlpha(explosionOuter, (255 * (1f - progress)).toInt().coerceIn(0, 255))),
            r, Offset(cx, cy)
        )
        scope.drawCircle(
            Color(Palette.withAlpha(explosionInner, (220 * (1f - progress)).toInt().coerceIn(0, 255))),
            r * 0.55f, Offset(cx, cy)
        )
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(BlastScorch(rx, ry, renderer.radius, theme.main.primary))
    }

    internal class BlastScorch(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        private val primary: Int
    ) : Effects.PersistentEffect {

        private class Spark(val dx: Float, val dy: Float, var alpha: Float, val fadeRate: Float)
        private val sparks: List<Spark>
        private val spikePaths: List<Path>
        private val spikeBrush: Brush
        private val emberColor = primary
        private var frame = 0
        override val isDone = false

        init {
            val rng = Random(cx.toInt() xor cy.toInt())
            val spikeCount = 12
            val lengthPattern = floatArrayOf(
                1.50f, 0.72f, 1.60f, 0.60f, 1.2f, 0.85f, 1.70f, 0.55f,
                1.90f, 0.68f, 1.2f, 0.78f
            )
            spikePaths = List(spikeCount) { i ->
                val baseAngle = (i.toFloat() / spikeCount) * 2f * PI.toFloat() +
                    (rng.nextFloat() - 0.5f) * (2f * PI.toFloat() / spikeCount) * 1f
                val len = radius * lengthPattern[i] * (0.90f + rng.nextFloat() * 0.40f)
                val halfWidth = radius * (0.1f + rng.nextFloat())
                val perpAngle = baseAngle + (PI / 2f).toFloat()
                val baseX1 = cx + cos(perpAngle) * halfWidth
                val baseY1 = cy + sin(perpAngle) * halfWidth
                val baseX2 = cx - cos(perpAngle) * halfWidth
                val baseY2 = cy - sin(perpAngle) * halfWidth
                val tipX = cx + cos(baseAngle) * len
                val tipY = cy + sin(baseAngle) * len
                Path().apply {
                    moveTo(baseX1, baseY1)
                    lineTo(tipX, tipY)
                    lineTo(baseX2, baseY2)
                    close()
                }
            }

            val rand = Random(cx.toLong())
            sparks = List((5..15).random()) {
                val angle = rand.nextFloat() * 2f * PI.toFloat()
                val dist = radius * (2f + rand.nextFloat() * 2f)
                Spark(cos(angle) * dist, sin(angle) * dist, 200f, 0.25f + rand.nextFloat() * 0.6f)
            }

            spikeBrush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f   to Color(0XFF777777),
                    0.3f to Color(0xCCCCCCCC),
                    0.55f to Color(primary).copy(alpha = .4f),
                    0.75f to Color(Palette.TRANSPARENT)
                ),

                center = Offset(cx, cy),
                radius = radius * 2f
            )
        }

        override fun step() { frame++ }

        override fun draw(scope: DrawScope) {
            val emberRadius = radius * 0.09f
            for (s in sparks) {
                if (s.alpha <= 0f) continue
                scope.drawCircle(
                    Color(Palette.withAlpha(emberColor, s.alpha.toInt().coerceIn(0, 255))),
                    emberRadius,
                    Offset(cx + s.dx, cy + s.dy)
                )
            }
            for (path in spikePaths) {
                scope.drawPath(path, spikeBrush)
            }
        }
    }

    private class MetalScorch(
        private val cx: Float, private val cy: Float,
        private val radius: Float
    ) : Effects.PersistentEffect {
        private val scorchColor = Palette.withAlpha(Palette.argb(255, 30, 20, 10), 160)
        private val sparkColor  = Palette.argb(255, 180, 160, 100)
        private var frame = 0
        override val isDone = false

        private class Spark(val dx: Float, val dy: Float, var alpha: Float, val fadeRate: Float)
        private val sparks: List<Spark>

        init {
            val rand = Random(cx.toLong())
            sparks = List(7) {
                val angle = rand.nextFloat() * 2f * PI.toFloat()
                val dist = radius * (0.9f + rand.nextFloat() * 1.5f)
                Spark(cos(angle) * dist, sin(angle) * dist, 200f, 0.25f + rand.nextFloat() * 0.6f)
            }
        }

        override fun step() {
            frame++
            for (s in sparks) s.alpha = (s.alpha - s.fadeRate).coerceAtLeast(0f)
        }

        override fun draw(scope: DrawScope) {
            scope.drawCircle(Color(scorchColor), radius * 1.1f, Offset(cx, cy))
            val sparkRadius = radius * 0.09f
            for (s in sparks) {
                if (s.alpha <= 0f) continue
                scope.drawCircle(
                    Color(Palette.withAlpha(sparkColor, s.alpha.toInt().coerceIn(0, 255))),
                    sparkRadius,
                    Offset(cx + s.dx, cy + s.dy)
                )
            }
        }
    }
}
