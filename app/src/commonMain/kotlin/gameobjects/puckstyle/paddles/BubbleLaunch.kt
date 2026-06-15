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
    private var lastPaddleX = Float.NaN
    private var lastPaddleY = Float.NaN

    override fun drawChargingPaddle(scope: DrawScope) {
        // animFrame follows the strobe clock in static UI, so the bubble sim keeps ticking (and the
        // canvas keeps repainting) even though the paddle frame is frozen in the Ball Designer.
        updateTrailingBubbles(scope, animFrame)
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

        // Body follows the responsive group so it strobes under a rainbow override (resolves inert
        // internally, and shield/main via renderer.draw()).
        val baseColor = responsivePrimary

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
        Effects.addPersistentEffect(BubblePopResidual(rx, ry, renderer.radius, renderer.bakedPrimary(theme.main.primary)))
    }

    private fun drawBubblePaddle(scope: DrawScope, cx: Float, cy: Float, fill: Float, ph: ChargePhase) {
        val r = renderer.radius * (0.4f + 0.2f * fill)

        val isInert = renderer.isInert || ph == ChargePhase.Inert
        val hitStunBlend = renderer.hitStunned && !isInert
        val hitStunR = if (hitStunBlend) renderer.hitStunRatio else 0f
        // Body follows the responsive group (strobes under rainbow); inert is resolved internally.
        val baseColor = when {
            hitStunBlend -> blendColor(responsivePrimary, theme.inert.primary, hitStunR)
            else -> responsivePrimary
        }
        val strokeColor = when {
            hitStunBlend -> blendColor(responsiveSecondary, theme.inert.secondary, hitStunR)
            else -> responsiveSecondary
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
            val chargeColor = renderer.invertedChargeColor(theme.shield.primary)
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

    private fun updateTrailingBubbles(scope: DrawScope, clock: Int) {
        if (!lastPaddleX.isNaN() && clock % 3 == 0 && trailingBubbles.size < 15) {
            val r = renderer.radius
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

    companion object {
        /**
         * One ring of bubbles, modelled after the bubble paddle: a translucent fill, a coloured
         * stroke ring and a small white highlight. They burst outward in a ring, then drift
         * chaotically (drag + jitter + buoyancy toward the player's "up"), each on its own random
         * lifespan, and finally pop into a scatter of specks before vanishing.
         *
         * This single burst is the building block: [gameobjects.puckstyle.skins.AxolotlSkin] fires it
         * on collisions/scores, and the victory loop in Logic spawns it repeatedly across the play
         * area to make the firework celebration.
         */
        fun spawnBubbleBurst(cx: Float, cy: Float, radius: Float, primary: Int, secondary: Int, isHigh: Boolean) {
            Effects.addPersistentEffect(BubbleBurst(cx, cy, radius, primary, secondary, isHigh))
        }
    }

    private class BubbleBurst(
        cx: Float, cy: Float,
        private val radius: Float,
        private val primary: Int,
        private val secondary: Int,
        isHigh: Boolean
    ) : Effects.PersistentEffect {

        private class Bubble(
            var x: Float, var y: Float,
            var vx: Float, var vy: Float,
            val size: Float,
            val maxLife: Int,
            val popFrames: Int,
            val seed: Float
        ) { var age = 0 }

        // Buoyancy lifts bubbles toward the owning player's "up". The high player's world is mirrored,
        // so their up is +y (screen-down); the low player's is -y.
        private val buoyancy = (if (isHigh) 1f else -1f) * radius * 0.004f
        private val bubbles: List<Bubble>
        private val maxAge: Int
        private var frame = 0
        override var isDone = false
            private set

        init {
            val count = 18
            val twoPi = 2f * PI.toFloat()
            bubbles = List(count) { i ->
                val angle = (i.toFloat() / count) * twoPi + (Random.nextFloat() - 0.5f) * 0.6f
                val speed = radius * (0.10f + Random.nextFloat() * 0.16f)
                Bubble(
                    cx, cy,
                    cos(angle) * speed, sin(angle) * speed,
                    radius * (0.10f + Random.nextFloat() * 0.14f),
                    24 + Random.nextInt(34),   // random lifespans
                    6,
                    Random.nextFloat() * twoPi
                )
            }
            maxAge = bubbles.maxOf { it.maxLife + it.popFrames }
        }

        override fun step() {
            frame++
            val jitter = radius * 0.012f
            for (b in bubbles) {
                b.age++
                if (b.age <= b.maxLife) {
                    b.x += b.vx; b.y += b.vy
                    b.vx = b.vx * 0.92f + (Random.nextFloat() - 0.5f) * jitter
                    b.vy = b.vy * 0.92f + (Random.nextFloat() - 0.5f) * jitter + buoyancy
                }
            }
            if (frame >= maxAge) isDone = true
        }

        override fun draw(scope: DrawScope) {
            val strokeW = Settings.strokeWidth * 0.4f
            for (b in bubbles) {
                if (b.age <= b.maxLife) {
                    // Alive: translucent fill + stroke ring + highlight, matching the bubble paddle.
                    scope.drawCircle(
                        Color(Palette.withAlpha(primary, 90)),
                        b.size, Offset(b.x, b.y)
                    )
                    scope.drawCircle(
                        Color(Palette.withAlpha(secondary, 220)),
                        b.size, Offset(b.x, b.y),
                        style = Stroke(width = strokeW)
                    )
                    val hl = b.size * 0.35f
                    scope.drawCircle(
                        Color.White.copy(alpha = 0.55f),
                        hl, Offset(b.x - b.size * 0.3f, b.y - b.size * 0.3f)
                    )
                } else {
                    // Popped: a scatter of specks expanding outward and fading to nothing.
                    val popT = ((b.age - b.maxLife).toFloat() / b.popFrames).coerceIn(0f, 1f)
                    val specks = 4
                    val speckR = (b.size * 0.30f * (1f - popT)).coerceAtLeast(0.5f)
                    val dist = b.size * (0.6f + popT * 2.2f)
                    val alpha = (220 * (1f - popT)).toInt().coerceIn(0, 255)
                    if (alpha > 0) {
                        val speckColor = Color(Palette.withAlpha(secondary, alpha))
                        for (k in 0 until specks) {
                            val a = b.seed + k * (2f * PI.toFloat() / specks)
                            scope.drawCircle(
                                speckColor, speckR,
                                Offset(b.x + cos(a) * dist, b.y + sin(a) * dist)
                            )
                        }
                    }
                }
            }
        }
    }
}
