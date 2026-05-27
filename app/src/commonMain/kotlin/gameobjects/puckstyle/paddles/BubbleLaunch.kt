package gameobjects.puckstyle.paddles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import utility.Effects
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class BubbleLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    private class TrailingBubble(
        var x: Float, var y: Float,
        val radius: Float,
        var life: Int
    )

    private val trailingBubbles = mutableListOf<TrailingBubble>()
    private var spawnTimer = 0
    private var lastPaddleX = Float.NaN
    private var lastPaddleY = Float.NaN

    override fun drawChargingPaddle(scope: DrawScope) {
        updateTrailingBubbles(scope)
        drawBubblePaddle(scope, paddleX, paddleY, chargeFillRatio, phase)
    }

    override fun drawStrikingPaddle(
        scope: DrawScope,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        val expandScale = 1f + progress * 1f
        val alpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
        val r = renderer.radius * 0.6f * expandScale
        val ph = if (sweet) ChargePhase.SweetSpot else if (fatigued) ChargePhase.Inert else ChargePhase.Building

        val isInert = renderer.isInert || ph == ChargePhase.Inert
        val baseColor = when {
            isInert -> theme.inert.primary
            renderer.shielded -> theme.shield.primary
            else -> theme.main.primary
        }

        scope.drawCircle(
            Color(Palette.withAlpha(baseColor, (alpha * 0.3f).toInt().coerceIn(0, 255))),
            r, Offset(cx, cy)
        )
        scope.drawCircle(
            Color(Palette.withAlpha(responsiveSecondary, alpha)),
            r, Offset(cx, cy),
            style = Stroke(width = Settings.strokeWidth * 0.8f)
        )
    }

    override fun drawIdlePaddle(scope: DrawScope) {
        trailingBubbles.clear()
        lastPaddleX = Float.NaN
    }

    override fun onReleaseSpawn(x: Float, y: Float, radius: Float, sweet: Boolean, fatigued: Boolean) {
        trailingBubbles.clear()
        lastPaddleX = Float.NaN
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(BubblePopResidual(rx, ry, renderer.radius, theme.main.primary))
    }

    private fun drawBubblePaddle(scope: DrawScope, cx: Float, cy: Float, fill: Float, ph: ChargePhase) {
        val r = renderer.radius * (0.4f + 0.2f * fill)

        val isInert = renderer.isInert || ph == ChargePhase.Inert
        val stateColors = when {
            isInert -> theme.inert
            renderer.shielded -> theme.shield
            else -> theme.main
        }
        val hitStunBlend = renderer.hitStunned && !isInert
        val hitStunR = if (hitStunBlend) renderer.hitStunRatio else 0f
        val baseColor = when {
            hitStunBlend -> blendColor(stateColors.primary, theme.inert.primary, hitStunR)
            else -> stateColors.primary
        }
        val strokeColor = when {
            hitStunBlend -> blendColor(stateColors.secondary, theme.inert.secondary, hitStunR)
            else -> stateColors.secondary
        }

        scope.drawCircle(
            Color(Palette.withAlpha(baseColor, 77)),
            r, Offset(cx, cy)
        )
        scope.drawCircle(
            Color(strokeColor),
            r, Offset(cx, cy),
            style = Stroke(width = Settings.strokeWidth * 0.8f)
        )

        if (fill > 0f && !isInert) {
            val chargeColor = theme.shield.primary
            val pulse = if (ph == ChargePhase.SweetSpot) 0.7f + 0.3f * sin(scope.hashCode().toFloat() * 0.35f) else 1f
            scope.drawCircle(
                Color(Palette.withAlpha(chargeColor, (100 * fill * pulse).toInt().coerceIn(0, 255))),
                r * fill, Offset(cx, cy)
            )
        }

        val hlAngle = -PI.toFloat() * 0.75f
        val hlDist = r * 0.4f
        val hlX = cx + cos(hlAngle) * hlDist
        val hlY = cy + sin(hlAngle) * hlDist
        scope.drawArc(
            Color.White.copy(alpha = 0.6f),
            startAngle = 200f, sweepAngle = 50f,
            useCenter = false,
            topLeft = Offset(hlX - r * 0.3f, hlY - r * 0.3f),
            size = androidx.compose.ui.geometry.Size(r * 0.6f, r * 0.6f),
            style = Stroke(width = Settings.strokeWidth * 0.6f, cap = StrokeCap.Round)
        )
    }

    private fun updateTrailingBubbles(scope: DrawScope) {
        spawnTimer++
        if (!lastPaddleX.isNaN() && spawnTimer >= 3 && trailingBubbles.size < 15) {
            val r = renderer.radius
            spawnTimer = 0
            trailingBubbles.add(TrailingBubble(
                x = paddleX + (Random.nextFloat() - 0.5f) * r * 0.3f,
                y = paddleY + (Random.nextFloat() - 0.5f) * r * 0.3f,
                radius = r * (0.05f + Random.nextFloat() * 0.07f),
                life = 20
            ))
        }
        lastPaddleX = paddleX
        lastPaddleY = paddleY

        val primary = responsivePrimary
        var i = trailingBubbles.size - 1
        while (i >= 0) {
            val b = trailingBubbles[i]
            b.y -= renderer.radius * 0.01f
            b.life--
            if (b.life <= 0) { trailingBubbles.removeAt(i); i--; continue }
            val alpha = (b.life * 255 / 20).coerceIn(0, 255)
            scope.drawCircle(
                Color(Palette.withAlpha(primary, (alpha * 0.4f).toInt())),
                b.radius, Offset(b.x, b.y)
            )
            scope.drawCircle(
                Color(Palette.withAlpha(primary, alpha)),
                b.radius, Offset(b.x, b.y),
                style = Stroke(width = Settings.strokeWidth * 0.3f)
            )
            i--
        }
    }

    private class BubblePopResidual(
        private val cx: Float, private val cy: Float,
        private val radius: Float, private val color: Int
    ) : Effects.PersistentEffect {
        private var frame = 0
        override var isDone = false

        override fun step() {
            frame++
            if (frame > 15) isDone = true
        }

        override fun draw(scope: DrawScope) {
            val t = (frame.toFloat() / 15f).coerceIn(0f, 1f)
            val ringR = radius * (1f + t * 2f)
            val alpha = (180 * (1f - t)).toInt().coerceIn(0, 255)
            if (alpha > 0) {
                scope.drawCircle(
                    Color(Palette.withAlpha(color, alpha)),
                    ringR, Offset(cx, cy),
                    style = Stroke(width = Settings.strokeWidth * 1.5f * (1f - t))
                )
            }
        }
    }
}
